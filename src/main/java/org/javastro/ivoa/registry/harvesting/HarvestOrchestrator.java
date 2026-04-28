package org.javastro.ivoa.registry.harvesting;

/*
 * Created on 28/04/2026 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.javastro.ivoa.entities.resource.Resource;
import org.javastro.ivoa.registry.Registry;
import org.javastro.ivoa.registry.XMLUtils;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Queue-based orchestrator for asynchronous incremental harvesting of IVOA registries.
 *
 * <h2>Architecture</h2>
 * <ul>
 *   <li>The RofR seed source is enrolled at first scheduler invocation.</li>
 *   <li>Each scheduler tick dequeues one pending source, harvests it
 *       incrementally (using the per-source {@code lastSuccessfulUntil} cursor),
 *       stores the records, and passes them to
 *       {@link RegistryDiscoveryService} to find new sources.</li>
 *   <li>Newly discovered sources are enqueued through the same queue.</li>
 *   <li>The queue is deduplicated: a source that is already {@code QUEUED} or
 *       {@code RUNNING} is never enqueued twice.</li>
 *   <li>All source state is persisted in BaseX via {@link HarvestSourceCatalog}
 *       and survives service restart.</li>
 * </ul>
 *
 * <p>Scheduling is controlled by the {@code ivoa.harvesting.rofr.cron} property
 * (default {@code "off"} = disabled).  Set it to a cron expression such as
 * {@code "0 0 * * * ?"} to enable periodic harvesting.</p>
 */
@ApplicationScoped
public class HarvestOrchestrator {

    @Inject
    Logger log;

    @Inject
    Registry registry;

    @Inject
    HarvestSourceCatalog catalog;

    @Inject
    RegistryDiscoveryService discoveryService;

    @ConfigProperty(name = "ivoa.harvesting.rofr.url", defaultValue = "https://rofr.ivoa.net/oai")
    String rofrUrl;

    @ConfigProperty(name = "ivoa.harvesting.discovery.enabled", defaultValue = "true")
    boolean discoveryEnabled;

    // -------------------------------------------------------------------------
    // Internal state
    // -------------------------------------------------------------------------

    /** Source keys waiting to be processed (FIFO, deduplicated). */
    private final ConcurrentLinkedDeque<String> queue = new ConcurrentLinkedDeque<>();
    /** True while a harvest job is executing. */
    private final AtomicBoolean running = new AtomicBoolean(false);
    /** Key of the source currently being harvested (for status reporting). */
    private final AtomicReference<String> currentSourceKey = new AtomicReference<>(null);
    /** Lazily initialized once after startup. */
    private final AtomicBoolean catalogInitialized = new AtomicBoolean(false);

    private final XMLUtils xmlUtils = new XMLUtils();

    // -------------------------------------------------------------------------
    // Scheduler entry point
    // -------------------------------------------------------------------------

    /**
     * Cron-driven queue processor.  Skips if a harvest is already running.
     * Initialises the catalog and enrolls the seed source on first invocation.
     */
    @Scheduled(cron = "{ivoa.harvesting.rofr.cron}")
    void processQueue() {
        ensureInitialized();
        if (!running.compareAndSet(false, true)) {
            log.debug("Harvest already running – skipping this tick");
            return;
        }
        try {
            String sourceKey = queue.poll();
            if (sourceKey == null) {
                // Nothing queued; check for ACTIVE sources that have never been run
                catalog.getAll().stream()
                        .filter(s -> s.getStatus() == SourceStatus.ACTIVE
                                && s.getLastAttempted() == null)
                        .findFirst()
                        .ifPresent(s -> {
                            queue.offer(s.getSourceKey());
                            processSourceKey(s.getSourceKey());
                        });
            } else {
                processSourceKey(sourceKey);
            }
        } finally {
            running.set(false);
            currentSourceKey.set(null);
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Enqueue a specific source for harvesting.
     *
     * @param sourceKey the key of a known source
     * @return {@code true} if the source was enqueued; {@code false} if it was already
     *         queued/running, unknown, disabled, or rejected.
     */
    public boolean enqueue(String sourceKey) {
        ensureInitialized();
        Optional<HarvestSource> opt = catalog.get(sourceKey);
        if (opt.isEmpty()) {
            log.warnv("enqueue: unknown source key {0}", sourceKey);
            return false;
        }
        HarvestSource s = opt.get();
        if (s.getStatus() == SourceStatus.QUEUED || s.getStatus() == SourceStatus.RUNNING) {
            log.debugv("enqueue: {0} already {1}", sourceKey, s.getStatus());
            return false;
        }
        if (s.getStatus() == SourceStatus.DISABLED || s.getStatus() == SourceStatus.REJECTED) {
            log.debugv("enqueue: {0} is {1} – skipping", sourceKey, s.getStatus());
            return false;
        }
        if (queue.contains(sourceKey)) {
            return false;
        }
        catalog.updateStatus(sourceKey, SourceStatus.QUEUED);
        queue.offer(sourceKey);
        log.infov("Enqueued source {0}", sourceKey);
        return true;
    }

    /**
     * Trigger an immediate, synchronous harvest of the seed (or a specific source).
     *
     * @param sourceKeyOrNull seed is used when {@code null}
     * @return number of records stored
     */
    public int triggerHarvest(String sourceKeyOrNull) {
        ensureInitialized();
        if (sourceKeyOrNull == null) {
            String seedKey = RegistryDiscoveryService.normalizeUrl(rofrUrl);
            if (!catalog.contains(seedKey)) {
                HarvestSource seed = HarvestSource.create(seedKey, "", rofrUrl, null, 0);
                seed.setStatus(SourceStatus.ACTIVE);
                catalog.upsert(seed);
            }
            catalog.updateStatus(seedKey, SourceStatus.ACTIVE);
            return processSourceKey(seedKey);
        }
        if (!catalog.contains(sourceKeyOrNull)) {
            log.warnv("triggerHarvest: unknown source {0}", sourceKeyOrNull);
            return 0;
        }
        catalog.updateStatus(sourceKeyOrNull, SourceStatus.ACTIVE);
        return processSourceKey(sourceKeyOrNull);
    }

    /**
     * Returns a snapshot of the current harvest status.
     */
    public HarvestStatus getStatus() {
        ensureInitialized();
        List<HarvestSource> all = catalog.getAll();
        long active = all.stream().filter(s -> s.getStatus() == SourceStatus.ACTIVE).count();
        long failed = all.stream().filter(s -> s.getStatus() == SourceStatus.FAILED).count();
        long rejected = all.stream().filter(s -> s.getStatus() == SourceStatus.REJECTED).count();
        return new HarvestStatus(
                running.get(),
                currentSourceKey.get(),
                List.copyOf(queue),
                all.size(),
                (int) active,
                queue.size(),
                (int) failed,
                (int) rejected);
    }

    /**
     * Returns all sources in the catalog.
     */
    public List<HarvestSource> getSources() {
        ensureInitialized();
        return catalog.getAll();
    }

    /**
     * Returns recent run records from all sources (most-recent first, bounded).
     */
    public List<HarvestRunRecord> getRecentRuns() {
        ensureInitialized();
        return catalog.getAll().stream()
                .flatMap(s -> s.getRecentRuns().stream())
                .sorted((a, b) -> {
                    if (a.getStartTime() == null) return 1;
                    if (b.getStartTime() == null) return -1;
                    return b.getStartTime().compareTo(a.getStartTime());
                })
                .limit(HarvestSource.MAX_RECENT_RUNS * 10L)
                .toList();
    }

    /**
     * Set the status of a source (e.g. to {@link SourceStatus#DISABLED}).
     *
     * @return {@code true} if the source was found, {@code false} otherwise.
     */
    public boolean setSourceStatus(String sourceKey, SourceStatus status) {
        ensureInitialized();
        if (!catalog.contains(sourceKey)) return false;
        catalog.updateStatus(sourceKey, status);
        return true;
    }

    /**
     * Enqueue the RofR seed source (idempotent).
     * If the seed is not yet in the catalog it is registered first.
     *
     * @return the seed source key, or {@code null} if enrollment failed.
     */
    public String enqueueSeed() {
        ensureInitialized();
        String seedKey = RegistryDiscoveryService.normalizeUrl(rofrUrl);
        if (!catalog.contains(seedKey)) {
            HarvestSource seed = HarvestSource.create(seedKey, "", rofrUrl, null, 0);
            seed.setStatus(SourceStatus.ACTIVE);
            catalog.upsert(seed);
        }
        return enqueue(seedKey) ? seedKey : null;
    }

    // -------------------------------------------------------------------------
    // Internal processing
    // -------------------------------------------------------------------------

    private int processSourceKey(String sourceKey) {
        currentSourceKey.set(sourceKey);
        catalog.updateStatus(sourceKey, SourceStatus.RUNNING);
        catalog.updateLastAttempted(sourceKey, Instant.now());

        Instant harvestStart = Instant.now();
        int stored = 0;
        int newSources = 0;
        String outcome = "SUCCESS";
        String details = "";

        HarvestSource source = catalog.get(sourceKey).orElse(null);
        if (source == null) {
            log.warnv("processSourceKey: source {0} vanished from catalog", sourceKey);
            return 0;
        }

        log.infov("Harvesting source {0} ({1}), incremental from {2}",
                sourceKey, source.getOaiUrl(),
                source.getLastSuccessfulUntil() != null
                        ? source.getLastSuccessfulUntil() : "beginning");

        try {
            HarvestClient client = new HarvestClient(source.getOaiUrl());
            if (!client.validate()) {
                log.errorv("Source {0} failed OAI-PMH validation", sourceKey);
                outcome = "FAILED";
                details = "OAI-PMH validation failed";
                catalog.updateStatus(sourceKey, SourceStatus.FAILED);
            } else {
                List<Resource> records = client.getRecords(source.getLastSuccessfulUntil(), null);
                for (Resource resource : records) {
                    try {
                        registry.getRegistryStoreInterface().create(xmlUtils.marshall(resource));
                        stored++;
                    } catch (Exception e) {
                        log.errorv("Failed to store resource {0}: {1}",
                                resource.getIdentifier(), e.getMessage());
                    }
                }

                Instant harvestEnd = Instant.now();
                catalog.updateCursor(sourceKey, harvestEnd);
                catalog.updateLastSuccessful(sourceKey, harvestEnd);
                catalog.updateStatus(sourceKey, SourceStatus.ACTIVE);
                log.infov("Source {0}: stored {1} records", sourceKey, stored);

                // Run discovery on the harvested records
                if (discoveryEnabled) {
                    List<String> newKeys = discoveryService.discoverNewSources(
                            records, sourceKey, source.getDepth(), catalog);
                    newSources = newKeys.size();
                    for (String newKey : newKeys) {
                        enqueue(newKey);
                    }
                }

                // Update identifier from identify() response if we don't have one yet
                if (source.getIdentifier() == null || source.getIdentifier().isEmpty()) {
                    try {
                        Resource identified = client.identify();
                        if (identified != null && identified.getIdentifier() != null
                                && !identified.getIdentifier().isEmpty()) {
                            HarvestSource updated = catalog.get(sourceKey).orElse(source);
                            updated.setIdentifier(identified.getIdentifier());
                            // Re-key if identifier gives a better key
                            String betterKey = RegistryDiscoveryService.deriveSourceKey(
                                    identified.getIdentifier(),
                                    RegistryDiscoveryService.normalizeUrl(source.getOaiUrl()));
                            if (!betterKey.equals(sourceKey)) {
                                updated.setSourceKey(betterKey);
                                catalog.upsert(updated);
                                // Note: old key remains in catalog as a duplicate until restart
                            } else {
                                catalog.upsert(updated);
                            }
                        }
                    } catch (Exception e) {
                        log.debugv("Could not update identifier for {0}: {1}",
                                sourceKey, e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            outcome = "FAILED";
            details = e.getMessage();
            log.errorv("Harvest of source {0} failed: {1}", sourceKey, e.getMessage());
            catalog.updateStatus(sourceKey, SourceStatus.FAILED);
        }

        catalog.appendRunRecord(sourceKey, new HarvestRunRecord(
                harvestStart, Instant.now(), stored, newSources, outcome, details));

        return stored;
    }

    // -------------------------------------------------------------------------
    // Lazy initialization
    // -------------------------------------------------------------------------

    private void ensureInitialized() {
        if (catalogInitialized.compareAndSet(false, true)) {
            try {
                catalog.init();
                // Re-enqueue any sources that were QUEUED or RUNNING at last shutdown
                catalog.getAll().stream()
                        .filter(s -> s.getStatus() == SourceStatus.QUEUED
                                || s.getStatus() == SourceStatus.RUNNING)
                        .forEach(s -> {
                            s.setStatus(SourceStatus.ACTIVE);
                            catalog.upsert(s);
                            queue.offer(s.getSourceKey());
                        });
                // Enroll the seed source if this is the first run
                String seedKey = RegistryDiscoveryService.normalizeUrl(rofrUrl);
                if (!catalog.contains(seedKey)) {
                    log.info("Enrolling RofR seed source");
                    HarvestSource seed = HarvestSource.create(seedKey, "", rofrUrl, null, 0);
                    seed.setStatus(SourceStatus.ACTIVE);
                    catalog.upsert(seed);
                    queue.offer(seedKey);
                    catalog.updateStatus(seedKey, SourceStatus.QUEUED);
                }
            } catch (Exception e) {
                catalogInitialized.set(false); // allow retry
                log.errorv("Orchestrator initialization failed: {0}", e.getMessage());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Status record
    // -------------------------------------------------------------------------

    /**
     * Snapshot of the harvesting queue and source catalog state.
     */
    public record HarvestStatus(
            boolean running,
            String currentSourceKey,
            List<String> queuedSourceKeys,
            int totalSources,
            int activeSources,
            int queuedCount,
            int failedSources,
            int rejectedSources) {
    }
}

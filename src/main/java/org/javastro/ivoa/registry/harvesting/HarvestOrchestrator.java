package org.javastro.ivoa.registry.harvesting;

/*
 * Created on 28/04/2026 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.javastro.ivoa.entities.resource.AccessURL;
import org.javastro.ivoa.entities.resource.Capability;
import org.javastro.ivoa.entities.resource.Resource;
import org.javastro.ivoa.entities.resource.registry.Harvest;
import org.javastro.ivoa.entities.resource.registry.OAIHTTP;
import org.javastro.ivoa.entities.oai.oaipmh.RecordType;
import org.javastro.ivoa.registry.Registry;
import org.javastro.ivoa.registry.XMLUtils;
import org.javastro.ivoa.registry.internal.HarvestSourceCatalog;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.URISyntaxException;
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
 *       stores the records, and parses them to
 *       find new sources.</li>
 *   <li>Newly discovered sources are enqueued through the same queue.</li>
 *   <li>The queue is deduplicated: a source that is already {@code QUEUED} or
 *       {@code RUNNING} is never enqueued twice.</li>
 *   <li>All source state is persisted in BaseX via {@link HarvestSourceCatalog}
 *       and survives service restart.</li>
 * </ul>
 *
 * <p>Scheduling is controlled by the {@code ivoa.harvesting.rofr.cron} property
 * (default {@code "off"} = disabled).  Set it to a cron expression such as
 * {@code "0 0/10 * * * ?"} to enable periodic harvesting.</p>
 */
@ApplicationScoped
public class HarvestOrchestrator {

    @Inject
    Logger log;

    @Inject
    Registry registry;

    @Inject
    HarvestSourceCatalog catalog;

    @ConfigProperty(name = "ivoa.harvesting.discovery.enabled", defaultValue = "true")
    boolean discoveryEnabled;

    @ConfigProperty(name = "ivoa.harvesting.discovery.max-sources", defaultValue = "100")
    int maxSources;

    @ConfigProperty(name = "ivoa.harvesting.discovery.max-depth", defaultValue = "3")
    int maxDepth;

    @ConfigProperty(name = "ivoa.harvesting.discovery.max-per-run", defaultValue = "5")
    int maxPerRun;

    @ConfigProperty(name = "ivoa.harvesting.discovery.doXMLValidation", defaultValue = "true")
    boolean doXMLValidation;

    static final String REGISTRY_STANDARD_ID_PREFIX = "ivo://ivoa.net/std/Registry";
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
    @Scheduled(cron = "{ivoa.harvesting.cron}")
    void processQueue() {
        ensureInitialized();
        if (!running.compareAndSet(false, true)) {
            log.warn("Harvest already running – skipping this tick");
            return;
        }
        log.info("Harvesting Run started");
        try {
            String sourceKey = queue.poll();
            if (sourceKey == null) {
                Comparator<HarvestSource> byLastAttempted = Comparator.comparing(HarvestSource::getLastAttempted,
                      Comparator.nullsFirst(Comparator.naturalOrder()));
                // Nothing queued; check for ACTIVE sources or any FAILED ones that have had a recent success
                catalog.getAll().stream()
                        .filter(s -> s.getStatus() == SourceStatus.ACTIVE ||
                              (s.getStatus() == SourceStatus.FAILED  &&
                                    s.getRecentRuns().stream().limit(5).anyMatch(r -> r.getOutcome().equals("SUCCESS")))
                               ).sorted(byLastAttempted).forEach(s -> {
                          queue.offer(s.getIdentifier());
                      });
            }
            while (!queue.isEmpty()) {
                String s = queue.poll();
                processSourceKey(s);
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
     * @param sourceKey seed is used when {@code null}
     * @return number of records stored
     */
    public int triggerHarvest(String sourceKey) {
        ensureInitialized();
        catalog.updateStatus(sourceKey, SourceStatus.ACTIVE);
        return processSourceKey(sourceKey);
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

    public void addSource(String key, String url) {
        catalog.upsert(HarvestSource.create(key, url, null, 0));
    }


    // -------------------------------------------------------------------------
    // Internal processing
    // -------------------------------------------------------------------------

    private int processSourceKey(String sourceKey) {
        currentSourceKey.set(sourceKey);
        catalog.updateStatus(sourceKey, SourceStatus.RUNNING);

        final Instant harvestStart = Instant.now();
        catalog.updateLastAttempted(sourceKey, harvestStart);
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
                source.getLastSuccessful() != null
                        ? source.getLastSuccessful() : "beginning");

        try {
            HarvestClient client = new HarvestClient(source.getOaiUrl(), doXMLValidation);
            if (!client.validate()) {
                log.errorv("Source {0} failed OAI-PMH validation", sourceKey);
                outcome = "FAILED";
                details = "OAI-PMH validation failed";
                catalog.updateStatus(sourceKey, SourceStatus.FAILED);
            } else {
                client.resetCursor();//should be done on creation, but just in case.
                do {
                    int stored_local=0;
                    List<RecordType> records = client.getRecords(source.getLastSuccessful(), null, source.getDiscoverySet());
                    final String path = makepath(sourceKey);
                    if (!registry.getRegistryStoreInterface().exists(path)) {
                        //TODO would be nice if the store functions actually returned the number of successful stores.
                        registry.getRegistryStoreInterface().create(xmlUtils.serializeRecords(records), path);
                        stored_local = records.size();
                    } else {
                        registry.getRegistryStoreInterface().createEntry(xmlUtils.serializeRecords(records), path);
                        stored_local = records.size();
                    }
                    log.infov("Source {0}: stored {1} records in this iteration", sourceKey, stored_local);
                    stored += stored_local;
                    // Run discovery on the harvested records
                    if (discoveryEnabled) {

                        List<Resource> resources = new ArrayList<>();
                        for (RecordType r : records) {
                            try {
                                Resource res = xmlUtils.OaiMetadataToResource(r);
                                resources.add(res);
                            } catch (Exception e) {
                                log.errorv("Failed to parse harvested record {0} ready for discovery: {1}",
                                      r.getHeader().getIdentifier(), e.getMessage());
                            }
                        }
                        List<String> newKeys = discoverNewSources(
                              resources, sourceKey, source.getDepth(), catalog);
                        newSources = newKeys.size();
                        for (String newKey : newKeys) {
                            enqueue(newKey);
                        }
                    }

                } while (client.hasMoreRecords());


                catalog.updateLastSuccessful(sourceKey, harvestStart);
                catalog.updateStatus(sourceKey, SourceStatus.ACTIVE);
                log.infov("Source {0}: stored {1} records in total", sourceKey, stored);


                // Update identifier from identify() response if we don't have one yet - TODO this is probably over complex - generated by AI
                if (source.getIdentifier() == null || source.getIdentifier().isEmpty()) {
                    try {
                        Resource identified = client.identify();
                        if (identified != null && identified.getIdentifier() != null
                                && !identified.getIdentifier().isEmpty()) {
                            HarvestSource updated = catalog.get(sourceKey).orElse(source);
                            updated.setIdentifier(identified.getIdentifier());
                            // Re-key if identifier gives a better key
                            String betterKey = deriveSourceKey(
                                    identified.getIdentifier(),
                                    normalizeUrl(source.getOaiUrl()));
                            if (!betterKey.equals(sourceKey)) {
                                updated.setIdentifier(betterKey);
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

    private String makepath(String sourceKey) {
         return "harvested/"+sourceKey.substring(6).replaceAll("[^a-z0-9]+", "_")+"/rec.xml";
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
                            queue.offer(s.getIdentifier());
                        });
            } catch (Exception e) {
                catalogInitialized.set(false); // allow retry
                log.errorv("Orchestrator initialization failed: {0}", e.getMessage());
            }
        }
    }

    /**
     * Inspect the list of harvested resources and register any newly discovered
     * harvestable registries in {@code catalog}.
     *
     * @param resources         records returned by a harvest run
     * @param fromSourceKey     the source key that produced these records (for provenance)
     * @param parentDepth       discovery depth of the producing source
     * @param catalog           the catalog to register new sources into
     * @return keys of sources that were <em>newly</em> accepted
     */
    public List<String> discoverNewSources(List<Resource> resources,
                                           String fromSourceKey,
                                           int parentDepth,
                                           HarvestSourceCatalog catalog) {
        List<String> accepted = new ArrayList<>();

        if (!discoveryEnabled) {
            log.debug("Discovery is disabled – skipping");
            return accepted;
        }

        int childDepth = parentDepth + 1;
        if (childDepth > maxDepth) {
            log.debugv("Max discovery depth {0} reached (parent depth {1})", maxDepth, parentDepth);
            return accepted;
        }

        for (Resource resource : resources) {
            if (accepted.size() >= maxPerRun) {
                log.debugv("Per-run discovery cap {0} reached", maxPerRun);
                break;
            }
            if (catalog.count() >= maxSources) { //FIXME - this is probably a bit silly
                log.infov("Source catalog cap {0} reached – stopping discovery", maxSources);
                break;
            }

            Optional<String> oaiUrl = findOaiUrl(resource);
            if (oaiUrl.isEmpty()) {
                continue; // not a harvestable registry
            }

            String candidateUrl = normalizeUrl(oaiUrl.get());
            String candidateId = resource.getIdentifier() != null ? resource.getIdentifier() : "";

            // Cycle / duplicate detection
            if (isSelf(fromSourceKey, candidateUrl, candidateId)) {
                log.debugv("Skipping self-reference: {0}", candidateUrl);
                recordRejection(catalog, candidateUrl, candidateId, fromSourceKey, childDepth,
                      "self-reference");
                continue;
            }

            String sourceKey = deriveSourceKey(candidateId, candidateUrl);

            if (catalog.contains(sourceKey)) {
                // Already known – update lastSeen but do not re-enqueue here
                HarvestSource existing = catalog.get(sourceKey).orElseThrow();
                catalog.upsert(existing);
                log.debugv("Duplicate source – already known: {0}", sourceKey);
                continue;
            }

            // Validate: connect to the endpoint before accepting
            log.infov("Validating candidate registry at {0}", candidateUrl);
            boolean valid;
            try {
                HarvestClient client = new HarvestClient(candidateUrl, doXMLValidation);
                valid = client.validate();
            } catch (Exception e) {
                valid = false;
                log.debugv("Validation of {0} threw: {1}", candidateUrl, e.getMessage());
            }

            if (!valid) {
                log.infov("Candidate {0} failed validation – rejected", candidateUrl);
                recordRejection(catalog, candidateUrl, candidateId, fromSourceKey, childDepth,
                      "validation failed");
                continue;
            }

            // Accept
            HarvestSource newSource = HarvestSource.create(candidateId, candidateUrl,
                  fromSourceKey, childDepth);
            catalog.upsert(newSource);
            accepted.add(sourceKey);
            log.infov("Discovered new harvest source: {0} (from {1}, depth {2})",
                  sourceKey, fromSourceKey, childDepth);
        }

        return accepted;
    }

    // -------------------------------------------------------------------------
    // Package-private helpers (tested directly)
    // -------------------------------------------------------------------------

    /**
     * Returns the OAI-PMH base URL if the resource is a harvestable registry
     * (has a {@link Harvest} capability with a {@link OAIHTTP} interface), or
     * {@link Optional#empty()} otherwise.
     */
    Optional<String> findOaiUrl(Resource resource) {
        if (!(resource instanceof org.javastro.ivoa.entities.resource.registry.Registry reg)) {
            return Optional.empty();
        }
        for (Capability cap : reg.getCapabilities()) {
            if (!(cap instanceof Harvest harvest)) {
                continue;
            }
            String stdId = harvest.getStandardID();
            if (stdId == null || !stdId.startsWith(REGISTRY_STANDARD_ID_PREFIX)) {
                continue;
            }
            for (Object iface : harvest.getInterfaces()) {
                if (iface instanceof OAIHTTP oaiHttp) {
                    for (AccessURL url : oaiHttp.getAccessURLs()) {
                        String val = url.getValue();
                        if (val != null && !val.isBlank()) {
                            return Optional.of(val.trim());
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Derive a stable source key: prefer the IVOA identifier, fall back to the
     * normalized OAI URL.
     */
    static String deriveSourceKey(String identifier, String normalizedOaiUrl) {
        if (identifier != null && !identifier.isBlank()
              && identifier.toLowerCase(Locale.ROOT).startsWith("ivo://")) {
            return identifier.toLowerCase(Locale.ROOT);
        }
        return normalizedOaiUrl;
    }

    /**
     * Normalize an OAI-PMH URL to a canonical form used for deduplication:
     * lower-case scheme+host+path, no trailing slash.
     */
    static String normalizeUrl(String url) {
        if (url == null) return "";
        try {
            URI uri = new URI(url.trim());
            String normalized = uri.getScheme().toLowerCase(Locale.ROOT)
                  + "://"
                  + uri.getHost().toLowerCase(Locale.ROOT)
                  + (uri.getPort() > 0 && uri.getPort() != 80 && uri.getPort() != 443
                  ? ":" + uri.getPort() : "")
                  + (uri.getPath() != null ? uri.getPath() : "");
            if (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            return normalized;
        } catch (URISyntaxException | NullPointerException e) {
            return url.trim().toLowerCase(Locale.ROOT);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private boolean isSelf(String fromSourceKey, String candidateUrl, String candidateId) {
        if (fromSourceKey == null) return false;
        String fromLower = fromSourceKey.toLowerCase(Locale.ROOT);
        return fromLower.equals(normalizeUrl(candidateUrl))
              || (!candidateId.isEmpty() && fromLower.equals(
              candidateId.toLowerCase(Locale.ROOT)));
    }

    private void recordRejection(HarvestSourceCatalog catalog,
                                 String candidateUrl, String candidateId,
                                 String fromSourceKey, int depth,
                                 String reason) {
        String key = deriveSourceKey(candidateId, candidateUrl);
        if (!catalog.contains(key)) {
            HarvestSource s = HarvestSource.create(key, candidateUrl,
                  fromSourceKey, depth);
            s.setStatus(SourceStatus.REJECTED);
            s.setRejectionReason(reason);
            catalog.upsert(s);
        }
    }

    public boolean resetSource(String key) {
        Optional<HarvestSource> opt = catalog.get(key);
        if (opt.isEmpty()) {
            log.warnv("resetSource: unknown source key {0}", key);
            return false;
        }
        HarvestSource s = opt.get();
        s.setLastSuccessful(null);
        s.setStatus(SourceStatus.ACTIVE);
        s.setRejectionReason(null);
        s.setRecentRuns(new ArrayList<>());
        catalog.upsert(s);
        registry.getRegistryStoreInterface().delete(makepath(s.getIdentifier()));
        queue.offer(key);
        log.infov("Reset and re-enqueued source {0}", key);
        return true;
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

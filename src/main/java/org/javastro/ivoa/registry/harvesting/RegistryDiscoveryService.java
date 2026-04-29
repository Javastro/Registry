package org.javastro.ivoa.registry.harvesting;

/*
 * Created on 28/04/2026 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.javastro.ivoa.entities.resource.AccessURL;
import org.javastro.ivoa.entities.resource.Capability;
import org.javastro.ivoa.entities.resource.Resource;
import org.javastro.ivoa.entities.resource.Service;
import org.javastro.ivoa.entities.resource.registry.Harvest;
import org.javastro.ivoa.entities.resource.registry.OAIHTTP;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Inspects harvested {@link Resource} objects to identify candidate harvestable
 * registries and registers new ones in the {@link HarvestSourceCatalog}.
 *
 * <h2>Discovery rules</h2>
 * <ol>
 *   <li>Only resources of type {@link org.javastro.ivoa.entities.resource.registry.Registry} are
 *       considered.</li>
 *   <li>The resource must have a {@link Harvest} capability whose
 *       {@code standardID} starts with {@code "ivo://ivoa.net/std/Registry"}.</li>
 *   <li>That capability must have at least one {@link OAIHTTP} interface with a
 *       non-empty access URL.</li>
 *   <li>The candidate is validated via {@link HarvestClient#validate()} before being
 *       accepted.</li>
 *   <li>The source catalog must not be full ({@code ivoa.harvesting.discovery.max-sources}).</li>
 *   <li>The discovery depth must not exceed {@code ivoa.harvesting.discovery.max-depth}.</li>
 *   <li>At most {@code ivoa.harvesting.discovery.max-per-run} new sources are accepted per
 *       call.</li>
 * </ol>
 *
 * <p>Cycle protection: a source whose normalized OAI URL <em>or</em> IVOA identifier
 * already exists in the catalog is treated as a duplicate and not re-added.</p>
 */
@ApplicationScoped
public class RegistryDiscoveryService {

    private static final Logger LOG = Logger.getLogger(RegistryDiscoveryService.class);

    static final String REGISTRY_STANDARD_ID_PREFIX = "ivo://ivoa.net/std/Registry";

    @ConfigProperty(name = "ivoa.harvesting.discovery.enabled", defaultValue = "true")
    boolean discoveryEnabled;

    @ConfigProperty(name = "ivoa.harvesting.discovery.max-sources", defaultValue = "50")
    int maxSources;

    @ConfigProperty(name = "ivoa.harvesting.discovery.max-depth", defaultValue = "3")
    int maxDepth;

    @ConfigProperty(name = "ivoa.harvesting.discovery.max-per-run", defaultValue = "5")
    int maxPerRun;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

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
            LOG.debug("Discovery is disabled – skipping");
            return accepted;
        }

        int childDepth = parentDepth + 1;
        if (childDepth > maxDepth) {
            LOG.debugv("Max discovery depth {0} reached (parent depth {1})", maxDepth, parentDepth);
            return accepted;
        }

        for (Resource resource : resources) {
            if (accepted.size() >= maxPerRun) {
                LOG.debugv("Per-run discovery cap {0} reached", maxPerRun);
                break;
            }
            if (catalog.count() >= maxSources) {
                LOG.infov("Source catalog cap {0} reached – stopping discovery", maxSources);
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
                LOG.debugv("Skipping self-reference: {0}", candidateUrl);
                recordRejection(catalog, candidateUrl, candidateId, fromSourceKey, childDepth,
                        "self-reference");
                continue;
            }

            String sourceKey = deriveSourceKey(candidateId, candidateUrl);

            if (catalog.contains(sourceKey)) {
                // Already known – update lastSeen but do not re-enqueue here
                HarvestSource existing = catalog.get(sourceKey).orElseThrow();
                existing.setLastSeen(java.time.Instant.now());
                catalog.upsert(existing);
                LOG.debugv("Duplicate source – already known: {0}", sourceKey);
                continue;
            }

            // Validate: connect to the endpoint before accepting
            LOG.infov("Validating candidate registry at {0}", candidateUrl);
            boolean valid;
            try {
                HarvestClient client = new HarvestClient(candidateUrl);
                valid = client.validate();
            } catch (Exception e) {
                valid = false;
                LOG.debugv("Validation of {0} threw: {1}", candidateUrl, e.getMessage());
            }

            if (!valid) {
                LOG.infov("Candidate {0} failed validation – rejected", candidateUrl);
                recordRejection(catalog, candidateUrl, candidateId, fromSourceKey, childDepth,
                        "validation failed");
                continue;
            }

            // Accept
            HarvestSource newSource = HarvestSource.create(sourceKey, candidateId, candidateUrl,
                    fromSourceKey, childDepth);
            catalog.upsert(newSource);
            accepted.add(sourceKey);
            LOG.infov("Discovered new harvest source: {0} (from {1}, depth {2})",
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
            HarvestSource s = HarvestSource.create(key, candidateId, candidateUrl,
                    fromSourceKey, depth);
            s.setStatus(SourceStatus.REJECTED);
            s.setRejectionReason(reason);
            catalog.upsert(s);
        }
    }
}

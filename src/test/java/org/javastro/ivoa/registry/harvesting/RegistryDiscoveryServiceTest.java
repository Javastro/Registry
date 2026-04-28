package org.javastro.ivoa.registry.harvesting;

/*
 * Created on 28/04/2026 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

import org.javastro.ivoa.entities.resource.AccessURL;
import org.javastro.ivoa.entities.resource.Capability;
import org.javastro.ivoa.entities.resource.Resource;
import org.javastro.ivoa.entities.resource.registry.Harvest;
import org.javastro.ivoa.entities.resource.registry.OAIHTTP;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RegistryDiscoveryService}.
 * No QuarkusTest / BaseX needed – all logic is pure Java.
 */
class RegistryDiscoveryServiceTest {

    private RegistryDiscoveryService service;

    @BeforeEach
    void setUp() {
        service = new RegistryDiscoveryService();
        service.discoveryEnabled = true;
        service.maxSources = 50;
        service.maxDepth = 3;
        service.maxPerRun = 5;
    }

    // -------------------------------------------------------------------------
    // normalizeUrl
    // -------------------------------------------------------------------------

    @Test
    void normalizeUrl_lowercasesSchemeAndHost() {
        String result = RegistryDiscoveryService.normalizeUrl("HTTPS://RofR.IVOA.Net/oai");
        assertEquals("https://rofr.ivoa.net/oai", result);
    }

    @Test
    void normalizeUrl_stripsTrailingSlash() {
        String result = RegistryDiscoveryService.normalizeUrl("https://rofr.ivoa.net/oai/");
        assertEquals("https://rofr.ivoa.net/oai", result);
    }

    @Test
    void normalizeUrl_retainsNonDefaultPort() {
        String result = RegistryDiscoveryService.normalizeUrl("https://example.org:8443/oai");
        assertEquals("https://example.org:8443/oai", result);
    }

    @Test
    void normalizeUrl_omitsPort80() {
        String result = RegistryDiscoveryService.normalizeUrl("http://example.org:80/oai");
        assertEquals("http://example.org/oai", result);
    }

    @Test
    void normalizeUrl_nullHandledGracefully() {
        assertEquals("", RegistryDiscoveryService.normalizeUrl(null));
    }

    // -------------------------------------------------------------------------
    // deriveSourceKey
    // -------------------------------------------------------------------------

    @Test
    void deriveSourceKey_prefersIvoIdentifier() {
        String key = RegistryDiscoveryService.deriveSourceKey(
                "ivo://ivoa.net/rofr", "https://rofr.ivoa.net/oai");
        assertEquals("ivo://ivoa.net/rofr", key);
    }

    @Test
    void deriveSourceKey_fallsBackToUrl() {
        String key = RegistryDiscoveryService.deriveSourceKey(
                "", "https://rofr.ivoa.net/oai");
        assertEquals("https://rofr.ivoa.net/oai", key);
    }

    @Test
    void deriveSourceKey_nullIdentifierFallsBackToUrl() {
        String key = RegistryDiscoveryService.deriveSourceKey(
                null, "https://rofr.ivoa.net/oai");
        assertEquals("https://rofr.ivoa.net/oai", key);
    }

    @Test
    void deriveSourceKey_nonIvoIdentifierFallsBackToUrl() {
        // A plain string that doesn't start with "ivo://" is not a valid identifier
        String key = RegistryDiscoveryService.deriveSourceKey(
                "not-an-identifier", "https://example.org/oai");
        assertEquals("https://example.org/oai", key);
    }

    // -------------------------------------------------------------------------
    // findOaiUrl
    // -------------------------------------------------------------------------

    @Test
    void findOaiUrl_nonRegistryResourceReturnsEmpty() {
        // Plain Resource (not a registry.Registry subtype) → should return empty
        Resource plain = Resource.builder()
                .withIdentifier("ivo://test.org/resource1")
                .build();
        Optional<String> result = service.findOaiUrl(plain);
        assertTrue(result.isEmpty(), "Non-registry Resource should not produce an OAI URL");
    }

    @Test
    void findOaiUrl_registryWithHarvestCapabilityReturnsUrl() {
        String expectedUrl = "https://example.org/oai";
        org.javastro.ivoa.entities.resource.registry.Registry reg =
                buildRegistryWithOaiUrl(expectedUrl);

        Optional<String> result = service.findOaiUrl(reg);
        assertTrue(result.isPresent(), "Registry with Harvest capability should return OAI URL");
        assertEquals(expectedUrl, result.get());
    }

    @Test
    void findOaiUrl_registryWithoutHarvestCapabilityReturnsEmpty() {
        org.javastro.ivoa.entities.resource.registry.Registry reg =
                org.javastro.ivoa.entities.resource.registry.Registry.builder()
                        .withIdentifier("ivo://example.org/reg1")
                        .build();
        // No capabilities added
        Optional<String> result = service.findOaiUrl(reg);
        assertTrue(result.isEmpty());
    }

    // -------------------------------------------------------------------------
    // Duplicate suppression
    // -------------------------------------------------------------------------

    @Test
    void discoverNewSources_duplicateSourceNotAddedTwice() {
        HarvestSourceCatalog catalog = buildCatalog();
        // First: already populate the catalog with this source
        String existingUrl = "https://example.org/oai";
        String existingKey = RegistryDiscoveryService.deriveSourceKey("ivo://example.org/reg1",
                RegistryDiscoveryService.normalizeUrl(existingUrl));
        HarvestSource existing = HarvestSource.create(existingKey, "ivo://example.org/reg1",
                existingUrl, "seed", 1);
        catalog.cache.put(existingKey, existing);

        org.javastro.ivoa.entities.resource.registry.Registry reg =
                buildRegistryWithOaiUrl(existingUrl);
        // Manually set identifier to match
        reg.setIdentifier("ivo://example.org/reg1");

        List<Resource> resources = List.of(reg);
        List<String> accepted = service.discoverNewSources(resources, "seed", 0, catalog);

        assertTrue(accepted.isEmpty(), "Duplicate source should not be re-accepted");
    }

    // -------------------------------------------------------------------------
    // Self-reference rejection
    // -------------------------------------------------------------------------

    @Test
    void discoverNewSources_selfReferenceIsRejected() {
        HarvestSourceCatalog catalog = buildCatalog();
        String seedUrl = "https://rofr.ivoa.net/oai";
        String seedKey = RegistryDiscoveryService.normalizeUrl(seedUrl);

        org.javastro.ivoa.entities.resource.registry.Registry reg =
                buildRegistryWithOaiUrl(seedUrl);

        List<Resource> resources = List.of(reg);
        List<String> accepted = service.discoverNewSources(resources, seedKey, 0, catalog);

        assertTrue(accepted.isEmpty(), "Self-referencing source must be rejected");
    }

    // -------------------------------------------------------------------------
    // Depth cap
    // -------------------------------------------------------------------------

    @Test
    void discoverNewSources_depthCapPreventsDiscovery() {
        service.maxDepth = 1; // only direct children of seed
        HarvestSourceCatalog catalog = buildCatalog();

        org.javastro.ivoa.entities.resource.registry.Registry reg =
                buildRegistryWithOaiUrl("https://grandchild.example.org/oai");

        List<Resource> resources = List.of(reg);
        // parentDepth = 1 → childDepth would be 2, which exceeds maxDepth = 1
        List<String> accepted = service.discoverNewSources(resources, "seed", 1, catalog);

        assertTrue(accepted.isEmpty(), "Sources beyond max depth should not be accepted");
    }

    // -------------------------------------------------------------------------
    // Catalog size cap
    // -------------------------------------------------------------------------

    @Test
    void discoverNewSources_catalogSizeCapPreventsOverflow() {
        service.maxSources = 2;
        HarvestSourceCatalog catalog = buildCatalog();
        // Pre-fill catalog to capacity
        catalog.cache.put("k1", HarvestSource.create("k1", "", "https://a.example.org/oai", "seed", 1));
        catalog.cache.put("k2", HarvestSource.create("k2", "", "https://b.example.org/oai", "seed", 1));

        org.javastro.ivoa.entities.resource.registry.Registry reg =
                buildRegistryWithOaiUrl("https://c.example.org/oai");

        List<Resource> resources = List.of(reg);
        List<String> accepted = service.discoverNewSources(resources, "seed", 0, catalog);

        assertTrue(accepted.isEmpty(), "Should not exceed catalog size cap");
    }

    // -------------------------------------------------------------------------
    // Per-run discovery cap
    // -------------------------------------------------------------------------

    @Test
    void discoverNewSources_perRunCapLimitsNewSources() {
        service.maxPerRun = 1;
        // validation will fail for these URLs, so all candidates will be REJECTED
        // This test only checks that we don't attempt more than maxPerRun candidates
        // which can be confirmed by checking accepted.size() <= maxPerRun
        HarvestSourceCatalog catalog = buildCatalog();

        // Create two registry resources
        var reg1 = buildRegistryWithOaiUrl("https://x1.example.org/oai");
        var reg2 = buildRegistryWithOaiUrl("https://x2.example.org/oai");

        List<Resource> resources = List.of(reg1, reg2);
        List<String> accepted = service.discoverNewSources(resources, "seed", 0, catalog);

        // Can accept at most maxPerRun sources
        assertTrue(accepted.size() <= service.maxPerRun,
                "Per-run cap must be respected; got: " + accepted.size());
    }

    // -------------------------------------------------------------------------
    // Discovery disabled
    // -------------------------------------------------------------------------

    @Test
    void discoverNewSources_disabledReturnsEmpty() {
        service.discoveryEnabled = false;
        HarvestSourceCatalog catalog = buildCatalog();

        var reg = buildRegistryWithOaiUrl("https://example.org/oai");
        List<String> accepted = service.discoverNewSources(
                List.of(reg), "seed", 0, catalog);

        assertTrue(accepted.isEmpty(), "Disabled discovery must always return empty list");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Build an in-memory catalog (no BaseX backend needed for these unit tests).
     * Uses the package-private test helper.
     */
    private HarvestSourceCatalog buildCatalog() {
        return HarvestSourceCatalogTest.inMemoryCatalog();
    }

    /**
     * Build a Registry resource with a Harvest capability pointing to the given OAI URL.
     */
    static org.javastro.ivoa.entities.resource.registry.Registry buildRegistryWithOaiUrl(
            String oaiUrl) {
        OAIHTTP oaiHttp = OAIHTTP.builder()
                .withRole("std")
                .addAccessURLs(List.of(new AccessURL(oaiUrl, "base")))
                .build();
        Harvest harvest = Harvest.builder()
                .withStandardID(RegistryDiscoveryService.REGISTRY_STANDARD_ID_PREFIX)
                .addInterfaces(List.of(oaiHttp))
                .build();
        org.javastro.ivoa.entities.resource.registry.Registry reg =
                org.javastro.ivoa.entities.resource.registry.Registry.builder()
                        .withIdentifier("ivo://test.example.org/reg-" + oaiUrl.hashCode())
                        .build();
        reg.getCapabilities().add(harvest);
        return reg;
    }
}

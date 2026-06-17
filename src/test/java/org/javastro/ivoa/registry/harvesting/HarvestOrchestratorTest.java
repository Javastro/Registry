package org.javastro.ivoa.registry.harvesting;

/*
 * Created on 28/04/2026 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.javastro.ivoa.registry.internal.HarvestSourceCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link HarvestOrchestrator} and the admin harvest endpoints.
 *
 * <p>Network calls to RofR are not made in these tests (the cron is disabled
 * by default and all direct-harvest calls would attempt a network connection,
 * so the assertions focus on the queue/catalog state and HTTP status codes).</p>
 */
@QuarkusTest
class HarvestOrchestratorTest {

    @Inject
    HarvestOrchestrator orchestrator;

    @Inject
    HarvestSourceCatalog catalog;

    // -------------------------------------------------------------------------
    // GET /harvesting/harvest/status – no auth required by test (update role enforced)
    // -------------------------------------------------------------------------

    @Test
    void adminHarvestStatus_returns200WithAuth() {
        given()
                .auth().basic("admin", "passwordchangeme")
                .when()
                .get("/harvesting/harvest/status")
                .then()
                .statusCode(200)
                .contentType(containsString("application/json"))
                .body("totalSources", greaterThanOrEqualTo(0));
    }

    @Test
    void adminHarvestStatus_returns401WithoutAuth() {
        given()
                .when()
                .get("/harvesting/harvest/status")
                .then()
                .statusCode(401);
    }

    // -------------------------------------------------------------------------
    // GET /harvesting/harvest/sources
    // -------------------------------------------------------------------------

    @Test
    void adminHarvestSources_returns200WithAuth() {
        given()
                .auth().basic("admin", "passwordchangeme")
                .when()
                .get("/harvesting/harvest/sources")
                .then()
                .statusCode(200)
                .contentType(containsString("application/json"));
    }

    // -------------------------------------------------------------------------
    // GET /harvesting/harvest/runs
    // -------------------------------------------------------------------------

    @Test
    void adminHarvestRuns_returns200WithAuth() {
        given()
                .auth().basic("admin", "passwordchangeme")
                .when()
                .get("/harvesting/harvest/runs")
                .then()
                .statusCode(200)
                .contentType(containsString("application/json"));
    }

    // -------------------------------------------------------------------------
    // POST /admin/harvest – enqueue seed
    // -------------------------------------------------------------------------

    @Test
    void adminHarvest_enqueueReturns202() {
        given()
                .auth().basic("admin", "passwordchangeme")
                .when()
                .post("/harvesting/harvest")
                .then()
                .statusCode(202)
                .body("status", anyOf(is("enqueued"), is("skipped")));
    }

    // -------------------------------------------------------------------------
    // POST /harvesting/harvest/source/{key}/disable
    // -------------------------------------------------------------------------

    @Test
    void adminDisableUnknownSource_returns404() {
        given()
                .auth().basic("admin", "passwordchangeme")
                .when()
                .post("/harvesting/harvest/source/unknown-key-xyz/disable")
                .then()
                .statusCode(404);
    }

    // -------------------------------------------------------------------------
    // Orchestrator unit-level tests (CDI-injected, no HTTP)
    // -------------------------------------------------------------------------

    @Test
    void orchestratorStatus_isNeverNull() {
        HarvestOrchestrator.HarvestStatus status = orchestrator.getStatus();
        assertNotNull(status);
        assertNotNull(status.queuedSourceKeys());
    }

    @Test
    void orchestratorSources_returnsNonNullList() {
        List<HarvestSource> sources = orchestrator.getSources();
        assertNotNull(sources);
    }

    @Test
    void orchestratorEnqueue_unknownKeyReturnsFalse() {
        boolean result = orchestrator.enqueue("ivo://nonexistent.key/registry");
        assertFalse(result, "Enqueueing an unknown source key should return false");
    }

    @Test
    void orchestratorSetSourceStatus_unknownKeyReturnsFalse() {
        boolean found = orchestrator.setSourceStatus("ivo://nonexistent.key/registry",
                SourceStatus.DISABLED);
        assertFalse(found);
    }

    // -------------------------------------------------------------------------
    // Catalog unit-level tests (in-memory – bypasses BaseX for assertions)
    // -------------------------------------------------------------------------

    @Test
    void catalogUpsert_idempotentForSameKey() {
        HarvestSourceCatalog inMem = new HarvestSourceCatalog(new MockRegistryStore());
        HarvestSource s1 = HarvestSource.create( "ivo://example.org/r1",
                "https://example.org/oai", null, 0);
        inMem.upsert(s1);
        inMem.upsert(s1); // second upsert with same key

        assertEquals(1, inMem.count(), "Upsert must be idempotent for the same source key");
    }

    @Test
    void catalogUpsert_preservesProvenance() {
        HarvestSourceCatalog inMem = new HarvestSourceCatalog(new MockRegistryStore());
        HarvestSource original = HarvestSource.create(
                "ivo://example.org/r2", "https://example.org/oai", "seedKey", 1);
        inMem.upsert(original);

        // Re-upsert with different discoveredFromSourceKey
        HarvestSource updated = HarvestSource.create(
                "ivo://example.org/r2", "https://example.org/oai", "otherParent", 99);
        inMem.upsert(updated);

        HarvestSource stored = inMem.get("ivo://example.org/r2").orElseThrow();
        assertEquals("seedKey", stored.getDiscoveredFromSourceKey(),
                "Provenance (discoveredFromSourceKey) must be preserved on re-upsert");
        assertEquals(1, stored.getDepth(), "Depth must be preserved on re-upsert");
    }

    @Test
    void catalogUpdateStatus_persistsChange() {
        HarvestSourceCatalog inMem = new HarvestSourceCatalog(new MockRegistryStore());
        inMem.upsert(HarvestSource.create("k-status", "", "https://s.example.org/oai", 0));
        inMem.updateStatus("k-status", SourceStatus.FAILED);

        assertEquals(SourceStatus.FAILED, inMem.get("k-status").orElseThrow().getStatus());
    }

    @Test
    void harvestSourceRunHistory_boundedToMax() {
        HarvestSource s = HarvestSource.create("k-history", "", "https://h.example.org/oai",
                 0);
        for (int i = 0; i < HarvestSource.MAX_RECENT_RUNS + 3; i++) {
            s.addRunRecord(new HarvestRunRecord(
                    java.time.Instant.now(), java.time.Instant.now(), i, 0, "SUCCESS", ""));
        }
        assertEquals(HarvestSource.MAX_RECENT_RUNS, s.getRecentRuns().size(),
                "Run history must be bounded to MAX_RECENT_RUNS");
    }
}

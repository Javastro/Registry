package org.javastro.ivoa.registry.harvesting;

/*
 * Created on 28/04/2026 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

/**
 * Test helper that exposes the package-private {@link HarvestSourceCatalog#initialized}
 * flag so unit tests can bypass the BaseX-backed initialisation.
 */
final class HarvestSourceCatalogTest {

    private HarvestSourceCatalogTest() {
    }

    /**
     * Mark a catalog as already initialized so that {@code ensureInit()} does not
     * attempt to connect to BaseX.
     */
    static void setInitialized(HarvestSourceCatalog catalog) {
        catalog.initialized.set(true);
    }

    /**
     * Create an in-memory-only catalog with no BaseX backend.
     * The {@code persist()} method is overridden with a no-op so tests that
     * call mutating catalog operations do not attempt to write to a database.
     */
    static HarvestSourceCatalog inMemoryCatalog() {
        HarvestSourceCatalog cat = new HarvestSourceCatalog() {
            @Override
            void persist() {
                // no-op: tests are in-memory only, no BaseX access
            }
        };
        cat.initialized.set(true);
        return cat;
    }
}

package org.javastro.ivoa.registry.harvesting;

/*
 * Created on 28/04/2026 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

import java.util.concurrent.atomic.AtomicBoolean;

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
     * Create an in-memory-only catalog (no BaseX backend).
     */
    static HarvestSourceCatalog inMemoryCatalog() {
        HarvestSourceCatalog cat = new HarvestSourceCatalog();
        setInitialized(cat);
        return cat;
    }
}

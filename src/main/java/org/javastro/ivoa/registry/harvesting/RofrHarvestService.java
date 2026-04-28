package org.javastro.ivoa.registry.harvesting;

/*
 * Created on 28/04/2026 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Facade kept for backward compatibility.  All actual work is delegated to
 * {@link HarvestOrchestrator}, which owns the scheduler, queue, and incremental
 * cursor persistence.
 *
 * @deprecated Use {@link HarvestOrchestrator} directly.
 */
@ApplicationScoped
public class RofrHarvestService {

    @Inject
    HarvestOrchestrator orchestrator;

    @Inject
    Logger log;

    /**
     * Triggers an immediate incremental harvest of the RofR seed source via the
     * orchestrator.
     *
     * @return number of records stored.
     */
    public int harvest() {
        log.info("RofrHarvestService.harvest() delegating to HarvestOrchestrator");
        return orchestrator.triggerHarvest(null);
    }
}

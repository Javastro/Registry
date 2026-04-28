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
import java.util.List;

/**
 * Scheduled service that incrementally harvests resource records from the
 * IVOA Registry of Registries (RofR) and stores them in the local registry.
 *
 * <p>Scheduling is controlled via the {@code ivoa.harvesting.rofr.cron} configuration
 * property (defaults to {@code off}, i.e. disabled).  Set it to a Quarkus-style cron
 * expression such as {@code "0 0 * * * ?"} to enable periodic runs.</p>
 *
 * <p>Each run is <em>incremental</em>: only records created or modified since the
 * previous successful harvest are requested from RofR.  A {@code null} harvest
 * timestamp triggers a full harvest on the first execution.</p>
 */
@ApplicationScoped
public class RofrHarvestService {

    @Inject
    Registry registry;

    @Inject
    Logger log;

    @ConfigProperty(name = "ivoa.harvesting.rofr.url", defaultValue = "https://rofr.ivoa.net/oai")
    String rofrUrl;

    /** Timestamp of the last successful harvest; {@code null} triggers a full harvest. */
    private volatile Instant lastHarvestTime = null;

    private final XMLUtils xmlUtils = new XMLUtils();

    /**
     * Scheduled entry point.  The cron expression is read from
     * {@code ivoa.harvesting.rofr.cron}; the default value {@code "off"} disables
     * automatic execution.
     */
    @Scheduled(cron = "{ivoa.harvesting.rofr.cron}")
    void scheduledHarvest() {
        harvest();
    }

    /**
     * Perform an incremental harvest from RofR.  Can also be called directly
     * (e.g. from an admin endpoint) to trigger an immediate harvest.
     *
     * @return the number of records successfully stored during this run.
     */
    public int harvest() {
        Instant harvestFrom = lastHarvestTime;
        Instant harvestStart = Instant.now();
        int stored = 0;

        log.infov("Starting harvest from RofR ({0}), incremental from {1}", rofrUrl,
                harvestFrom != null ? harvestFrom.toString() : "beginning");

        HarvestClient client = new HarvestClient(rofrUrl);
        try {
            if (!client.validate()) {
                log.error("RofR endpoint failed validation – harvest aborted");
                return 0;
            }

            List<Resource> records = client.getRecords(harvestFrom, null);
            for (Resource resource : records) {
                try {
                    registry.getRegistryStoreInterface().create(xmlUtils.marshall(resource));
                    stored++;
                } catch (Exception e) {
                    log.errorv("Failed to store resource {0}: {1}",
                            resource.getIdentifier(), e.getMessage());
                }
            }

            lastHarvestTime = harvestStart;
            log.infov("Harvest complete: stored {0} records from RofR", stored);
        } catch (Exception e) {
            log.errorv("Harvest from RofR failed: {0}", e.getMessage());
        }

        return stored;
    }

    /**
     * Returns the timestamp of the most recent successful harvest, or
     * {@code null} if no harvest has been completed yet.
     */
    public Instant getLastHarvestTime() {
        return lastHarvestTime;
    }
}

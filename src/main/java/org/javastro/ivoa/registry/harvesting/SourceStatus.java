package org.javastro.ivoa.registry.harvesting;

/*
 * Created on 28/04/2026 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

/**
 * Lifecycle status for a known harvest source.
 */
public enum SourceStatus {
    /** Source is enabled and eligible for queuing. */
    ACTIVE,
    /** Source has been submitted to the work queue and is waiting to be processed. */
    QUEUED,
    /** A harvest of this source is currently in progress. */
    RUNNING,
    /** Last harvest attempt failed. Source will be retried on the next scheduler cycle. */
    FAILED,
    /** Source was inspected and found not to be a valid harvestable registry. */
    REJECTED,
    /** Source has been manually disabled by an operator. */
    DISABLED,
   CACHED,
   /** Source has been capped by a discovery policy limit (depth, catalog size, etc.). */
    LIMITED
}

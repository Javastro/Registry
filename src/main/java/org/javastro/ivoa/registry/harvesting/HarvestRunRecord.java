package org.javastro.ivoa.registry.harvesting;

/*
 * Created on 28/04/2026 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import java.time.Instant;

/**
 * A single bounded run record stored inside a {@link HarvestSource}.
 * At most {@link HarvestSource#MAX_RECENT_RUNS} of these are retained per source.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(propOrder = {
        "startTime", "endTime", "recordsStored", "newSourcesDiscovered", "outcome", "details"
})
public class HarvestRunRecord {

    @XmlJavaTypeAdapter(InstantXmlAdapter.class)
    private Instant startTime;
    @XmlJavaTypeAdapter(InstantXmlAdapter.class)
    private Instant endTime;
    private int recordsStored;
    private int newSourcesDiscovered;
    /** {@code "SUCCESS"} or {@code "FAILED"}. */
    private String outcome;
    private String details;

    public HarvestRunRecord() {
    }

    public HarvestRunRecord(Instant startTime, Instant endTime,
                             int recordsStored, int newSourcesDiscovered,
                             String outcome, String details) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.recordsStored = recordsStored;
        this.newSourcesDiscovered = newSourcesDiscovered;
        this.outcome = outcome;
        this.details = details;
    }

    public Instant getStartTime() { return startTime; }
    public void setStartTime(Instant startTime) { this.startTime = startTime; }

    public Instant getEndTime() { return endTime; }
    public void setEndTime(Instant endTime) { this.endTime = endTime; }

    public int getRecordsStored() { return recordsStored; }
    public void setRecordsStored(int recordsStored) { this.recordsStored = recordsStored; }

    public int getNewSourcesDiscovered() { return newSourcesDiscovered; }
    public void setNewSourcesDiscovered(int newSourcesDiscovered) {
        this.newSourcesDiscovered = newSourcesDiscovered;
    }

    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}


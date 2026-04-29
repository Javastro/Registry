package org.javastro.ivoa.registry.harvesting;

/*
 * Created on 28/04/2026 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a known harvest source (a remote OAI-PMH registry endpoint).
 *
 * <p>The {@code sourceKey} uniquely identifies a source.  For the RofR seed it is
 * the normalized OAI URL; for discovered sources it is the IVOA identifier when
 * available, otherwise the normalized OAI URL.</p>
 */
@XmlRootElement(name = "source")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(propOrder = {
        "sourceKey", "identifier", "oaiUrl", "status",
        "discoveredFromSourceKey", "depth",
        "firstSeen", "lastSeen", "lastAttempted",
        "lastSuccessful", "lastSuccessfulUntil",
        "rejectionReason", "recentRuns"
})
public class HarvestSource {

    /** Maximum number of recent run records retained per source. */
    public static final int MAX_RECENT_RUNS = 5;

    /** Stable unique key for this source (IVOA identifier or normalized OAI URL). */
    private String sourceKey;
    /** IVOA identifier from Identify response, may be empty until first successful identify. */
    private String identifier;
    /** Bare OAI-PMH endpoint URL. */
    private String oaiUrl;
    private SourceStatus status;
    /** {@code null} for the seed source. */
    private String discoveredFromSourceKey;
    /** Hop distance from the seed (0 = seed). */
    private int depth;

    @XmlJavaTypeAdapter(InstantXmlAdapter.class)
    private Instant firstSeen;
    @XmlJavaTypeAdapter(InstantXmlAdapter.class)
    private Instant lastSeen;
    @XmlJavaTypeAdapter(InstantXmlAdapter.class)
    private Instant lastAttempted;
    @XmlJavaTypeAdapter(InstantXmlAdapter.class)
    private Instant lastSuccessful;
    /**
     * The upper bound of the time range used in the last successful harvest.
     * Used as the {@code from} parameter on the next incremental harvest.
     */
    @XmlJavaTypeAdapter(InstantXmlAdapter.class)
    private Instant lastSuccessfulUntil;
    /** Non-null when status is {@link SourceStatus#REJECTED} or {@link SourceStatus#LIMITED}. */
    private String rejectionReason;
    /** Bounded recent run history – at most {@link #MAX_RECENT_RUNS} entries. */
    @XmlElementWrapper(name = "recentRuns")
    @XmlElement(name = "run")
    private List<HarvestRunRecord> recentRuns = new ArrayList<>();

    public HarvestSource() {
    }

    /**
     * Factory for creating a new source entry.
     */
    public static HarvestSource create(String sourceKey, String identifier, String oaiUrl,
                                        String discoveredFromSourceKey, int depth) {
        HarvestSource s = new HarvestSource();
        s.sourceKey = sourceKey;
        s.identifier = identifier != null ? identifier : "";
        s.oaiUrl = oaiUrl;
        s.status = SourceStatus.QUEUED;
        s.discoveredFromSourceKey = discoveredFromSourceKey != null ? discoveredFromSourceKey : "";
        s.depth = depth;
        Instant now = Instant.now();
        s.firstSeen = now;
        s.lastSeen = now;
        return s;
    }

    /** Append a run record and trim to {@link #MAX_RECENT_RUNS}. */
    public void addRunRecord(HarvestRunRecord record) {
        recentRuns.add(0, record); // most-recent first
        if (recentRuns.size() > MAX_RECENT_RUNS) {
            recentRuns = new ArrayList<>(recentRuns.subList(0, MAX_RECENT_RUNS));
        }
    }

    // --- getters / setters ---------------------------------------------------

    public String getSourceKey() { return sourceKey; }
    public void setSourceKey(String sourceKey) { this.sourceKey = sourceKey; }

    public String getIdentifier() { return identifier; }
    public void setIdentifier(String identifier) { this.identifier = identifier; }

    public String getOaiUrl() { return oaiUrl; }
    public void setOaiUrl(String oaiUrl) { this.oaiUrl = oaiUrl; }

    public SourceStatus getStatus() { return status; }
    public void setStatus(SourceStatus status) { this.status = status; }

    public String getDiscoveredFromSourceKey() { return discoveredFromSourceKey; }
    public void setDiscoveredFromSourceKey(String discoveredFromSourceKey) {
        this.discoveredFromSourceKey = discoveredFromSourceKey;
    }

    public int getDepth() { return depth; }
    public void setDepth(int depth) { this.depth = depth; }

    public Instant getFirstSeen() { return firstSeen; }
    public void setFirstSeen(Instant firstSeen) { this.firstSeen = firstSeen; }

    public Instant getLastSeen() { return lastSeen; }
    public void setLastSeen(Instant lastSeen) { this.lastSeen = lastSeen; }

    public Instant getLastAttempted() { return lastAttempted; }
    public void setLastAttempted(Instant lastAttempted) { this.lastAttempted = lastAttempted; }

    public Instant getLastSuccessful() { return lastSuccessful; }
    public void setLastSuccessful(Instant lastSuccessful) { this.lastSuccessful = lastSuccessful; }

    public Instant getLastSuccessfulUntil() { return lastSuccessfulUntil; }
    public void setLastSuccessfulUntil(Instant lastSuccessfulUntil) {
        this.lastSuccessfulUntil = lastSuccessfulUntil;
    }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public List<HarvestRunRecord> getRecentRuns() { return recentRuns; }
    public void setRecentRuns(List<HarvestRunRecord> recentRuns) { this.recentRuns = recentRuns; }
}


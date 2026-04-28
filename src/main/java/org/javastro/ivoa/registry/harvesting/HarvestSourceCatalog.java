package org.javastro.ivoa.registry.harvesting;

/*
 * Created on 28/04/2026 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

import jakarta.enterprise.context.ApplicationScoped;
import org.basex.core.BaseXException;
import org.basex.core.cmd.XQuery;
import org.basex.query.QueryException;
import org.basex.query.QueryProcessor;
import org.basex.query.value.Value;
import org.javastro.ivoa.registry.internal.BaseXStoreBase;
import org.jboss.logging.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Persistent catalog of known harvest sources, backed by a BaseX XML document at
 * {@code Registry/harvest/sources.xml}.
 *
 * <p>An in-memory {@link ConcurrentHashMap} is kept in sync with BaseX so that
 * hot-path reads never need a database round-trip.  All mutating operations
 * persist the full catalog document back to BaseX immediately.</p>
 */
@ApplicationScoped
public class HarvestSourceCatalog extends BaseXStoreBase {

    private static final Logger LOG = Logger.getLogger(HarvestSourceCatalog.class);

    static final String CATALOG_PATH = "harvest/sources.xml";

    final ConcurrentHashMap<String, HarvestSource> cache = new ConcurrentHashMap<>();
    final AtomicBoolean initialized = new AtomicBoolean(false);

    private DocumentBuilder docBuilder;

    public HarvestSourceCatalog() {
        super();
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(false);
            dbf.setValidating(false);
            docBuilder = dbf.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException("Cannot create DOM builder", e);
        }
    }

    // -------------------------------------------------------------------------
    // Initialisation
    // -------------------------------------------------------------------------

    /**
     * Idempotent initialisation.  Creates the catalog document in BaseX if
     * it does not yet exist, then loads any previously persisted sources into
     * the in-memory cache.
     */
    public synchronized void init() {
        if (!initialized.compareAndSet(false, true)) {
            return;
        }
        try {
            String initQ = "if (not(db:exists(\"Registry\",\"" + CATALOG_PATH + "\"))) "
                    + "then db:put(\"Registry\", <harvestSources/>, \"" + CATALOG_PATH + "\") "
                    + "else ()";
            try (QueryProcessor p = new QueryProcessor(initQ, context)) {
                p.value();
            }

            String xml = new XQuery(
                    "serialize(db:get(\"Registry\",\"" + CATALOG_PATH + "\"))").execute(context);
            if (xml != null && !xml.isBlank()) {
                loadFromXml(xml);
            }
            LOG.infov("Source catalog initialized – {0} source(s) loaded", cache.size());
        } catch (QueryException | BaseXException e) {
            LOG.warnv("Could not initialize source catalog: {0}", e.getMessage());
            initialized.set(false); // allow retry
        }
    }

    private void ensureInit() {
        if (!initialized.get()) {
            init();
        }
    }

    // -------------------------------------------------------------------------
    // Public catalog operations
    // -------------------------------------------------------------------------

    /**
     * Insert or update a source.
     * If a source with the same {@code sourceKey} already exists its {@code status},
     * {@code lastSeen}, {@code identifier}, and {@code oaiUrl} fields are refreshed;
     * provenance ({@code discoveredFromSourceKey}, {@code depth}, {@code firstSeen})
     * is preserved.
     *
     * @return the stored source (after merge)
     */
    public synchronized HarvestSource upsert(HarvestSource source) {
        ensureInit();
        HarvestSource existing = cache.get(source.getSourceKey());
        if (existing != null) {
            // idempotent refresh – preserve provenance
            existing.setLastSeen(Instant.now());
            if (source.getIdentifier() != null && !source.getIdentifier().isEmpty()) {
                existing.setIdentifier(source.getIdentifier());
            }
            if (source.getOaiUrl() != null) {
                existing.setOaiUrl(source.getOaiUrl());
            }
            cache.put(existing.getSourceKey(), existing);
            persist();
            return existing;
        }
        source.setFirstSeen(Instant.now());
        source.setLastSeen(Instant.now());
        cache.put(source.getSourceKey(), source);
        persist();
        return source;
    }

    public synchronized Optional<HarvestSource> get(String sourceKey) {
        ensureInit();
        return Optional.ofNullable(cache.get(sourceKey));
    }

    public synchronized List<HarvestSource> getAll() {
        ensureInit();
        return List.copyOf(cache.values());
    }

    public synchronized int count() {
        ensureInit();
        return cache.size();
    }

    public synchronized boolean contains(String sourceKey) {
        ensureInit();
        return cache.containsKey(sourceKey);
    }

    public synchronized void updateStatus(String sourceKey, SourceStatus status) {
        ensureInit();
        HarvestSource s = cache.get(sourceKey);
        if (s != null) {
            s.setStatus(status);
            persist();
        }
    }

    public synchronized void updateStatus(String sourceKey, SourceStatus status,
                                           String rejectionReason) {
        ensureInit();
        HarvestSource s = cache.get(sourceKey);
        if (s != null) {
            s.setStatus(status);
            s.setRejectionReason(rejectionReason);
            persist();
        }
    }

    /** Update the incremental cursor used as {@code from} on the next harvest. */
    public synchronized void updateCursor(String sourceKey, Instant cursor) {
        ensureInit();
        HarvestSource s = cache.get(sourceKey);
        if (s != null) {
            s.setLastSuccessfulUntil(cursor);
            persist();
        }
    }

    public synchronized void updateLastAttempted(String sourceKey, Instant ts) {
        ensureInit();
        HarvestSource s = cache.get(sourceKey);
        if (s != null) {
            s.setLastAttempted(ts);
            persist();
        }
    }

    public synchronized void updateLastSuccessful(String sourceKey, Instant ts) {
        ensureInit();
        HarvestSource s = cache.get(sourceKey);
        if (s != null) {
            s.setLastSuccessful(ts);
            persist();
        }
    }

    public synchronized void appendRunRecord(String sourceKey, HarvestRunRecord record) {
        ensureInit();
        HarvestSource s = cache.get(sourceKey);
        if (s != null) {
            s.addRunRecord(record);
            persist();
        }
    }

    // -------------------------------------------------------------------------
    // Persistence helpers
    // -------------------------------------------------------------------------

    private void persist() {
        String xml = serializeToXml();
        String putQ = "declare variable $xml as xs:string external; "
                + "db:put(\"Registry\", parse-xml($xml), \"" + CATALOG_PATH + "\")";
        try (QueryProcessor p = new QueryProcessor(putQ, context)) {
            p.variable("xml", xml);
            p.value();
        } catch (QueryException e) {
            LOG.errorv("Failed to persist source catalog: {0}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // XML serialization / deserialization
    // -------------------------------------------------------------------------

    private String serializeToXml() {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<harvestSources>\n");
        for (HarvestSource s : cache.values()) {
            appendSourceXml(sb, s);
        }
        sb.append("</harvestSources>\n");
        return sb.toString();
    }

    private void appendSourceXml(StringBuilder sb, HarvestSource s) {
        sb.append("  <source>\n");
        xml(sb, "sourceKey", s.getSourceKey());
        xml(sb, "identifier", s.getIdentifier());
        xml(sb, "oaiUrl", s.getOaiUrl());
        xml(sb, "status", s.getStatus() != null ? s.getStatus().name() : "");
        xml(sb, "discoveredFromSourceKey", s.getDiscoveredFromSourceKey());
        xml(sb, "depth", String.valueOf(s.getDepth()));
        xml(sb, "firstSeen", instant(s.getFirstSeen()));
        xml(sb, "lastSeen", instant(s.getLastSeen()));
        xml(sb, "lastAttempted", instant(s.getLastAttempted()));
        xml(sb, "lastSuccessful", instant(s.getLastSuccessful()));
        xml(sb, "lastSuccessfulUntil", instant(s.getLastSuccessfulUntil()));
        xml(sb, "rejectionReason", s.getRejectionReason());
        sb.append("    <recentRuns>\n");
        for (HarvestRunRecord r : s.getRecentRuns()) {
            sb.append("      <run>\n");
            xml(sb, "startTime", instant(r.getStartTime()), 8);
            xml(sb, "endTime", instant(r.getEndTime()), 8);
            xml(sb, "recordsStored", String.valueOf(r.getRecordsStored()), 8);
            xml(sb, "newSourcesDiscovered", String.valueOf(r.getNewSourcesDiscovered()), 8);
            xml(sb, "outcome", r.getOutcome(), 8);
            xml(sb, "details", r.getDetails(), 8);
            sb.append("      </run>\n");
        }
        sb.append("    </recentRuns>\n");
        sb.append("  </source>\n");
    }

    private static void xml(StringBuilder sb, String tag, String value) {
        xml(sb, tag, value, 4);
    }

    private static void xml(StringBuilder sb, String tag, String value, int indent) {
        sb.append(" ".repeat(indent))
          .append("<").append(tag).append(">")
          .append(escape(value))
          .append("</").append(tag).append(">\n");
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String instant(Instant i) {
        return i != null ? i.toString() : "";
    }

    private void loadFromXml(String xml) {
        try {
            Document doc = docBuilder.parse(new InputSource(new StringReader(xml)));
            NodeList sourceNodes = doc.getElementsByTagName("source");
            for (int i = 0; i < sourceNodes.getLength(); i++) {
                if (sourceNodes.item(i) instanceof Element el) {
                    HarvestSource s = parseSource(el);
                    cache.put(s.getSourceKey(), s);
                }
            }
        } catch (SAXException | IOException e) {
            LOG.warnv("Could not parse source catalog XML: {0}", e.getMessage());
        }
    }

    private HarvestSource parseSource(Element el) {
        HarvestSource s = new HarvestSource();
        s.setSourceKey(child(el, "sourceKey"));
        s.setIdentifier(child(el, "identifier"));
        s.setOaiUrl(child(el, "oaiUrl"));
        s.setStatus(parseStatus(child(el, "status")));
        s.setDiscoveredFromSourceKey(child(el, "discoveredFromSourceKey"));
        s.setDepth(parseInt(child(el, "depth"), 0));
        s.setFirstSeen(parseInstant(child(el, "firstSeen")));
        s.setLastSeen(parseInstant(child(el, "lastSeen")));
        s.setLastAttempted(parseInstant(child(el, "lastAttempted")));
        s.setLastSuccessful(parseInstant(child(el, "lastSuccessful")));
        s.setLastSuccessfulUntil(parseInstant(child(el, "lastSuccessfulUntil")));
        s.setRejectionReason(child(el, "rejectionReason"));

        NodeList runNodes = el.getElementsByTagName("run");
        List<HarvestRunRecord> runs = new ArrayList<>();
        for (int i = 0; i < runNodes.getLength(); i++) {
            if (runNodes.item(i) instanceof Element re) {
                HarvestRunRecord r = new HarvestRunRecord();
                r.setStartTime(parseInstant(child(re, "startTime")));
                r.setEndTime(parseInstant(child(re, "endTime")));
                r.setRecordsStored(parseInt(child(re, "recordsStored"), 0));
                r.setNewSourcesDiscovered(parseInt(child(re, "newSourcesDiscovered"), 0));
                r.setOutcome(child(re, "outcome"));
                r.setDetails(child(re, "details"));
                runs.add(r);
            }
        }
        s.setRecentRuns(runs);
        return s;
    }

    private static String child(Element el, String tag) {
        NodeList nl = el.getElementsByTagName(tag);
        if (nl.getLength() > 0 && nl.item(0).getFirstChild() != null) {
            return nl.item(0).getFirstChild().getNodeValue();
        }
        return "";
    }

    private static SourceStatus parseStatus(String s) {
        try {
            return SourceStatus.valueOf(s);
        } catch (IllegalArgumentException | NullPointerException e) {
            return SourceStatus.ACTIVE;
        }
    }

    private static Instant parseInstant(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return Instant.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}

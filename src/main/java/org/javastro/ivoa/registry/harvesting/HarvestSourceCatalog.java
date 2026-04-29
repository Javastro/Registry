package org.javastro.ivoa.registry.harvesting;

/*
 * Created on 28/04/2026 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import org.basex.core.BaseXException;
import org.basex.core.cmd.XQuery;
import org.basex.query.QueryException;
import org.basex.query.QueryProcessor;
import org.javastro.ivoa.registry.internal.BaseXStoreBase;
import org.jboss.logging.Logger;

import java.io.StringReader;
import java.io.StringWriter;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Persistent catalog of known harvest sources, backed by a BaseX XML document at
 * {@code Registry/harvest/sources.xml}.
 *
 * <p>An in-memory {@link ConcurrentHashMap} is kept in sync with BaseX so that
 * hot-path reads never need a database round-trip.  All mutating operations
 * persist the full catalog document back to BaseX immediately.  Serialization
 * and deserialization are handled by JAXB.</p>
 */
@ApplicationScoped
public class HarvestSourceCatalog extends BaseXStoreBase {

    private static final Logger LOG = Logger.getLogger(HarvestSourceCatalog.class);

    static final String CATALOG_PATH = "harvest/sources.xml";

    final ConcurrentHashMap<String, HarvestSource> cache = new ConcurrentHashMap<>();
    final AtomicBoolean initialized = new AtomicBoolean(false);

    private final JAXBContext jaxbContext;

    public HarvestSourceCatalog() {
        super();
        try {
            jaxbContext = JAXBContext.newInstance(HarvestSourceList.class);
        } catch (JAXBException e) {
            throw new RuntimeException("Cannot create JAXB context for harvest catalog", e);
        }
    }

    @Override
    public void open() {
        // BaseX lifecycle is managed by Registry.onStart() via BasexStore.open().
        // HarvestSourceCatalog does not independently manage the BaseX database.
    }

    @Override
    public void close() {
        // BaseX lifecycle is managed by Registry.onStart() via BasexStore.open().
        // HarvestSourceCatalog does not own the shared context and must not close it.
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
        doPersist(serializeToXml());
    }

    /**
     * Writes the serialized catalog XML to BaseX.
     * Subclasses (or anonymous subclasses in tests) may override this to suppress
     * the database write, following the Template Method pattern.
     */
    protected void doPersist(String xml) {
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
    // JAXB serialization / deserialization
    // -------------------------------------------------------------------------

    private String serializeToXml() {
        HarvestSourceList list = new HarvestSourceList();
        list.setSources(List.copyOf(cache.values()));
        try {
            Marshaller m = jaxbContext.createMarshaller();
            m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
            m.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
            StringWriter sw = new StringWriter();
            m.marshal(list, sw);
            return sw.toString();
        } catch (JAXBException e) {
            throw new RuntimeException("Failed to marshal harvest source catalog", e);
        }
    }

    private void loadFromXml(String xml) {
        try {
            Unmarshaller u = jaxbContext.createUnmarshaller();
            Object result = u.unmarshal(new StringReader(xml));
            if (result instanceof HarvestSourceList list) {
                for (HarvestSource s : list.getSources()) {
                    cache.put(s.getSourceKey(), s);
                }
            }
        } catch (JAXBException e) {
            LOG.warnv("Could not parse source catalog XML: {0}", e.getMessage());
        }
    }
}


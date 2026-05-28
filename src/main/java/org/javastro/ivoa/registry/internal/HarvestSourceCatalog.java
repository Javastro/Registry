package org.javastro.ivoa.registry.internal;

/*
 * Created on 28/04/26 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import org.basex.core.BaseXException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.javastro.ivoa.registry.harvesting.HarvestRunRecord;
import org.javastro.ivoa.registry.harvesting.HarvestSource;
import org.javastro.ivoa.registry.harvesting.HarvestSourceList;
import org.javastro.ivoa.registry.harvesting.SourceStatus;
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
public class HarvestSourceCatalog  {

    @ConfigProperty(name = "ivoa.harvesting.rofr.url", defaultValue = "http://rofr.ivoa.net/oai")
    String rofrUrl;

    @ConfigProperty(name = "ivoa.harvesting.rofr.ivoid", defaultValue = "ivo://ivoa.net/rofr")
    String rofrIvoid;

    private static final Logger LOG = Logger.getLogger(HarvestSourceCatalog.class);

    static final String CATALOG_PATH = "harvest/sources.xml";

    final ConcurrentHashMap<String, HarvestSource> cache = new ConcurrentHashMap<>();
    final AtomicBoolean initialized = new AtomicBoolean(false);

    private final JAXBContext jaxbContext;

    @Inject //IMPL perhaps shoul use initializer method
    public HarvestSourceCatalog( RegistryStoreInterface store) {
        this.store = store;
        try {
            jaxbContext = JAXBContext.newInstance(HarvestSourceList.class);
        } catch (JAXBException e) {
            throw new RuntimeException("Cannot createEntry JAXB context for harvest catalog", e);
        }
    }

       private final RegistryStoreInterface store;

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
            if (!store.exists(CATALOG_PATH)) {
                LOG.infov("No existing source catalog found at {0}, creating new RofR", CATALOG_PATH);


                final HarvestSource e1 = HarvestSource.create(rofrIvoid,rofrUrl,null,0);
                //e1.setDiscoverySet("ivo_publishers");//TODO restore when https://github.com/ivoa/registry-housekeeping/issues/10 fixed
                store.create(serializeToXml(List.of(e1)),CATALOG_PATH);
            }
            String xml = store.read(CATALOG_PATH);
            if (xml != null && !xml.isBlank()) {
                loadFromXml(xml);
            }
            LOG.infov("Source catalog initialized – {0} source(s) loaded", cache.size());
        } catch (BaseXException e) {
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
        HarvestSource existing = cache.get(source.getIdentifier());
        if (existing != null) {
            // idempotent refresh – preserve provenance
            existing.setLastSeen(Instant.now());
            if (source.getIdentifier() != null && !source.getIdentifier().isEmpty()) {
                existing.setIdentifier(source.getIdentifier());
            }
            if (source.getOaiUrl() != null) {
                existing.setOaiUrl(source.getOaiUrl());
            }
            cache.put(existing.getIdentifier(), existing);
            persist();
            return existing;
        }
        source.setFirstSeen(Instant.now());
        source.setLastSeen(Instant.now());
        cache.put(source.getIdentifier(), source);
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
        // Persist catalog as a standalone document; createEntry/update.xq only mutates ri:VOResources.
        store.create(serializeToXml(), CATALOG_PATH);
    }



    // -------------------------------------------------------------------------
    // JAXB serialization / deserialization
    // -------------------------------------------------------------------------

    private String serializeToXml()
    {
        return serializeToXml(List.copyOf(cache.values()));
    }

    private String serializeToXml(List<HarvestSource> sources) {
        HarvestSourceList list = new HarvestSourceList();
        list.setSources(sources);
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
                    cache.put(s.getIdentifier(), s);
                }
            }
        } catch (JAXBException e) {
            LOG.warnv("Could not parse source catalog XML: {0}", e.getMessage());
        }
    }
}

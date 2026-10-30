package org.javastro.ivoa.registry.tap;

import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.XsltExecutable;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.ivoa.dm.regtap.RegTAPModel;
import org.ivoa.dm.regtap.Resource;
import org.javastro.ivoa.registry.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;

import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.List;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loads the XML registry content into the RegTAP relational tables using the RegTAPDM library.
 */
@io.quarkus.arc.profile.IfBuildProfile("regtap")
@ApplicationScoped
public class RegTapLoader implements org.javastro.ivoa.registry.harvesting.RelationalRecordSink {
   private static final Logger log = LoggerFactory.getLogger(RegTapLoader.class);
   private static final String XSL = "convertToRegTap.xsl";
   private static final String MODEL_PACKAGE = "org.ivoa.dm.regtap.";
   // all content documents, excluding the harvest catalogue
   private static final String DOC_PATHS = "string-join(db:list('Registry')[not(starts-with(., 'harvest/') or contains(.,'ivoa_net_rofr'))], '&#10;')";

   public enum State {IDLE, RUNNING, COMPLETED, FAILED, CANCELLED}

   /** Snapshot of the progress of the (most recent) load job. */
   public record LoadStatus(State state, int total, int processed, String currentDocument,
                            Instant started, Instant finished, String error) {}

   @ConfigProperty(name = "ivoa.tap.load.batch-size", defaultValue = "30")
   int batchSize;

   private final Processor processor = new Processor(false);
   private XsltExecutable xslt;
   private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "regtap-loader");
      t.setDaemon(true);
      return t;
   });
   private final Object lock = new Object();
   private volatile LoadStatus status = new LoadStatus(State.IDLE, 0, 0, null, null, null, null);
   private volatile boolean cancelRequested;

   @Inject
   Registry registry;

   @PersistenceContext
   EntityManager em;

   @PreDestroy
   void shutdown() {
      cancelRequested = true;
      executor.shutdownNow();
   }

   public LoadStatus status() {
      return status;
   }

   /**
    * Starts a background load.
    * @throws IllegalStateException if a load is already in progress
    */
   public LoadStatus start() {
      synchronized (lock) {
         if (status.state() == State.RUNNING) {
            throw new IllegalStateException("load already in progress");
         }
         cancelRequested = false;
         status = new LoadStatus(State.RUNNING, 0, 0, null, Instant.now(), null, null);
         executor.submit(this::runLoad);
         return status;
      }
   }

   /** Requests cancellation; the job stops after the current batch. */
   public boolean cancel() {
      synchronized (lock) {
         if (status.state() != State.RUNNING) return false;
         cancelRequested = true;
         return true;
      }
   }

   private void updateRecord(int total, int processed, String doc) {
      var s = status;
      status = new LoadStatus(State.RUNNING, total, processed, doc, s.started(), null, null);
   }

   private void finish(State state, String error) {
      var s = status;
      status = new LoadStatus(state, s.total(), s.processed(), null, s.started(), Instant.now(), error);
   }

   /**
    * Streams the registry content through the conversion in batches so that only
    * {@code batchSize} records are held in memory at once.
    */
   private void runLoad() {
      try {
         var q = registry.getRegistryQueryInterface();
         Map<String, Integer> docs = new LinkedHashMap<>();
         int total = 0;
         for (String path : q.xquery(DOC_PATHS).split("\n")) {
            if (path.isBlank()) continue;
            int n = Integer.parseInt(q.xquery("count(" + docExpr(path) + ")").trim());
            docs.put(path.trim(), n);
            total += n;
         }
         updateRecord(total, 0, null);
         QuarkusTransaction.requiringNew().run(() -> em.createQuery("delete from resource").executeUpdate());
         int loaded = 0;
         for (var e : docs.entrySet()) {
            String doc = docExpr(e.getKey());
            log.info("RegTAP load: {} resources from {}", e.getValue(), e.getKey());
            for (int from = 1; from <= e.getValue(); from += batchSize) {
               if (cancelRequested) {
                  log.info("RegTAP load cancelled after {} resources", loaded);
                  finish(State.CANCELLED, null);
                  return;
               }
               updateRecord(total, loaded, e.getKey());
               String xml = q.xquery("<ri:Resources xmlns:ri=\"http://www.ivoa.net/xml/RegistryInterface/v1.0\">{ "
                     + "subsequence(" + doc + ", " + from + ", " + batchSize + ") }</ri:Resources>");
               RegTAPModel model = convert(xml);
               loaded += QuarkusTransaction.requiringNew().call(() -> store(model));
            }
         }
         updateRecord(total, loaded, null);

         log.info("loading RofR");
         String xml = q.xquery("db:get('Registry','harvested/ivoa_net_rofr/rec.xml')/*");
         if(xml.isEmpty()) {
            log.warn("RofR document not found in registry store");
         } else {
            RegTAPModel model = convert(xml);
            QuarkusTransaction.requiringNew().call(() -> update(model));
         }
         finish(State.COMPLETED, null);
         log.info("loaded {} resources into RegTAP", loaded);
      } catch (Throwable t) {
         log.error("RegTAP load failed", t);
         finish(State.FAILED, String.valueOf(t.getMessage()));
      }
   }

   private static String docExpr(String path) {
      return "db:get('Registry', '" + path.trim().replace("'", "''") + "')/*/ri:Resource";
   }

   /**
    * Convert XML to a RegTAPModel.
    * @param xml the XML sourceg.
    * @return the RegTAPModel.
    * @throws Exception if the XML cannot be converted.
    */
   RegTAPModel convert(Source xml) throws Exception {
      StringWriter out = new StringWriter();
      executable().load30().transform(xml, processor.newSerializer(out));
      return (RegTAPModel) RegTAPModel.contextFactory().createUnmarshaller()
            .unmarshal(new StringReader(out.toString()));
   }

   /**
    * Convert XML string to RegTAPModel
    * @param xml XML String
    * @return
    * @throws Exception
    */
   RegTAPModel convert(String xml) throws Exception {
      return convert(new StreamSource(new StringReader(xml)));
   }

   private synchronized XsltExecutable executable() throws Exception {
      if (xslt == null) {
         try (InputStream in = RegTAPModel.class.getClassLoader().getResourceAsStream(XSL)) {
            if (in == null) throw new IllegalStateException(XSL + " not found on classpath");
            xslt = processor.newXsltCompiler().compile(new StreamSource(in));
         }
      }
      return xslt;
   }

   int store(RegTAPModel model) {
      List<Resource> resources = model.getResource();
      for (Resource r : resources) {
         log.info("storing resource {} ({})", r.getIvoid(), r.getRes_title());
         truncateStrings(r);
         em.persist(r);
      }
      em.flush();
      em.clear();
      return resources.size();
   }
   
   @Override
   public void addResource(String resourceXml) throws Exception {
      RegTAPModel model = convert(resourceXml);
      QuarkusTransaction.requiringNew().run(() -> {
         for (Resource r : model.getResource()) {
            removeByIvoid(r.getIvoid());
            truncateStrings(r);
            em.persist(r);
         }
         em.flush();
         em.clear();
      });
   }

   @Override
   public void addResource(Node resourceXml) throws Exception {
      RegTAPModel model = convert(new DOMSource(resourceXml));
      QuarkusTransaction.requiringNew().run(() -> {
         for (Resource r : model.getResource()) {
            removeByIvoid(r.getIvoid());
            truncateStrings(r);
            em.persist(r);
         }
         em.flush();
         em.clear();
      });
   }


   @Override
   public void deleteResource(String identifier) {
      QuarkusTransaction.requiringNew().run(() -> {
         removeByIvoid(identifier);
         em.flush();
         em.clear();
      });
   }

   private void removeByIvoid(String ivoid) {
      for (Object o : em.createQuery("select r from resource r where r.ivoid = :id")
            .setParameter("id", ivoid).getResultList()) {
         em.remove(o);
      }
      em.flush();
   }

   int update(RegTAPModel model) {
      List<Resource> resources = model.getResource();
      for (Resource r : resources) {
         log.info("updating resource {} ({})", r.getIvoid(), r.getRes_title());
         truncateStrings(r);
         em.merge(r);
      }
      em.flush();
      em.clear();
      return resources.size();
   }
   
   public boolean incrementalUpdate(String xml) throws Exception {
      RegTAPModel model = convert(xml);
      int updated = update(model);
      log.info("incremental update: updated {} resources into RegTAP", updated);
      return true;
   }

   /** Truncates strings to the declared column length (default 255) and defaults a missing std flag, recursing into the RegTAP child entities. */
   private static void truncateStrings(Object o) {
      if (o == null) return;
      for (Class<?> c = o.getClass(); c != null && c.getName().startsWith(MODEL_PACKAGE); c = c.getSuperclass()) {
         for (Field f : c.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            try {
               f.setAccessible(true);
               Object v = f.get(o);
               if (v instanceof String str) {
                  Column col = f.getAnnotation(Column.class);
                  int max = col != null ? col.length() : 255;
                  if (str.length() > max) {
                     log.warn("truncating {} from {} to {} characters", f.getName(), str.length(), max);
                     f.set(o, str.substring(0, max));
                  }
//               } else if (v == null && "std".equals(f.getName()) && f.getType() == Integer.class) {
//                  // convertToRegTap.xsl omits std when @std is absent; VOResource defaults it to false
//                  f.set(o, 0);
               } else if (v instanceof Collection<?> coll) {
                  coll.forEach(RegTapLoader::truncateStrings);
               } else if (v != null && v.getClass().getName().startsWith(MODEL_PACKAGE)) {
                  truncateStrings(v);
               }
            } catch (IllegalAccessException e) {
               throw new IllegalStateException(e);
            }
         }
      }
   }
}

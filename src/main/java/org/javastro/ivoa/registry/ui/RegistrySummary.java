package org.javastro.ivoa.registry.ui;

import io.quarkus.qute.TemplateData;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.javastro.ivoa.registry.Registry;
import org.javastro.ivoa.registry.harvesting.HarvestSource;
import org.javastro.ivoa.registry.harvesting.RelationalRecordSink;
import org.javastro.ivoa.registry.internal.HarvestSourceCatalog;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.TreeMap;

/**
 * Summary information about the registry content, shown on the landing page.
 */
@ApplicationScoped
public class RegistrySummary {

   /** Snapshot of the registry content and state. */
   @TemplateData
   public record Summary(int managedRecords, int harvestedRecords, int sources,
                         Map<String, Long> sourcesByStatus, boolean regtap) {}

   private static final Logger log = Logger.getLogger(RegistrySummary.class);

   @Inject
   Registry registry;

   @Inject
   HarvestSourceCatalog catalog;

   @Inject
   Instance<RelationalRecordSink> relationalSink;

   public Summary summary() {
      Map<String, Long> byStatus = new TreeMap<>();
      int sources = 0;
      try {
         for (HarvestSource s : catalog.getAll()) {
            sources++;
            byStatus.merge(String.valueOf(s.getStatus()), 1L, Long::sum);
         }
      } catch (Exception e) {
         log.warnv("could not read harvest sources: {0}", e.getMessage());
      }
      return new Summary(count("managed/base.xml"), harvested(), sources, byStatus, relationalSink.isResolvable());
   }

   private int count(String path) {
      try {
         return Integer.parseInt(registry.getRegistryQueryInterface()
               .xquery("count(db:get('Registry','" + path + "')/*/ri:Resource)").trim());
      } catch (Exception e) {
         log.debugv("could not count records in {0}: {1}", path, e.getMessage());
         return 0;
      }
   }

   private int harvested() {
      try {
         return Integer.parseInt(registry.getRegistryQueryInterface()
               .xquery("count(db:list('Registry')[starts-with(., 'harvested/')] ! db:get('Registry', .)/*/ri:Resource)").trim());
      } catch (Exception e) {
         log.debugv("could not count harvested records: {0}", e.getMessage());
         return 0;
      }
   }
}

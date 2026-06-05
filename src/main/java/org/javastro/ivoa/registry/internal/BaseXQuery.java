package org.javastro.ivoa.registry.internal;


/*
 * Created on 07/08/2025 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.xml.bind.JAXBException;
import org.basex.core.BaseXException;
import org.basex.core.Context;
import org.basex.core.cmd.Open;
import org.basex.core.cmd.XQuery;
import org.basex.io.serial.Serializer;
import org.basex.query.QueryException;
import org.basex.query.QueryProcessor;
import org.basex.query.iter.Iter;
import org.basex.query.value.item.Item;
import org.javastro.ivoa.entities.Ivoid;
import org.javastro.ivoa.entities.resource.Resource;
import org.javastro.ivoa.entities.resource.Service;
import org.javastro.ivoa.registry.XMLUtils;
import org.jboss.logging.Logger;
import org.w3c.dom.Node;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.time.ZonedDateTime;
import java.util.List;

@ApplicationScoped
public class BaseXQuery   implements RegistryQueryInterface {

   private final Logger log = Logger.getLogger(this.getClass());
   private Context contextRO;
   private static final String namespaces;
   private static String listIDsXQuery;
   private static String listRecordsXQuery;
   private static String getRecordXQuery;

   static {
      try {
         namespaces = new String(BaseXQuery.class.getResourceAsStream("/xquery/namespaces.xq").readAllBytes());
         listIDsXQuery = new String(BaseXQuery.class.getResourceAsStream("/xquery/oaiIdentifiers.xq").readAllBytes());
         listRecordsXQuery = new String(BaseXQuery.class.getResourceAsStream("/xquery/oaiListRecords.xq").readAllBytes());
         getRecordXQuery = new String(BaseXQuery.class.getResourceAsStream("/xquery/oaiGetRecord.xq").readAllBytes());
      } catch (IOException e) {
         throw new RuntimeException("Failed to read default queries",e);
      }
   }

   public BaseXQuery(BasexStore store) {

      contextRO = new Context(store.context);
      contextRO.user(contextRO.users.get("reader"));
      try {
         log.info("opening database for querying user=" + new XQuery("user:current()").execute(contextRO));
      } catch (BaseXException e) {
         throw new RuntimeException(e);
      }
   }


   @Override
   public String oaiListIDs(ZonedDateTime start, ZonedDateTime end, String setName, String metadataPrefix){
      try( QueryProcessor proc = new QueryProcessor(listIDsXQuery, contextRO)) {
         //TODO need to implement and add the other variables.
         proc.variable("metadataPrefix", metadataPrefix);
         return queryResultToString(proc);
      } catch (QueryException e) {
         throw new RuntimeException(e);
      }
   }


   @Override
   public String oaiListRecords(ZonedDateTime start, ZonedDateTime end, String setName, String metadataPrefix)  {
      try( QueryProcessor proc = new QueryProcessor(listRecordsXQuery, contextRO)) {
         //TODO need to implement and add the other variables.
         proc.variable("metadataPrefix", metadataPrefix);
         return queryResultToString(proc);
      } catch (QueryException e) {
         throw new RuntimeException(e);
      }
   }

   @Override
   public String oaiGetRecord(Ivoid id, String metadataPrefix)  {
      try( QueryProcessor proc = new QueryProcessor(getRecordXQuery, contextRO)) {
         proc.variable("id", id.toString());
         proc.variable("metadataPrefix", metadataPrefix);
         return queryResultToString(proc);
      } catch (QueryException e) {
         throw new RuntimeException(e);
      }    }

   private String queryResultToString(QueryProcessor processor) {
      //TODO this is not really taking advantage of the possible chunked streaming nature, which could propagate up from here to allow "reactive" at quarkus level...
      Node doc = null;
      try {
         Iter iter = processor.iter();

         final ByteArrayOutputStream out = new ByteArrayOutputStream();
         try(Serializer ser = processor.serializer(new PrintStream(out))) {
            // Iterate through all items and serialize contents
            for(Item item; (item = iter.next()) != null;) {
               Object o = item.toJava();
               if(o instanceof Node) {
                  doc = (Node)o;
               }
               ser.serialize(item);
            }
         } catch (IOException e) {
            throw new RuntimeException(e);
         }
         return out.toString();

      } catch (QueryException e) {
         throw new RuntimeException(e);
      }

   }


   @Override
   public String xquery(String query) throws BaseXException { //TODO make this more sophisticated - streaming add context etc.
      //prepend common namespaces
   return new XQuery(namespaces+query).execute(contextRO);
   }

   @Override
   public List<Resource> listResources() {
      XMLUtils util = new XMLUtils();
      String query = """
            <ri:VOResources from="1" more="false" numberReturned="1">
              {for $res in fn:collection("Registry/managed")/*/ri:Resource
                return $res}
            </ri:VOResources>
            """;
      try {
         String res = new XQuery(namespaces+query).execute(contextRO);
         return util.unmarshal(res);
      } catch (BaseXException | JAXBException e) {
         throw new RuntimeException(e);
      }
   }

   @Override
   public Resource getResource(Ivoid id) {
      XMLUtils util = new XMLUtils();
      try {
         String res = new XQuery(namespaces+"//ri:Resource[identifier=\""+id.toString()+"\"]").execute(contextRO);
         return util.unmarshal(res).get(0);
      } catch (JAXBException | BaseXException e) {
         throw new RuntimeException(e);
      }
   }

   @Override
   public List<Service> listServices() {
      XMLUtils util = new XMLUtils();
      String query = """
            <ri:VOResources from="1" more="false" numberReturned="1">
              {for $res in fn:collection("Registry/managed")/*/ri:Resource[capability]
                return $res}
            </ri:VOResources>
            """;
      try {
         String res = new XQuery(namespaces+query).execute(contextRO);
         return util.unmarshal(res).stream().map(r->(Service)r).toList();
      } catch (BaseXException | JAXBException e) {
         throw new RuntimeException(e);
      }
   }
}

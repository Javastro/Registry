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
import org.javastro.ivoa.entities.Ivoid;
import org.javastro.ivoa.entities.resource.Resource;
import org.javastro.ivoa.entities.resource.Service;
import org.javastro.ivoa.registry.XMLUtils;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class BaseXQuery  extends BaseXStoreBase implements RegistryQueryInterface {

   private final Logger log = Logger.getLogger(this.getClass());
   private  Context contextRO;
   private static final String namespaces = """
         declare namespace cs="http://www.ivoa.net/xml/ConeSearch/v1.0";
         declare namespace dc="http://purl.org/dc/elements/1.1/";
         declare namespace oai="http://www.openarchives.org/OAI/2.0/";
         declare namespace ri="http://www.ivoa.net/xml/RegistryInterface/v1.0";
         declare namespace sia="http://www.ivoa.net/xml/SIA/v1.1";
         declare namespace slap="http://www.ivoa.net/xml/SLAP/v1.0";
         declare namespace ssap="http://www.ivoa.net/xml/SSA/v1.1";
         declare namespace tr="http://www.ivoa.net/xml/TAPRegExt/v1.0";
         declare namespace vg="http://www.ivoa.net/xml/VORegistry/v1.0";
         declare namespace vr="http://www.ivoa.net/xml/VOResource/v1.0";
         declare namespace vs="http://www.ivoa.net/xml/VODataService/v1.1";
         declare namespace vstd="http://www.ivoa.net/xml/StandardsRegExt/v1.0";
         """;

   @Override
   public void open() {
      contextRO = new Context(context);
      contextRO.user(context.users.get("reader"));

      try {
         new Open(REGDB_NAME).execute(contextRO);
         log.info("opening database for querying user="+new XQuery("user:current()").execute(contextRO));
      } catch (BaseXException e) {
         throw new RuntimeException(e);
      }
   }

   @Override
   public void close() {
      contextRO.close();
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

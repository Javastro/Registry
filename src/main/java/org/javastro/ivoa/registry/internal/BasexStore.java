package org.javastro.ivoa.registry.internal;
/*
 * Created on 30/05/2023 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

import jakarta.enterprise.context.ApplicationScoped;
import org.basex.core.BaseXException;
import org.basex.core.MainOptions;
import org.basex.core.cmd.*;
import org.basex.core.users.User;
import org.basex.query.QueryException;
import org.basex.query.QueryProcessor;
import org.basex.query.value.Value;
import org.javastro.ivoa.entities.Ivoid;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;


/**
 A registry store implemented with <a href="https://basex.org">...</a> .
 */
@ApplicationScoped
public class BasexStore extends BaseXStoreBase  implements RegistryStoreInterface {


   private static String updateQuery;


   Logger log = Logger.getLogger(this.getClass());//IMPL would like to inject - did not work... see https://quarkus.io/guides/logging#injection-of-a-configured-logger

   public BasexStore() {
      super();


      try (InputStream s =BasexStore.class.getResourceAsStream("/xquery/update.xq")) {
         assert s != null;
         updateQuery = new String(s.readAllBytes());
      } catch (IOException e) {
         throw new RuntimeException(e);
      }



      try {
            //TODO - this can be set with  Java system property org.basex.path when deploying
            log.info("BaseX home directory="+new XQuery("Q{java:org.basex.util.Prop}HOMEDIR()").execute(context));
            if(!context.databases.list().contains(REGDB_NAME)) {
                log.info("creating "+REGDB_NAME);
                String result = new CreateDB(REGDB_NAME).execute(context);
                new Put("managed/base.xml", Objects.requireNonNull(BasexStore.class.getResource("/RegistryBase.xml")).toString()).execute(context);

            }
            User reader = context.users.get("reader");
            if( reader ==  null)
            {
               reader = new User("reader");
               context.users.add(reader);
            }
            new Grant("read","reader").execute(context);
            new Set(MainOptions.WRITEBACK, true).execute(context);
            new Set(MainOptions.MIXUPDATES, true).execute(context);


        } catch (BaseXException e) {
            throw new RuntimeException(e);
        }



   }

    @Override
    public void open() {
       try {
          new Open(REGDB_NAME).execute(context);
           log.info("DB opened for writing user="+new XQuery("user:current()").execute(context));
       } catch (BaseXException e) {
          throw new RuntimeException("problem opening BaseX database",e);
       }

    }

    @Override
    public void close() {
       try {
          new Close().execute(context);
       } catch (BaseXException e) {
          throw new RuntimeException(e);
       }
    }



   @Override
   public void create(String xml) {

     try( QueryProcessor proc = new QueryProcessor(updateQuery, context))
     {
        proc.variable("rin", xml);
        Value result = proc.value();
        
     } catch (QueryException e) {
        throw new RuntimeException(e);
     }
   }

   @Override
   public void delete(Ivoid id) {

   }
}

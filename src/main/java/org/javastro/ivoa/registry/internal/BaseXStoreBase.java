package org.javastro.ivoa.registry.internal;

/*
 * Created on 07/08/2025 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

import org.basex.core.Context;
import org.basex.io.serial.Serializer;
import org.basex.query.QueryException;
import org.basex.query.QueryProcessor;
import org.basex.query.iter.Iter;
import org.basex.query.value.item.Item;
import org.javastro.ivoa.entities.Ivoid;
import org.w3c.dom.Node;

import java.io.*;
import java.time.ZonedDateTime;

public abstract class BaseXStoreBase implements RegistryInterface{
    public static final String REGDB_NAME = "Registry";
    protected static final Context context = new Context();
    private static String listIDsXQuery;
    private static String listRecordsXQuery;
    private static String getRecordXQuery;

    public BaseXStoreBase() {
       try {
          InputStream s = BaseXStoreBase.class.getResourceAsStream("/xquery/oaiIdentifiers.xq");
          assert s != null;
          listIDsXQuery = new String(s.readAllBytes());
          s=BaseXStoreBase.class.getResourceAsStream("/xquery/oaiListRecords.xq");
          assert s != null;
          listRecordsXQuery = new String(s.readAllBytes());
          s=BaseXStoreBase.class.getResourceAsStream("/xquery/oaiIdentifiers.xq");
          assert s != null;
          s=BaseXStoreBase.class.getResourceAsStream("/xquery/oaiGetRecord.xq");

          assert s != null;
          getRecordXQuery = new String(s.readAllBytes());;

       } catch (IOException e) {
          throw new RuntimeException(e);
       }
    }

    @Override
    public String oaiListIDs(ZonedDateTime start, ZonedDateTime end, String setName, String metadataPrefix){
       try( QueryProcessor proc = new QueryProcessor(listIDsXQuery, context)) {
          //TODO need to implement and add the other variables.
           proc.variable("metadataPrefix", metadataPrefix);
           return queryResultToString(proc);
       } catch (QueryException e) {
          throw new RuntimeException(e);
       }
    }


   @Override
    public String oaiListRecords(ZonedDateTime start, ZonedDateTime end, String setName, String metadataPrefix)  {
      try( QueryProcessor proc = new QueryProcessor(listRecordsXQuery, context)) {
         //TODO need to implement and add the other variables.
         proc.variable("metadataPrefix", metadataPrefix);
         return queryResultToString(proc);
      } catch (QueryException e) {
         throw new RuntimeException(e);
      }
    }

    @Override
    public String oaiGetRecord(Ivoid id, String metadataPrefix)  {
       try( QueryProcessor proc = new QueryProcessor(getRecordXQuery, context)) {
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


}

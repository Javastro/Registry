package org.javastro.ivoa.registry.oaipmh.client;

import io.quarkus.test.junit.QuarkusTest;
import org.javastro.ivoa.entities.resource.Resource;
import org.javastro.ivoa.entities.resource.registry.iface.ResourceInstance;
import org.javastro.ivoa.entities.resource.registry.oaipmh.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 *  IMPL this is really a sort of integration test using the RofR - really need a mock registry
 */
@QuarkusTest
class OaiPMHClientTest {


   private OaiPMHClient client;

   @BeforeEach
   void setUp() {

      client = new OaiPMHClient("https://rofr.ivoa.net/oai",true);
      assertNotNull(client);
   }
   @Test
   void identify() {
       IdentifyType id = client.identify();
       assertNotNull(id);
       assertEquals(1,id.getDescriptions().size(), "there should only be 1 identify description");
       Resource res = ((ResourceInstance)(id.getDescriptions().get(0).getAny())).getValue();
       System.out.println(res.getIdentifier());
   }

   @Test
   void listMetadata() {
      ListMetadataFormatsType mt = client.listMetadataFormats();
      mt.getMetadataFormats().forEach(System.out::println);
      //
      assertTrue(mt.getMetadataFormats().stream().anyMatch(m -> m.getMetadataPrefix().equals("ivo_vor")),"IVOA registry metadata prefix should be 'ivo_vor'");
   }

   @Test
   void listSets() {
      ListSetsType st = client.listSets();
      st.getSets().forEach(System.out::println);

      assertTrue(st.getSets().stream().anyMatch(m -> m.getSetSpec().equals("ivo_managed"))," IVOA registry should have set named 'ivo_managed'");
   }
   @Test
   void getRecord(){
      RecordType r = client.getRecord("ivo://ivoa.net/rofr", "ivo_vor");
      assertNotNull(r);
      Resource res = ((ResourceInstance) r.getMetadata().getAny()).getValue();
      assertNotNull(res);
      assertEquals("ivo://ivoa.net/rofr", res.getIdentifier());

   }

   @Test
   void listIdentifiers(){
      ListIdentifiersType li = client.listIdentifiers("ivo_vor","ivo_managed",null, null,null);
      assertNotNull(li);
      li.getHeaders().forEach(System.out::println);

   }

   @Test
   void listRecords(){
      ListRecordsType li = client.listRecords("ivo_vor","ivo_publishers",null, Instant.parse("2016-01-01T00:00:00Z"),null);//IMPL the 2016 until date is because many records after that are not XML valid
      assertNotNull(li);
      assertTrue(!li.getRecords().isEmpty());
      for (RecordType r : li.getRecords()) { // make sure all the metadata suceeded
         System.out.println(r.getHeader().getIdentifier());
         Resource res = ((ResourceInstance) r.getMetadata().getAny()).getValue();
         assertNotNull(res);
      }

   }


}
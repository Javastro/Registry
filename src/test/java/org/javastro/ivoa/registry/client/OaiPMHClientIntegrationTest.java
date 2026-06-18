package org.javastro.ivoa.registry.client;

import io.quarkus.test.junit.QuarkusTest;
import org.javastro.ivoa.entities.resource.Resource;
import org.javastro.ivoa.entities.oai.oaipmh.*;
import org.javastro.ivoa.registry.XMLUtils;
import org.javastro.ivoa.registry.oaipmh.client.OaiInfoExtractor;
import org.javastro.ivoa.registry.oaipmh.client.OaiPMHException;
import org.javastro.ivoa.registry.oaipmh.client.OaiPMHClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Node;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 *  IMPL this is really a sort of integration test using the RofR - really need a mock registry
 */
@Tag("integration")
class OaiPMHlientIntegrationTest {


   private OaiPMHClient client;
   private XMLUtils xmlUtils = new XMLUtils();

   @BeforeEach
   void setUp() {

      client = new OaiPMHClient("http://rofr.ivoa.net/oai",true);
      assertNotNull(client);
   }
   @Test
   void identify() throws OaiPMHException {
       IdentifyType id = client.identify();
       assertNotNull(id);
       assertEquals(1,id.getDescriptions().size(), "there should only be 1 identify description");
       Resource res = xmlUtils.extractResource((Node) id.getDescriptions().get(0).getAny());
       System.out.println(res.getIdentifier());
   }

   @Test
   void listMetadata() throws OaiPMHException {
      ListMetadataFormatsType mt = client.listMetadataFormats();
      mt.getMetadataFormats().forEach(System.out::println);
      //
      assertTrue(mt.getMetadataFormats().stream().anyMatch(m -> m.getMetadataPrefix().equals("ivo_vor")),"IVOA registry metadata prefix should be 'ivo_vor'");
   }

   @Test
   void listSets() throws OaiPMHException {
      ListSetsType st = client.listSets();
      st.getSets().forEach(System.out::println);

      assertTrue(st.getSets().stream().anyMatch(m -> m.getSetSpec().equals("ivo_managed"))," IVOA registry should have set named 'ivo_managed'");
   }
   @Test
   void getRecord() throws OaiPMHException {
      RecordType r = client.getRecord("ivo://ivoa.net/rofr", "ivo_vor");
      assertNotNull(r);
      Resource res = xmlUtils.OaiMetadataToResource(r);
      assertNotNull(res);
      assertEquals("ivo://ivoa.net/rofr", res.getIdentifier());

   }

   @Test
   void listIdentifiers() throws OaiPMHException {
      ListIdentifiersType li = client.listIdentifiers("ivo_vor","ivo_managed",null, null,null);
      assertNotNull(li);
      li.getHeaders().forEach(System.out::println);

   }

   @Test
   void listRecords() throws OaiPMHException {
      ListRecordsType li = client.listRecords("ivo_vor","ivo_publishers",null, Instant.parse("2016-01-01T00:00:00Z"),null);//IMPL the 2016 until date is because many records after that are not XML valid
      assertNotNull(li);
      assertFalse(li.getRecords().isEmpty());
      for (RecordType r : li.getRecords()) { // make sure all the metadata suceeded
         System.out.println(r.getHeader().getIdentifier());
         Resource res = xmlUtils.OaiMetadataToResource(r);;
         assertNotNull(res);
      }

   }

   @Test
   void rawListRecords() throws IOException, InterruptedException, OaiPMHException {
      Path path = Files.createTempFile("oai", ".xml");
      OaiInfoExtractor.OaiRecordInfo result = client.ListRecordsRaw(path, "ivo_vor", "ivo_publishers", null, null, null);
      assertNotNull(result);
      assertTrue(path.toFile().length() > 0);
      assertNotNull(result.timestamp());
      System.out.println(Duration.between(result.timestamp(), Instant.now()).toMillis() + " milli seconds since request");
   }



}
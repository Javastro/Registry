package org.javastro.ivoa.registry.harvesting;

import io.quarkus.test.junit.QuarkusTest;
import org.javastro.ivoa.entities.resource.Resource;
import org.javastro.ivoa.entities.resource.registry.oaipmh.HeaderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/*
 * Created on 11/02/2025 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */
@QuarkusTest
class HarvestClientTest {

    private HarvestClient harvestClient;

    @BeforeEach
   void setUp() {
       // harvestClient = new HarvestClient("https://registry.euro-vo.org/oai.jsp"); // EuroVO registry because it does resumption tokens
//        harvestClient = new HarvestClient("https://cds.unistra.fr/registry/"); // EuroVO registry because it does resumption tokens
       harvestClient = new HarvestClient("http://localhost:8085/oai");
   }

   @Test
   void validate() {
        assertTrue(harvestClient.validate());
   }

   @Test
   void identify() {
        assertNotNull(harvestClient.identify());
   }

   @Test
   void getIdentifiers() {

       List<HeaderType> ids = harvestClient.getIdentifiers(null, null);
       assertNotNull(ids);
   }

   @Test
   void getRecords() {
      List<Resource> records = harvestClient.getRecords(null, null);
      assertNotNull(records);

   }
}
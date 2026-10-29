package org.javastro.ivoa.registry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConcatenateCacheTest {

   private static final XMLUtils utils = new XMLUtils();

   private static String chunk(String id) {
      return "<OAI-PMH xmlns=\"http://www.openarchives.org/OAI/2.0/\"><ListRecords><record><header><identifier>" + id
            + "</identifier></header><metadata><ri:Resource xmlns:ri=\"http://www.ivoa.net/xml/RegistryInterface/v1.0\">"
            + "<identifier>" + id + "</identifier></ri:Resource></metadata></record></ListRecords></OAI-PMH>";
   }

   @Test
   void convertsSingleFile() throws Exception {

      String xml = utils.rawOAIToResources(new StreamSource(XMLUtils.class.getResourceAsStream("/OAIListRecord.xml")));
      assertTrue(xml.contains("VOResources"));
      assertEquals(84, xml.split("<ri:Resource", -1).length - 1);
   }

   @Test
   void concatenatesChunks(@TempDir Path dir) throws Exception {
      Files.writeString(dir.resolve("0000.xml"), chunk("ivo://a/1"));
      Files.writeString(dir.resolve("0001.xml"), chunk("ivo://a/2"));
      Path out = utils.concatenateCache(dir);
      String xml = Files.readString(out);
      Files.delete(out);
      assertTrue(xml.contains("VOResources"));
      assertEquals(2, xml.split("<ri:Resource", -1).length - 1);
   }

// This was a test on the full harvest of vizier - so pretty large - results in 64MBfile - made me
// decide to work with the chunks that it replies with on harvest
//   @Test
//   void concatReal(){
//      Path path = Path.of("/Users/pharriso/Work/javastro/registry/harvestCache/cds_vizier_registry");
//      Path out = new XMLUtils().concatenateCache(path);
//      System.out.println(out);
//      assertNotNull(out);
//   }

}

package org.javastro.ivoa.registry;

import org.javastro.ivoa.entities.Ivoid;
import org.javastro.ivoa.entities.resource.Resource;
import org.javastro.ivoa.registry.internal.BaseXQuery;
import org.javastro.ivoa.registry.internal.BasexStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.xml.transform.stream.StreamSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A more extensive store test to exercise the store and query classes. This more of an integration test,
 * though it runs as a unit test.
 *
 * @author Paul Harrison (paul.harrison@manchester.ac.uk)
 */
public class ExtendedStoreTest {

   @Test
   public void extendedTest(@TempDir java.nio.file.Path tempDir) throws Exception {
      System.setProperty("org.basex.dbpath", tempDir.toAbsolutePath().toString());

      BasexStore store = new BasexStore();
      store.open();
      BaseXQuery query = new BaseXQuery(store);
      String regivoid = "ivo://test.org/registry";
      XMLUtils util = new XMLUtils();
      InputStream initial = ExtendedStoreTest.class.getResourceAsStream("/OAIListRecord.xml");
      store.create(util.rawOAIToResources(new StreamSource(initial)),
      makepath(regivoid, "01.xml"));
      String result = query.xquery("count(//ri:Resource)");
      int i = Integer.parseInt(result);
      assertEquals(84, i);

      InputStream extra = ExtendedStoreTest.class.getResourceAsStream("/VOResource.xml");
      store.create(new String(Objects.requireNonNull(extra).readAllBytes(), StandardCharsets.UTF_8),makepath(regivoid, "02.xml"));
      result = query.xquery("count(//ri:Resource)");
      i = Integer.parseInt(result);
      assertEquals(89, i);


      //the second harvested file has an updated and a new record
      InputStream harvested = ExtendedStoreTest.class.getResourceAsStream("/OAIListRecord2.xml");
      store.updateEntries(util.rawOAIToResources(new StreamSource(harvested)),makepath(regivoid, ""));
      result = query.xquery("count(//ri:Resource)");
      i = Integer.parseInt(result);
      assertEquals(90, i);
      Resource res = query.getResource(new Ivoid("ivo://obelix.lambrate.inaf.it"));
      assertNotNull(res);
      assertTrue(res.getTitle().contains("Updated"));

      res =  query.getResource(new Ivoid("ivo://arches2"));
      assertNotNull(res);

   }

   private String makepath(String sourceKey, String filename) {
      return "harvested/"+sourceKey.substring(6).replaceAll("[^a-z0-9]+", "_")+"/"+filename;
   }

}

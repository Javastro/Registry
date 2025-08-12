package org.javastro.ivoa.registry;

/*
 * Created on 07/08/2025 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

import org.javastro.ivoa.entities.Ivoid;
import org.javastro.ivoa.registry.internal.BaseXQuery;
import org.javastro.ivoa.registry.internal.BasexStore;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests the registry access directly.
 */
public class DirectStoreTest {

   //  @Test // this is probably not a good unit test, as it will attempt to access the backend simultaneously to  the more standard client side tests
   public void doinit() throws IOException, URISyntaxException {
      BasexStore store = new BasexStore();
      BaseXQuery query = new BaseXQuery();
      query.open();
      String result = query.xquery("//ri:Resource");
      assertNotNull(result);
      InputStream sin = BasexStore.class.getResourceAsStream("/VOResource.xml");
      assertNotNull(sin);
      String xml = new String(sin.readAllBytes());
      store.create(xml);
      result = query.oaiListIDs(null, ZonedDateTime.now(),"ivo_managed","ivo_vor");
      assertNotNull(result);
      result = query.oaiListRecords(null, ZonedDateTime.now(),"ivo_managed","ivo_vor");
      assertNotNull(result);
      result = query.oaiGetRecord(new Ivoid("ivo://test.org/resource1"), "ivo_vor");
      assertNotNull(result);
      System.out.println(result);
   }
}

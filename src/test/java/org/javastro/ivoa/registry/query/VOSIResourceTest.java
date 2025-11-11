package org.javastro.ivoa.registry.query;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static io.restassured.RestAssured.given;

/*
 * Created on 28/08/2025 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

@QuarkusTest
class VOSIResourceTest {

   @Test
   void getCapabilities() {
      given().when().get("VOSI/capabilities")
            .then().statusCode(200); //TODO test the returned capabilities
   }
}
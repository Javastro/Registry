package org.javastro.ivoa.registry.oaipmh;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

/*
 * Created on 07/07/2025 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

@QuarkusTest
public class OaiPMHResourceTest {

   @Test
   void ident() {
       given()
             .when()
             .param("verb", "Identify")
             .get("/oai")
             .then()
             .log().body()
             .statusCode(200);


   }

   @Test
   void listSets() {
      given()
            .when()
            .param("verb", "ListSets")
            .get("/oai")
            .then()
            .log().body()
            .statusCode(200);


   }
   @Test
   void getRecord() {
      given()
            .when()
            .param("verb", "GetRecord")
            .param("metadataPrefix","ivo_vor")
            .param("identifier", "ivo://authority.changeme/Registry")
            .get("/oai")
            .then()
            .log().body()
            .statusCode(200);
   }
   @Test
   void listRecords() {
      given()
            .when()
            .param("verb", "ListRecords")
            .param("metadataPrefix","ivo_vor")
            .get("/oai")
            .then()
            .log().body()
            .statusCode(200);
   }
   @Test
   void listIdentifiers() {
      given()
            .when()
            .param("verb", "ListIdentifiers")
            .param("metadataPrefix","ivo_vor")
            .get("/oai")
            .then()
            .log().body()
            .statusCode(200);
   }

   @Test
   void deliberateverberror() {
      given()
            .when()
            .param("verb", "bad")
            .get("/oai")
            .then()
            .log().body()
            .statusCode(200);


   }

}
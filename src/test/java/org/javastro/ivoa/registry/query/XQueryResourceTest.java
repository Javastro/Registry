package org.javastro.ivoa.registry.query;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

import static io.restassured.RestAssured.given;


/*
 * Created on 07/08/2025 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

@QuarkusTest
class XQueryResourceTest {

   @Test
   void plain() {
       given().when()
             .body("count(//ri:Resource)")
             .post("/xquery").then().log().all().statusCode(200); //TODO test the return body

   }

   @Test
   void trytowrite()
   {
      given().when()
            .body("insert nodes <silly>mad</silly>\n" +
                  "   into fn:collection(\"Registry/managed/base.xml\")/ri:VOResources")
            .post("/xquery")
            .then().log().all().statusCode(500);
   }
}
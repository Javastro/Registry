package org.javastro.ivoa.registry;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

@QuarkusTest
class LandingPageTest {
   @Test
   void landingPageShowsSummary() {
      given().when().get("/").then().statusCode(200)
            .body(containsString("Registry content"))
            .body(containsString("Published (managed) records"))
            .body(containsString("Recursive discovery"));
   }
}

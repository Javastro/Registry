package org.javastro.ivoa.registry.tap;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@io.quarkus.test.junit.TestProfile(RegTapTestProfile.class)
class RegTapLoadTest {

   private static io.restassured.specification.RequestSpecification admin() {
      return given().auth().preemptive().basic("admin", "passwordchangeme");
   }

   @Test
   void load_returns401WithoutAuth() {
      given().when().post("/tap/admin/load").then().statusCode(401);
      given().when().get("/tap/admin/load/status").then().statusCode(401);
   }

   @Test
   void load_runsInBackgroundAndReportsStatus() throws Exception {
      admin().post("/tap/admin/load").then().statusCode(202).body("state", equalTo("RUNNING"));
      admin().get("/tap/admin/load/status").then().statusCode(200)
            .body("state", anyOf(equalTo("RUNNING"), equalTo("COMPLETED"), equalTo("FAILED")));
      // stop the (potentially long) job so the test does not depend on the full registry size
      admin().delete("/tap/admin/load");
      for (int i = 0; i < 100; i++) {
         String state = admin().get("/tap/admin/load/status").jsonPath().getString("state");
         if (!"RUNNING".equals(state)) break;
         Thread.sleep(200);
      }
      admin().get("/tap/admin/load/status").then().body("state", anyOf(equalTo("CANCELLED"), equalTo("COMPLETED")));
   }
}

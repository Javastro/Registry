package org.javastro.ivoa.registry.tap;

import io.quarkus.test.junit.QuarkusTestProfile;

/** Enables the optional RegTAP relational mechanism for a test. */
public class RegTapTestProfile implements QuarkusTestProfile {
   @Override
   public String getConfigProfile() {
      return "regtap";
   }
}

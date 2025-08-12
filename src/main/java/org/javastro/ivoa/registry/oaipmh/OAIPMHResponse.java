package org.javastro.ivoa.registry.oaipmh;


/*
 * Created on 08/08/2025 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

/**
 * Wrapper round the response. This is a bit of a kludge with {@link OAIPMHResponseBodyHandler} as at the Quarkus mechanism wants to XMLify the string response
 * from the XQuery layer.
 */
public class OAIPMHResponse {

   public OAIPMHResponse(String response) {
      this.response = response;
   }

   String response;
}

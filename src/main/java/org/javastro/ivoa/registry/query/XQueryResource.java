package org.javastro.ivoa.registry.query;


/*
 * Created on 07/08/2025 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.basex.core.BaseXException;
import org.javastro.ivoa.registry.Registry;


@Path("xquery")
public class XQueryResource {
   @Inject
   Registry registry;

   @POST
   @Produces(MediaType.TEXT_PLAIN)
   public String plain(String query) throws BaseXException {
     return registry.getRegistryQueryInterface().xquery(query);
   }
}

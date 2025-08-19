package org.javastro.ivoa.registry.query;


/*
 * Created on 19/08/2025 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.javastro.ivoa.entities.resource.Capability;
import org.javastro.ivoa.entities.vosi.capabilities.Capabilities;
import org.javastro.ivoa.registry.Registry;

import java.util.List;

@Tag(name="VOSI", description = "the standard VOSI endpoints")
@Path("vosi")
public class VOSIResource {
   @Inject
   Registry registry;
   @GET
   @Path("capabilities")
   @Produces(MediaType.APPLICATION_XML)
   public Capabilities getCapabilities() {
      return new Capabilities(registry.getSelfAsResource().getCapabilities());
   }
}

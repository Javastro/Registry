package org.javastro.ivoa.registry.tap;


/*
 * Created on 19/08/2025 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

import io.quarkus.arc.All;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.javastro.ivoa.entities.vosi.capabilities.Capabilities;
import org.javastro.ivoa.registry.Registry;

import java.util.List;

@Tag(name="VOSI", description = "specific VOSI endpoints just for TAP")
@io.quarkus.arc.profile.IfBuildProfile("regtap")
@Path("tap/VOSI")
public class VOSIResource {
   @Inject
   Registry registry;

   @Inject
   @All
   List<Capabilities> capabilities;

   @GET
   @Path("capabilities")
   @Produces(MediaType.APPLICATION_XML)
   public Capabilities getTAPCapabilities() {
      Capabilities ret = new Capabilities();
      if(capabilities == null) return ret;
      capabilities.forEach(c -> ret.getCapabilities().addAll(c.getCapabilities()));
      return ret;
   }
}

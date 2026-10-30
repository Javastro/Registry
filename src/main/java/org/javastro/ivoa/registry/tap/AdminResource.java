package org.javastro.ivoa.registry.tap;


import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.javastro.ivoa.registry.Registry;

import java.net.URI;

@io.quarkus.arc.profile.IfBuildProfile("regtap")
@Path("tap/admin")
@Tag(name = "TAP Administration", description = """
        perform TAP administration on the Registry - this will typically
        require authorization.
        """)
public class AdminResource {

   @Inject
   Registry registry;

   @Inject
   RegTapLoader loader;

   @POST
   @Path("load")
   @RolesAllowed("update")
   @Produces(MediaType.APPLICATION_JSON)
   @Operation(summary = "start a background job reloading the RegTAP relational tables from the XML registry content")
   public Response load() {
      try {
         return Response.accepted(loader.start()).location(URI.create("load/status")).build();
      } catch (IllegalStateException e) {
         return Response.status(Response.Status.CONFLICT).entity(loader.status()).build();
      }
   }

   @GET
   @Path("load/status")
   @RolesAllowed("update")
   @Produces(MediaType.APPLICATION_JSON)
   @Operation(summary = "progress of the current or most recent RegTAP load job")
   public RegTapLoader.LoadStatus loadStatus() {
      return loader.status();
   }

   @DELETE
   @Path("load")
   @RolesAllowed("update")
   @Produces(MediaType.APPLICATION_JSON)
   @Operation(summary = "request cancellation of the running RegTAP load job")
   public Response cancelLoad() {
      return loader.cancel() ? Response.accepted(loader.status()).build()
            : Response.status(Response.Status.CONFLICT).entity(loader.status()).build();
   }

}

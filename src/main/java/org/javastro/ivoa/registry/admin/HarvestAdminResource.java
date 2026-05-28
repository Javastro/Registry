package org.javastro.ivoa.registry.admin;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.javastro.ivoa.registry.Registry;
import org.javastro.ivoa.registry.harvesting.HarvestOrchestrator;
import org.javastro.ivoa.registry.harvesting.HarvestSource;
import org.javastro.ivoa.registry.harvesting.SourceStatus;
import org.jboss.resteasy.reactive.RestQuery;

import java.util.List;

@Path("harvesting")
@Tag(name = "Harvesting", description = """
        perform harvesting operations on the Registry - this will typically
        require authorization.
        """)
public class HarvestAdminResource {

   @Inject
   Registry registry;

   @Inject
   HarvestOrchestrator harvestOrchestrator;

   //IMPL would like to to have this duplication with HarvestSourceCatalog but it is not straightforward to inject the catalog into the service and then the service into the resource
   @ConfigProperty(name = "ivoa.harvesting.rofr.ivoid", defaultValue = "ivo://ivoa.net/rofr")
   String rofrIvoid;

   //harvesting below
   @Operation(summary = "Enqueue or immediately trigger a harvest",
         description = """
                    Enqueues the seed RofR source (default) or a specific source for harvesting.
                    If `immediate=true` the harvest runs synchronously in the request thread.
                    """)
   @POST
   @Path("harvest")
   @RolesAllowed("update")
   @Produces(MediaType.APPLICATION_JSON)
   public Response harvest(
         @RestQuery String sourceKey,
         @RestQuery boolean immediate) {

      String theKey = sourceKey != null ? sourceKey : rofrIvoid;

      if (immediate) {
         int count = harvestOrchestrator.triggerHarvest(theKey);
         return Response.accepted()
               .entity(new HarvestTriggerResult("triggered", count))
               .build();
      }
      boolean enqueued;

      enqueued = harvestOrchestrator.enqueue(theKey);

      return Response.accepted()
            .entity(new HarvestTriggerResult(enqueued ? "enqueued" : "skipped", 0))
            .build();
   }

   @Operation(summary = "Harvest queue / engine status",
         description = "Returns a snapshot of the harvest queue and source catalog counters.")
   @GET
   @Path("harvest/status")
   @RolesAllowed("update")
   @Produces(MediaType.APPLICATION_JSON)
   public Response harvestStatus() {
      return Response.ok(harvestOrchestrator.getStatus()).build();
   }

   @Operation(summary = "List all known harvest sources",
         description = "Returns all sources registered in the source catalog.")
   @GET
   @Path("harvest/sources")
   @RolesAllowed("update")
   @Produces(MediaType.APPLICATION_JSON)
   public Response harvestSources() {
      List<HarvestSource> sources = harvestOrchestrator.getSources();
      return Response.ok(sources).build();
   }

   @Operation(summary = "Add a new harvest source",
         description = "Adds a fresh harvest source to the catalogue.")
   @POST
   @Path("harvest/sources")
   @RolesAllowed("update")
   public Response addSource(@FormParam("ivoid") String ivoid, @FormParam("url") String url  ){
      harvestOrchestrator.addSource(ivoid, url);
      return Response.accepted().build();
   }
   @Operation(summary = "List recent harvest run records",
         description = "Returns the bounded recent run history across all sources.")
   @GET
   @Path("harvest/runs")
   @RolesAllowed("update")
   @Produces(MediaType.APPLICATION_JSON)
   public Response harvestRuns() {
      return Response.ok(harvestOrchestrator.getRecentRuns()).build();
   }

   @Operation(summary = "Disable a discovered source",
         description = "Sets the source status to DISABLED so it is no longer harvested.")
   @POST
   @Path("harvest/source/{key}/disable")
   @RolesAllowed("update")
   @Produces(MediaType.TEXT_PLAIN)
   public Response disableSource(@PathParam("key") String key) {
      boolean found = harvestOrchestrator.setSourceStatus(key, SourceStatus.DISABLED);
      if (!found) {
         return Response.status(Response.Status.NOT_FOUND)
               .entity("Source not found: " + key)
               .build();
      }
      return Response.accepted().entity("Source " + key + " disabled").build();
   }

   @Operation(summary = "reset a source",
         description = "resets the source and the database records for the source, so it can be reharvested from scratch. This is a destructive operation and should be used with caution.")
   @POST
   @Path("harvest/source/{key}/reset")
   @RolesAllowed("update")
   @Produces(MediaType.TEXT_PLAIN)
   public Response resetSource(@PathParam("key") String key) {
      boolean found = harvestOrchestrator.resetSource(key);
      if (!found) {
         return Response.status(Response.Status.NOT_FOUND)
               .entity("Source not found: " + key)
               .build();
      }
      return Response.accepted().entity("Source " + key + " reset").build();
   }

   // -------------------------------------------------------------------------
   // Inner DTO
   // -------------------------------------------------------------------------

   /** Simple JSON result for harvest trigger endpoint. */
   public record HarvestTriggerResult(String status, int recordsStored) {
   }

}

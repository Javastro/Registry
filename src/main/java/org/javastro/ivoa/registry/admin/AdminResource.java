package org.javastro.ivoa.registry.admin;


/*
 * Created on 07/08/2025 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.javastro.ivoa.registry.Registry;
import org.javastro.ivoa.registry.harvesting.HarvestOrchestrator;
import org.javastro.ivoa.registry.harvesting.HarvestSource;
import org.javastro.ivoa.registry.harvesting.RofrHarvestService;
import org.javastro.ivoa.registry.harvesting.SourceStatus;
import org.javastro.ivoa.schema.XMLValidator;
import org.jboss.resteasy.reactive.RestQuery;

import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.List;

@Path("admin")
@Tag(name = "Administration", description = """
        perform administrative operations on the Registry - this will typically
        require authorization.
        """)
public class AdminResource {

    @Inject
    Registry registry;

    @Inject
    RofrHarvestService rofrHarvestService;

    @Inject
    HarvestOrchestrator harvestOrchestrator;

    @Operation(summary = "adds resources to the registry",
            description = """
                    Add a resource to the database in strict mode - strict means
                       * validated against the VOResource schema
                    
                    Does not mean that there are any other checks like
                       * is the authority managed by this registry
                       * are related resources actually resolvable
                       * etc.
                    """)
    @POST
    @Path("strictAdd")
    @RolesAllowed("update")
    @Produces(MediaType.TEXT_PLAIN)
    @Consumes(MediaType.APPLICATION_XML)
    public Response strictAdd(@RequestBody(required = true, description = """
            XML valid against VOResource schema
            
            Either a single ri:Resource or ri:VOResources is acceptable
            """) String resource) {
        XMLValidator validator = new XMLValidator();
        boolean result = validator.validate(new StreamSource(new StringReader(resource)));
        if (result) {
            registry.getRegistryStoreInterface().create(resource);
        } else {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            validator.printErrors(new PrintStream(os));
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(os.toString())
                    .build();
        }
        return Response.accepted().build();
    }

    @Operation(summary = "Trigger an incremental harvest from RofR",
            description = """
                    Immediately runs an incremental harvest from the IVOA Registry of Registries (RofR).
                    Only records that have changed since the last successful harvest are retrieved.
                    The first call always performs a full harvest.
                    """)
    @POST
    @Path("harvestRofr")
    @RolesAllowed("update")
    @Produces(MediaType.TEXT_PLAIN)
    public Response harvestRofr() {
        int count = rofrHarvestService.harvest();
        return Response.accepted()
                .entity("Harvested " + count + " records from RofR")
                .build();
    }

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
        if (immediate) {
            int count = harvestOrchestrator.triggerHarvest(sourceKey);
            return Response.accepted()
                    .entity(new HarvestTriggerResult("triggered", count))
                    .build();
        }
        boolean enqueued;
        if (sourceKey != null) {
            enqueued = harvestOrchestrator.enqueue(sourceKey);
        } else {
            enqueued = harvestOrchestrator.enqueueSeed() != null;
        }
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

    // -------------------------------------------------------------------------
    // Inner DTO
    // -------------------------------------------------------------------------

    /** Simple JSON result for harvest trigger endpoint. */
    public record HarvestTriggerResult(String status, int recordsStored) {
    }
}


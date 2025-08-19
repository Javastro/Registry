package org.javastro.ivoa.registry.admin;


/*
 * Created on 07/08/2025 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.javastro.ivoa.registry.Registry;
import org.javastro.ivoa.schema.XMLValidator;

import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;

@Path("admin")
@Tag(name="Administration",description = """
      perform administrative operations on the Registry - this will typically
      require authorization.
      """)
public class AdminResource {

    @Inject
    Registry registry;

    @Operation( summary = "adds resources to the registry",
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
          """) String resource){
        XMLValidator validator = new XMLValidator();
        boolean result = validator.validate(new StreamSource(new StringReader(resource)));
        if (result) {
            registry.getRegistryStoreInterface().create(resource);
        }
        else {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            validator.printErrors(new PrintStream(os));
            return Response.status(Response.Status.BAD_REQUEST)
                  .entity(os.toString())
                  .build();
        }
        return Response.accepted().build();
    }
}

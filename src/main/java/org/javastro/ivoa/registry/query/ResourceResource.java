package org.javastro.ivoa.registry.query;


/*
 * Created on 19/08/2025 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.basex.core.BaseXException;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.javastro.ivoa.entities.Ivoid;
import org.javastro.ivoa.entities.resource.Capability;
import org.javastro.ivoa.entities.resource.Resource;
import org.javastro.ivoa.entities.resource.Service;
import org.javastro.ivoa.registry.Registry;
import org.jboss.resteasy.reactive.RestQuery;

import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;

@Tag(name="Resource", description = "REST interface to the resource")
@Path("resource")
public class ResourceResource {

   @Inject
   Registry registry;

   @GET
   @Schema()
   public Resource getResource(
         @Parameter(
               description = "The ivoid of the resource",
               required = true,
               example = "ivo://authority/id")
         @RestQuery String id) throws BaseXException, URISyntaxException {
      Ivoid ivoid = new Ivoid(id);
     return registry.getRegistryQueryInterface().getResource(ivoid);
   }

   @GET
   @Path("capabilities")
   public List<Capability> getCapabilities(
         @Parameter(
               description = "The ivoid of the resource",
               required = true,
               example = "ivo://authority/id")
         @RestQuery String id) throws BaseXException, URISyntaxException {
      Ivoid ivoid = new Ivoid(id);
      Resource resource = registry.getRegistryQueryInterface().getResource(ivoid);
      if(resource instanceof Service s) {
         return s.getCapabilities();
      }
      else return Collections.emptyList();
   }
}

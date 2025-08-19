package org.javastro.ivoa.registry.graphql;


import jakarta.inject.Inject;
import org.basex.core.BaseXException;
import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Query;
import org.javastro.ivoa.entities.Ivoid;
import org.javastro.ivoa.entities.resource.Resource;
import org.javastro.ivoa.entities.resource.Service;
import org.javastro.ivoa.registry.Registry;

import java.net.URISyntaxException;
import java.util.List;

/*
 * Created on 18/08/2025 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */
@GraphQLApi
public class ResourceResource {
   @Inject
   Registry registry;

   @Query("allResources")
   @Description("get all the resources in the registry")
   public List<Resource> getResources() {
     return registry.getRegistryQueryInterface().listResources();
   }

   @Query("allServices")
   @Description("get all the Services in the registry")
   public List<Service> getservices() {
      return registry.getRegistryQueryInterface().listServices();
   }

   @Query("getResource")
   @Description("get a single resource by its identifier")
   public Resource getResource(String id) throws URISyntaxException, BaseXException {
      return registry.getRegistryQueryInterface().getResource(new Ivoid(id));
   }

   @Query("getService")
   @Description("get a single service by its identifier")
   public Service getService(String id) throws URISyntaxException, BaseXException {
      return (Service) registry.getRegistryQueryInterface().getResource(new Ivoid(id));
   }

}

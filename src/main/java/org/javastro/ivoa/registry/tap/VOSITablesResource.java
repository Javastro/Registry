/*
 * Copyright (c) 2026. Paul Harrison, University of Manchester.
 *
 */

package org.javastro.ivoa.registry.tap;


import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.javastro.ivoa.entities.vosi.tables.Tableset;
import org.javastro.ivoacore.tap.schema.SchemaProvider;

/*
 * Created on 03/03/2026 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */
@Tag(name="Table", description = "the standard VOSI Tables endpoint")
@Path("/tables")
public class VOSITablesResource {

   @Inject
   SchemaProvider  schemaProvider;

   @GET
   @Produces(MediaType.APPLICATION_XML)
   public Tableset tables() {
     return schemaProvider.asVOSI();
   }

}

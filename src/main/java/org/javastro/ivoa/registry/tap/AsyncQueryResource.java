/*
 * Copyright (c) 2026. Paul Harrison, University of Manchester.
 *
 */

package org.javastro.ivoa.registry.tap;


import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.javastro.ivoa.quarkus.tap.BaseAsyncTAPResource;
import org.javastro.ivoa.quarkus.tap.TAPHelper;

/**
 * Main Async TAP Query.
 * Created on 04/03/2026 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */
@Tag(name="TAP Query", description = "the TAP query endpoints")
@ApplicationScoped
@Path("async")
public class AsyncQueryResource extends BaseAsyncTAPResource {

   @Inject
   TAPHelper tapHelper;

   @Override
   protected TAPHelper getTapHelper() {
      return tapHelper;
   }
}

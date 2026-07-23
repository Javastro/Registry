/*
 * Copyright (c) 2026. Paul Harrison, University of Manchester.
 *
 */

package org.javastro.ivoa.registry.tap;


import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.javastro.ivoa.quarkus.tap.BaseSyncTAPResource;
import org.javastro.ivoa.quarkus.tap.TAPHelper;

/**
 * Main TAP Query.
 * This works by creating an asynchronous job and then waiting for it to complete,
 * returning the result if it does complete within a reasonable time, or an error VOTable if it doesn't.
 * The client can then poll the async endpoint if they want to wait longer.
 * Created on 04/03/2026 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */
@Tag(name = "TAP Query", description = "the TAP query endpoints")
@ApplicationScoped
@Path("sync")
public class QueryResource extends BaseSyncTAPResource {

   @ConfigProperty(name="ivoa.tap.sync-timeout-seconds", defaultValue = "5")
   int syncTimeoutSeconds;

   @Inject
   TAPHelper tapHelper;

   @Override
   protected TAPHelper getTapHelper() {
      return tapHelper;
   }

   @Override
   protected int getSyncWait() {
      return syncTimeoutSeconds;
   }
}

package org.javastro.ivoa.registry.ui;

import io.quarkiverse.qute.web.DataInitializer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.javastro.ivoa.quarkus.config.RegistrationMetadata;
import org.javastro.ivoa.registry.RegistryConfig;
import org.javastro.ivoa.registry.harvesting.HarvestingConfig;

import java.net.URL;

@Singleton
public class QuteInitializer implements DataInitializer {

   @Inject
   RegistryConfig registryConfig;

   @ConfigProperty(name="thisapp.baseAddress")
   URL baseUrl;

   @Inject
   RegistrationMetadata registrationMetadata;


   @Inject
   HarvestingConfig harvesting;

   @Override
   public void initialize(InitContext ctx) {
      if (ctx.path().endsWith("index")) { // IMPL could just add these globally
         ctx.templateInstance().data("baseURL", baseUrl);
         ctx.templateInstance().data("registryConfig", registryConfig);
         ctx.templateInstance().data("meta", registrationMetadata);
         ctx.templateInstance().data("harvesting", harvesting);
      }
   }
}

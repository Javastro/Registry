package org.javastro.ivoa.registry;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "ivoa.registry")
public interface RegistryConfig {

   /**
    * the "home" authority of this registry.
     * @return
    */
   @WithDefault("authority")
   String authority();
}

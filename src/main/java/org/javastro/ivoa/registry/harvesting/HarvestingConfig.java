package org.javastro.ivoa.registry.harvesting;


import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.nio.file.Path;

@ConfigMapping(prefix = "ivoa.registry.harvesting")
public interface HarvestingConfig {

  @WithDefault("http://rofr.ivoa.net/oai")
   String rofrUrl();

  @WithDefault("ivo://ivoa.net/rofr")
   String rofrIvoid();

   /**
    * Use a local copy of the harvesting catalogue.
    * @return
    */
  @WithDefault("false")
   boolean rofrUseLocal();

  interface Discovery {
     @WithDefault("true")
     boolean enabled();

     @WithDefault("100")
     int maxSources();

     @WithDefault("3")
     int maxDepth();

     @WithDefault("5")
     int maxPerRun();

     @WithDefault("true")
     boolean doXmlValidation();
  }

  Discovery discovery();

  @WithDefault("off")
  String cron();

  @WithDefault("harvestCache")
   Path cacheDir();

}

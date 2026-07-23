package org.javastro.ivoa.registry.tap;

import jakarta.inject.Inject;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.javastro.ivoacore.vosi.BaseVOSIResource;
import org.javastro.ivoacore.vosi.VOSIProvider;

@Tag(name="VOSI", description = "the standard VOSI endpoints")
@Path("/VOSI")
public class VOSIResource extends BaseVOSIResource {
   @Inject
   public VOSIResource(VOSIProvider provider) {
      super(provider);
   }
}

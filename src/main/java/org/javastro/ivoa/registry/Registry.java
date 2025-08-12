package org.javastro.ivoa.registry;
/*
 * Created on 30/05/2023 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.xml.bind.JAXBException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.javastro.ivoa.entities.resource.*;
import org.javastro.ivoa.entities.resource.registry.Authority;
import org.javastro.ivoa.entities.IvoaJAXBUtils;
import org.javastro.ivoa.entities.resource.registry.Harvest;
import org.javastro.ivoa.entities.resource.registry.OAIHTTP;
import org.javastro.ivoa.registry.internal.RegistryQueryInterface;
import org.javastro.ivoa.registry.internal.RegistryStoreInterface;
import org.jboss.logging.Logger;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class Registry {

    @ConfigProperty(name="ivoa.registry.baseAddress")
    URL baseUrl;

    @ConfigProperty(name="ivoa.registry.authority", defaultValue = "authority")
    String mainAuthority;
    @ConfigProperty(name="ivoa.dc.organizationName")
    String organizationName;
    @ConfigProperty(name="ivoa.dc.contactName")
    String contactName;


    @ConfigProperty(name="ivoa.dc.contactAddress")
    String contactAddress;
   @ConfigProperty(name="ivoa.dc.contactEmail")
   String contactEmail;
   @ConfigProperty(name="ivoa.dc.contactTelephone")
   String contactTelephone;

    Set<Authority> managedAuthorities;
    org.javastro.ivoa.entities.resource.registry.Registry thisRegistry;

    Authority authority = null;
    Set<RegistrySet> sets =  Set.of(RegistrySet.IVO_MANAGED);


   @Inject
    RegistryStoreInterface registryStoreInterface;

    @Inject
   RegistryQueryInterface registryQueryInterface;

    @Inject
    Logger log;

    private final XMLUtils xmlUtils = new XMLUtils();

    void onStart(@Observes StartupEvent ev) {
        log.info("registry starting up");
       registryStoreInterface.open();
       try {
          authority = IvoaJAXBUtils.unmarshall(Objects.requireNonNull(this.getClass().getResourceAsStream("/AuthorityTemplate.xml")), Authority.class);
          authority.setIdentifier("ivo://"+mainAuthority);
          Creator c = authority.getCuration().getCreators().get(0);
          c.setName(ResourceName.builder().withValue(organizationName).build());
          setContact(authority);
          registryStoreInterface.create(xmlUtils.marshall(authority));
       } catch (JAXBException | IOException | SAXException e) {
          throw new RuntimeException("cannot create authority",e); //IMPL this is probably terminal at this stage.
       }

       try {
          thisRegistry = IvoaJAXBUtils.unmarshall(Objects.requireNonNull(this.getClass().getResourceAsStream("/RegistryTemplate.xml")), org.javastro.ivoa.entities.resource.registry.Registry.class);
          thisRegistry.setIdentifier("ivo://"+mainAuthority+"/Registry");
          setContact(thisRegistry);
          List<Capability> caps = thisRegistry.getCapabilities();
          caps.clear();
          caps.add(Harvest.builder().withStandardID("ivo://ivoa.net/std/Registry")
                      .addInterfaces(List.of(
                            OAIHTTP.builder()
                                  .withRole("std")
                                  .addAccessURLs(List.of(
                                        new AccessURL(baseUrl.toURI().resolve(new URI("./oai")).toString(),"base")
                                  ))
                                  .build()
                      ))
                .build()
          );

          this.managedAuthorities = Set.of(authority);

          registryStoreInterface.create(xmlUtils.marshall(thisRegistry));
       } catch (JAXBException | SAXException | IOException | URISyntaxException e) {
          throw new RuntimeException("cannot create registry record",e);
       }


      registryQueryInterface.open();

    }

    void onStop(@Observes ShutdownEvent ev) {
       log.info("registry stopping");
       registryQueryInterface.close();
       registryStoreInterface.close();
    }

    public Registry() {


    }

   public String getOAIUrl() {
       return "/oai"; //FIXME needs to be the full deployed URL
   }

   public String getName() {
       return thisRegistry.getShortName();
   }

   public String getContactEmail() {
      return contactEmail;
   }

   public String getContactAddress() {
      return contactAddress;
   }

   public String getContactName() {
      return contactName;
   }

   public String getOrganizationName() {
      return organizationName;
   }

   public String getMainAuthority() {
      return mainAuthority;
   }

   public Set<RegistrySet> getSets() {
       return sets;
   }
   public org.javastro.ivoa.entities.resource.registry.Registry getSelfAsResource() {
       return thisRegistry;
   }
   public RegistryStoreInterface getRegistryStoreInterface() {
      return registryStoreInterface;
   }

   public RegistryQueryInterface getRegistryQueryInterface() {
      return registryQueryInterface;
   }


   private void setContact(Resource res) {
      Curation cur = res.getCuration();
      Contact contact = cur.getContacts().get(0);
      contact.setAddress(contactAddress);
      contact.setEmail(contactEmail);
      contact.setTelephone(contactTelephone);

      ResourceName name = new ResourceName();
      name.setValue(contactName);
      contact.setName(name);
   }
}

package org.javastro.ivoa.registry.oaipmh;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.javastro.ivoa.entities.Ivoid;
import org.javastro.ivoa.entities.resource.registry.oaipmh.*;
import org.javastro.ivoa.registry.Registry;
import org.javastro.ivoa.registry.RegistrySet;
import org.javastro.ivoa.registry.XMLUtils;
import org.javastro.ivoa.registry.internal.RegistryQueryInterface;
import org.javastro.ivoa.schema.Namespaces;
import org.jboss.resteasy.reactive.RestQuery;
import org.xml.sax.SAXException;

import javax.xml.namespace.QName;
import java.io.IOException;
import java.net.URISyntaxException;
import java.time.*;
import java.util.ArrayList;
import java.util.List;

/*
 * Created on 28/05/2023 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */
@Tag(name="OAI-PMH", description = "The metadata harvesting interface")
@Path("oai")
public class OaiPMHResource {


    private final XMLUtils xmlUtils = new XMLUtils();


    @Inject
    Registry registry;

    private final List<OAIPMHerrorType> errors = new ArrayList<>();

    public OaiPMHResource() {

    }

    // was nice to make the verb appear in OpenAPI - however non-matching did not work
    public  enum OAIVerb {
       Identify,
       ListSets,
       ListMetadataFormats,
       GetRecord,
       ListRecords,
       ListIdentifiers
    }

    @GET
    @Produces(MediaType.TEXT_XML)
    public Response action(@Parameter(required = true,description = "the action") @RestQuery("verb") String verb,
                            @RestQuery String identifier,
                           @RestQuery String metadataPrefix,
                           @RestQuery ZonedDateTime from,
                           @RestQuery ZonedDateTime until,
                           @RestQuery String set
                           //    , @RestQuery String resumptionToken // TODO add resumption token support
    ) throws JAXBException, IOException, SAXException
    {

        RegistryQueryInterface qi = registry.getRegistryQueryInterface();
        
        OAIPMH.Builder<Void> builder = OAIPMH.builder().withResponseDate(Instant.now().atZone(ZoneOffset.UTC));


        switch (verb) {

           case "Identify":
                builder.withRequest(RequestType.builder().withValue("Identify").build());
                IdentifyType ident = IdentifyType.builder().
                      withBaseURL(registry.getOAIUrl())
                      .withDeletedRecord(DeletedRecordType.NO)
                      .withRepositoryName(registry.getName())
                      .withProtocolVersion("2.0")
                      .withAdminEmails(registry.getContactEmail())
                      .withEarliestDatestamp("1970-01-01T00:00:00Z") //TODO actually put accurate value in?
                      .withGranularity(GranularityType.YYYY_MM_DD_THH_MM_SS_Z)
                      .withDescriptions(new DescriptionType(new JAXBElement<>(new QName(Namespaces.RI.getNamespace(),
                            "Resource",Namespaces.RI.getPrefix()),
                            org.javastro.ivoa.entities.resource.registry.Registry.class,
                            registry.getSelfAsResource())))
                      .build();

                builder.withIdentify(ident);
                OAIPMH response = builder.build();
                return Response.ok(new OAIPMHResponse(xmlUtils.marshallOAI(response))).build();
           case "ListSets":
                builder.withRequest(RequestType.builder().withValue("ListSets").build());
                ListSetsType listSets = ListSetsType.builder()
                      .addSets(registry.getSets().stream().map(RegistrySet::asOAIPMH).toList())
                      .build();
                builder.withListSets(listSets);
                return Response.ok(new OAIPMHResponse(xmlUtils.marshallOAI(builder.build()))).build();
           case "ListMetadataFormats":

                builder.withRequest(RequestType.builder().withValue("ListMetadataFormats").build());
                ListMetadataFormatsType listMeta = ListMetadataFormatsType.builder()
                      .addMetadataFormats(List.of(
                            new MetadataFormatType("oai_dc","https://www.openarchives.org/OAI/2.0/oai_dc.xsd","http://www.openarchives.org/OAI/2.0/oai_dc"),
                            new MetadataFormatType("ivo://authority.changeme/Registry","https://www.ivoa.net/xml/RegistryInterface/RegistryInterface-1.0.xsd","http://www.ivoa.net/xml/RegistryInterface/v1.0")
                      ))
                      .build();

                builder.withListMetadataFormats(listMeta);
                return Response.ok(new OAIPMHResponse(xmlUtils.marshallOAI(builder.build()))).build();

           case "GetRecord":
                Ivoid id = null ;
               try {
                   id = new Ivoid(identifier);
               } catch (URISyntaxException e) {
                   errors.add(new OAIPMHerrorType("bad identifier -"+identifier,OAIPMHerrorcodeType.ID_DOES_NOT_EXIST));
               }
               if(metadataPrefix==null || !metadataPrefix.matches("ivo_vor|oai_dc"))
                {
                    errors.add(new OAIPMHerrorType(metadataPrefix==null?"no metadataPrefix specified": metadataPrefix+" is not valid",OAIPMHerrorcodeType.NO_METADATA_FORMATS));
                }
                if(!errors.isEmpty())
                {
                    builder.withRequest(RequestType.builder().withValue("GetRecord").withMetadataPrefix(metadataPrefix).withIdentifier(identifier).build());
                    builder.addErrors(errors);
                    return Response.ok(new OAIPMHResponse(xmlUtils.marshallOAI(builder.build()))).build();
                }
                else {
                    return Response.ok(new OAIPMHResponse(qi.oaiGetRecord(id,metadataPrefix))).build();
                }


            case "ListRecords":
               if(metadataPrefix==null || !metadataPrefix.matches("ivo_vor|oai_dc"))
               {
                  errors.add(new OAIPMHerrorType(metadataPrefix==null?"no metadataPrefix specified must be 'ivo_vor' or 'oai_dc'": metadataPrefix+" is not valid",OAIPMHerrorcodeType.NO_METADATA_FORMATS));
               }
               if(!errors.isEmpty())
               {
                  builder.withRequest(RequestType.builder().withValue("ListRecords").withMetadataPrefix(metadataPrefix).withSet(set).build());
                  builder.addErrors(errors);
                  return Response.ok(new OAIPMHResponse(xmlUtils.marshallOAI(builder.build()))).build();
               }
               else {
                  return Response.ok(new OAIPMHResponse(qi.oaiListRecords(from, until, set, metadataPrefix))).build();
               }
            case "ListIdentifiers":
               if(metadataPrefix==null || !metadataPrefix.matches("ivo_vor|oai_dc"))
               {
                  errors.add(new OAIPMHerrorType(metadataPrefix==null?"no metadataPrefix specified - must be 'ivo_vor' or 'oai_dc' ": metadataPrefix+" is not valid",OAIPMHerrorcodeType.NO_METADATA_FORMATS));
               }
               if(!errors.isEmpty())
               {
                  builder.withRequest(RequestType.builder().withValue("ListIdentifiers").withMetadataPrefix(metadataPrefix).withSet(set).build());
                  builder.addErrors(errors);
                  return Response.ok(new OAIPMHResponse(xmlUtils.marshallOAI(builder.build()))).build();
               }
               else {
                  return Response.ok(new OAIPMHResponse(qi.oaiListIDs(from, until, set, metadataPrefix))).build();
               }
            default:
                builder.withRequest(RequestType.builder().withValue(verb.toString()).build());
                builder.addErrors(new OAIPMHerrorType(verb +" is not recognised",OAIPMHerrorcodeType.BAD_VERB));
                return Response.ok(new OAIPMHResponse(xmlUtils.marshallOAI(builder.build()))).build();
        }

    }




}

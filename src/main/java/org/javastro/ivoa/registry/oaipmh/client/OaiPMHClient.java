package org.javastro.ivoa.registry.oaipmh.client;


/*
 * Created on 07/02/2025 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.util.ValidationEventCollector;
import org.javastro.ivoa.entities.IvoaJAXBContextFactory;
import org.javastro.ivoa.entities.resource.registry.oaipmh.*;
import org.javastro.ivoa.schema.SchemaMap;
import org.jboss.logging.Logger;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xmlresolver.ResolverFeature;
import org.xmlresolver.XMLResolver;
import org.xmlresolver.XMLResolverConfiguration;
import org.xmlresolver.catalog.entry.EntryCatalog;

import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.StringReader;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletionStage;

/**
 * An OAI-PMH client that creates an object-tree representation of the responses.
 */
public class OaiPMHClient {
   private static final Logger LOG = Logger.getLogger(OaiPMHClient.class);

   private final OaiPMHClientInterface oaiPMHInterface;
   private final JAXBContext jaxbconxt ;
   private final String url;
   private final boolean doXMLValidation;

   public static void main(String[] args) {

   }

   /**
    * Create an OaiPMH client.
    * @param url the OAIPMH endpoint to connect to.
    * @param doXMLValidation  if true then XML validation is done, otherwise the more lax interpretation that JAXB does is the default.
    */
   public  OaiPMHClient(String url, boolean doXMLValidation){
      this.url = url;
      this.doXMLValidation = doXMLValidation;
      try {
         jaxbconxt = IvoaJAXBContextFactory.newInstance();
      } catch (JAXBException e) {
         throw new RuntimeException(e);
      }
      oaiPMHInterface = QuarkusRestClientBuilder.newBuilder()
            .baseUri(URI.create(url))
            .build(OaiPMHClientInterface.class);

   }

   public IdentifyType identify(){
      OAIPMH oai = processOAIPMH(oaiPMHInterface.identify()).toCompletableFuture().join();
      return oai.getIdentify();
   }

   public ListMetadataFormatsType listMetadataFormats(){
      //IMPL  ignoring the optional identifier type
      OAIPMH oai = processOAIPMH(oaiPMHInterface.listMetadataFormats( null)).toCompletableFuture().join();
      return oai.getListMetadataFormats();
   }

   public ListSetsType listSets(){
      OAIPMH oai = processOAIPMH(oaiPMHInterface.listSets()).toCompletableFuture().join();
      return oai.getListSets();
   }

   public RecordType getRecord(String identifier, String metadataPrefix){
      OAIPMH oai = processOAIPMH(oaiPMHInterface.getRecord(identifier, metadataPrefix)).toCompletableFuture().join();
      return oai.getGetRecord().getRecord();
   }

   public ListIdentifiersType listIdentifiers(String metadataPrefix, String set, Instant from, Instant until, String resumptionToken ){
      String fromstr = from!=null?from.atZone(ZoneId.of("UTC")).format(DateTimeFormatter.ISO_INSTANT):null;
      String untilstr = until!=null?until.atZone(ZoneId.of("UTC")).format(DateTimeFormatter.ISO_INSTANT):null;
      OAIPMH oai = processOAIPMH(oaiPMHInterface.listIdentifiers(metadataPrefix,fromstr,untilstr,set,resumptionToken)).toCompletableFuture().join();
      return oai.getListIdentifiers();
   }

   public ListRecordsType listRecords(String metadataPrefix, String set, Instant from, Instant until, String resumptionToken ){
      String fromstr = from!=null?from.atZone(ZoneId.of("UTC")).format(DateTimeFormatter.ISO_INSTANT):null;
      String untilstr = until!=null?until.atZone(ZoneId.of("UTC")).format(DateTimeFormatter.ISO_INSTANT):null;
      OAIPMH oai = processOAIPMH(oaiPMHInterface.listRecords(metadataPrefix,fromstr,untilstr,set,resumptionToken)).toCompletableFuture().join();
      return oai.getListRecords();
   }




   private CompletionStage<OAIPMH> processOAIPMH(CompletionStage<String> response)
   {
      return response.thenApply(xmlResponse -> {
         OAIPMH retval;
         try {

            Unmarshaller um= jaxbconxt.createUnmarshaller();
            ValidationEventCollector validationEventCollector = new ValidationEventCollector();
            um.setEventHandler(validationEventCollector);
            if (doXMLValidation) {
               SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
               Schema schema = sf.newSchema(SchemaMap.getRegistrySchemaAsSources());
               sf.setResourceResolver(makeXMLResolver().getLSResourceResolver());
               um.setSchema(schema);
            }

            Source ss = new StreamSource(new StringReader(xmlResponse));
            //TODO add schema validation.

            JAXBElement<OAIPMH> retvalEl = um.unmarshal(ss, OAIPMH.class);
            retval = retvalEl.getValue();
            if (!retval.getErrors().isEmpty()) {
                for(OAIPMHerrorType e:retval.getErrors())
                {
                   LOG.error(e); //TODO throw as exception?
                }
            }

         } catch (JAXBException | SAXException e) {
            throw new RuntimeException(e);
         }
         return retval;
      }).exceptionally(throwable -> {
         // Handle errors
         System.err.println("Error: " + throwable.getMessage());
         return null; // Or throw a RuntimeException if you prefer
      });
   }

   public String getUrl() {
      return url;
   }

  private XMLResolver makeXMLResolver() {
      XMLResolverConfiguration config = new XMLResolverConfiguration();
      config.setFeature(ResolverFeature.DEFAULT_LOGGER_LOG_LEVEL, "info");
      config.setFeature(ResolverFeature.ACCESS_EXTERNAL_DOCUMENT, "all");// it would be nice if this actually did stop external lookups as suggested
      config.setFeature(ResolverFeature.THROW_URI_EXCEPTIONS, true);
      config.setFeature(ResolverFeature.ALWAYS_RESOLVE, true);
      config.setFeature(ResolverFeature.PREFER_PUBLIC, false);
      config.setFeature(ResolverFeature.CLASSPATH_CATALOGS, true);
      config.setFeature(ResolverFeature.CLASSLOADER, ClassLoader.getSystemClassLoader());//trying to get a classloader that will load resources from inside jar...

      org.xmlresolver.CatalogManager manager = config
            .getFeature(ResolverFeature.CATALOG_MANAGER);
      URI caturi = URI.create("https://ivoa.net/vodml/catalog.xml");//IMPL - not sure is this should be more obviously false.
      config.addCatalog(caturi.toString());
      EntryCatalog cat = manager.loadCatalog(caturi, new InputSource(new StringReader(SchemaMap.xmlCatalogue)));
      //N.B. cat not actually needed
     return new XMLResolver(config);
   }

}

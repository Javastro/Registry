package org.javastro.ivoa.registry.oaipmh.client;

/*
 * Created on 13/06/2026 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

import jakarta.annotation.Nullable;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.util.ValidationEventCollector;
import net.sf.saxon.s9api.SaxonApiException;
import org.javastro.ivoa.entities.oai.oaipmh.*;
import org.javastro.ivoa.registry.XMLUtils;
import org.javastro.ivoa.schema.SchemaMap;
import org.jboss.logging.Logger;
import org.json.XML;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xmlresolver.ResolverFeature;
import org.xmlresolver.XMLResolver;
import org.xmlresolver.XMLResolverConfiguration;
import org.xmlresolver.catalog.entry.EntryCatalog;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * OAIPMH client using java.net.http transport. i.e. not relying on Quarkus.
 */
public class OaiPMHClient {
    private static final Logger LOG = Logger.getLogger(OaiPMHClient.class);

    private final HttpClient httpClient;
    private final JAXBContext jaxbconxt;
    private final boolean doXMLValidation;
    private final URI baseUri;
    private final OaiInfoExtractor oaiInfoExtractor;
    private final DocumentBuilder docBuilder;
    private final XMLUtils xmlUtils = new XMLUtils();

   public final static String METADATA_PREFIX = "ivo_vor";
   public static final String IVO_MANAGED_SET = "ivo_managed";

    public OaiPMHClient(String url, boolean doXMLValidation) {
        this.doXMLValidation = doXMLValidation;
        this.baseUri = URI.create(url);
        this.httpClient = HttpClient.newHttpClient();
        try {
            jaxbconxt = JAXBContext.newInstance(
                  "org.javastro.ivoa.entities.oai.oaipmh"
                        + ":org.javastro.ivoa.entities.oai.dublincore"
                        + ":org.javastro.ivoa.entities.oai.dublincore.simple");
            oaiInfoExtractor = new OaiInfoExtractor();
           DocumentBuilderFactory dbf = DocumentBuilderFactory
                 .newInstance();
           dbf.setNamespaceAware(true);
           dbf.setValidating(false);
           docBuilder = dbf.newDocumentBuilder();
        } catch (JAXBException | SaxonApiException | ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    public IdentifyType identify() throws OaiPMHException {
        UriBuilder uriBuilder = UriBuilder.fromUri(baseUri).queryParam("verb", "Identify");
        OAIPMH oai = processOAIPMH(request(uriBuilder.build()));
        return oai.getIdentify();
    }

    public ListMetadataFormatsType listMetadataFormats() throws OaiPMHException {
        UriBuilder uriBuilder = UriBuilder.fromUri(baseUri).queryParam("verb", "ListMetadataFormats");
        OAIPMH oai = processOAIPMH(request(uriBuilder.build()));
        return oai.getListMetadataFormats();
    }

    public ListSetsType listSets() throws OaiPMHException {
        UriBuilder uriBuilder = UriBuilder.fromUri(baseUri).queryParam("verb", "ListSets");
        OAIPMH oai = processOAIPMH(request(uriBuilder.build()));
        return oai.getListSets();
    }

    public RecordType getRecord(String identifier, String metadataPrefix) throws OaiPMHException {
        UriBuilder uriBuilder = UriBuilder.fromUri(baseUri).queryParam("verb", "GetRecord");
        uriBuilder.queryParam("identifier", identifier);
        uriBuilder.queryParam("metadataPrefix", metadataPrefix);
        OAIPMH oai = processOAIPMH(request(uriBuilder.build()));
        return oai.getGetRecord().getRecord();
    }

    public ListIdentifiersType listIdentifiers(String metadataPrefix, String set, Instant from, Instant until, String resumptionToken) throws OaiPMHException {
        UriBuilder uriBuilder = UriBuilder.fromUri(baseUri).queryParam("verb", "ListIdentifiers");
        processParams(uriBuilder, metadataPrefix, set, from, until, resumptionToken);
        OAIPMH oai = processOAIPMH(request(uriBuilder.build()));
        return oai.getListIdentifiers();
    }

    public ListRecordsType listRecords(String metadataPrefix, String set, Instant from, Instant until, String resumptionToken) throws OaiPMHException {

        UriBuilder uriBuilder = UriBuilder.fromUri(baseUri).queryParam("verb", "ListRecords");
        processParams(uriBuilder, metadataPrefix, set, from, until, resumptionToken);
        OAIPMH oai = processOAIPMH(request(uriBuilder.build()));
        return oai.getListRecords();
    }


    public OaiInfoExtractor.OaiRecordInfo ListRecordsRaw(Path path, String metadataPrefix, String set, Instant from, Instant until, String resumptionToken) throws OaiPMHException, IOException, InterruptedException {
        UriBuilder uriBuilder = UriBuilder.fromUri(baseUri).queryParam("verb", "ListRecords");
        processParams(uriBuilder, metadataPrefix, set, from, until, resumptionToken);
        final URI uri = uriBuilder.build();
        HttpRequest request = HttpRequest.newBuilder(uri)
              .GET()
              .build();
        LOG.infov("Raw ListRecords request to {0}", uri);
        HttpResponse<Path> response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(path));
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new OaiPMHException("OAI-PMH request to " + uri + " failed with status " + status );
        }
        LOG.infov("Raw ListRecords response written to : {0}", response.body());

        return oaiInfoExtractor.extract(response.body());
    }

   /**
    * check that the registry is set up to be harvestable
    * @return true if OK
    */
   public boolean validate(){
      boolean retval = true;
      try {
         ListMetadataFormatsType mt = this.listMetadataFormats();

         if(mt == null || mt.getMetadataFormats().stream().noneMatch(m -> m.getMetadataPrefix().equals("ivo_vor")))
         {
            retval = false;
            LOG.errorv("the Registry at {0} does not serve metadata format {1}",this.getUri(), METADATA_PREFIX);
         }

         ListSetsType st = this.listSets();
         if(st == null || st.getSets().stream().noneMatch(m -> m.getSetSpec().equals(IVO_MANAGED_SET)))
         {
            retval = false;
            LOG.errorv("the Registry at {0} does not have a harvestable set {1}",this.getUri(),IVO_MANAGED_SET );
         }
      } catch (OaiPMHException e) {
         retval = false;
         LOG.errorv("the Registry at {0} has returned an error {}",this.getUri(), e.getMessage());

      }

      return retval;
   }


    private void processParams(UriBuilder req, String metadataPrefix, String set, Instant from, Instant until, String resumptionToken) throws OaiPMHException {

        if (resumptionToken != null && !resumptionToken.isBlank())
            req.queryParam("resumptionToken", resumptionToken.trim());
        else { //only set these if no resumption token
            if (from != null) req.queryParam("from", getUtc(from));
            if (until != null) req.queryParam("until", getUtc(until));
            req.queryParam("metadataPrefix", metadataPrefix.trim()); // IMPL esa reg does not like metadata prefix set when resumption token
            if (set != null && !set.isBlank()) req.queryParam("set", set.trim());
        }
    }

    @Nullable
    private static String getUtc(Instant from) {
        return from != null ? from.truncatedTo(java.time.temporal.ChronoUnit.SECONDS).atZone(ZoneId.of("UTC")).format(DateTimeFormatter.ISO_INSTANT) : null;
    }

    private String request(URI uri) throws OaiPMHException {
        LOG.infov("Making OAI-PMH request to {0}", uri);
        HttpRequest request = HttpRequest.newBuilder(uri)
              .GET()
              .build();
        try {
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = resp.statusCode();
            String payload = resp.body() == null ? "" : resp.body();
            if (status < 200 || status >= 300) {
                throw new OaiPMHException("OAI-PMH request to " + uri + " failed with status " + status + ": " + payload);
            }
            return payload;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


    private OAIPMH processOAIPMH(String xmlResponse) throws OaiPMHException {

        OAIPMH retval;
        try {
            Unmarshaller um = jaxbconxt.createUnmarshaller();
            ValidationEventCollector validationEventCollector = new ValidationEventCollector();
            um.setEventHandler(validationEventCollector);
            if (doXMLValidation) {
                SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
                Schema schema = sf.newSchema(SchemaMap.getRegistrySchemaAsSources());
                sf.setResourceResolver(makeXMLResolver().getLSResourceResolver());
                um.setSchema(schema);
            }

            Source ss = new StreamSource(new StringReader(xmlResponse));
            JAXBElement<OAIPMH> retvalEl = um.unmarshal(ss, OAIPMH.class);
            retval = retvalEl.getValue();
            if (!retval.getErrors().isEmpty()) {
                for (OAIPMHerrorType e : retval.getErrors()) {
                    LOG.warn(e);
                }
                throw new OaiPMHException(retval.getErrors(), "Errors returned from OAI-PMH request: ");
            }
        } catch (JAXBException | SAXException e) {
            throw new OaiPMHException("failed to interpret XML", e);
        }
        return retval;

        //       throw new RuntimeException("OAI-PMH request to " + url + " failed: " + throwable.getMessage(), throwable);

    }

    public URI getUri() {
        return baseUri;
    }

    private XMLResolver makeXMLResolver() {
        XMLResolverConfiguration config = new XMLResolverConfiguration();
        config.setFeature(ResolverFeature.DEFAULT_LOGGER_LOG_LEVEL, "info");
        config.setFeature(ResolverFeature.ACCESS_EXTERNAL_DOCUMENT, "all");
        config.setFeature(ResolverFeature.THROW_URI_EXCEPTIONS, true);
        config.setFeature(ResolverFeature.ALWAYS_RESOLVE, true);
        config.setFeature(ResolverFeature.PREFER_PUBLIC, false);
        config.setFeature(ResolverFeature.CLASSPATH_CATALOGS, true);
        config.setFeature(ResolverFeature.CLASSLOADER, ClassLoader.getSystemClassLoader());

        org.xmlresolver.CatalogManager manager = config.getFeature(ResolverFeature.CATALOG_MANAGER);
        URI caturi = URI.create("https://ivoa.net/vodml/catalog.xml");
        config.addCatalog(caturi.toString());
        EntryCatalog cat = manager.loadCatalog(caturi, new InputSource(new StringReader(SchemaMap.xmlCatalogue)));
        return new XMLResolver(config);
    }

   /**
    * This will
    * @param records - a records response from {@link org.javastro.ivoa.registry.oaipmh.client.OaiPMHClient}
    * @return
    * @throws IOException
    * @throws SAXException
    * @throws TransformerException
    */
   public String serializeRecords(List<RecordType> records) throws IOException, SAXException, TransformerException {
      Document resourcesDOM = docBuilder.parse(XMLUtils.class.getResourceAsStream("/VOResourcesBase.xml"));
      for(RecordType r : records) {
         if(r.getMetadata()!=null && r.getMetadata().getAny() instanceof Node resourceNode) {
            Node imported = resourcesDOM.importNode(resourceNode, true);
            resourcesDOM.getDocumentElement().appendChild(imported);
         }
      }
      //IMPL - there might be some delarations at OAI level that are lost.....
      return xmlUtils.rawOAIToResources(new DOMSource(resourcesDOM));
   }

}




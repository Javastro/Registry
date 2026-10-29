package org.javastro.ivoa.registry;


/*
 * Created on 06/08/2025 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

import graphql.com.google.common.collect.Lists;
import jakarta.xml.bind.*;
import net.sf.saxon.s9api.*;
import org.javastro.ivoa.entities.IvoaJAXBContextFactory;
import org.javastro.ivoa.entities.resource.Resource;
import org.javastro.ivoa.entities.resource.registry.iface.ResourceInstance;
import org.javastro.ivoa.entities.resource.registry.iface.VOResources;
import org.javastro.ivoa.entities.oai.oaipmh.IdentifyType;
import org.javastro.ivoa.entities.oai.oaipmh.OAIPMH;
import org.javastro.ivoa.entities.oai.oaipmh.RecordType;
import org.javastro.ivoa.schema.Namespaces;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;

//TODO replace this with similar from ivoacore:common
public class XMLUtils {

   private final static Namespaces ns = Namespaces.RI;
   private static final Logger log = LoggerFactory.getLogger(XMLUtils.class);
   private final Marshaller marshaller;
   private final Processor processor = new Processor(false);
   private final  XsltExecutable cleaningStylesheet;
   private final DocumentBuilder docBuilder;
   private final JAXBContext context;
   private final Unmarshaller unmarshaller;

   public XMLUtils() {
      try {
         context = IvoaJAXBContextFactory.newInstance();
         marshaller = context.createMarshaller();
         marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
         marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
         XsltCompiler compiler = processor.newXsltCompiler();
         cleaningStylesheet = compiler.compile(new StreamSource(XMLUtils.class.getResourceAsStream("/xslt/fixHarvest.xslt")));
         DocumentBuilderFactory dbf = DocumentBuilderFactory
               .newInstance();
         dbf.setNamespaceAware(true);
         dbf.setValidating(false);
         docBuilder = dbf.newDocumentBuilder();
         unmarshaller = context.createUnmarshaller();


      } catch (ParserConfigurationException |JAXBException | SaxonApiException e) {
         throw new RuntimeException(e); // should not happen in practice
      }
   }

   public  String marshallResources(VOResources a) {
      return marshallElement(new JAXBElement<VOResources>(
            new QName(ns.getNamespace(), "VOResources", ns.getPrefix()),
            VOResources.class, a));
   }


   @SuppressWarnings("unchecked")
   public <T> String marshall(T a) {
      return marshallElement(new JAXBElement<T>(
            new QName(ns.getNamespace(), "Resource", ns.getPrefix()),
            (Class<T>) a.getClass(), a));
   }

   public List<Resource> unmarshal(String xml) throws JAXBException {
      Object rv = unmarshaller.unmarshal(new StringReader(xml));
      List<Resource> retval = Lists.newArrayList();
      if (rv instanceof Resource r) {
            retval = Lists.newArrayList(r);
      }
      else if (rv instanceof ResourceInstance r) {
         retval = Lists.newArrayList(r.getValue());
      }
      else if (rv instanceof VOResources j) {
         retval = j.getResources();
      }

      return retval;

   }


   /**
    * Convert an OAI-PMH ListRecords response to a VOResources document.
    * @param input path to raw OAI-PMH ListRecords response XML file.
    * @return XML string containing a VOResources document with all the resources from the OAI-PMH ListRecords response in the input file.
    */
   public String rawOAIToResources(Path input) {
      return rawOAIToResources(new StreamSource(input.toFile()));
   }

   /**
    * Convert an OAI-PMH ListRecords response to a VOResources document.
    * @param input path to raw OAI-PMH ListRecords response XML file.
    * @return XML string containing a VOResources document with all the resources from the OAI-PMH ListRecords response in the input file.
    */
   public String rawOAIToResources(Source input) {
      try {
         StringWriter sw = new StringWriter();
         Serializer out = processor.newSerializer(sw);
         out.setOutputProperty(Serializer.Property.METHOD, "xml");
         out.setOutputProperty(Serializer.Property.INDENT, "yes");
         Xslt30Transformer transformer = cleaningStylesheet.load30();
         transformer.transform(input, out);
         return sw.toString();
      } catch (SaxonApiException e) {
         throw new RuntimeException(e);
      }
   }


   /**
    * Marshal an Object into a XML string. The XML is cleaned up with the cleaning stylesheet to ensure that it is a valid VOResources document.
    * @param element the input object.
    * @return
    */
   public String marshallElement(JAXBElement<?> element) {


      try {
         StringWriter sw = new StringWriter();
         Serializer out = processor.newSerializer(sw);
         out.setOutputProperty(Serializer.Property.METHOD, "xml");
         out.setOutputProperty(Serializer.Property.INDENT, "yes");
         Xslt30Transformer transformer = cleaningStylesheet.load30();
         Document doc = docBuilder.newDocument(); // IMPL not sure is this is the most efficient intermediate form?
         marshaller.marshal(element, doc);
         transformer.transform( new DOMSource(doc), out);
         return sw.toString();
      } catch (JAXBException | SaxonApiException e) {
         throw new RuntimeException(e);
      }
   }


   /**
    * Concatenate all the OAI ListRecords chunk files (*.xml) in a directory into a single
    * VOResources document, using the fixHarvest stylesheet.
    * @param dir directory containing the cached chunk files.
    * @return path of a temporary file containing the combined XML (caller should delete it).
    */
   public java.nio.file.Path concatenateCache(java.nio.file.Path dir) {
      try {
         java.nio.file.Path tmp = java.nio.file.Files.createTempFile("concatCache", ".xml");
         try {
            Serializer out = processor.newSerializer(tmp.toFile());
            out.setOutputProperty(Serializer.Property.METHOD, "xml");
            out.setOutputProperty(Serializer.Property.INDENT, "yes");
            Xslt30Transformer transformer = cleaningStylesheet.load30();
            transformer.setStylesheetParameters(java.util.Map.of(
                  new net.sf.saxon.s9api.QName("inputDir"), new XdmAtomicValue(dir.toAbsolutePath().toString())));
            transformer.transform(new StreamSource(new StringReader("<x/>")), out);
            return tmp;
         } catch (SaxonApiException | RuntimeException e) {
            java.nio.file.Files.deleteIfExists(tmp);
            throw e;
         }
      } catch (SaxonApiException | IOException e) {
         throw new RuntimeException(e);
      }
   }

   public String marshallOAI(OAIPMH element) {
      StringWriter sw = new StringWriter();
      try {
         marshaller.marshal(element, sw);
      } catch (JAXBException e) {
         throw new RuntimeException(e);
      }
      return sw.toString();
   }

   public String serializeRecords(List<RecordType> records) throws IOException, SAXException, TransformerException {
      Document resourcesDOM = docBuilder.parse(XMLUtils.class.getResourceAsStream("/RegistryBase.xml"));
      for(RecordType r : records) {
         if(r.getMetadata()!=null && r.getMetadata().getAny() instanceof Node resourceNode) {
            Node imported = resourcesDOM.importNode(resourceNode, true);
            resourcesDOM.getDocumentElement().appendChild(imported);
         }
      }
      StringWriter writer = new StringWriter();
      Transformer transformer = TransformerFactory.newInstance().newTransformer();
      transformer.setOutputProperty(OutputKeys.INDENT, "yes");
      transformer.transform(new DOMSource(resourcesDOM), new StreamResult(writer));
      return writer.toString();
   }

   public String serializeRecord(RecordType r) throws TransformerException {
      if(r.getMetadata()!=null && r.getMetadata().getAny() instanceof Node resourceNode) {
         StringWriter writer = new StringWriter();
         Transformer transformer = TransformerFactory.newInstance().newTransformer();
         transformer.setOutputProperty(OutputKeys.INDENT, "yes");
         transformer.transform(new DOMSource(resourceNode), new StreamResult(writer));
         return writer.toString();
      }
      else{
         log.warn("record has no metadata or metadata is not a Node, cannot serialize: {}", r.getHeader().getIdentifier());
         return "";
      }
   }

   public Resource OaiMetadataToResource(RecordType oai) {

      return extractResource((Node) oai.getMetadata().getAny());

   }

   public Resource extractResource(Node xml) {
      try {

         Object o = unmarshaller.unmarshal(xml);
         if (o instanceof ResourceInstance r) {
            return r.getValue();
         }
         else if (o instanceof Resource r) {
            return r;
         }
         else {
            throw new RuntimeException("unexpected type in OAI metadata: "+o.getClass());
         }
      } catch (JAXBException e) {
         throw new RuntimeException(e);
      }
   }


   public Resource OaiIdentifyToResource(IdentifyType oai) {

      return extractResource((Node) oai.getDescriptions().get(0).getAny());
   }
}

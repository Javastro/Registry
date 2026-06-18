package org.javastro.ivoa.registry.oaipmh.client;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.sf.saxon.s9api.*;

import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/**
 * Extracts information from an OAI ListRecords response.
 * Does this by using XSLT for the implementation - this is in the spirit of using XML tooling to
 * extract information.
 *
 * @author Paul Harrison (paul.harrison@manchester.ac.uk) */
public final class OaiInfoExtractor {
   private final Processor processor = new Processor(false);
   private final XsltExecutable executable;
   private final ObjectMapper mapper = JsonMapper.builder()
         .addModule(new JavaTimeModule())
         .build();

   public OaiInfoExtractor() throws SaxonApiException {
      XsltCompiler compiler = processor.newXsltCompiler();
      var xsltStream = getClass().getResourceAsStream("/xslt/extractResumptionTokenJson.xslt");
      if (xsltStream == null) {
         throw new IllegalStateException("Missing /xslt/extractResumptionTokenJson.xslt");
      }
      executable = compiler.compile(new StreamSource(xsltStream));
   }

   public OaiRecordInfo extract(Path oaiListRecordsXmlFilePath) {
      try {
         String xmlContent = java.nio.file.Files.readString(oaiListRecordsXmlFilePath);
         return extract(xmlContent);
      } catch (Exception e) {
         throw new RuntimeException("Failed to read or extract token JSON from file: " + oaiListRecordsXmlFilePath, e);
      }
   }

   public OaiRecordInfo extract(String oaiListRecordsXml) {
      try {
         Xslt30Transformer transformer = executable.load30();
         XdmNode source = processor.newDocumentBuilder()
               .build(new StreamSource(new StringReader(Objects.requireNonNull(oaiListRecordsXml))));

         StringWriter out = new StringWriter();
         Serializer serializer = processor.newSerializer(out);
         transformer.applyTemplates(source, serializer);

         return mapper.readValue(out.toString().trim(), OaiRecordInfo.class);
      } catch (Exception e) {
         throw new RuntimeException("Failed to extract token JSON", e);
      }
   }

   public record OaiRecordInfo(Instant timestamp, String resumptionToken, Integer total, Integer cursor ) {};

}
package org.javastro.ivoa.registry.oaipmh.client;

import net.sf.saxon.s9api.SaxonApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link OaiInfoExtractor}.
 */
class OaiInfoExtractorTest {
    private OaiInfoExtractor extractor;
    private String oaiListRecordXml;

    @BeforeEach
    void setUp() throws IOException, SaxonApiException {
        // Load test data from classpath
        try (var in = getClass().getResourceAsStream("/OAIListRecord.xml")) {
            assertNotNull(in, "Test resource /OAIListRecord.xml not found");
            oaiListRecordXml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        // Initialize extractor
        extractor = new OaiInfoExtractor();
    }

    @Test
    void extractResumptionTokenInfo() {
        OaiInfoExtractor.OaiRecordInfo info = extractor.extract(oaiListRecordXml);

        assertNotNull(info, "OaiRecordInfo should not be null");
        assertEquals(Instant.parse("2026-05-29T07:18:02Z"), info.timestamp());
        assertEquals("49bba0f9-3445-4b63-894e-3074c57f0a71||100", info.resumptionToken());
        assertEquals(390, info.total());
        assertEquals(0, info.cursor());
        assertEquals(100, info.nreturned() );
    }

    @Test
    void extractHandlesNullToken() {
        String xmlNoToken = """
                <?xml version="1.0" encoding="UTF-8"?>
                <oai:OAI-PMH xmlns:oai="http://www.openarchives.org/OAI/2.0/">
                  <oai:ListRecords>
                    <oai:record><oai:header></oai:header></oai:record>
                  </oai:ListRecords>
                </oai:OAI-PMH>
                """;

        OaiInfoExtractor.OaiRecordInfo info = extractor.extract(xmlNoToken);

        assertNotNull(info);
        assertEquals("", info.resumptionToken());
        assertNull(info.total());
        assertNull(info.cursor());
    }

    @Test
    void extractHandlesInvalidNumericAttributes() {
        String xmlInvalidAttrs = """
                <?xml version="1.0" encoding="UTF-8"?>
                <oai:OAI-PMH xmlns:oai="http://www.openarchives.org/OAI/2.0/">
                  <oai:ListRecords>
                    <oai:resumptionToken completeListSize="not-a-number" cursor="also-not">token-value</oai:resumptionToken>
                  </oai:ListRecords>
                </oai:OAI-PMH>
                """;

        OaiInfoExtractor.OaiRecordInfo info = extractor.extract(xmlInvalidAttrs);

        assertNotNull(info);
        assertEquals("token-value", info.resumptionToken());
        assertNull(info.total(), "Non-numeric completeListSize should be null");
        assertNull(info.cursor(), "Non-numeric cursor should be null");
    }
}
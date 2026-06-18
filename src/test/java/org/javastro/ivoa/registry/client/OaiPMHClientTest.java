package org.javastro.ivoa.registry.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.junit.QuarkusTest;
import org.javastro.ivoa.entities.oai.oaipmh.IdentifyType;
import org.javastro.ivoa.entities.oai.oaipmh.ListMetadataFormatsType;
import org.javastro.ivoa.entities.oai.oaipmh.ListRecordsType;
import org.javastro.ivoa.registry.oaipmh.client.OaiPMHClient;
import org.javastro.ivoa.registry.oaipmh.client.OaiPMHException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class OaiPMHClientTest {

    private HttpServer server;
    private OaiPMHClient client;
    private final AtomicReference<Map<String, String>> lastQuery = new AtomicReference<>(Map.of());



    @AfterEach
    void tearDown() throws Exception {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void identifyParsesResponse() throws IOException, OaiPMHException {
        startServer();
        client = new OaiPMHClient(serverUrl(), false);

        IdentifyType identify = client.identify();

        assertNotNull(identify);
        assertEquals("Test Registry", identify.getRepositoryName());
        assertEquals("Identify", lastQuery.get().get("verb"));
    }

    @Test
    void listMetadataFormatsParsesResponse() throws IOException, OaiPMHException {
        startServer();
        client = new OaiPMHClient(serverUrl(), false);

        ListMetadataFormatsType formats = client.listMetadataFormats();

        assertNotNull(formats);
        assertTrue(formats.getMetadataFormats().stream().anyMatch(f -> "ivo_vor".equals(f.getMetadataPrefix())));
        assertEquals("ListMetadataFormats", lastQuery.get().get("verb"));
    }

    @Test
    void listRecordsUsesResumptionTokenRules() throws IOException, OaiPMHException {
        startServer();
        client = new OaiPMHClient(serverUrl(), false);

        ListRecordsType records = client.listRecords(
                "ivo_vor",
                "ivo_managed",
                Instant.parse("2026-01-01T01:02:03.999Z"),
                null,
                "abc token");

        assertNotNull(records);
        assertEquals(1, records.getRecords().size());
        Map<String, String> query = lastQuery.get();
        assertEquals("ListRecords", query.get("verb"));
        assertEquals("abc token", query.get("resumptionToken"));
        assertEquals("ivo_managed", query.get("set"));
        assertFalse(query.containsKey("metadataPrefix"));
        assertEquals("2026-01-01T01:02:03Z", query.get("from"));
    }

    private void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/oai", this::handle);
        server.start();
    }

    private void handle(HttpExchange exchange) throws IOException {
        URI requestUri = exchange.getRequestURI();
        Map<String, String> query = parseQuery(requestUri.getRawQuery());
        lastQuery.set(query);
        String verb = query.get("verb");
        String body = switch (verb) {
            case "Identify" -> identifyResponse();
            case "ListMetadataFormats" -> listMetadataFormatsResponse();
            case "ListRecords" -> listRecordsResponse();
            default -> errorResponse("badVerb", "Unsupported verb in test server");
        };

        byte[] responseBytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/xml; charset=utf-8");
        exchange.sendResponseHeaders(200, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    private String serverUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/oai";
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> query = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return query;
        }
        Arrays.stream(rawQuery.split("&"))
                .filter(p -> !p.isBlank())
                .forEach(pair -> {
                    int i = pair.indexOf('=');
                    String key = i < 0 ? pair : pair.substring(0, i);
                    String value = i < 0 ? "" : pair.substring(i + 1);
                    query.put(
                            URLDecoder.decode(key, StandardCharsets.UTF_8),
                            URLDecoder.decode(value, StandardCharsets.UTF_8));
                });
        return query;
    }

    private static String identifyResponse() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <OAI-PMH xmlns="http://www.openarchives.org/OAI/2.0/">
                  <responseDate>2026-01-01T00:00:00Z</responseDate>
                  <request verb="Identify">http://example.org/oai</request>
                  <Identify>
                    <repositoryName>Test Registry</repositoryName>
                    <baseURL>http://example.org/oai</baseURL>
                    <protocolVersion>2.0</protocolVersion>
                    <adminEmail>ops@example.org</adminEmail>
                    <earliestDatestamp>2000-01-01T00:00:00Z</earliestDatestamp>
                    <deletedRecord>no</deletedRecord>
                    <granularity>YYYY-MM-DDThh:mm:ssZ</granularity>
                  </Identify>
                </OAI-PMH>
                """;
    }

    private static String listMetadataFormatsResponse() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <OAI-PMH xmlns="http://www.openarchives.org/OAI/2.0/">
                  <responseDate>2026-01-01T00:00:00Z</responseDate>
                  <request verb="ListMetadataFormats">http://example.org/oai</request>
                  <ListMetadataFormats>
                    <metadataFormat>
                      <metadataPrefix>ivo_vor</metadataPrefix>
                      <schema>http://www.ivoa.net/xml/RegistryInterface/v1.0</schema>
                      <metadataNamespace>http://www.ivoa.net/xml/RegistryInterface/v1.0</metadataNamespace>
                    </metadataFormat>
                  </ListMetadataFormats>
                </OAI-PMH>
                """;
    }

    private static String listRecordsResponse() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <OAI-PMH xmlns="http://www.openarchives.org/OAI/2.0/"
                         xmlns:oai_dc="http://www.openarchives.org/OAI/2.0/oai_dc/"
                         xmlns:dc="http://purl.org/dc/elements/1.1/"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                  <responseDate>2026-01-01T00:00:00Z</responseDate>
                  <request verb="ListRecords">http://example.org/oai</request>
                  <ListRecords>
                    <record>
                      <header>
                        <identifier>ivo://example.org/reg</identifier>
                        <datestamp>2026-01-01T00:00:00Z</datestamp>
                      </header>
                      <metadata>
                        <oai_dc:dc xsi:schemaLocation="http://www.openarchives.org/OAI/2.0/oai_dc/
                                        http://www.openarchives.org/OAI/2.0/oai_dc.xsd
                                        http://purl.org/dc/elements/1.1/
                                        http://dublincore.org/schemas/xmls/simpledc20021212.xsd">
                          <dc:title>Example</dc:title>
                        </oai_dc:dc>
                      </metadata>
                    </record>
                  </ListRecords>
                </OAI-PMH>
                """;
    }

    private static String errorResponse(String code, String message) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <OAI-PMH xmlns="http://www.openarchives.org/OAI/2.0/">
                  <responseDate>2026-01-01T00:00:00Z</responseDate>
                  <request>http://example.org/oai</request>
                  <error code="%s">%s</error>
                </OAI-PMH>
                """.formatted(code, message);
    }
}

# AGENTS.md - Registry Codebase Guide for AI Agents

## Architecture Overview

This is an **IVOA Publishing Registry** - a metadata registry implementing the [OAI-PMH](http://openarchives.org/OAI/openarchivesprotocol.html) harvesting protocol. It stores XML registry documents in [BaseX](https://docs.basex.org/) - a native XML database.

**Core Stack:**
- **Framework:** Quarkus (Jakarta/JakartaEE) for reactive microservices
- **Data Store:** BaseX XML database (embedded, accessed via XQuery)
- **Domain Model:** `org.javastro.ivoa:ivoa-entities` (external library defining IVOA VOResource JAXB classes)
- **XML Processing:** JAXB for marshalling/unmarshalling, Saxon-HE for XSLT 3.0

## Key Component Architecture

```
Registry (ApplicationScoped singleton)
├── RegistryStoreInterface (write operations) → BasexStore (XQuery updates)
├── RegistryQueryInterface (read operations) → BaseXQuery (XQuery reads)
├── REST Endpoints (Quarkus @Path resources):
│   ├── OaiPMHResource (/oai) - OAI-PMH protocol implementation
│   ├── XQueryResource (/xquery) - Direct XQuery endpoint
│   ├── ResourceResource (/resource) - Single resource lookup
│   ├── VOSIResource (/VOSI) - VOSI capabilities
│   ├── AdminResource (/admin) - Admin operations (BasicAuth protected)
│   └── GraphQL endpoint (/graphql)
└── XMLUtils - JAXB marshalling/unmarshalling utilities
```

**Lifecycle Management:**
- `Registry.onStart()` – initializes store/query interfaces, loads Authority and Registry resource templates
- `Registry.onStop()` – cleanly closes BaseX connections
- Managed via Quarkus `@Observes StartupEvent/ShutdownEvent`

## Essential Files & Patterns

### Data Access & Queries
- **src/main/xquery/** - XQuery scripts for OAI operations
  - `oaiListRecords.xq` – retrieves records filtered by date/set
  - `oaiIdentifiers.xq` – lists identifiers for harvest resumption
  - `oaiGetRecord.xq` – retrieves single record by identifier
  - `update.xq` – handles record insertions/updates
- **BaseXQuery/BasexStore** – implement interfaces; both extend `BaseXStoreBase` (loads XQuery scripts)
- Query execution: `new XQuery(namespaces + query).execute(contextRO)` returns XML string

### Configuration
- **application.properties** - Quarkus config + registry-specific settings:
  - `ivoa.registry.baseAddress` – base URL for capabilities
  - `ivoa.registry.authority` – authority ID (changeable: `authority.changeme`)
  - `ivoa.dc.*` – datacenter contact metadata
  - `quarkus.security.users.embedded.users."admin"` – admin password (override via `QUARKUS_SECURITY_USERS_EMBEDDED_USERS__admin__` env var)
- **Quarkus Dev UI** available at `http://localhost:8080/q/dev/` during dev mode
- **Swagger UI** at `http://localhost:8080/q/dev-ui/io.quarkus.quarkus-smallrye-openapi/swagger-ui`

### Testing Strategy
- **Unit/Integration Tests:** `src/test/java` – use `@QuarkusTest` annotation
  - `OaiPMHResourceTest` – tests Registry's own OAI-PMH endpoints
  - `OaiPMHClientTest` – integration tests against external registries (RofR)
  - `rest-assured` library for HTTP assertions
- **Native Tests:** `src/native-test/java` – same test classes with `@QuarkusIntegrationTest` to test packaged mode
- **Test Fixtures:** `src/test/resources/` contains mock XML documents (rawRegistry.xml, VOResource.xml)

## Development Workflows

### Running Locally
```bash
# Dev mode (live reload, Dev UI, Swagger UI available)
./gradlew quarkusDev

# Run tests
./gradlew test

# Build for Docker
./gradlew quarkusBuild
docker-compose up
```

### Database & Content Management
- **BaseX Database Initialization:** Occurs at startup in `Registry.onStart()` via `BasexStore.open()`
- **Adding Content:** Use `/admin/strictAdd` endpoint (BasicAuth: admin/passwordchangeme)
- **Default Loaded:** Authority and Registry metadata records (~ivo://authority.changeme/*)

### Key Build Commands
- `./gradlew quarkusDev` – watch mode with live coding
- `./gradlew quarkusBuild` – creates optimized docker image in `build/quarkus-app/`
- `./gradlew test` – runs all tests (unit + integration)
- Tests use embedded BaseX (no external DB dependency)

## Code Patterns & Conventions

### Dependency Injection (Jakarta CDI)
- Inject via `@Inject` (Quarkus/Arc standard)
- `@ApplicationScoped` for singletons: `Registry`, `BaseXQuery`, `BasexStore`, `OaiBuilder`
- Configuration injection: `@ConfigProperty(name="...", defaultValue="...")`

### XML/JAXB Processing
- JAXB classes from `org.javastro.ivoa.entities` (external JAR)
- Marshalling: `xmlUtils.marshall(resource)` → XML string
- Unmarshalling: `IvoaJAXBUtils.unmarshall(stream, Class)` → domain object
- Namespace prefixes in XQuery queries prepended automatically in `BaseXQuery`

### OAI-PMH Protocol Implementation
- Verbs handled as enum cases in `OaiPMHResource.action()`
- Each verb maps to XQuery methods in `RegistryQueryInterface`
- Responds with JAXB-marshalled `OAIPMH` objects
- Error handling: `OAIPMHerrorType` objects collected and added to response

### REST Endpoints (Quarkus REST patterns)
- Use `@Path`, `@GET`, `@Produces(MediaType.TEXT_XML)`
- Query parameters via `@RestQuery` annotation
- Return `jakarta.ws.rs.core.Response` for control over status/headers

### IVOA Standards Integration
- Implements Registry Interface v1.1 (https://www.ivoa.net/documents/RegistryInterface/)
- Metadata follows VOResource schema
- Supports multiple metadata prefixes: `ivo_vor` (VOResource), `oai_dc` (Dublin Core)

## Critical Implementation Notes

1. **BaseX Context Management:** `BaseXQuery` opens read-only context at startup, closed at shutdown. Write operations use separate context in `BasexStore`.

2. **XQuery String Concatenation:** Queries are built by string concatenation; namespace declarations prepended. Be careful with user input in queries (potential injection risk if exposed).

3. **OAI-PMH Resumption Tokens:** Currently TODO - `ListRecords` does not support pagination via resumption tokens (commented in code).

4. **Authority Management:** On startup, Registry creates two root resources:
   - Authority (ivo://[authority.changeme])
   - Registry self-description (ivo://[authority.changeme]/Registry)
   
   These are stored in BaseX database and returned by OAI-PMH Identify verb.

5. **IVOA Entities Library:** Domain classes (Authority, Registry, Service, etc.) come from external JAR. Use builder patterns: `Authority.builder().withIdentifier().build()`.

6. **Logging:** Quarkus/JBoss logging; configure via `quarkus.log.category.*` in application.properties.

## External Dependencies to Know

- **ivoa-entities:0.9.12** – JAXB-generated VOResource classes, maintained externally
- **basex-api:12.0** – BaseX client API for XQuery execution
- **xmlresolver:6.0.18** – XML catalog resolution
- **Saxon-HE:12.5** – XSLT 3.0 support (used in XMLUtils)
- **Quarkus platform** – brings rest-jaxb, smallrye-openapi, kubernetes, container-image-docker

## Related Resources

- **OAI-PMH Spec:** http://openarchives.org/OAI/openarchivesprotocol.html
- **IVOA Registry Interface:** https://www.ivoa.net/documents/RegistryInterface/
- **RofR (Reference Registry):** http://rofr.ivoa.net (used in integration tests)
- **Quarkus Guides:** https://quarkus.io/guides/
- **BaseX Documentation:** https://docs.basex.org/


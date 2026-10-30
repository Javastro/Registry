# Endpoint reference

| Path | Description | Auth |
|---|---|---|
| `/oai` | OAI-PMH (`Identify`, `ListSets`, `ListMetadataFormats`, `ListIdentifiers`, `ListRecords`, `GetRecord`); metadata prefixes `ivo_vor` and `oai_dc` | none |
| `/resource` | Lookup of a single resource | none |
| `/xquery` | Direct XQuery (POST) | none |
| `/VOSI/capabilities` | VOSI capabilities | none |
| `/graphql` | GraphQL endpoint (UI included) | none |
| `/admin/strictAdd` | Add content | `update` |
| `/harvesting/*` | Harvest administration, see {doc}`harvesting` | `update` |
| `/tap/*` | RegTAP (only when enabled) | see {doc}`regtap-admin` |
| `/q/swagger-ui/`, `/q/dev/` | Swagger UI and Dev UI (development mode) | - |

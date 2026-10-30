# Architecture

* `Registry` - application-scoped service that opens the stores and creates the authority and registry records at start-up.
* `RegistryStoreInterface` / `BasexStore` - writes (XQuery in `src/main/resources/xquery`: `update.xq`, `add.xq`, `delete.xq`).
* `RegistryQueryInterface` / `BaseXQuery` - reads (`oai*.xq`).
* `HarvestOrchestrator` - queue-based harvester using `OaiPMHClient`; `HarvestSourceCatalog` persists per-source state.
* `XMLUtils` - JAXB marshalling and the Saxon XSLT used to concatenate and fix harvested records (`fixHarvest.xslt`).
* REST resources: `OaiPMHResource`, `XQueryResource`, `ResourceResource`, `VOSIResource`, `AdminResource`, `HarvestAdminResource`.
* `registry.tap` - optional RegTAP classes, see {doc}`regtap-build`.

Domain classes come from the external `org.javastro.ivoa:ivoa-entities` library.

API documentation: [Javadoc](../api/index.html).

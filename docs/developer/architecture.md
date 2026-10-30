# Architecture

* `Registry` - application-scoped service that opens the stores and creates the authority and registry records at start-up.
* `RegistryStoreInterface` / `BasexStore` - writes (XQuery in `src/main/resources/xquery`: `update.xq`, `add.xq`, `delete.xq`).
* `RegistryQueryInterface` / `BaseXQuery` - reads (`oai*.xq`).
* `HarvestOrchestrator` - queue-based harvester using `OaiPMHClient`; `HarvestSourceCatalog` persists per-source state.
* `XMLUtils` - JAXB marshalling and the Saxon XSLT used to concatenate and fix harvested records (`fixHarvest.xslt`).
* REST resources: `OaiPMHResource`, `XQueryResource`, `ResourceResource`, `VOSIResource`, `AdminResource`, `HarvestAdminResource`.
* `registry.tap` - optional RegTAP classes, see {doc}`regtap-build`.

XML Domain classes and schema come from the external [`org.javastro.ivoa:ivoa-entities`]()https://github.com/Javastro/ivoa-entities library.

RegTAP is defined as a VO-DML model in [RegTAP github](https://ivoa.github.io/RegTAPDM/) which in turn uses the libraries in [ivoa-core](https://javastro.github.io/ivoacore/) and [quarkusIVOA](https://github.com/Javastro/quarkusIVOA) to build the interface with little code in this repo.

API documentation: [Javadoc](../api/index.html).

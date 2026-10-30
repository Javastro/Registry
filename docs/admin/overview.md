# Overview

A deployed registry consists of a single Quarkus application containing:

* an embedded BaseX database (`Registry`) holding the registry's own records and harvested records;
* the OAI-PMH endpoint (`/oai`) that other registries harvest from;
* admin endpoints (HTTP Basic auth) for adding content and managing harvesting;
* a scheduled harvester that copies records from other registries, starting from the Registry of Registries (RofR);
* optionally, a PostgreSQL-backed RegTAP service (see {doc}`regtap`).

On first start the registry creates two records for itself: the authority (`ivo://<authority>`) and the registry
description (`ivo://<authority>/Registry`). Change `ivoa.registry.authority` before publishing anything.

# Harvesting

The harvesting of other registries can be both compute and network intensive, both for the harvesting registry and the harvested registries. This registry implementation has several tools that allow the operator bootstrap and manage the process in a flexible way that

* minimises the network load on the harvested registries - especially in the case of failures.
* allows the operator to control the process and its schedule, and
* allows the operator to inspect the results and diagnose problems.


## The Harvest cache

The harvest cache is a local storage area where harvested records are temporarily stored before being processed and loaded into the main database. This helps to reduce the load on the harvested registries and provides a way to recover from failures.

The cache consists of a series of directories (one for each remote repository) containing
a set of files of raw OAI-PMH ListRecord responses. The cache root directory is configured with the `ivoa.registry.harvesting.cache-dir` property (see {doc}`configuration`). It is populated for a particular registry ivoa identifier by the `POST /harvesting/harvest/source/{key}/cache` endpoint, and can be loaded into the database with `POST /harvesting/harvest/source/{key}/cache/load`. The cache is not used for incremental harvesting, but it can be used to bootstrap a harvest or to recover from a failure.

## Bootstrapping the harvest

It is possible to bootstrap the harvest from a local copy of the Registry of Registries (RofR) instead of fetching it from the IVOA. This is useful for testing and for reducing the load on the RofR. The local copy is bundled with the application and can be used by setting `ivoa.registry.harvesting.rofr-use-local=true` in the configuration.

In addition, it is often a good idea to use an existing cache for the larger registries such as Vizier (`ivo://cds.vizier/registry`) and the EuroVO registry (`ivo://esavo/registry`).


## Harvest record

The registry keeps a catalog of OAI-PMH *sources*, persisted in the database at `harvest/sources.xml`. The seed is the
RofR (or the bundled catalog when `rofr-use-local=true`). Each source has a status:
`ACTIVE`, `QUEUED`, `RUNNING`, `FAILED`, `DISABLED`, `REJECTED` (and `CACHED` for a source that has a local cache waiting to
be loaded). Harvests are incremental from each source's last successful harvest. Harvested records are stored
under `harvested/<source-key>/`.

Harvesting runs on the cron schedule, or on request through the admin endpoints (all need the `update` role):

| Endpoint | Purpose |
|---|---|
| `POST /harvesting/harvest` | Queue a harvest (`sourceKey`, `immediate` query parameters) |
| `GET /harvesting/harvest/status` | Current harvest status |
| `GET /harvesting/harvest/sources` / `POST /harvesting/harvest/sources` | List / add sources |
| `GET /harvesting/harvest/runs` | Recent run records |
| `POST /harvesting/harvest/source/{key}/disable` | Stop harvesting a source |
| `POST /harvesting/harvest/source/{key}/reset` | Reset a source so that the next harvest starts from the beginning |
| `GET`/`POST /harvesting/harvest/source/{key}/cache` | Inspect / populate the file cache for a source |
| `POST /harvesting/harvest/source/{key}/cache/load` | Load a source's cached files into the database |

## Run log

Each source keeps its most recent run records: start/end time, records stored, new sources discovered, outcome
(`SUCCESS`/`FAILED`) and details. With RegTAP enabled the record also contains `relationalAdded`, `relationalDeleted`,
`relationalFailed` and up to 20 `relationalFailure` entries (`identifier: message`).
A resource that cannot be loaded relationally does not fail the harvest.

## Deleted records

Records flagged `deleted` by a source are removed from the XML database and, if enabled, from the RegTAP tables.

## Discovery

With discovery enabled, registries described in harvested records are added as new sources, bounded by the
`discovery.*` limits in {doc}`configuration`.

# Harvesting

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

# RegTAP (optional)

The relational RegTAP service (PostgreSQL, Hibernate ORM and the `/tap/*` endpoints) is **not** part of the default
build. It is selected when the application is built (see {doc}`../developer/regtap-build`):

```shell
./gradlew quarkusBuild -Pregtap -Dquarkus.profile=prod,regtap
```

## Configuration

Settings are in `application-regtap.properties`, loaded only with the `regtap` profile:

| Property | Meaning |
|---|---|
| `quarkus.datasource.*` | PostgreSQL connection (`quarkus.datasource.jdbc.url`, `username`, `password`) - in production set these explicitly |
| `ivoa.tap.schema` | TAP schema description |
| `ivoa.tap.dbCaseSensitive` | Whether the database is case sensitive |
| `ivoa.tap.load.batch-size` | Resources converted per batch during a full load (default 30) |
| `ivoa.tap.sync-timeout-seconds` | Sync query timeout before falling back to the async job (default 5) |

:::{warning}
The shipped settings use `drop-and-create` schema management, which recreates the schema at start-up. Change
`quarkus.hibernate-orm.schema-management.strategy` for a production database.
:::

## Loading the tables

* Full reload from the XML database: `POST /tap/admin/load` (progress at `GET /tap/admin/load/status`, cancel with
  `DELETE /tap/admin/load`).
* Incremental: each harvest adds its new records one resource at a time (see {doc}`harvesting`).

## Endpoints

`/tap/sync`, `/tap/async`, `/tap/tables`, `/tap/VOSI/capabilities`.

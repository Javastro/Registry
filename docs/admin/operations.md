# Operations

managing {doc}`harvesting` is described in more detail in its own page.


## Storage and backup

All state is in the BaseX database directory (see {doc}`installation`) and the harvest cache directory. Stop the
application before copying the database directory. With RegTAP, back up the PostgreSQL database as well, or reload it with
`POST /tap/admin/load`.

## Security checklist

* Change the `admin` password.
* Serve the application behind HTTPS (e.g. an ingress); Basic auth sends credentials in the clear otherwise.
* The `/xquery` endpoint executes queries against the database - restrict access to it at the proxy if it should not be public.

## Troubleshooting

* *Database "Registry" opened by another process* - another BaseX instance (e.g. the BaseX GUI) holds the database; stop it
  or set `org.basex.path` to a different directory.
* *A source is `FAILED`* - check `GET /harvesting/harvest/runs` for the details; it is retried on a later run.
* *Relational failures* - see the `relationalFailure` entries in the run record.

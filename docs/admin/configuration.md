# Configuration

Properties are set in `application.properties`, as Java system properties, or as environment variables
(upper-case, with `.`/`-` replaced by `_`, e.g. `IVOA_REGISTRY_HARVESTING_CRON`).

## Identity

| Property | Meaning |
|---|---|
| `ivoa.registry.authority` | Authority identifier for the registry's own records (default `authority.changeme`) |
| `ivoa.dc.name`, `title`, `description` | Registry name, title and description |
| `ivoa.dc.reference-url` | Public URL of the registry |
| `ivoa.dc.authority` | Authority used in the registry description |
| `ivoa.dc.organisation-name`, `contact-name`, `contact-email`, `contact-telephone` | Contact details published in the registry record |
| `thisapp.baseAddress` (env `APP_BASE_URL`) | Base URL used when generating capabilities |

## Security

The admin endpoints require the `update` role. A single embedded user `admin` is configured; **change the password**:

```shell
QUARKUS_SECURITY_USERS_EMBEDDED_USERS__admin__=betterpassword
```

## Harvesting

| Property | Default | Meaning |
|---|---|---|
| `ivoa.registry.harvesting.rofr-url` | `http://rofr.ivoa.net/oai` | OAI endpoint of the Registry of Registries |
| `ivoa.registry.harvesting.rofr-use-local` | `false` (`true` in the shipped file) | Seed the source catalog from the bundled `initialCatalog.xml` instead of the RofR |
| `ivoa.registry.harvesting.cron` | `off` | Quarkus cron expression for periodic harvests, e.g. `0 0 2 * * ?`; `off` harvests only on request |
| `ivoa.registry.harvesting.cache-dir` (env `IVOA_REGISTRY_HARVESTING_CACHE_DIR`) | `harvestCache` | Directory for cached harvested files |
| `ivoa.registry.harvesting.discovery.enabled` | `true` (`false` in the shipped file) | Discover further registries from harvested records |
| `ivoa.registry.harvesting.discovery.max-sources` | 100 | Maximum number of sources kept |
| `ivoa.registry.harvesting.discovery.max-depth` | 3 | Maximum hops from the seed |
| `ivoa.registry.harvesting.discovery.max-per-run` | 5 | Maximum new sources accepted per run |
| `ivoa.registry.harvesting.discovery.do-xml-validation` | `true` (`false` in the shipped file) | Validate records from discovered sources (non-compliant sources then yield fewer records) |

## HTTP

`quarkus.http.port` (default 8080). Logging is configured with the usual `quarkus.log.*` properties.

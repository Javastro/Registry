# IVOA Publishing Registry

An implementation of an [IVOA publishing registry](https://www.ivoa.net/documents/RegistryInterface/20180723/REC-RegistryInterface-1.1.html).
Resource records are held in the [BaseX](https://docs.basex.org/) XML database and exposed through
[OAI-PMH](http://www.openarchives.org/OAI/openarchivesprotocol.html). The registry can also harvest other registries,
and optionally expose the content relationally through [RegTAP](https://www.ivoa.net/documents/RegTAP/).

```{toctree}
:caption: Administrator guide
:maxdepth: 2

admin/overview
admin/installation
admin/configuration
admin/content
admin/harvesting
admin/regtap
admin/endpoints
admin/operations
```

```{toctree}
:caption: Developer guide
:maxdepth: 2

developer/building
developer/regtap-build
developer/architecture
```

The generated [Javadoc API documentation](api/index.html) is published alongside this site.

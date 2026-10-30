XQuery
======

[XQuery](https://www.w3.org/TR/xquery/) is a powerful language for querying and transforming XML data. This registry implementation offers an XQuery 3.1 level of support for making queries on registry metadata.
In many ways XQuery is a natural language to query IVOA registries as their metadata is defined by the [VOResource](https://www.ivoa.net/documents/VOResource/) family of XML schema. However, using XQuery is not an IVOA recommendation for querying the metadata, {doc}`regtap` is the recommended approach for querying registry metadata.

The power of XQuery means that it is often a useful tool for registry administrators. An XQuery endpoint is available at `/xquery` and accepts POST requests with the query in the body. 

To make writing the queries easier, the following namespaces are pre-defined in the XQuery context:

```properties
declare namespace cs="http://www.ivoa.net/xml/ConeSearch/v1.0";
declare namespace dc="http://purl.org/dc/elements/1.1/";
declare namespace oai="http://www.openarchives.org/OAI/2.0/";
declare namespace ri="http://www.ivoa.net/xml/RegistryInterface/v1.0";
declare namespace sia="http://www.ivoa.net/xml/SIA/v1.1";
declare namespace slap="http://www.ivoa.net/xml/SLAP/v1.0";
declare namespace ssap="http://www.ivoa.net/xml/SSA/v1.1";
declare namespace tr="http://www.ivoa.net/xml/TAPRegExt/v1.0";
declare namespace vg="http://www.ivoa.net/xml/VORegistry/v1.0";
declare namespace vr="http://www.ivoa.net/xml/VOResource/v1.0";
declare namespace vs="http://www.ivoa.net/xml/VODataService/v1.1";
declare namespace vstd="http://www.ivoa.net/xml/StandardsRegExt/v1.0";
declare namespace doc="http://www.ivoa.net/xml/DocRegExt/v1";
```
and so do not have to be declared in the POSTed query.

 The whole registry can be treated a single XML document, and the root element is a `ri:VOResources` element, though the actual layout is more complex than that, and in principle be subdivided based on which registry actually published the records. (details TBC). However the easiest way to
ensure that all records are included in the query is to use the `//ri:Resource` path expression, which will select all records regardless of their actual location in the store. So the following will count all of the records.


```
curl -X 'POST' \
  'http://localhost:8080/xquery' \
  -H 'accept: text/plain' \
  -H 'Content-Type: text/plain' \
  -d 'count(/*/ri:Resource)'
```
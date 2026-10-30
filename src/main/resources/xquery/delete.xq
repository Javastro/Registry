declare namespace ri="http://www.ivoa.net/xml/RegistryInterface/v1.0";

declare variable $id as xs:string external;
declare variable $path as xs:string external;

delete node fn:collection(fn:string-join(("Registry/",$path)))/ri:VOResources/ri:Resource[identifier = $id]

declare namespace ri="http://www.ivoa.net/xml/RegistryInterface/v1.0";
declare namespace vg="http://www.ivoa.net/xml/VORegistry/v1.0";

declare variable $rin as xs:string external;

(: declare context value := fn:collection("Registry/managed/base.xml"); :)
(: 
IMPL not sure is this is the most efficient, but it seems to work
:)


for $r in fn:collection("Registry/managed/base.xml")/ri:VOResources
   let $in := fn:parse-xml($rin)//ri:Resource
   let $existingIDs := $r/ri:Resource/identifier[text() = $in//identifier/text()]
   let $new := $in[not(identifier = ($existingIDs) )]
   let $ups := $in[identifier = ($existingIDs) ]
    return       (insert nodes $new into $r, 
    (: now update any existing :)
  for $u in 
      fn:collection("Registry/managed/base.xml")/ri:VOResources/ri:Resource[identifier = ($existingIDs)]
      let $upid := $u/identifier
    return replace node $u with $in[identifier=$upid])
    
       
   
   

  



declare namespace ri="http://www.ivoa.net/xml/RegistryInterface/v1.0";
declare namespace vg="http://www.ivoa.net/xml/VORegistry/v1.0";

declare variable $rin as xs:string external;
declare variable $path as xs:string external;

(: declare context value := fn:collection("Registry/managed/base.xml"); :)
(: 
IMPL definitely not efficient takes progressively longer as data base gets bigger FIXME needs to be faster.

Some  testing.
2000 records - raw save (not using this code) 2s
2000  added to above, using this code 2m 30s

:)

(:cannot put this here, as it then becomes the expression ....
let $_ := admin:write-log('writing to  ' || $path, 'INFO')
:)

(# db:updindex false #) (# db:autooptimize false #) {
for $r in fn:collection(fn:string-join(("Registry/",$path)))/ri:VOResources
   let $in := fn:parse-xml($rin)//ri:Resource
   let $existingIDs := $r/ri:Resource/identifier[text() = $in//identifier/text()]
   let $new := $in[not(identifier = ($existingIDs) )]
   let $ups := $in[identifier = ($existingIDs) ]
    return       (insert nodes $new into $r, 
    (: now update any existing :)
  (for $u in
      fn:collection(fn:string-join(("Registry/",$path)))/ri:VOResources/ri:Resource[identifier = ($existingIDs)]
      let $upid := $u/identifier
     
    return (replace node $u with $in[identifier=$upid]),
    (# db:updindex true #) {
    db:flush("Registry")
  }
  ))
}    
       
   
   

  



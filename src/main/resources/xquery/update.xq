declare namespace ri="http://www.ivoa.net/xml/RegistryInterface/v1.0";
declare namespace vg="http://www.ivoa.net/xml/VORegistry/v1.0";

declare variable $rin as xs:string external;
declare variable $path as xs:string external;

(: declare context value := fn:collection("Registry/managed/base.xml"); :)
(: 
IMPL definitely not efficient for bulk creation takes progressively longer as data base gets bigger
should only be used for update - where comparatively few new records appear.

Some  testing.
2000 records - raw save (not using this code) 2s
2000  added to above, using this code 2m 30s

:)

(:cannot put this here, as it then becomes the expression ....
let $_ := admin:write-log('writing to  ' || $path, 'INFO')
:)

(# db:updindex false #) (# db:autooptimize true #) {
 let $in := fn:parse-xml($rin)//ri:Resource
 for $r in fn:collection(fn:string-join(("Registry/",$path)))/ri:VOResources/ri:Resource[identifier = $in/identifier/text()]
    let $upid := $r/identifier
    return  replace node $r with $in[identifier=$upid] 
}  
  


  
       
   
   

  



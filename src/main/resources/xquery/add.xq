declare namespace ri="http://www.ivoa.net/xml/RegistryInterface/v1.0";
declare namespace vg="http://www.ivoa.net/xml/VORegistry/v1.0";

declare variable $rin as xs:string external;
declare variable $path as xs:string external;

(: 
IMPL definitely not efficient for bulk creation takes progressively longer as data base gets bigger
should only be used for update - where comparatively few new records appear.

Some  testing.
2000 records - raw save (not using this code) 2s
2000  added to above, using this code 2m 30s

:)

(# db:updindex true #) (# db:autooptimize true #) {
(: would be nice to put this into "random" one of the possible VOResources rather than just the first":)  
let $dest :=(fn:collection(fn:string-join(("Registry/",$path)))/ri:VOResources)[1]
for $r in fn:parse-xml($rin)//ri:Resource
  let $a := count(fn:collection(fn:string-join(("Registry/",$path)))/ri:VOResources/ri:Resource[identifier=$r/identifier])  (: let $bb := fn:message("resource " || $r/identifier || " a=" || $a) :)
  where $a = 0
  return insert nodes $r into $dest  
}    
       
   
   

  



declare namespace ri="http://www.ivoa.net/xml/RegistryInterface/v1.0";
declare namespace oai="http://www.openarchives.org/OAI/2.0/";


declare variable $from as xs:string external;
declare variable $until as xs:string external;
declare variable $setSpec as xs:string external;
declare variable $metadataPrefix as xs:string external;

(: cannot get consitent behaviour between various invocation methods with this
declare context item  :=  db:get("Registry");  seem to need this here - probably better to do outside :)

<oai:OAI-PMH xmlns:oai="http://www.openarchives.org/OAI/2.0/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <oai:responseDate>{adjust-dateTime-to-timezone(current-dateTime(), xs:dayTimeDuration('PT0H'))}</oai:responseDate>
<oai:request metadataPrefix="{$metadataPrefix}" verb="ListIdentifiers"/>
<oai:ListIdentifiers>{
  for $res in fn:collection("Registry/managed")/*/ri:Resource
    let $id := $res/identifier/text()
    let $date := if ($res[@updated])
                 then $res/@updated
                 else $res/@created
     order by $res/identifier
     return 
     <oai:header>
       <oai:identifier>{$id}</oai:identifier>
       <oai:datestamp>{$date}</oai:datestamp>
       <oai:setSpec>ivo_managed</oai:setSpec>
     </oai:header>
   }
</oai:ListIdentifiers>     
</oai:OAI-PMH>




declare namespace ri="http://www.ivoa.net/xml/RegistryInterface/v1.0";
declare namespace oai="http://www.openarchives.org/OAI/2.0/";


declare variable $id as xs:string external;
declare variable $metadataPrefix as xs:string external;


<oai:OAI-PMH xmlns:oai="http://www.openarchives.org/OAI/2.0/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <oai:responseDate>{adjust-dateTime-to-timezone(current-dateTime(), xs:dayTimeDuration('PT0H'))}</oai:responseDate>
<oai:request metadataPrefix="{$metadataPrefix}" verb="GetRecord" identifier="{$id}"/>
{if(fn:collection("Registry/managed")/*/ri:Resource[identifier = $id]) then

    let $res:= fn:collection("Registry/managed")/*/ri:Resource[identifier = $id]
    let $id := $res/identifier/text()
    let $date := if ($res[@updated])
                 then $res/@updated
                 else $res/@created
return <oai:GetRecord>
     <oai:record>
     <oai:header>
       <oai:identifier>{$id}</oai:identifier>
       <oai:datestamp>{$date}</oai:datestamp>
       <oai:setSpec>ivo_managed</oai:setSpec>
     </oai:header>
     <oai:metadata>
       {$res}
     </oai:metadata>
     </oai:record> 
</oai:GetRecord>    
   else
   <oai:error code="idDoesNotExist">id '{$id}' does not exist</oai:error>  
}    
</oai:OAI-PMH>

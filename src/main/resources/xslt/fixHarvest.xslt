<?xml version="1.0" encoding="UTF-8" ?>
<!-- This XSLT is used to fix up the output of other registries that are often not correct because of namespace problems.

  It does the following:
* ensure that all the standard namespaces are declared at the root element, - it is a common error that
namespace declarations are missing.
  - This is done fairly crudely by assuming that the "standard" namespace prefixes are used.
  - then any namespace declarations further down the element tree are stripped out (unless they are non-standard)
  -->

<xsl:stylesheet version="3.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:map="http://www.w3.org/2005/xpath-functions/map"
                xmlns:xsd="http://www.w3.org/2001/XMLSchema"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xmlns:ri="http://www.ivoa.net/xml/RegistryInterface/v1.0"
                xmlns:cs="http://www.ivoa.net/xml/ConeSearch/v1.0"
                xmlns:doc="http://www.ivoa.net/xml/DocRegExt/v1"
                xmlns:oai="http://www.openarchives.org/OAI/2.0/"
                xmlns:sia="http://www.ivoa.net/xml/SIA/v1.1"
                xmlns:slap="http://www.ivoa.net/xml/SLAP/v1.0"
                xmlns:ssap="http://www.ivoa.net/xml/SSA/v1.1"
                xmlns:stc="http://www.ivoa.net/xml/STC/stc-v1.30.xsd"
                xmlns:tr="http://www.ivoa.net/xml/TAPRegExt/v1.0"
                xmlns:vg="http://www.ivoa.net/xml/VORegistry/v1.0"
                xmlns:voe="http://www.ivoa.net/xml/VOEventRegExt/v2"
                xmlns:vr="http://www.ivoa.net/xml/VOResource/v1.0"
                xmlns:vs="http://www.ivoa.net/xml/VODataService/v1.1"
                xmlns:vstd="http://www.ivoa.net/xml/StandardsRegExt/v1.0"
                xmlns:xlink="http://www.w3.org/1999/xlink"
                xmlns:mf="urn:local:function"
                exclude-result-prefixes="xsd map"
>
    <xsl:output omit-xml-declaration="no" indent="yes"/>
    
    <xsl:param name="inputDir" select="''"/>

    <xsl:mode on-no-match="shallow-copy"/>

    <xsl:variable name="std-namespaces" as="map(xsd:string, xsd:string)">
        <xsl:map>
            <xsl:map-entry key="'ri'" select="'http://www.ivoa.net/xml/RegistryInterface/v1.0'"/>
            <xsl:map-entry key="'cs'" select="'http://www.ivoa.net/xml/ConeSearch/v1.0'"/>
            <xsl:map-entry key="'doc'" select="'http://www.ivoa.net/xml/DocRegExt/v1'"/>
            <xsl:map-entry key="'oai'" select="'http://www.openarchives.org/OAI/2.0/'"/>
            <xsl:map-entry key="'sia'" select="'http://www.ivoa.net/xml/SIA/v1.1'"/>
            <xsl:map-entry key="'slap'" select="'http://www.ivoa.net/xml/SLAP/v1.0'"/>
            <xsl:map-entry key="'ssap'" select="'http://www.ivoa.net/xml/SSA/v1.1'"/>
            <xsl:map-entry key="'stc'" select="'http://www.ivoa.net/xml/STC/stc-v1.30.xsd'"/>
            <xsl:map-entry key="'tr'" select="'http://www.ivoa.net/xml/TAPRegExt/v1.0'"/>
            <xsl:map-entry key="'vg'" select="'http://www.ivoa.net/xml/VORegistry/v1.0'"/>
            <xsl:map-entry key="'voe'" select="'http://www.ivoa.net/xml/VOEventRegExt/v2'"/>
            <xsl:map-entry key="'vr'" select="'http://www.ivoa.net/xml/VOResource/v1.0'"/>
            <xsl:map-entry key="'vs'" select="'http://www.ivoa.net/xml/VODataService/v1.1'"/>
            <xsl:map-entry key="'vstd'" select="'http://www.ivoa.net/xml/StandardsRegExt/v1.0'"/>
            <xsl:map-entry key="'xlink'" select="'http://www.w3.org/1999/xlink'"/>
        </xsl:map>
    </xsl:variable>

    <!-- this attempts to see if the namespace declared is used in the element or any of its descendants -->
    <xsl:function name="mf:nsused" as="xsd:boolean">
        <xsl:param name="el"/>
        <xsl:param name="ns"/>
        <xsl:param name="prefix"/>

        <!-- the first part of this select (before the and) effectively "updates" a "standard" prefix to the most recent definition in the map above
         The second part tries to work out if a non-standard namespace is actually used in the elements below it-->
        <xsl:sequence select="
                    not(map:contains($std-namespaces, $prefix)) and
                    count($el/descendant-or-self::*[namespace-uri() = $ns  or
                    substring-before(name(),':') = $prefix or
                    @*[substring-before(name(),':') = $prefix] or
                    @*[contains(.,concat($prefix,':'))]
                ]) > 0"/>
    </xsl:function>

    <xsl:template match="@xsi:schemaLocation[count(ancestor::*) > 1]">
        <!-- drop any schemaLocations that are not on the top level - TODO probably should parse to make sure that it is not for an "unknown" namespace -->
    </xsl:template>


    <!--IMPL might be better to have a named template that is called for the "scan" style processing as then there is no "main" document anyway -->
    <xsl:template match="/">
        <xsl:choose>
            <xsl:when test="normalize-space($inputDir)=''">
                <xsl:apply-templates select="*"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:message>doing scan</xsl:message>
                <xsl:variable name="files" select="collection(concat('file://',$inputDir,'?select=*.xml;recurse=no'))"/>
                   <xsl:element name="VOResources" namespace="http://www.ivoa.net/xml/RegistryInterface/v1.0">
                    <!-- put in the standard set of namespace -->
                    <xsl:for-each select="map:keys($std-namespaces)">
                        <xsl:namespace name="{.}" select="map:get($std-namespaces, .)"/>
                    </xsl:for-each>
                   </xsl:element>
                    <xsl:for-each select="$files">
                        <xsl:message>processing chunk <xsl:value-of select="concat(position(), ' of ', count($files))"/></xsl:message>
                        <xsl:apply-templates select="./oai:OAI-PMH/oai:ListRecords/oai:record/oai:metadata/ri:Resource"/>
                    </xsl:for-each>
                
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>
               

    <xsl:template match="/*">
        <xsl:element name="{name()}" namespace="{namespace-uri()}">
            <!-- put in the standard set of namespace -->
            <xsl:for-each select="map:keys($std-namespaces)">
                <xsl:namespace name="{.}" select="map:get($std-namespaces, .)"/>
            </xsl:for-each>
            <!-- allow any other namespaces that are not in the standard set -->
            <xsl:call-template name="addOnlyNecessaryNamespaces"/>
            <xsl:apply-templates select="@*"/>

            <xsl:apply-templates select="node()"/>
        </xsl:element>
    </xsl:template>

    <xsl:template match="*">
        <xsl:element name="{name()}" namespace="{namespace-uri()}">
            <xsl:call-template name="addOnlyNecessaryNamespaces"/>
            <xsl:apply-templates select="node()|@*"/>
        </xsl:element>
    </xsl:template>
    
    <xsl:template name="addOnlyNecessaryNamespaces" >
        <xsl:variable name="vtheElem" select="."/>
        <xsl:for-each select="namespace::*">
            <xsl:variable name="vPrefix" select="name()"/>
            <xsl:if test= "mf:nsused($vtheElem,current(),$vPrefix)">
                <xsl:copy-of select="."/>
            </xsl:if>
        </xsl:for-each>
    </xsl:template>

    <xsl:template match="node()|@*" priority="-2">
        <xsl:copy>
            <xsl:apply-templates select="node()|@*"/>
        </xsl:copy>
    </xsl:template>


</xsl:stylesheet>

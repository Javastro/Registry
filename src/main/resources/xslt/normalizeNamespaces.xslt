<?xml version="1.0" encoding="UTF-8" ?>
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xsd="http://www.w3.org/2001/XMLSchema"
                xmlns:ri="http://www.ivoa.net/xml/RegistryInterface/v1.0"
                xmlns:mf="urn:local:function"
xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"  >
    <xsl:output omit-xml-declaration="no" indent="yes"/>
    <!-- this xsl attempts to strip any unnecessary Namespace declarations -->

    <xsl:function name="mf:nsused" as="xsd:boolean">
        <xsl:param name="el"/>
        <xsl:param name="ns"/>
        <xsl:param name="prefix"/>

            <xsl:sequence select="count($el/descendant-or-self::*[namespace-uri() = $ns  or
                   substring-before(name(),':') = $prefix or
                   @*[substring-before(name(),':') = $prefix] or
                   @*[contains(.,concat($prefix,':'))]
                  ]) > 0"/>
    </xsl:function>

    <xsl:template match="@xsi:schemaLocation">
        <!-- drop -->
    </xsl:template>


    <xsl:template match="*">
        <xsl:variable name="vtheElem" select="."/>
        <xsl:element name="{name()}" namespace="{namespace-uri()}">
            <xsl:for-each select="namespace::*">
                <xsl:variable name="vPrefix" select="name()"/>
                <xsl:if test= "mf:nsused($vtheElem,current(),$vPrefix)">
                    <xsl:copy-of select="."/>
                </xsl:if>
            </xsl:for-each>
            <xsl:apply-templates select="node()|@*"/>
        </xsl:element>
    </xsl:template>

    <xsl:template match="node()|@*" priority="-2">
        <xsl:copy>
            <xsl:apply-templates select="node()|@*"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>

<xsl:stylesheet version="3.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:oai="http://www.openarchives.org/OAI/2.0/"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                expand-text="yes"
                exclude-result-prefixes="oai xs">

    <xsl:output method="text" encoding="UTF-8"/>

    <xsl:template match="/">
        <xsl:variable name="rt" select="/oai:OAI-PMH/oai:ListRecords/oai:resumptionToken[1]"/>

        <xsl:variable name="totalStr" select="normalize-space(string($rt/@completeListSize))"/>
        <xsl:variable name="cursorStr" select="normalize-space(string($rt/@cursor))"/>

        <xsl:variable name="totalVal" as="item()?">
            <xsl:sequence select="
        if ($totalStr castable as xs:integer)
        then xs:integer($totalStr)
        else ()
      "/>
        </xsl:variable>

        <xsl:variable name="cursorVal" as="item()?">
            <xsl:sequence select="
        if ($cursorStr castable as xs:integer)
        then xs:integer($cursorStr)
        else ()
      "/>
        </xsl:variable>

        <xsl:sequence select="
      serialize(
        map{
          'timestamp': normalize-space(string(/oai:OAI-PMH/oai:responseDate)),
          'resumptionToken': normalize-space(string($rt)),
          'total': $totalVal,
          'cursor': $cursorVal,
          'nreturned': count(/oai:OAI-PMH/oai:ListRecords/oai:record)
        },
        map{'method':'json'}
      )
    "/>
    </xsl:template>
</xsl:stylesheet>
<?xml version="1.0" encoding="utf-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
<xsl:template match="/">
 <ArrayOfResults>
   <xsl:for-each select="results/result">
    <Result>
      <Label><xsl:value-of select="label" /></Label>
      <URI><xsl:value-of select="resource" /></URI>
      <Description><xsl:value-of select="comment" /></Description>
      <Classes>
        <xsl:for-each select="typeName">
          <Class>
            <Label><xsl:value-of select="." /></Label>
            <URI>http://dbpedia.org/ontology/<xsl:value-of select="." /></URI>
          </Class>
        </xsl:for-each>
      </Classes>
      <Refcount><xsl:value-of select="refCount" /></Refcount>
    </Result>
  </xsl:for-each>
 </ArrayOfResults>
</xsl:template>
</xsl:stylesheet>

<?xml version="1.0" encoding="utf-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:template match="/">
    <ArrayOfResults>
      <xsl:for-each select="results/result">
        <Result>
          <Label>
            <xsl:value-of select="label" />
          </Label>
          <URI>
            <xsl:value-of select="resource" />
          </URI>
          <Description>
            <xsl:value-of select="comment" />
          </Description>
          <Classes>
            <xsl:for-each select="typeName">
              <Class>
                <xsl:variable name="s1" select="replace(.,'([A-Z][a-z]+)','$1 ')" />
                <Label>
                  <xsl:value-of select="substring($s1, 1, string-length($s1) - 1)" />
                </Label>
                <URI>http://dbpedia.org/ontology/<xsl:value-of select="." /></URI>
              </Class>
            </xsl:for-each>
          </Classes>
          <Categories>
            <xsl:for-each select="category">
              <Category>
                <URI>
                  <xsl:value-of select="." />
                </URI>
              </Category>
            </xsl:for-each>
          </Categories>
          <Refcount>
            <xsl:value-of select="refCount" />
          </Refcount>
        </Result>
      </xsl:for-each>
    </ArrayOfResults>
  </xsl:template>
</xsl:stylesheet>
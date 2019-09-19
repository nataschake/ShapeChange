<?xml version="1.0" encoding="UTF-8"?><schema xmlns="http://purl.oclc.org/dsdl/schematron" xmlns:sch="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">
  <title>Schematron constraints for schema 'Test Schema 9 - Exists'</title>
  <ns prefix="sch" uri="http://purl.oclc.org/dsdl/schematron"/>
  <ns prefix="ex9" uri="http://example.org/shapechange/sch/xslt2QueryBinding/s9"/>
  <ns prefix="gml" uri="http://www.opengis.net/gml/3.2"/>
  <ns prefix="xlink" uri="http://www.w3.org/1999/xlink"/>
  <pattern>
    <rule context="ex9:TS9_FT1">
      <assert test="some $x in ex9:att satisfies $x = 'test'">ts9_ft1_constraint1: att-&gt;exists(x|x='test')</assert>
      <assert test="some $x in for $VAR1 in (current()/ex9:rFT1toFT2/* | //*[concat('#',@gml:id)=current()/ex9:rFT1toFT2/@xlink:href]) return $VAR1 satisfies $x/ex9:att = 5">ts9_ft1_constraint2: rFT1toFT2-&gt;exists(x|x.att=5)</assert>
      <assert test="some $x in for $VAR1 in (current()/ex9:rFT1toFT2/* | //*[concat('#',@gml:id)=current()/ex9:rFT1toFT2/@xlink:href]) return (for $VAR2 in ($VAR1/ex9:rFT2toFT3/* | //*[concat('#',@gml:id)=$VAR1/ex9:rFT2toFT3/@xlink:href]) return $VAR2) satisfies $x/ex9:att = true()">ts9_ft1_constraint3: rFT1toFT2.rFT2toFT3-&gt;exists(x|x.att = true)</assert>
      <assert test="some $x in for $VAR1 in (current()/ex9:rFT1toFT2/* | //*[concat('#',@gml:id)=current()/ex9:rFT1toFT2/@xlink:href]) return $VAR1 satisfies some $y in for $VAR1 in ($x/ex9:rFT2toFT3/* | //*[concat('#',@gml:id)=$x/ex9:rFT2toFT3/@xlink:href]) return $VAR1 satisfies $y/ex9:att = true()">ts9_ft1_constraint4: rFT1toFT2-&gt;exists(x|x.rFT2toFT3-&gt;exists(y|y.att = true))</assert>
    </rule>
  </pattern>
</schema>

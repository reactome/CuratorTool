#!/bin/bash
# Generate all xml files
java -Xmx1024m -Dfile.encoding=UTF-8 -cp webELVTool.jar:mysql.jar:jdom.jar:xerces.jar:xml-apis.jar org.gk.biosystems.ReactomeToBioSystemsConverter $1 $2 $3 $4 $5 $6

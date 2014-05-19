#!/bin/bash
# the first file contains a list of URLs for monitoring; the second file contains a list of email to be notified; the last parameter is for the time period for checing in millsecond.
java -cp smtp.jar:pop3.jar:mailapi.jar:imap.jar:dna.jar:log4j-1.2.12.jar:. org.gk.scripts.URLMonitor resources/URLsForMonitor.txt resources/EmailsForURLMonitor.txt 300000 >& out.txt &
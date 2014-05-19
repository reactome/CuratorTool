#!/bin/bash
# Generate a mysqldump for gk_central
#NOW=$(date +"%m-%d-%y")
#DUMP_FILE_NAME=gk_central_$NOW.sql
#mysqldump --lock-tables=false -uauthortool -pT001test gk_central >DUMP_FILE_NAME
# compress the dumped file
#DUMP_FILE_NAME_ZIP=DUMP_FILE_NAME.tar.gz
#tar -czf DUMP_FILE_NAME_ZIP DUMP_FILE_NAME

# Check how many files in the directory. Make sure the total back-up number is 7 for one week only
# don't output null information
shopt -s nullglob
files=`ls -t *.tar.gz`
count=0
for file in $files
do
    #echo "$file"
    let count++
    if [ $count -gt 7 ]; then
        rm $file
    fi
done


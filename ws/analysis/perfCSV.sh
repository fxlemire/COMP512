#!/bin/bash

path=$1
outputFile="$2.csv"

echo "method,time" > $outputFile

cat $path | grep "\[PERF\]" $path | while read -r line ; do
    stringarray=($line)
    echo "${stringarray[1]},${stringarray[3]}" >> $outputFile
done

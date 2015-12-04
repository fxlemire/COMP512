#!/bin/bash

NCLI=$1
RATE=$2

NTXN=`echo "1000/$NCLI" | bc` # How many transactions per client?
NCLI=`echo "$NCLI-1" | bc` # For proper iteration below

# Recreate the data
ant autoclient -Dautocli.ntxn=-1 -Dautocli.txnfile=./autoruns/xcli_create.txt -Dautocli.mode=seq

# Assume that the MW and RMs are already running.
for CLI in $(seq 0 $NCLI)
do
	FILE=`echo "$CLI%3" | bc`
	# Start an autoclient...
	ant autoclient_perf -Dlogdir=logs -Dacid=$CLI -Dautocli.txnrate=$RATE -Dautocli.ntxn=$NTXN -Dautocli.txnfile=./autoruns/xcli_$FILE.txt -Dautocli.mode=rand &
done
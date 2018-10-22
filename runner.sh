#!/bin/bash

COUNTER=0
REACH=30

while [ $COUNTER -lt $REACH ]; do
   echo "fork on 3d parallel-3 -1"
   echo $COUNTER
   MODE=0 SHOULD_WAIT=false java -cp src/main amazed.Main maps/small.map parallel-3 -1
   echo "-----"
   COUNTER=$(( $COUNTER + 1 ))
done

COUNTER=0

while [ $COUNTER -lt $REACH ]; do
   echo "fork on 9th parallel-9 -1"
   echo $COUNTER
   MODE=0 SHOULD_WAIT=false java -cp src/main amazed.Main maps/small.map parallel-9 -1
   echo "-----"
   COUNTER=$(( $COUNTER + 1 ))
done

COUNTER=0

while [ $COUNTER -lt $REACH ]; do
   echo "branch parallel-3 -1"
   echo $COUNTER
   MODE=1 SHOULD_WAIT=false java -cp src/main amazed.Main maps/small.map parallel-9 -1
   echo "-----"
   COUNTER=$(( $COUNTER + 1 ))
done

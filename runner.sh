#!/bin/bash

# MODE 0 == Fork after; 1 == Fork at each branch
# SHOULD_WAIT - true|false if the parrent process should wait on forked threads before it continues.
# POOL_SIZE int - workers in the pool

REACH=10

function run {
    settings=(1 2 4 8)
    counter=0
    
    for pool_size in "${settings[@]}"
    do  
        echo "${1} on map ${2} with pool size:${pool_size} wait: ${4}"

        while [ $counter -lt $REACH ]; do
            POOL_SIZE=${pool_size} MODE=$3 SHOULD_WAIT=$4 java -cp src/main amazed.Main $2 $1 -1
            
            counter=$(( $counter + 1 ))
        done
        counter=0
    done  
}

maps=(small medium large huge)

for name in "${maps[@]}"
do
    echo "Running: ${name}"

    echo "parallel ${name}.map branch with wait"
    run parallel-3 maps/${name}.map 1 true
    echo "------------------------------------"

    echo "parallel-3 ${name}.map fork after"
    run parallel-3 maps/${name}.map 0 false
    echo "------------------------------------"

    echo "parallel-3 ${name}.map branch with wait"
    run parallel-3 maps/${name}.map 1 true
    echo "------------------------------------"

    echo "parallel-9 ${name}.map fork after"
    run parallel-9 maps/${name}.map 0 false
    echo "------------------------------------"

    echo "parallel-9 ${name}.map fork after with wait"
    run parallel-9 maps/${name}.map 0 true
    echo "------------------------------------"

    echo "parallel ${name}.map branch"
    run parallel-3 maps/${name}.map 1 false
    echo "------------------------------------"

    echo "parallel ${name}.map branch with wait"
    run parallel-3 maps/${name}.map 1 true
    echo "------------------------------------"
done

exit 0


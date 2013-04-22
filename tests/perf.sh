#!/bin/sh

MS="models.txt"
#CS="1 2 4 8 16 24 32 48"
CS="1"
#CS="1 8 16 48"

for a in `cat $MS`; do 
    A="0"
    G=""
    x=0
    F="$3/$a-$1.out"
    if [ -e $F ]; then 
    if [ -z "$2" -o $2 = "time" ]; then 
        G=`grep -m1 "saw in " $F \
          |sed -e 's/.* \([0-9\.]*\) sec.*/\1/g'`
    else 
        if [ "$2" = "divine" ]; then 
            G=`grep -m1 "Wall-Time" $F \
             |sed -e 's/.*Wall-Time: \([0-9\.]*\)$/\1/g'`
        else
        if [ "$2" = "waits" ]; then 
            G=`grep -m1 "waits: " $F \
             |sed -e 's/.*waits: \([0-9]*\) .*/\1/g'`
        else
        if [ "$2" = "wtime" ]; then 
            G=`grep -m1 "waits: " $F \
             |sed -e 's/.*waits: [0-9]* (\([0-9\.]*\) sec).*/\1/g'`
        else
        if [ "$2" = "bogus" ]; then 
            G=`grep -m1 "bogus " $F \
             |sed -e 's/.*bogus \([0-9\.]*\) (.*/\1/g'`
        else
        if [ "$2" = "states" ]; then 
            G=`grep -m1 "Explored: " $F \
             |sed -e 's/.*Explored: \([0-9\.]*\)$/\1/g'`
        else
        if [ "$2" = "allred" ]; then 
            G=`grep -m1 "all-red" $F \
              |sed -e 's/.*all-red states: \([0-9]*\) (.*/\1/g'`
            if [ -z "$G" ]; then
                G="0"
            fi
        else
        if [ "$2" = "trans" ]; then 
            G=`grep -m1 "Transitions:" $F \
              |sed -e 's/.*: \([0-9]*\)$/\1/g'`
        else
        if [ "$2" = "groups" ]; then 
            G=`grep -m1 "groups" $F \
              |sed -e 's/.*are \([0-9]*\) groups.*/\1/g'`
        else
        if [ "$2" = "length" ]; then 
            G=`grep -m1 "length is" $F \
              |sed -e 's/.* \([0-9]*\)\,.*/\1/g'`
        else
        if [ "$2" = "acc" ]; then 
            G=`grep -m1 "State space has" $F \
              |sed -e 's/.* \([0-9]*\) are.*/\1/g'`
        else
        if [ "$2" = "blue" ]; then 
            G=`grep -m1 "blue states:" $F \
            |sed -e 's/.*es: \([0-9\.]*\) (.*/\1/g'`
        else
        if [ "$2" = "red" ]; then 
            G=`grep -m1 "red states:" $F \
            |sed -e 's/.*red states: \([0-9]*\) (.*/\1/g'`
        fi fi fi fi fi fi fi fi fi fi fi fi fi
    else
        G="nofile"
    fi
    if [ ! -z "$G" ]; then
        A="$G"
    else
        A="-100000"
    fi
    echo "$A"
done


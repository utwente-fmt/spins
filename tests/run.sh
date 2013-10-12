#!/bin/bash

## SETTINGS:
STORAGE="tree";
STORAGE_SIZE="26";
EXP=$1
EXTRA=$2

## PATHS:
RUNDIR=`dirname "$0"`
RUNDIR=`realpath "$RUNDIR"`

LTSMINDIR="$RUNDIR/../.."
SPINSDIR="$LTSMINDIR/spins"
if [ ! -e "$LTSMINDIR/CODING-STANDARDS" ]; then
    echo place script in LTSmin source code distribution: ltsmin/spins
    exit 
fi

LTSMIN="${LTSMINDIR}/src/pins2lts-mc/prom2lts-mc"

if [ ! -e "$LTSMIN" ]; then
    echo "Compiled LTSmin tool not found (in: ${LTSMIN})." 
    echo "Using installed tool."
    LTSMIN=prom2lts-mc
fi

SPINS="${SPINSDIR}/spins.sh"
CASESTUDIES="${SPINSDIR}/tests/case_studies"

## COLORS:
CYAN="\033[0;36m"
RED="\033[0;31m"
GREEN="\033[0;32m"
YELLOW="\033[0;33m"
LIGHT_CYAN="\033[1;36m"
NO_COLOR="\033[0m"

if [ -z "$EXP" ]; then
    echo supply dirname
    exit
fi

if [ ! -e "$EXP" ]; then
    mkdir "$EXP"
fi
if [ ! -e "models-$EXP" ]; then
    mkdir "models-$EXP"
fi

function off {
    DIR="$1"
    FILE="$2"
    echo -e "${YELLOW}SKIPPING $FILE"
}

function runtest {
    DIR="$1"
    FILE="$2"
    STATES="$3"
    TRANS="$4"

    echo -en "$CYAN$FILE$GREEN"
    echo -ne "\t|$STATES\t$TRANS"
    
    OUT="$EXP/$FILE-por-r1-$STORAGE-s$STORAGE_SIZE-$THREADS.out"
    if [ ! -e $OUT ]; then
        PINS="models-$EXP/$FILE.spins"
        EXPLORE=`$LTSMIN --ratio=1 --strategy=dfs --state=$STORAGE \
             -s$STORAGE_SIZE -v $EXTRA $PINS 2>&1 | tee $OUT`

        STATESNEW=`echo "$EXPLORE"|grep "Explored: "|sed 's/.* \([0-9]*\)$/\1/g'`
        TRANSNEW=`echo "$EXPLORE"|grep "Transitions: "|sed 's/.* \([0-9]*\)$/\1/g'`
        echo -ne "\t|$STATESNEW\t$TRANSNEW"
        if [ "$STATES" != "$STATESNEW" -o "$TRANS" != "$TRANSNEW" ]; then
            echo -n "$RED WRONG STATE/TRANSITION COUNT!"
        fi
    else
        STATES=`grep "Explored: " $OUT|sed 's/.* \([0-9]*\)$/\1/g'`
        TRANS=`grep "Transitions: " $OUT|sed 's/.* \([0-9]*\)$/\1/g'`
        echo -ne "\t|($STATES)\t($TRANS)"
    fi

    echo
}

function compile {
    DIR="$1"
    FILE="$2"
    PINS="models-$EXP/$FILE.spins"
    if [ ! -e "$PINS" ]; then
        cd "models-$EXP"
        "$SPINS" $5 "$CASESTUDIES/$DIR/$FILE" > "$FILE.out"
        cd ..
    fi
}

function test {
    compile $@
    runtest $@
}


# print header
echo -e "$CYAN      \t|NORMAL\t     \t|LTSmin POR  \t|SPIN POR"
echo -e "$CYAN Model\t|States\tTrans\t|States\tTrans\t|States\tTrans"

function comment { echo ""
}

test "Tests" "sort-copies" 659683 3454988 -o3
test "dbm" "dbm.prm" 5112 20476
test "x509" "X.509.prm" 9028 35999
test "Tests" "peterson2" 55 98
test "Tests" "peterson3" 45915 128653 -o3
test "Tests" "peterson4" 12645068 47576805 -o3
test "Tests" "snoopy" 81013 273781
test "Tests" "zune.pml" 1050  1831
test "i-protocol/code/spin" "i0" 9798465 45932747 #846 deadlocks
test "i-protocol/code/spin" "i2" 14309427 48024048 -o3 #41436 deadlocks (states in spin -o1 13276960/44568068)\
test "i-protocol/code/spin" "i3" 388929 1161274 -o3 #0 deadlocks
test "i-protocol/code/spin" "i4" 95756 204405 -o3 #501 deadlocks
test "phils" "philo.pml" 1640881 16091905
test "smcs" "smcs.promela" 5066 19470 -o3
test "jspin-examples" "frogs.pml" 81 91
test "needham" "model_2init_fixed_types.spin" 3597 9654 #error in deadlocks due to unimplemented valid end states
test "needham" "model_2init_original.spin" 4143 10751 #error in deadlocks due to unimplemented valid end states
test "relay" "LOGICAL" 1026 3715
test "relay" "SMALL1" 36970 163058
test "relay" "SMALL2" 7496 32276
test "relay" "SMALLSTA" 876 2147
test "Samples" "p96.2.pml" 48 65
test "Samples" "p104.2.pml" 1594 3109 
test "Samples" "p105.1.pml" 33 47 # 1 deadlock
test "Samples" "p107.pml" 3 2
test "Samples" "p116.pml" 29 38
test "Samples" "p117.pml" 354 828 # 1 deadlock
test "Samples" "p312.pml" 160 185 -o3 # 5 assertion errors
test "Samples" "p319.pml" 1203 3017 
test "Samples" "p320.pml" 81 116
test "SystemC-TLM/chain_assert0" "chain17.spin" 188 193 #170 deadlocks
test "SystemC-TLM/chain_assert1" "chain13.spin" 148 153 #130 deadlocks
test "fgs" "fgs.promela" 242 3388
test "garp/garp-a" "garp" 48363145 247135869 #2636 deadlocks, large
test "brp" "brp.prm" 3280269 7058556 -o3


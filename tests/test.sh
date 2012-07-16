#!/bin/bash

DIR=`dirname $0`
pushd $DIR > /dev/null
BASE=`pwd`

STORAGE="tree";
STORAGE_SIZE="26";
THREADS="2"
LTSMIN="spinja2lts-mc"
SPINPOR="$BASE/spinpor.sh"
SPINJA="$BASE/../spinjal.sh"
CASESTUDIES="$BASE/case_studies"

CYAN="\033[0;36m"
RED="\033[0;31m"
GREEN="\033[0;32m"
YELLOW="\033[0;33m"
LIGHT_CYAN="\033[1;36m"
NO_COLOR="\033[0m"

BACKUP=previous
POR=0

promela_file=
while (( $# > 0 )); do
    if [ "$1" == "--por" ]; then
        POR=1;
    else
        BACKUP="$1"
    fi
    shift;
done

function backup {
    DIR=$1
    FILE=$2
    
    DST="backup-$BACKUP"
    if [ ! -e $DST ]; then
        mkdir $DST
    fi
    cp -f $FILE.spinja $DST
    if [ $? -ne 0 ]; then
        echo Backing up $FILE.spinja failed!
        #exit -10
    fi
    cp -f $FILE.spinja.c $DST
    if [ $? -ne 0 ]; then
        echo Backing up $FILE.spinja.c failed!
        #exit -11
    fi
}

function off {
    DIR=$1
    FILE=$2
    echo -e "${YELLOW}SKIPPING $FILE"
}

function por {
    DIR=$1
    FILE=$2
    STATES=$3
    TRANS=$4

    echo -en "$CYAN$FILE$GREEN"
    cd $DIR
    
    # explore
    #EXPLORE=`$LTSMIN --strategy=dfs --state=$STORAGE --threads=$THREADS -s$STORAGE_SIZE -v $FILE.spinja 2>&1`
    #if [ $? -ne 0 ]; then
    #    echo -e "${RED}FAILED"
    #    echo "ERROR in exploration of $FILE"
    #    echo "$EXPLORE"
    #    exit -2
    #fi
    #STATES=`echo "$EXPLORE"|grep "Explored: "|sed 's/.* \([0-9]*\)$/\1/g'`
    #TRANS=`echo "$EXPLORE"|grep "Transitions: "|sed 's/.* \([0-9]*\)$/\1/g'`

    echo -ne "\t|$STATES\t$TRANS"
    
    # por
    EXPLORE=`$LTSMIN --strategy=dfs --state=$STORAGE --threads=$THREADS -s$STORAGE_SIZE -v --por $FILE.spinja 2>&1`
    if [ $? -ne 0 ]; then
        echo -e "${RED}FAILED"
        echo "ERROR in POR exploration of $FILE"
        echo "$EXPLORE"
        exit -2
    fi

    STATES=`echo "$EXPLORE"|grep "Explored: "|sed 's/.* \([0-9]*\)$/\1/g'`
    TRANS=`echo "$EXPLORE"|grep "Transitions: "|sed 's/.* \([0-9]*\)$/\1/g'`
    echo -ne "\t|$STATES\t$TRANS"

    # SPIN POR
    EXPLORE=`$SPINPOR -o1 $5 $FILE 2>&1`
    if [ $? -ne 0 ]; then
        echo -e "${RED}FAILED"
        echo "ERROR in exploration of $FILE"
        echo "$EXPLORE"
        exit -2
    fi

    STATES=`echo "$EXPLORE"|grep "states, stored"|sed 's/.* \([0-9]*\) states,.*/\1/g'`
    TRANS=`echo "$EXPLORE"|grep "transitions"|sed 's/.* \([0-9]*\) transitio.*/\1/g'`
    echo -ne "\t|$STATES\t$TRANS"
    echo
}

function check {
    DIR=$1
    FILE=$2
    STATES=$3
    TRANS=$4

    echo -en "$CYAN+ Testing $DIR/$FILE: "
    cd $DIR
    
    backup $DIR $FILE
    if [ $? -ne 0 ]; then
        echo Backing up $FILE.spinja.c failed!
        exit -11
    fi

    # compile
    OUT=`$SPINJA $5 $FILE 2> /tmp/err`
    if [ $? -ne 0 ]; then
        echo -e "${RED}FAILED"
        echo "ERROR while compiling $FILE"
        echo "$OUT"
        cat /tmp/err
        exit -1
    fi
    
    # explore
    EXPLORE=`$LTSMIN --strategy=dfs --state=$STORAGE --threads=$THREADS -s$STORAGE_SIZE -v $FILE.spinja 2>&1`
    if [ $? -ne 0 ]; then
        echo -e "${RED}FAILED"
        echo "ERROR in exploration of $FILE"
        echo "$EXPLORE"
        exit -2
    fi

    FOUND_STATES=`echo "$EXPLORE"|grep "Explored: "|sed 's/.* \([0-9]*\)$/\1/g'`
    if [ "$STATES" != "$FOUND_STATES" ]; then
        echo -e "${RED}FAILED"
        echo "STATE COUNT mismatch, found $FOUND_STATES, expected $STATES."
        echo "$EXPLORE"
        exit -3
    fi

    FOUND_TRANS=`echo "$EXPLORE"|grep "Transitions: "|sed 's/.* \([0-9]*\)$/\1/g'`
    if [ "$TRANS" != "$FOUND_TRANS" ]; then
        echo -e "${RED}FAILED"
        echo "TRANS COUNT mismatch, found $FOUND_TRANS, expected $TRANS."
        echo "$EXPLORE"
        exit -4 
    fi
    ERR=`cat /tmp/err`
    if [ ! -z "$ERR" ]; then
        echo -e "${YELLOW}WARNINGS"
        echo -e "$ERR"
    else
        echo -e "${GREEN}ALL_OK"
    fi
}

function test {
    cd $CASESTUDIES
    if [ "$POR" == "1" ]; then
        por $@
    else
        check $@
    fi
}


if [ "$POR" == "1" ]; then
    # print header
    echo -e "$CYAN      \t|NORMAL\t     \t|LTSmin POR  \t|SPIN POR"
    echo -e "$CYAN Model\t|States\tTrans\t|States\tTrans\t|States\tTrans"
fi

function comment { echo ""
}
test "brp" "brp.prm" 3280269 7058556 -o3
test "dbm" "dbm.prm" 5112 20476
test "fgs" "fgs.promela" 242 3388
off "garp/garp-a" "garp" 48363145 247135869 #2636 deadlocks, large
test "i-protocol/code/spin" "i0" 9798465 45932747 #846 deadlocks
test "i-protocol/code/spin" "i2" 14309427 48024048 -o3 #41436 deadlocks
test "i-protocol/code/spin" "i3" 388929 1161274 -o3 #0 deadlocks
test "i-protocol/code/spin" "i4" 95756 204405 -o3 #501 deadlocks
test "jspin-examples" "frogs.pml" 81 91
test "needham" "model_2init_fixed_types.spin" 3552 9570 -o3 #error in deadlocks due to undoc die behavior spin
test "needham" "model_2init_original.spin" 4047 10575 -o3 #error in deadlocks due to undoc die behavior spin
test "relay" "LOGICAL" 1026 3715
test "relay" "SMALL1" 36970 163058
test "relay" "SMALL2" 7496 32276
test "relay" "SMALLSTA" 876 2147
test "smcs" "smcs.promela" 5066 19470 -o3
test "Tests" "peterson2" 55 98
test "Tests" "peterson3" 45915 128653 -o3
test "Tests" "peterson4" 12645068 47576805 -o3
test "Tests" "snoopy" 81013 273781
test "Tests" "zune.pml" 1050  1831
test "Tests" "sort-copies" 659683 3454988 -o3
test "Samples" "p96.2.pml" 48 65
test "Samples" "p104.2.pml" 1594 3109 
test "Samples" "p105.1.pml" 33 47 # 1 deadlock
test "Samples" "p107.pml" 3 2
test "Samples" "p116.pml" 29 38
test "Samples" "p117.pml" 354 828 # 1 deadlock
test "Samples" "p312.pml" 160 185 -o3 # 5 assertion errors
test "Samples" "p319.pml" 1203 3017 
test "Samples" "p320.pml" 81 116
test "phils" "philo.pml" 1640881 16091905
test "SystemC-TLM/chain_assert0" "chain17.spin" 188 193 #170 deadlocks
test "SystemC-TLM/chain_assert1" "chain13.spin" 148 153 #130 deadlocks
test "x509" "X.509.prm" 9028 35999

popd > /dev/null

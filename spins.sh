#!/bin/sh
# Simple bash shell script to launch SpinS and generate a PINS binary
# for the LTSmin tool set.

script_dir=`dirname "$0"`
if [ "${script_dir:0:1}" != "/" ]; then
    script_dir=$(pwd)/$script_dir
fi

CP="$script_dir/spins.jar"
CP="$script_dir/build/classes"
promela_file=
no_compile=0
verbose=0

for option in ${1+"$@"}; do
    if [ "${option:0:2}" == "-I" ]; then
        no_compile=1
    elif [ "${option:0:2}" == "-v" ]; then
        verbose=1
    elif [ "${option:0:1}" != "-" ]; then
		promela_file="$option"
    fi
done

#if [ -z "$promela_file" ]; then
#    echo "usage: spins [options] promela_file" ;
#    echo "options will be passed to GCC"
#    exit 1
#fi

promela_name=`basename $promela_file`
output_file="${promela_name}.spins.c"

if [ -f "$output_file" ]; then
	rm -f "$output_file";
fi

# Total memory in KB
if [ -e /proc/meminfo ]; then 
    totalMemKB=`awk '/MemTotal:/ { print $2 }' /proc/meminfo`
    totalMemMB=$(( $totalMemKB/1024 ))
else
    totalMem=`top -l 1 | grep PhysMem:`
    totalMemMB=`echo "$totalMem" | sed "s/PhysMem:[ ]*\([0-9]*\)M.*/\1/g"`
    if [ "$totalMem" = "$totalMemMB" ]; then
        totalMemGB=`echo "$totalMem" | sed "s/PhysMem:[ ]*\([0-9]*\)G.*/\1/g"`
        totalMemMB=$(( $totalMemGB*1024 ))
    fi
fi
heapMB=$(( $totalMemMB*100/75 ))
commitedMB=$(( $heapMB/2 ))

java -Xms${commitedMB}m -Xmx${heapMB}m -Xss64m -cp $CP spins.Compile ${1+"$@"}
ERROR=$?
if [ $ERROR -ne 0 ]; then
	echo "Compilation of $promela_file failed"
	exit $ERROR;
fi

if [ $no_compile == 1 ]; then
    echo
    exit 0
fi

CC="gcc -fPIC -shared -O2 -ggdb $CFLAGS -Wno-unused-variable \
    -Wno-parentheses-equality \
    -Wno-initializer-overrides \
    -Wno-unused-but-set-variable \
    -Wno-unknown-warning-option \
    $output_file -o $promela_name.spins"

if [ $verbose = 1 ]; then
    echo $CC
fi

$CC

if [ ! $? -eq 0 ]; then
	echo "Compilation of $output_file failed"
else
	echo "Compiled C code to PINS library $promela_name.spins"
fi

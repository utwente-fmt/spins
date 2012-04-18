#!/bin/bash
# Simple bash shell script to launch SpinJa using the LTSmin backend

script_dir=`dirname $0`
if [ "${script_dir:0:1}" != "/" ]; then
    script_dir=$(pwd)/$script_dir
fi

CP="$script_dir/spinja.jar"
#CP="$script_dir/build/classes"
show_usage=
promela_file=
gcc_options=

if [ $# -eq 0 ] ; then
    echo "usage: spinja [gcc_options] promela_file" ;
    echo "options will be passed to GCC"
    exit 1
fi


promela_file=
while (( $# > 0 )); do
    if [ "$1" == "-s" ]; then
        SKIP_SPINJA="1";
    elif [ "${1:0:1}" == "-" ]; then
        OPTIONS="$OPTIONS $1"
    else
		promela_file=$1
    fi
    shift;
done

#while [ $# -gt 1 ] ; do
#    gcc_options="$gcc_options $1"
#    shift
#done

promela_name=`basename $promela_file`
output_file="${promela_name}.spinja.c"

if [ "$SKIP_SPINJA" != "1" ]; then
	if [ -f "$output_file" ]; then
		rm -f "$output_file";
	fi
	
	java -Xms120m -Xmx2048m -cp $CP spinja.Compile $OPTIONS -l $promela_file
	ERROR=$?
	if [ $ERROR -ne 0 ]; then
		echo "Compilation of $promela_file failed"
		exit $ERROR;
	fi
else
	echo "Skipping compilation...";
fi

gcc -fPIC -gstabs -shared $output_file -o $promela_name.spinja -O2 -gstabs $gcc_options
if [ ! $? -eq 0 ]; then
	echo "Compilation of $output_file failed"
else
	echo "Compiled C model to $promela_name.spinja"
fi

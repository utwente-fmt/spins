#!/bin/bash
# Simple bash shell script to launch SpinJa using the LTSmin backend

echo $0

script_dir=`dirname $0`
if [ "${script_dir:0:1}" != "/" ]; then
    script_dir=$(pwd)/$script_dir
fi

show_usage=
promela_file=
gcc_options=

if [ $# -eq 0 ] ; then
    echo "usage: spinja [gcc_options] promela_file" ;
    echo "options will be passed to GCC"
    exit 1
fi


while [ $# -gt 1 ] ; do
    gcc_options="$gcc_options $1"
    shift
done
promela_file=$1
promela_name=`basename $1`
output_file="${promela_name}.spinja.c"

if [ -f "$output_file" ]; then
	rm "$output_file";
fi

java  -cp "$script_dir/spinja.jar"   spinja.Compile -o3 -l "$promela_file"
if [ ! -f "$output_file" ]; then
	echo "Compilation of $promela_file failed"
	exit;
fi

gcc -fPIC -shared $output_file -o $promela_file.spinja -O0 -g $gcc_options
if [ ! $? -eq 0 ]; then
	echo "Compilation of $output_file failed"
else
	echo "Compiled C model to $output_file"
fi

#!/bin/bash
# Simple bash shell script to launch SpinJa

echo $0

script_dir=$(pwd)/`dirname $0`

show_usage=
promela_file=
spinja_options=

if [ $# -eq 0 ] ; then
    echo "usage: spinja [options] promela_file" ;
    echo "options will be passed to the SpinJa model checker"
    exit 1
fi


while [ $# -gt 1 ] ; do
    spinja_options="$spinja_options $1"
    shift
done
promela_file=$1
output_file="$1.spinja.c"

if [ -f $output_file ]; then
	rm $output_file;
fi

java  -cp "$script_dir/spinja.jar"   spinja.Compile -o3 -l $promela_file
if [ ! -f $output_file ]; then
	echo "Compilation of $promela_file failed"
	exit;
fi

gcc -fPIC -shared $output_file -o $promela_file.spinja -O0 -g $spinja_options
if [ ! $? -eq 0 ]; then
	echo "Compilation of $output_file failed"
fi

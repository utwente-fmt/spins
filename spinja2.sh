#!/bin/bash
# Simple bash shell script to launch SpinJa

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

java  -cp "$script_dir/spinja.jar"   spinja.Compile -o3 $promela_file
javac -cp "$script_dir/spinja.jar":. spinja/PanModel.java
java  -Xss16m -Xms256m -Xmx1024m -cp "$script_dir/spinja.jar":. spinja.PanModel $spinja_options

#!/bin/bash

if [ -z "$1" ]; then
    echo use $0 <dir> [<flags>]
    exit
fi

if [ ! -e "$1" ]; then
    mkdir "$1"
fi
cd $1
for a in ../$3/*.1.pm; do
    echo -en "`basename $a`\t"
    if [ ! -e "`basename $a`.cpp" ]; then
        echo COMPILING
        ~/workspaces/ltsmin/spinja/spinjal.sh $a
    else
        echo SKIPPING
    fi
done
cd ..

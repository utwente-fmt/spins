#!/bin/bash

spin -a $@
cc -O3 -DNOFAIR -DREDUCE -DNOBOUNDCHECK -DCOMP -DNOCOLLAPSE -DSAFETY -DMEMLIM=100000 -o pan pan.c
./pan -m10000000 -c0 -n -w20 

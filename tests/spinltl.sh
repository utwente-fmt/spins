#!/bin/bash

spin -a $@
cc -O3 -DNOFAIR -DNOREDUCE -DNOBOUNDCHECK -DCOMP -DNOCOLLAPSE -DMEMLIM=100000 -o pan pan.c
./pan -m10000000 -c0 -n -w20 -a 

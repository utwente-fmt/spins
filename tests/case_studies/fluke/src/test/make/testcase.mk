# Copyright (c) 1996 The University of Utah and
# the Computer Systems Laboratory at the University of Utah (CSL).
# All rights reserved.
#
# Permission to use, copy, modify and distribute this software is hereby
# granted provided that (1) source code retains these copyright, permission,
# and disclaimer notices, and (2) redistributions including binaries
# reproduce the notices in supporting documentation, and (3) all advertising
# materials mentioning features or use of this software display the following
# acknowledgement: ``This product includes software developed by the
# Computer Systems Laboratory at the University of Utah.''
#
# THE UNIVERSITY OF UTAH AND CSL ALLOW FREE USE OF THIS SOFTWARE IN ITS "AS
# IS" CONDITION.  THE UNIVERSITY OF UTAH AND CSL DISCLAIM ANY LIABILITY OF
# ANY KIND FOR ANY DAMAGES WHATSOEVER RESULTING FROM THE USE OF THIS SOFTWARE.
#
# CSL requests users of this software to return to csl-dist@cs.utah.edu any
# improvements that they make and grant CSL redistribution rights.
#
# testcase.mk - Rules for building test cases for Fluke verification
#------------------------------------------------------------------------------
#
#  Things you can define to affect these rules:
#
#    CASE=xxx          - Name of the test case in question (basename of files)
#                          This *must* be defined for the rules to work.
#    PROMELA=xxx.pr    - Name of promela source file containing test.  Defaults
#                          to $(CASE).pr.  Override when doing many test runs
#                          with the same source
#
#    MUNGE=bool        - "munging" to produce sensible output -- def=="yes"
#
#    MODE=xxx          - "safety" or "progress" -- def=="safety"
#
#    MAXDEPTH=nnn      - Maximum search depth for state space DFS, def 100000
#    SUPERTRACE        - If defined, compiles and runs with supertracing
#    MEMBITS=nnn       - Maximum memory to use, def 27 (2^27=256Mb)
#    HASHBITS=nnn      - Maximum size of hash table, def 22 (2^22=4Mb)
#    LARGE=bool        - Shorthand for MEMBITS=29,HASHBITS=29,SUPERTRACE=yes
#                           default=="no"
#
#-- Configuration Decoding ----------------------------------------------------

ifeq ($(LARGE),yes)
MEMBITS=29
HASHBITS=29
SUPERTRACE=yes
endif

ifndef PROMELA
PROMELA=$(CASE).pr
endif

ifndef MAXDEPTH
MAXDEPTH=100000
endif

ifndef MEMBITS
MEMBITS=27
endif

ifndef HASHBITS
HASHBITS=22
endif

ifndef MODE
MODE=safety
endif

ifeq ($(MODE),progress)
GCCMODEFLAGS=-DNP -DREACH
# -l == find non-progress cycles
PANMODEFLAG=-l
else
GCCMODEFLAGS=-DSAFETY
PANMODEFLAG=
endif

GCCARGS=-D_POSIX_SOURCE -DNOCLAIM -DNOFAIR -DMEMCNT=$(MEMBITS) $(GCCMODEFLAGS)
##
## -n == no listing of unreached states
## -w == size of hash table
## -m == max depth to recurse too
## -I == approximate search for shortest path to error
## -c1 == stop at 1st error 
## plus the PANMODEFLAGS from above
PANARGS=-n -w$(HASHBITS) -m$(MAXDEPTH) -I -c1 $(PANMODEFLAG)

ifeq ($(SUPERTRACE),yes)
GCCARGS+=-DBITSTATE
endif

CPPARGS=-E -I.. -P
ifeq ($(MUNGE),no)
MUNGELINE=cat $(PROMELA) | gcc $(GCCMODEFLAGS) $(CPPARGS) - > $(CASE)
DUMPMUNGE=
else
MUNGELINE=cat $(PROMELA) | gcc $(GCCMODEFLAGS) $(CPPARGS) - | prMunge > $(CASE)
DUMPMUNGE= | dumpMunge
endif

#-- End of Config Decoding ----------------------------------------------------

MAKEFLAGS=--no-print-directory --stop

.SUFFIXES:			   # delete existing suffix rules
.SUFFIXES: .pr .pass .trail .dump

all: $(TESTS)

clean:
	rm -f pan*
	for i in $(TESTS) ; do $(MAKE) CASE=`basename $${i} .pass` cleancase ; done

testcase:
	@echo "-------------------------------------------------------------------------------"
	@echo "Test Code: $(PROMELA)   Desired Result: $(CASE).pass"
	@echo ""
	@#
	@# Clean up from last testcase (if any)
	@#
	@$(MAKE) CASE=$(CASE) cleancase
	@rm -f pan*
	@#
	@# Munge if desired
	@#
	@echo "Spinning verifier..."
	$(MUNGELINE)
	## -X == send errors to stdout, not stderr
	spin -a -X $(CASE)
	@#
	@# Compile the verifier
	@#
	@echo "Compiling the verifier..."
	time gcc -o pan $(GCCARGS) pan.c
	@test -f pan
	@#
	@# Run the verification
	@#
	@echo "Running the verification..."
	@echo "" >> $(CASE).out
	time ./pan $(PANARGS) | tee $(CASE).out
	@if [ -f $(CASE).trail ] ; then        \
	  $(MAKE) $(CASE).dump ;                  \
	  echo "" ;                               \
	  egrep "^pan:" < $(CASE).out ;           \
	  echo "" ;                               \
	  echo "*** TEST FAILED ***" ;            \
	  echo "" ;                               \
	  echo "Trace is in: $(CASE).dump" ;      \
	  echo "" ;                               \
	  egrep "^spin" < $(CASE).dump ;          \
	  echo "" ;                               \
	  false ;                                 \
	fi
	@touch $(CASE).pass
	@echo "" ; echo "TEST PASSED" ; echo ""

cleancase:
	rm -f $(CASE) $(CASE).pass $(CASE).trail $(CASE).dump $(CASE).out

%.pass: %.pr
	@$(MAKE) CASE=$* testcase

%.dump: %.trail
	-spin -p -t $* $(DUMPMUNGE) > $@

depend:
	@rm -f .depend
	@echo "" > .depend
	( for i in *.pr ; do \
	  cat $$i | gcc -E -MM - | sed "s/-:/`basename $$i .pr`\.pass:/g" ; \
	done ) >> .depend

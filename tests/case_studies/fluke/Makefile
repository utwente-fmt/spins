##
## root Makefile.  Really only makes tar files.
##

# Tar stuff
TARFILE=verify.tgz
TARTARGETS=Makefile README README.SPIN TODO                         \
	doc/*.tex doc/README doc/TODO doc/Makefile        	    \
	doc/papers/*						    \
	src/README src/control/*.pr src/fluke/*.pr src/support/*.pr \
	src/test/* bin/*

all:
	@echo "make tar|clean"

tar:
	tar cvfz /dev/null $(TARTARGETS) | grep -v "/$$" | grep -v "CVS" | grep -v "\.#" | xargs tar cvfz $(TARFILE)

clean: 
	make -C doc clean
	make -C src/test clean
	rm -f .~* */.~* */*/.~* 

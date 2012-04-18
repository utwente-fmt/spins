# Makefile for multitude of LaTeX docs

# XXX: Still have to make twice to get TOC and Bib right
# XXX: nested dependencies are ignored

PSFILES =  report.ps ipc-outline.ps sim-spec.ps proposal.ps progress.ps \
           properties.ps
DVIFILES = ${PSFILES:.ps=.dvi}
TEXFILES = ${PSFILES:.ps=.tex}

all: $(DVIFILES) $(PSFILES)

#-- Generic TeX Rules ---------------------------------------------------------

.SUFFIXES: .dvi .ps .tex

.dvi.ps: $<
	dvips -o $@ $<

.tex.dvi: $<
	latex $<
	-if [ -f `basename $< .tex`.bib ] ; then bibtex `basename $< .tex` ; fi

#XXX I don't know what this is, but Pat put it here
#%-right: %.dvi
#	rm $<
#	bibtex $(basename $<)
#	make $<

clean:
	rm -f *.aux *.dvi *.ps *.bbl *.blg *.log *.toc

#-- Dependencies --------------------------------------------------------------

depend:
	@rm -f .depend
	@touch .depend
	for i in $(TEXFILES) ; do                                           \
	  echo `basename $${i} .tex`.dvi:                                   \
            `egrep '^\\\\input{' $${i} | cut -c8- | cut -d'}' -f1`          \
             >> .depend                                                    ;\
	done

-include .depend

#-- XXX: Pat's Shortcuts (now superfluous?) -----------------------------------

rep: report.ps

report: report-right

ipc: ipc-outline.ps

sim: sim-spec.ps

proposal: proposal.ps

progress: progress.ps

properties: properties.ps

ps: ipc sim proposal progress properties

dvi: proposal.dvi ipc-outline.dvi sim-spec.dvi progress.dvi properties.dvi

right: proposal-right

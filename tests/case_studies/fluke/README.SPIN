README.SPIN
-----------

This file details the change we made to SPIN (v.2.9.4) to let it
compile our larger simulations; it is also a collected list of the
grips and shortcomings of Promela and SPIN that we discovered.  Of
course, some of these grips are a result of our using SPIN outside its
core domain of protocol verification.

Many of our proposed changes would dilute Promela and would probably
best be implemented in a language "on top of", or that generated
Promela.

o d_step jump table was too short (we bumped it to 1024).  I'm not
	quite sure what it does, but bumping the size of the 
	Jumpto, Tojump, and Special arrays to 1024 entries (And the
	code that checks the bounds in this file) fixed our problem.

Gripes:

o non-deterministic if's are annoying when translating large blocks
	of C if-else-if-else-if-... statements, and C switch
	statements.  A deterministic switch() would probably be 
	a handy tool.

o Cannot nest atomic and dstep blocks.  Nesting dsteps should be
	quite trivial, nesting atomic blocks is less obvious,
	I'm not sure what semantics I'd want from nested
	atomic-dsteps or dstep-atomics. (Of course, this is
	only a real problem with overzealous use of macros...)

o CPP is hardcoded in main.c.  It should be a configuration option,
	or at least, a command line option.

o nested scope variable _names_.  We abused CPP macros in our
	implementation, and had the need for "local variables".
	It would be nice if a variable could be declared in a
	local scope;

o hex digits (0xF00).  Standard octal and hex constant parsing
	would be very nice.

o pan -? is ugly to type (have to escape the '?' in Unix) use '-h'.

o Instead of "skip" I used (1==1), (0==0), or any other ineffectual
	statement, solely so that it showed up meaningfully in the
	dump; Spin could easily support this.  skip could take an
	optional string argument describing it, and that should be
	used in the dump.

o A panic() function that would printf even during verification,
	very useful.

o SPIN should understand the #file and #line directives spit out 
	by most CPPs.

o Return codes should follow Unix conventions---it currently
	returns non-zero when generating a dump file from a trail;
	Thus, making use of this in a Makefile is unnecessarily complicated.

o No control over the basename of files to generate (ie, needs
	a '-o' option.)  This is especially true of the pan.* files.
	Again, this makes using spin in a makefile more difficult,
	especially when there are multiple test files being run.


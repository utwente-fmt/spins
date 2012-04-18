SpinJa - tests

This is the ./tests/ directory of SpinJa.
This directory contains two subdirectories with several Promela models
that have been used for testing and benchmarking SpinJa.

tests/beem
    Promela models from the BEEM database (http://anna.fi.muni.cz/models/) 
    which have been used to benchmark SpinJa. Results of these experiments
    can be found in Marc de Jonge's Master Thesis.

tests/beem/all
    All BEEM models.

tests/sumo
    Additional Promela benchmark models. The SUMO language is a (small) 
    subset of the Promela language. The SUMO language has been used at 
    the Univerisity of Twente for teaching model checking techniques.

tests/regression
    Small studies on the semantics of SPIN. TODO: rename files, reorganize and
    script.

tests/case_studies
    (Large) case studies found in the Spin 5.2.5 release and on
    http://www.albertolluch.com/research/promelamodels.
    A surprisingly large amount of these advanced SPIN models runs with the
    current version of SpinJa. Models included in the test.sh script yield
    the same amount of states as SPIN does (sometimes turning of control flow
    and state merging optimizations: -o1 and -o3).
    In the head of the fight, some of these models have been slightly changed
    to be parsed by both SPIN and SpinJa. For example, comments that crashed
    the lexer have been taken out, (in) variables renamed and channels arrays
    split to be able to pass them to functions in SpinJa. Files can be eaily
    compared with the originals available from the above sources. Additionally,
    SpinJa requires in advance the maximum number of proctype instances. This
    number is prompted for per proctype, but can (and is) also be recorded in
    the models with the reserved preprocessor define __instances_<proctype_name>.

test.sh
    Main black-box testing facility for SpinJa. Large case studies are executed
    and checked for a correct state/transition count. Options:
     o --por: use partial order reduction and show reductions
     o <name>: "backup-<name>" is the location where a backup is created of
       previously compiled models. Default: "backup-previous".

spin*.sh
    Helper scripts to execute SPIN with different settings on the test models.

dot.sh
    Helper script to generate DOT/PNG images from models. TODO: currently broken.

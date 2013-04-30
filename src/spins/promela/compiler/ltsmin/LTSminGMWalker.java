package spins.promela.compiler.ltsmin;

import static spins.promela.compiler.ltsmin.util.LTSminUtil.assign;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.chanContentsGuard;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.chanLength;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.channelBottom;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.channelNext;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.compare;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.constant;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.decr;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.id;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.incr;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.not;
import static spins.promela.compiler.parser.PromelaConstants.ASSIGN;
import static spins.promela.compiler.parser.PromelaConstants.CH_READ;
import static spins.promela.compiler.parser.PromelaConstants.CH_SEND_SORTED;
import static spins.promela.compiler.parser.PromelaConstants.DECR;
import static spins.promela.compiler.parser.PromelaConstants.EQ;
import static spins.promela.compiler.parser.PromelaConstants.GT;
import static spins.promela.compiler.parser.PromelaConstants.GTE;
import static spins.promela.compiler.parser.PromelaConstants.INCR;
import static spins.promela.compiler.parser.PromelaConstants.LT;
import static spins.promela.compiler.parser.PromelaConstants.LTE;
import static spins.promela.compiler.parser.PromelaConstants.NEQ;
import static spins.promela.compiler.parser.PromelaConstants.tokenImage;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import spins.promela.compiler.ProcInstance;
import spins.promela.compiler.Proctype;
import spins.promela.compiler.actions.Action;
import spins.promela.compiler.actions.AssignAction;
import spins.promela.compiler.actions.ChannelReadAction;
import spins.promela.compiler.actions.ChannelSendAction;
import spins.promela.compiler.actions.ExprAction;
import spins.promela.compiler.actions.OptionAction;
import spins.promela.compiler.expression.AritmicExpression;
import spins.promela.compiler.expression.BooleanExpression;
import spins.promela.compiler.expression.ChannelLengthExpression;
import spins.promela.compiler.expression.ChannelOperation;
import spins.promela.compiler.expression.ChannelReadExpression;
import spins.promela.compiler.expression.CompareExpression;
import spins.promela.compiler.expression.ConstantExpression;
import spins.promela.compiler.expression.EvalExpression;
import spins.promela.compiler.expression.Expression;
import spins.promela.compiler.expression.Identifier;
import spins.promela.compiler.expression.RemoteRef;
import spins.promela.compiler.expression.RunExpression;
import spins.promela.compiler.ltsmin.LTSminPrinter.ExprPrinter;
import spins.promela.compiler.ltsmin.matrix.DepMatrix;
import spins.promela.compiler.ltsmin.matrix.DepRow;
import spins.promela.compiler.ltsmin.matrix.GuardInfo;
import spins.promela.compiler.ltsmin.matrix.LTSminGuard;
import spins.promela.compiler.ltsmin.matrix.LTSminGuardAnd;
import spins.promela.compiler.ltsmin.matrix.LTSminGuardBase;
import spins.promela.compiler.ltsmin.matrix.LTSminGuardContainer;
import spins.promela.compiler.ltsmin.matrix.LTSminGuardNand;
import spins.promela.compiler.ltsmin.matrix.LTSminGuardNor;
import spins.promela.compiler.ltsmin.matrix.LTSminGuardOr;
import spins.promela.compiler.ltsmin.matrix.LTSminLocalGuard;
import spins.promela.compiler.ltsmin.model.LTSminIdentifier;
import spins.promela.compiler.ltsmin.model.LTSminModel;
import spins.promela.compiler.ltsmin.model.LTSminTransition;
import spins.promela.compiler.ltsmin.model.ResetProcessAction;
import spins.promela.compiler.ltsmin.state.LTSminPointer;
import spins.promela.compiler.ltsmin.state.LTSminStateVector;
import spins.promela.compiler.ltsmin.util.LTSminDebug;
import spins.promela.compiler.parser.ParseException;
import spins.promela.compiler.parser.PromelaTokenManager;
import spins.promela.compiler.variable.ChannelType;
import spins.promela.compiler.variable.Variable;
import spins.promela.compiler.variable.VariableType;

/**
 * A container for boolean state labels (part of which are guards), guard
 * matrices and state label matrix 
 * 
 * TODO: avoid allocation of dependency matrices in recurring tree searches
 * TODO: optimize case "missing" in nes search
 * TODO: RunExpr
 * 
 * @author FIB, Alfons Laarman
 */
public class LTSminGMWalker {

	static public class Params {
		public final LTSminModel model;
		public final GuardInfo guardMatrix;
		public int guards;
		public LTSminDebug debug;

		public Params(LTSminModel model, GuardInfo guardMatrix, LTSminDebug debug) {
			this.model = model;
			this.guardMatrix = guardMatrix;
			this.guards = 0;
			this.debug = debug;
		}
	}

	/**
	 * Adds all guards labels and generates the guards matrices for POR
	 * @param model
	 * @param debug
	 */
	static void generateGuardInfo(LTSminModel model, LTSminDebug debug) {
		debug.say("Generating guard information ...");
		debug.say_indent++;

		if(model.getGuardInfo()==null)
			model.setGuardInfo(new GuardInfo(model.getTransitions().size()));
		GuardInfo guardInfo = model.getGuardInfo();
		Params params = new Params(model, guardInfo, debug);

		// extact guards
		generateTransitionGuardLabels (params);

		// add the normal state labels
        for (Map.Entry<String, LTSminGuard> label : model.getLabels()) {
            guardInfo.addLabel(label.getKey(), label.getValue());
        }

        // We extend the NES and NDS matrices to include all labels
        // The special labels, e.g. progress and valid end, can then be used in
        // LTL properties with precise (in)visibility information.
        int nLabels = guardInfo.getNumberOfLabels();
        int nTrans = model.getTransitions().size();

        // generate label / slot read matrix
        generateLabelMatrix (model);


        // generate Maybe Coenabled matrix
        int nmce = generateCoenMatrix (model, guardInfo);
        int mceSize = nTrans*nTrans/2;
        debug.say(report(nmce, mceSize, "!MCE guards"));

        // generate Maybe Coenabled matrix
        int nice = generateICoenMatrix (model, guardInfo);
        int miceSize = nTrans*nTrans;
        debug.say(report(nice, miceSize, "!ICE guards"));

        // generate NES matrix
        int nnes = generateNESMatrix (model, guardInfo);
        int nesSize = nTrans * nLabels;
        debug.say(report( nnes, nesSize, "!NES guards"));

        // generate NDS matrix
        int nnds = generateNDSMatrix (model, guardInfo);
        debug.say(report( nnds, nesSize, "!NDS guards"));
        
        // generate Do Not Accord Matrix
        int ndna = generateDoNoAccord (model, guardInfo);
        int dnaSize = nTrans*nTrans/2;
        debug.say(report(ndna, dnaSize, "!DNA transitions"));
        
		debug.say_indent--;
		debug.say("Generating guard information done");
		debug.say("");
	}

    private static String report(int n, int size, String msg) {
        double perc = ((double)n * 100)/size;
        return String.format("Found %,8d /%,8d (%5.1f%%) %s.", n, size, perc, msg);
    }

	private static void generateLabelMatrix(LTSminModel model) {
        GuardInfo guardInfo = model.getGuardInfo();
		int nlabels = guardInfo.getNumberOfLabels();
	    DepMatrix dm = new DepMatrix(nlabels, model.sv.size());
		guardInfo.setDepMatrix(dm);
		for (int i = 0; i < nlabels; i++) {
			LTSminDMWalker.walkOneGuard(model, dm, guardInfo.get(i), i);
		}

		int T = model.getDepMatrix().getNrRows();
        DepMatrix testset = new DepMatrix(T, model.sv.size());
        guardInfo.setTestSetMatrix(testset);
        DepMatrix gm = guardInfo.getDepMatrix();
        for (int t = 0; t < T; t++) {
            for (int gg : guardInfo.getTransMatrix().get(t)) {
                for (int slot : gm.getRow(gg).getReads()) {
                    testset.incRead(t, slot);
                }
            }
        }
	}

	/**************
	 * NDS
	 * ************/

    /**
     * Over estimates whether guard, transition in NDS
     *
     * @return false of trans,guard not in nes, TRUE IF UNKNOWN
     */
	private static int generateNDSMatrix(LTSminModel model, GuardInfo guardInfo) {
        int nlabels = guardInfo.getNumberOfLabels();
		DepMatrix nds = new DepMatrix(nlabels, model.getTransitions().size());
		guardInfo.setNDSMatrix(nds);
		DepMatrix coen = guardInfo.getCoMatrix();
		int notNDS = 0;
		for (int g = 0; g <  nds.getNrRows(); g++) {
            LTSminGuard guard = (LTSminGuard) guardInfo.get(g);
            for (LTSminTransition trans : model.getTransitions()) {

                boolean ce = true;
                for (int g1 : guardInfo.getTransMatrix().get(trans.getGroup())) {  
                    if (!coen.isRead(g, g1)) ce = false;
                }
                
                if (ce && atomicNES(model, guardInfo, guard, g, trans, true)) {
                    nds.incRead(g, trans.getGroup());
                } else {
                    notNDS += 1;
                }
			}
		}
		return notNDS;
	}

	/**************
	 * NES
	 * ************/

	private static int generateNESMatrix(LTSminModel model, GuardInfo guardInfo) {
        int nlabels = guardInfo.getNumberOfLabels();
		DepMatrix nes = new DepMatrix(nlabels, model.getTransitions().size());
		guardInfo.setNESMatrix(nes);
        DepMatrix icoen = guardInfo.getICoMatrix();
		int notNES = 0;
		for (int g = 0; g <  nes.getNrRows(); g++) {
            LTSminGuard guard = (LTSminGuard) guardInfo.get(g);
			for (LTSminTransition trans : model.getTransitions()) {
			    
		        boolean ice = true;
		        for (int g1 : guardInfo.getTransMatrix().get(trans.getGroup())) {  
		            if (!icoen.isRead(g, g1)) ice = false;
		        }
			    
                if (ice && atomicNES(model, guardInfo, guard, g, trans, false)) {
                    nes.incRead(g, trans.getGroup());
                } else {
                    notNES += 1;
                }
			}
		}
		return notNES;
	}


    /**
     * For atomic transitions, the guards of the first should be coenabled.
     * If one of the actions of all transitive atomic transitions enables,
     * then the atomic transitions is enabling.
     */
    private static boolean atomicNES(LTSminModel model,
                                     GuardInfo guardInfo,
                                     LTSminGuard guard, int g,
                                     LTSminTransition trans,
                                     boolean invert) {
        if (!limitMCE(model, guardInfo, trans.getGroup(), guard, g, !invert)) {
            return false;
        }

        if (enables(model, trans, guard.getExpr(), g, invert)) {
            return true;
        }

        for (LTSminTransition atomic : trans.getTransitions()) {
            if (enables(model, atomic, guard.getExpr(), g, invert)) {
                return true;
            }
        }
        return false;
    }

    /**
     * This check for maybe coenabledness of the transition t with a guard g
     * (or its inverse for necessary enabledness).
     * We conjecture that in our setting, g needs only to be
     * coenabled in those variables that are written to by the transition
     * (if these exist). Because for NES/NDS we may assume g to be disabled/
     * enabled in advance and the transition only affects a few variables that
     * should make g enabled/disabled.
     * Limiting the MCE check to these variables gives additional precision
     * in the presence of disjunctions.
     */
    private static boolean limitMCE(LTSminModel model, GuardInfo guardInfo,
                                    int t, LTSminGuard guard, int g, boolean invert) {
        DepMatrix rw = model.getDepMatrix();
        DepMatrix guardReads = new DepMatrix(1, model.sv.size());
        for (int gg : guardInfo.getTransMatrix().get(t)) {
            LTSminGuard gguard = guardInfo.get(gg);
            guardReads.clear();
            LTSminDMWalker.walkOneGuard(model, guardReads, gguard, 0);
            Expression gge = gguard.getExpr();

            if (rw.isWrite(t, guardReads.getReads(0))) {
                Boolean coenabled = MCE(model, guard.getExpr(), gge, invert, false, rw.getRow(t), null);
                if (coenabled != null && !coenabled) {
                    return false;
                }
            } else {
                if (!MCE(model, guard.getExpr(), gge, invert, false, null, null)) {
                    return false;
                }
            }
        }
        return true;
    }

	/**
	 * Over estimates whether a transition can enable a guard 
     *
	 * @return false of trans definitely does not enable the guard, else true
	 */
	private static boolean enables (LTSminModel model,
	                                LTSminTransition t,
									Expression e, int g,
									boolean invert) {
        if (e instanceof EvalExpression) {
            EvalExpression eval = (EvalExpression)e;
            return enables(model, t, eval.getExpression(), g,invert);
        } else if (e instanceof BooleanExpression) {
            BooleanExpression ce = (BooleanExpression)e;
            if (ce.getToken().kind == PromelaTokenManager.BNOT ||
                ce.getToken().kind == PromelaTokenManager.LNOT) {
                return enables(model, t, ce.getExpr1(), g,!invert);
            } else {
                return enables(model, t, ce.getExpr1(), g,invert) ||
                       enables(model, t, ce.getExpr2(), g,invert);
            }
        } else {
            List<SimplePredicate> sps = new ArrayList<SimplePredicate>();
            boolean missed = extract_conjunct_predicates(model, sps, e, invert);
            if (missed) {
                DepMatrix deps = model.getDepMatrix();
                DepMatrix temp = new DepMatrix(1, model.sv.size());
                LTSminDMWalker.walkOneGuard(model, temp, new LTSminGuard(e), 0);
                return deps.isWrite(t.getGroup(), temp.getReads(0));
            }
            for (SimplePredicate sp : sps) {
                if (invert)
                    sp = sp.invert();

                if (agrees(model, t, g, sp)) {
                    return true;
                }
            }
            return false;
        }
	}

    private static boolean agrees (LTSminModel model,
                                   LTSminTransition t, int g,
                                   SimplePredicate sp) {
        DepMatrix testSet = new DepMatrix(1, model.sv.size());
        DepMatrix writeSet = new DepMatrix(1, model.sv.size());

        for (Action a : t.getActions()) {
            testSet.clear();
            writeSet.clear();
            LTSminDMWalker.walkOneGuard(model, testSet, new LTSminGuard(sp.e), 0);
            LTSminDMWalker.walkOneAction(model, writeSet, a, 0);
            if (!writeSet.isWrite(0, testSet.getReads(0)))
                continue;

            boolean conflicts = conflicts(model, a, sp, t, g, false);
            if (!conflicts) {
                return true;
        	}
        }
        return false;
    }
    
    /**************
     * DNA
     * ************/

    /**
     *  s --t1--> s'
     *  |         |
     *  t2        t2
     *  |         |
     *  v         v
     * s1 --t1--> s1'
     * 
     * Roughly, transitions do not accord if:
     * - they are never coenabled
     * - they disable each other (and are maybe coenabled as implied by DNS)
     * - their actions do commute (see is_commuting_assignment)
     */
    private static int generateDoNoAccord(LTSminModel model, GuardInfo guardInfo) {
        int nTrans = model.getTransitions().size();
        DepMatrix nda = new DepMatrix(nTrans, nTrans);
        guardInfo.setDNAMatrix(nda);
        int neverDNA = 0;
        for (int t1 = 0; t1 < nTrans; t1++) {
            nda.incRead(t1, t1);
            for (int t2 = t1+1; t2 < nTrans; t2++) {

                // check for co-enabledness
                if (!maybeCoenabled(guardInfo, t1, t2)) {
                    neverDNA++;
                    continue;
                }

                if ( canDisable(guardInfo, t1, t2) ||
                     canDisable(guardInfo, t2, t1)) {
                    nda.incRead(t1, t2);
                    nda.incRead(t2, t1);
                    continue;
                }
               
                if (!actionsCommute(model, t1, t2)) {
                    nda.incRead(t1, t2);
                    nda.incRead(t2, t1);
                    continue;
                }

                // For atomic transitions (internal steps),
                // we require a more stringent condition
                // the atomic steps may neither be disabled nor enabled so
                // that the outcome of the group is not influenced
                if ( mayAffectAtomic(model, guardInfo, t1, t2) ||
                     mayAffectAtomic(model, guardInfo, t2, t1)) {
                    nda.incRead(t1, t2);
                    nda.incRead(t2, t1);
                    continue;   
                }
                
                neverDNA++;
            }
        }
        return neverDNA;
    }

    private static boolean mayAffectAtomic (LTSminModel model,
                                            GuardInfo guardInfo,
                                            int t1, int t2) {
        LTSminTransition trans = model.getTransitions().get(t1);
        for (LTSminTransition atomic : trans.getTransitions()) {
            int a = atomic.getGroup();

            // check for co-enabledness
            if (!maybeCoenabled(guardInfo, a, t2))
                continue;

            // internal atomic action disabled / enabled by t2
            // (we may assume t2 to be enabled during atomic sequence;
            //  if it isn't the nds check below should fail)
            if ( canDisable(guardInfo, t2, a) ||
                 canEnable(guardInfo, t2, a)) {
                return true;
            }

            // internal action disables t2
            if ( canDisable(guardInfo, a, t2) ) {
                return true;
            }
            
            if (!actionsCommute(model, a, t2)) {
                return true;
            }
        }
        return false;
    }

    private static boolean actionsCommute(LTSminModel model, int t1, int t2) {

        LTSminTransition trans1 = model.getTransitions().get(t1);
        LTSminTransition trans2 = model.getTransitions().get(t2);
        
        DepMatrix depsA = new DepMatrix(1, model.sv.size());
        DepMatrix depsB = new DepMatrix(1, model.sv.size());

        // check for non-commuting actions
        for (Action acta: trans1.getActions()) {
            depsA.clear(); // get actions deps:
            LTSminDMWalker.walkOneAction(model, depsA, acta, 0);
     
            for (Action actb : trans2.getActions()) {
                depsB.clear(); // get actions deps:
                LTSminDMWalker.walkOneAction(model, depsB, actb, 0);

                // extract simple predicates for the actions
                List<SimplePredicate> allA, allB;
                try {
                    allA = allAssigns (model, acta, depsB.getWrites(0));
                    allB = allAssigns (model, actb, depsA.getWrites(0));
                } catch (ParseException e) {
                    return false;
                } 
                
                for (SimplePredicate spa : allA) {
                    for (SimplePredicate spb : allB) {
                        if (!is_commuting_assignment(model, spa, spb)) {
                            return false;
                        }
                    }
                }
            }
        }
        
        return true;
    }

    /**
     * Since complex array expressions in an identifier id are not written to
     * (see allAssigns), id refers to the same variable in s1 as in s' (see
     * mayMutuallyAffect). Therefore, simple predicates commute iff:
     * - id1 != id2, or
     * - both assign the same constant, or
     * - both are increment/decrement, or
     * - read and write to a channel.
     */
    private static boolean is_commuting_assignment(LTSminModel model,
                                                   SimplePredicate p1,
                                                   SimplePredicate p2) {
        String ref1, ref2;
        try {
            ref1 = p1.getRef(model); // convert to c code string
            ref2 = p2.getRef(model);
        } catch (AssertionError ae) {
            throw new AssertionError("Serialization of expression "+ p1.id +" or "+ p2.id +" failed: "+ ae);
        }
        if (!ref1.equals(ref2)) {
            return true;
        } else if (p1.comparison == EQ && p2.comparison == EQ) {
            return p1.constant == p2.constant;
        } else if ((p1.comparison == INCR || p1.comparison == DECR) &&
                   (p2.comparison == INCR || p2.comparison == DECR)) {
            return true;
        } else if ((p1.comparison == CH_READ && p2.comparison == CH_SEND_SORTED) ||
                   (p1.comparison == CH_SEND_SORTED && p2.comparison == CH_READ)) {
            return true;
        }
        return false;
    }

    /**
     * Extract simple predicates that help check commutativity:
     * - assignments with constants
     * - increments / decrements
     * - fifo channel reads / writes
     * All complex actions are translated to assignments. Possibly assignments
     * use complex expressions, in that case we check whether the other transition
     * (see mayMutuallyAffect) writes to these. If this is the case, or if the
     * identifier contains a complex array expression that is written to, we
     * give up, i.e. throw a ParseException.
     */
    private static List<SimplePredicate> allAssigns (LTSminModel model,
                                                     Action a,
                                                     List<Integer> writes) throws ParseException {
        List<SimplePredicate> sps = new ArrayList<SimplePredicate>();
        if (a instanceof AssignAction) {
            SimplePredicate sp1 = new SimplePredicate();
            AssignAction ae = (AssignAction)a;
            try {
                sp1.id = getConstantId(model, ae.getIdentifier(), writes);
            } catch (ParseException e1) {
                depCheck(model, a, writes); // may rethrow
                return sps; // empty
            }
            switch (ae.getToken().kind) {
                case ASSIGN:
                    sp1.comparison = EQ;
                    try {
                        sp1.constant = ae.getExpr().getConstantValue();
                    } catch (ParseException e) {
                        depCheck(model, ae.getExpr(), writes); // may rethrow
                        return sps; // empty
                    }
                    break;
                case INCR:   sp1.comparison = INCR; break;
                case DECR:   sp1.comparison = DECR; break;
                default: throw new AssertionError("unknown assignment type");
            }
            sps.add(sp1);
        } else if (a instanceof ResetProcessAction) {
            ResetProcessAction rpa = (ResetProcessAction)a;
            Action end = assign(model.sv.getPC(rpa.getProcess()), -1);
            sps.addAll(allAssigns(model, end, writes));
            Action procs = decr(id(LTSminStateVector._NR_PR));
            sps.addAll(allAssigns(model, procs, writes));
            // TODO: results in incomplete identifiers
/*            for (Variable v : rpa.getProcess().getVariables()) {
                if (v.getName().equals(C_STATE_PROC_COUNTER)) continue;
                Expression e = v.getInitExpr();
                if (e == null)
                    e = constant(0); 
                Action init = assign(id(v), e);
                sps.addAll(allAssigns(model, init, writes));
            }*/
        } else if (a instanceof ExprAction) {
            Expression expr = ((ExprAction)a).getExpression();
            if (expr.getSideEffect() == null) return sps; // simple expressions are guards
            
            RunExpression re = (RunExpression)expr;
            Action procs = incr(id(LTSminStateVector._NR_PR));
            sps.addAll(allAssigns(model, procs, writes));

            for (Proctype p : re.getInstances()) {
                for (ProcInstance instance : re.getInstances()) { // sets a pc to 0
                    Action step = assign(model.sv.getPC(instance), 0);
                    sps.addAll(allAssigns(model, step, writes));
                }
                //write to the arguments of the target process
                Iterator<Expression> rei = re.getExpressions().iterator();
                for (Variable v : p.getArguments()) {
                    Expression param = rei.next();
                    if (v.getType() instanceof ChannelType || v.isStatic())
                        continue; //passed by reference or already in state vector
                    Action arg = assign(v, param);
                    sps.addAll(allAssigns(model, arg, writes));
                }
            }
            for (Action rea : re.getInitActions()) {
                sps.addAll(allAssigns(model, rea, writes));
            }
        } else if(a instanceof ChannelSendAction) {
            ChannelSendAction csa = (ChannelSendAction)a;
            Identifier id = csa.getIdentifier();
            SimplePredicate send = new SimplePredicate();
            send.comparison = CH_SEND_SORTED;
            try {
                send.id = getConstantId(model, chanLength(id), writes);
            } catch (ParseException e1) {
                depCheck(model, a, writes); // may rethrow
                return sps; // empty
            }
            sps.add(send);
        } else if(a instanceof OptionAction) { // options in a d_step sequence
            depCheck(model, a, writes); // may rethrow
        } else if(a instanceof ChannelReadAction) {
            ChannelReadAction cra = (ChannelReadAction)a;
            Identifier id = cra.getIdentifier();
            if (!cra.isPoll()) {
                SimplePredicate read = new SimplePredicate();
                read.comparison = CH_READ;
                try {
                    read.id = getConstantId(model, chanLength(id), writes);
                } catch (ParseException e1) {
                    depCheck(model, a, writes); // may rethrow
                    return sps; // empty
                }
                sps.add(read);
            } else {
                depCheck(model, a, writes); // may throw
            }
        } else {
            throw new ParseException();
        }
        return sps;
    }

    private static void depCheck(LTSminModel model, Expression e,
                                 List<Integer> writes) throws ParseException {
        DepMatrix deps = new DepMatrix(1, model.sv.size());
        LTSminDMWalker.walkOneGuard(model, deps, new LTSminGuard(e), 0);
        if (deps.isRead(0, writes) || deps.isWrite(0, writes)) {
            throw new ParseException();
        }
    }

    private static void depCheck(LTSminModel model, Action a,
                                 List<Integer> writes) throws ParseException {
        DepMatrix deps = new DepMatrix(1, model.sv.size());
        LTSminDMWalker.walkOneAction(model, deps, a, 0);
        if (deps.isRead(0, writes) || deps.isWrite(0, writes)) {
            throw new ParseException();
        }
    }

    private static boolean maybeCoenabled(GuardInfo guardInfo, int t1, int t2) {
        DepMatrix coen = guardInfo.getCoMatrix();
        for (int g1 : guardInfo.getTransMatrix().get(t1)) {
            for (int g2 : guardInfo.getTransMatrix().get(t2)) {  
                if (!coen.isRead(g1, g2)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean canDisable(GuardInfo guardInfo, int t1, int t2) {
        DepMatrix nds = guardInfo.getNDSMatrix();
        for (int g2 : guardInfo.getTransMatrix().get(t2)) {  
            if (nds.isRead(g2, t1)) {
                return true;
            }
        }
        return false;
    }

    private static boolean canEnable(GuardInfo guardInfo, int t1, int t2) {
        DepMatrix nes = guardInfo.getNESMatrix();
        for (int g2 : guardInfo.getTransMatrix().get(t2)) {  
            if (nes.isRead(g2, t1)) {
                return true;
            }
        }
        return false;
    }
    
	/**************
	 * MCE
	 * ************/

	private static int generateCoenMatrix(LTSminModel model, GuardInfo guardInfo) {
	    int nlabels = guardInfo.getNumberOfLabels();
		DepMatrix co = new DepMatrix(nlabels, nlabels);
		guardInfo.setCoMatrix(co);
		int neverCoEnabled = 0;
		for (int g1 = 0; g1 < nlabels; g1++) {
            co.incRead(g1, g1);
            Expression ge1 = guardInfo.get(g1).getExpr();
            for (int g2 = g1+1; g2 < nlabels; g2++) {
                Expression ge2 = guardInfo.get(g2).getExpr();
                Boolean coenabled = MCE(model, ge1, ge2, false, false, null, null);
                if (coenabled == null || coenabled) {
                    co.incRead(g1, g2);
                    co.incRead(g2, g1);
                } else {
                    neverCoEnabled++;
                }
			}
		}
		return neverCoEnabled;
	}

    private static int generateICoenMatrix(LTSminModel model, GuardInfo guardInfo) {
        int nlabels = guardInfo.getNumberOfLabels();
        int neverICoEnabled = 0;
        DepMatrix ico = new DepMatrix(nlabels, nlabels);
        guardInfo.setICoMatrix(ico);
        for (int g1 = 0; g1 < nlabels; g1++) {
            Expression ge1 = guardInfo.get(g1).getExpr();
            for (int g2 = 0; g2 < nlabels; g2++) {
                Expression ge2 = guardInfo.get(g2).getExpr();
                
                Boolean icoenabled = MCE(model, ge1, ge2, true, false, null, null);
                if (icoenabled == null || icoenabled) {
                    ico.incRead(g1, g2);
                } else {
                    neverICoEnabled = neverICoEnabled + 1;
                }
            }
        }

        return neverICoEnabled;
	}

    /**
     * Over estimates whether a transition can enable a guard 
     * @param limit1 deprow of writes to variables, to which the mce check is limited 
     * @return false of trans definitely does not enable the guard, else true
     */
    private static Boolean MCE (LTSminModel model,
                                Expression e1,
                                Expression e2,
                                boolean invert1,
                                boolean invert2,
                                DepRow limit1, DepRow limit2) {
        if (e1 instanceof EvalExpression) {
            EvalExpression eval = (EvalExpression)e1;
            return MCE (model, eval.getExpression(), e2, invert1, invert2, limit1, limit2);
        } else if (e1 instanceof BooleanExpression) {
            BooleanExpression ce1 = (BooleanExpression)e1;
            switch (ce1.getToken().kind) {
            case PromelaTokenManager.BNOT:
            case PromelaTokenManager.LNOT:
                return MCE (model, ce1.getExpr1(), e2, !invert1, invert2, limit1, limit2);
            case PromelaTokenManager.BAND:
            case PromelaTokenManager.LAND:
                if (invert1) {
                    Boolean left = MCE(model, ce1.getExpr1(), e2, invert1, invert2, limit1, limit2);
                    Boolean right = MCE(model, ce1.getExpr2(), e2, invert1, invert2, limit1, limit2);
                    return OR3(left, right);
                    
                } else {
                    Boolean left = MCE(model, ce1.getExpr1(), e2, invert1, invert2, limit1, limit2);
                    Boolean right = MCE(model, ce1.getExpr2(), e2, invert1, invert2, limit1, limit2);
                    return AND3(left, right);
                }
            case PromelaTokenManager.BOR:
            case PromelaTokenManager.LOR:
                if (invert1) {
                    Boolean left = MCE(model, ce1.getExpr1(), e2, invert1, invert2, limit1, limit2);
                    Boolean right = MCE(model, ce1.getExpr2(), e2, invert1, invert2, limit1, limit2);
                    return AND3(left, right);
                } else {
                    Boolean left = MCE(model, ce1.getExpr1(), e2, invert1, invert2, limit1, limit2);
                    Boolean right = MCE(model, ce1.getExpr2(), e2, invert1, invert2, limit1, limit2);
                    return OR3(left, right);
                }
            default: throw new RuntimeException("Unknown boolean expression: "+ e1);
            }
        } else if (e2 instanceof BooleanExpression ||
                   e2 instanceof EvalExpression) {
            return MCE(model, e2, e1, invert2, invert1, limit2, limit1);
        } else {
            if (limit1 != null | limit2 != null) {
                DepRow limit = limit1 != null ? limit1 : limit2;
                Expression e = limit1 != null ? e1 : e2;
                DepMatrix testSet = new DepMatrix(1, model.sv.size());
                LTSminDMWalker.walkOneGuard(model, testSet, new LTSminGuard(e), 0);
                if (!testSet.isRead(0, limit.getWrites())) return null;
            }
            List<SimplePredicate> ga_sp = new ArrayList<SimplePredicate>();
            boolean missed = extract_conjunct_predicates(model, ga_sp, e1, invert1);
            if (invert1) {
                if (missed)
                    return true; // don't know
                for(SimplePredicate a : ga_sp) {                   
                    a = a.invert();
                    if (mce(model, e2, a, invert2)) {
                        return true;
                    }
                }
                return false;
            } else {
                for(SimplePredicate a : ga_sp) {
                    if (!mce(model, e2, a, invert2)) {
                        return false;
                    }
                }
                return true;
            }
        }
    }

    private static Boolean AND3(Boolean left, Boolean right) {
        if (left == null) return right;
        if (right == null) return left;
        return left && right;
    }

    private static Boolean OR3(Boolean left, Boolean right) {
        if (left == null) return right;
        if (right == null) return left;
        return left || right;
    }

    private static boolean mce(LTSminModel model, Expression e2,
                               SimplePredicate a, boolean invert2) {
        List<SimplePredicate> gb_sp = new ArrayList<SimplePredicate>();
        boolean missed = extract_conjunct_predicates(model, gb_sp, e2, invert2);
        if (invert2) {
            if (missed)
                return true; // don't know
            for(SimplePredicate b : gb_sp) {
                b = b.invert();
                if (!is_conflict_predicate(model, a, b)) {
                    return true;
                }
            }
            return false;
        } else {
            for(SimplePredicate b : gb_sp) {
                if (is_conflict_predicate(model, a, b)) {
                    return false;
                }
            }
            return true;
        }
    }

    /***************************** HELPER FUNCTIONS ***************************/

    static class SimplePredicate {
        public SimplePredicate() {}
        public SimplePredicate(Expression e, Identifier id, int c) {
            this.comparison = e.getToken().kind;
            this.id = id;
            this.constant = c;
            this.e = e;
        }

        public Expression e;
        public int comparison;
        public Identifier id;
        public String ref = null;
        public int constant;

        public String getRef(LTSminModel model) {
            if (null!= ref)
                return ref;
            LTSminPointer svp = new LTSminPointer(model.sv, "");
            ExprPrinter p = new ExprPrinter(svp);
            ref = p.print(id);
            assert (!ref.equals(LTSminPrinter.SCRATCH_VARIABLE)); // write-only
            return ref;
        }
        public String toString() {
            String comp = tokenImage[comparison].replace('"', ' ');
            return id + comp + constant;
        }

        public SimplePredicate invert() {
            SimplePredicate copy = new SimplePredicate(e, id, constant);
            switch(comparison) {
                case LT:
                    copy.comparison = GTE;
                    break;
                case LTE:
                    copy.comparison = GT;
                    break;
                case EQ:
                    copy.comparison = NEQ;
                    break;
                case NEQ:
                    copy.comparison = EQ;
                    break;
                case GT:
                    copy.comparison = LTE;
                    break;
                case GTE:
                    copy.comparison = LT;
                    break;
            }
            return copy;
        }
    }

    /**
     * Returns whether an action CONFLICTS with a simple predicate, e.g.:
     * x := 5 && x == 4
     * 
     * This is an under-estimation! Therefore, a negative result is useless. For
     * For testing on the negation, use the invert flag.
     * 
     * @param model
     * @param a the action
     * @param sp the simple predicate (x == 4)
     * @param invert if true: the action is inverted: x := 5 --> x := !5
     * @return true if conflict is found, FALSE IF UNKNOWN
     */
    private static boolean conflicts (LTSminModel model, Action a,
                                      SimplePredicate sp, LTSminTransition t, int g, boolean invert) {
        SimplePredicate sp1 = new SimplePredicate();
        if (a instanceof AssignAction) {
            AssignAction ae = (AssignAction)a;
            try {
                sp1.id = getConstantId(model, ae.getIdentifier(), null);
            } catch (ParseException e1) {
                return false;
            }
            switch (ae.getToken().kind) {
                case ASSIGN:
                    try {
                        sp1.constant = ae.getExpr().getConstantValue();
                    } catch (ParseException e) {
                        return false;
                    }
                    sp1.comparison = invert ? NEQ : EQ;
                    if (is_conflict_predicate(model, sp1, sp))
                        return true;
                    break;
                case INCR:
                    if (sp1.getRef(model).equals(sp.getRef(model)))
                        if (invert ? gt(sp) : lt(sp))
                            return true;
                    break;
                case DECR:
                    if (sp1.getRef(model).equals(sp.getRef(model)))
                        if (invert ? lt(sp) : gt(sp))
                            return true;
                    break;
                default:
                    throw new AssertionError("unknown assignment type");
            }
        } else if (a instanceof ResetProcessAction) {
            ResetProcessAction rpa = (ResetProcessAction)a;
            Variable pc = model.sv.getPC(rpa.getProcess());
            if (conflicts(model, assign(pc, -1), sp, t, g, invert))
                return true;
            if (conflicts(model, decr(id(LTSminStateVector._NR_PR)), sp, t, g, invert))
                return true;
            //return false;
            Expression e = sp.id.getVariable().getInitExpr();
            if (e == null)
                e = constant(0); 
            Action init = assign(sp.id, e);
            return conflicts(model, init, sp, t, g, invert);
        } else if (a instanceof ExprAction) {
            Expression expr = ((ExprAction)a).getExpression();
            if (expr.getSideEffect() == null) return false; // simple expressions are guards
            RunExpression re = (RunExpression)expr;
            
            if (conflicts(model, incr(id(LTSminStateVector._NR_PR)), sp, t, g, invert))
                return true;

            for (Proctype p : re.getInstances()) {
                for (ProcInstance instance : re.getInstances()) { // sets a pc to 0
                    Variable pc = model.sv.getPC(instance);
                    if (conflicts(model, assign(pc, 0), sp, t, g, invert)) {
                        return true;
                    }
                }
                //write to the arguments of the target process
                Iterator<Expression> rei = re.getExpressions().iterator();
                for (Variable v : p.getArguments()) {
                    Expression param = rei.next();
                    if (v.getType() instanceof ChannelType || v.isStatic())
                        continue; //passed by reference or already in state vector
                    try {
                        int val = param.getConstantValue();
                        if (conflicts(model, assign(v, val), sp, t, g, invert)) {
                            return true;
                        }
                    } catch (ParseException e) {}
                }
            }
            for (Action rea : re.getInitActions()) {
                if (conflicts(model, rea,  sp, t, g, invert)) {
                    return true;
                }
            }
        } else if(a instanceof ChannelSendAction) {
            ChannelSendAction csa = (ChannelSendAction)a;
            Identifier id = csa.getIdentifier();
            for (int i = 0; i < csa.getExprs().size(); i++) {
                try {
                    int val = csa.getExprs().get(i).getConstantValue();
                    Identifier next = channelNext(id, i);
                    if (conflicts(model, assign(next, constant(val)), sp, t, g, invert)) {
                        return true;
                    }
                } catch (ParseException e) {}
            }
            return conflicts(model, incr(chanLength(id)), sp, t, g, invert);
        } else if(a instanceof OptionAction) { // options in a d_step sequence
            //OptionAction oa = (OptionAction)a;
            //for (Sequence seq : oa) { //TODO
                //Action act = seq.iterator().next(); // guaranteed by parser
                //if (act instanceof ElseAction)
            //}
        } else if(a instanceof ChannelReadAction) {
            ChannelReadAction cra = (ChannelReadAction)a;
            Identifier id = cra.getIdentifier();
            if (!cra.isPoll()) {
                return conflicts(model, decr(chanLength(id)), sp, t, g, invert);
            }
        }
        return false;
    }

    private static boolean lt(SimplePredicate sp) {
        return sp.comparison == LT || 
            sp.comparison == LTE;
    }

    private static boolean gt(SimplePredicate sp) {
        return sp.comparison == GT || 
            sp.comparison == GTE;
    }

	/**
	 * Collects all simple predicates in an expression e.
	 * SimplePred ::= cvarref <comparison> constant | constant <comparison> cvarref
	 * where cvarref is a reference to a singular (channel) variable or a
	 * constant index in array variable.
	 * @param invert Influences outcome of missed
	 */
	private static boolean extract_conjunct_predicates(LTSminModel model,
	                                                   List<SimplePredicate> sp,
	                                                   Expression e, boolean invert) {
		int c;
        boolean missed = false;
    	if (e instanceof CompareExpression) {
    		CompareExpression ce1 = (CompareExpression)e;
    		Identifier id;
    		try {
    			id = getConstantId(model, ce1.getExpr1(), null); // non-strict, since MCE/NES/NDS holds for the same state
    			c = ce1.getExpr2().getConstantValue();
        		sp.add(new SimplePredicate(e, id, c));
    		} catch (ParseException pe) {
        		try {
        			id = getConstantId(model, ce1.getExpr2(), null);
        			c = ce1.getExpr1().getConstantValue();
            		sp.add(new SimplePredicate(e, id, c));
        		} catch (ParseException pe2) {
        		    missed = true; // missed one!
        		}
    		}
		} else if (e instanceof ChannelReadExpression) {
			ChannelReadExpression cre = (ChannelReadExpression)e;
			Identifier id = cre.getIdentifier();
			missed |= extract_conjunct_predicates(model, sp, chanContentsGuard(id), invert);
			List<Expression> exprs = cre.getExprs();
			for (int i = 0; i < exprs.size(); i++) {
				try { // this is a conjunction of matchings
					int val = exprs.get(i).getConstantValue();
					Identifier read = channelBottom(id, i);
					CompareExpression compare = compare(EQ,
														read, constant(val));
					missed |= extract_conjunct_predicates(model, sp, compare, invert);
		    	} catch (ParseException pe2) {
		    	    missed = true; // missed one!
		    	}
			}
    	} else if (e instanceof ChannelOperation) {
			ChannelOperation co = (ChannelOperation)e;
			String name = co.getToken().image;
			Identifier id = (Identifier)co.getExpression();
			if (((ChannelType)id.getVariable().getType()).isRendezVous())
				return false; // Spin returns true in this case (see garp model)
			VariableType type = id.getVariable().getType();
			int buffer = ((ChannelType)type).getBufferSize();
			Expression left = chanLength(id);
			Expression right = null;
			int op = -1;
			if (name.equals("empty")) {
				op = EQ;
				right = constant (0);
			} else if (name.equals("nempty")) {
				op = NEQ;
				right = constant (0);
			} else if (name.equals("full")) {
				op = EQ;
				right = constant (buffer);
			} else if (name.equals("nfull")) {
				op = NEQ;
				right = constant (buffer);
			} else {
			    throw new AssertionError();
			}
			missed |= extract_conjunct_predicates(model, sp, compare(op, left, right), invert);
		} else if (e instanceof RemoteRef) {
			RemoteRef rr = (RemoteRef)e;
			Variable pc = rr.getPC(null);
			int num = rr.getLabelId();
			Expression comp = compare(EQ, id(pc), constant(num));
			missed |= extract_conjunct_predicates(model, sp, comp, invert);
		} else if (e instanceof ConstantExpression) {
		    try {
                int val = e.getConstantValue();
                missed |= invert ? val != 0 : val == 0;
            } catch (ParseException e1) {
                throw new RuntimeException("Dynamic constants?");
            }
        } else if (e instanceof AritmicExpression ||
                   e instanceof Identifier) {
            try {
                int val = e.getConstantValue();
                missed |= invert ? val != 0 : val == 0;
            } catch (ParseException e1) {
                missed = true;
            }
        } else if (e instanceof EvalExpression) {
            EvalExpression eval = (EvalExpression) e;
            missed |= extract_conjunct_predicates(model, sp, eval.getExpression(), invert);
		} else if (e instanceof BooleanExpression) {
    	   throw new RuntimeException("Was expecting leaf");
		} else {
		    return true; // missed one!
		}
    	return missed;
	}

    /**
     * Tries to parse an expression as a reference to a singular (channel)
     * variable or a constant index in array variable (a cvarref).
     */
    private static Identifier getConstantId(LTSminModel model,
                                            Expression e,
                                            List<Integer> writes)
                                                    throws ParseException {
        if (e instanceof LTSminIdentifier) {
        } else if (e instanceof Identifier) {
            Identifier id = (Identifier)e;
            Variable var = id.getVariable();
            if (var.isHidden())
                    throw new ParseException();
            Expression ar = id.getArrayExpr();
            if ((null == ar) != (-1 == var.getArraySize()))
                throw new AssertionError("Invalid array semantics in expression: "+ id);
            if (null != ar) { 
                try {
                    ar = constant(ar.getConstantValue());
                } catch (ParseException pe) {
                    if (writes != null)
                        depCheck(model, ar, writes); // may rethrow
                } // non-strict: do nothing. See getRef().
            }
            Identifier sub = null;
            if (null != id.getSub())
                sub = getConstantId(model, id.getSub(), writes);
            return id(var, ar, sub);
        } else if (e instanceof ChannelLengthExpression)  {
            ChannelLengthExpression cle = (ChannelLengthExpression)e;
            Identifier id = (Identifier)cle.getExpression();
            return getConstantId(model, chanLength(id), writes);
        }
        throw new ParseException();
    }

	private static boolean is_conflict_predicate(LTSminModel model, SimplePredicate p1, SimplePredicate p2) {
	    // assume no conflict
	    boolean no_conflict = true;
	    // conflict only possible on same variable
	    String ref1, ref2;
	    try {
		    ref1 = p1.getRef(model); // convert to c code string
			ref2 = p2.getRef(model);
	    } catch (AssertionError ae) {
	    	throw new AssertionError("Serialization of expression "+ p1.id +" or "+ p2.id +" failed: "+ ae);
	    }
		if (ref1.equals(ref2)) { // syntactic matching, this suffices if we assume expression is evaluated on the same state vector
	        switch(p1.comparison) {
	            case LT:
	                // no conflict if one of these cases
	                no_conflict =
	                (p2.constant < p1.constant - 1) ||
	                (p2.constant == p1.constant - 1 && p2.comparison != GT) ||
	                (lt(p2) || p2.comparison == NEQ);
	                break;
	            case LTE:
	                // no conflict if one of these cases
	                no_conflict =
	                (p2.constant < p1.constant) ||
	                (p2.constant == p1.constant && p2.comparison != GT) ||
	                (lt(p2) || p2.comparison == NEQ);
	                break;
	            case EQ:
	                // no conflict if one of these cases
	                no_conflict =
	                (p2.constant == p1.constant && (p2.comparison == EQ || p2.comparison == LTE || p2.comparison == GTE)) ||
	                (p2.constant != p1.constant && p2.comparison == NEQ) ||
	                (p2.constant < p1.constant && p2.comparison == GT || p2.comparison == GTE) ||
	                (p2.constant > p1.constant && lt(p2));
	                break;
	            case NEQ:
	                // no conflict if one of these cases
	                no_conflict =
	                (p2.constant != p1.constant) ||
	                (p2.constant == p1.constant && p2.comparison != EQ);
	                break;
	            case GT:
	                // no conflict if one of these cases
	                no_conflict =
	                (p2.constant > p1.constant + 1) ||
	                (p2.constant == p1.constant + 1 && p2.comparison != LT) ||
	                (gt(p2) || p2.comparison == NEQ);
	                break;
	            case GTE:
	                // no conflict if one of these cases
	                no_conflict =
	                (p2.constant > p1.constant) ||
	                (p2.constant == p1.constant && p2.comparison != LT) ||
	                (gt(p2) || p2.comparison == NEQ);
	                break;
	        }
	    }
	    return !no_conflict;
	}

	static void generateTransitionGuardLabels(Params params) {
		for(LTSminTransition t : params.model.getTransitions()) {
			walkTransition(params, t);
		}
	}

	static void walkTransition(Params params, LTSminTransition t) {
		for (LTSminGuardBase g : t.getGuards()) {
			walkGuard(params, t, g);
		} // we do not have to handle atomic actions since the first guard only matters
	}

	/* Split guards */
	static void walkGuard(Params params, LTSminTransition t, LTSminGuardBase guard) {
		if (guard instanceof LTSminLocalGuard) { // Nothing
		} else if (guard instanceof LTSminGuard) {
			LTSminGuard g = (LTSminGuard)guard;
			if (g.getExpression() == null)
			    return;
            params.guardMatrix.addGuard(t.getGroup(), g);
		} else if (guard instanceof LTSminGuardAnd) {
			for(LTSminGuardBase gb : (LTSminGuardContainer)guard)
				walkGuard(params, t, gb);
        } else if (guard instanceof LTSminGuardNand) {
            LTSminGuardNand g = (LTSminGuardNand)guard;
            Expression e = g.getExpression();
            if (e == null) return;
            params.guardMatrix.addGuard(t.getGroup(), e);
		} else if (guard instanceof LTSminGuardNor) { // DeMorgan
			for (LTSminGuardBase gb : (LTSminGuardContainer)guard) {
			    Expression expr = gb.getExpression();
			    if (expr == null) continue;
                params.guardMatrix.addGuard(t.getGroup(), not(expr));
			}
		} else if (guard instanceof LTSminGuardOr) {
		    LTSminGuardOr g = (LTSminGuardOr)guard;
			Expression e = g.getExpression();
            if (e == null) return;
			params.guardMatrix.addGuard(t.getGroup(), e);
		} else {
			throw new AssertionError("UNSUPPORTED: " + guard.getClass().getSimpleName());
		}
	}
}

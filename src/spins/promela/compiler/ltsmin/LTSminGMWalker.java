package spins.promela.compiler.ltsmin;

import static spins.promela.compiler.Specification._NR_PR;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.assign;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.chanLength;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.decr;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.id;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.incr;
import static spins.promela.compiler.ltsmin.util.SimplePredicate.depCheck;
import static spins.promela.compiler.ltsmin.util.SimplePredicate.extract_conjunct_predicates;
import static spins.promela.compiler.parser.PromelaConstants.ASSIGN;
import static spins.promela.compiler.parser.PromelaConstants.CH_READ;
import static spins.promela.compiler.parser.PromelaConstants.DECR;
import static spins.promela.compiler.parser.PromelaConstants.EQ;
import static spins.promela.compiler.parser.PromelaConstants.INCR;
import static spins.promela.compiler.parser.PromelaConstants.LNOT;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import spins.promela.compiler.ProcInstance;
import spins.promela.compiler.Proctype;
import spins.promela.compiler.actions.Action;
import spins.promela.compiler.actions.AssignAction;
import spins.promela.compiler.actions.ChannelReadAction;
import spins.promela.compiler.actions.ChannelSendAction;
import spins.promela.compiler.actions.ExprAction;
import spins.promela.compiler.actions.OptionAction;
import spins.promela.compiler.expression.BooleanExpression;
import spins.promela.compiler.expression.EvalExpression;
import spins.promela.compiler.expression.Expression;
import spins.promela.compiler.expression.Identifier;
import spins.promela.compiler.expression.RunExpression;
import spins.promela.compiler.expression.TimeoutExpression;
import spins.promela.compiler.ltsmin.LTSminTreeWalker.Options;
import spins.promela.compiler.ltsmin.matrix.DepMatrix;
import spins.promela.compiler.ltsmin.matrix.DepMatrix.DepRow;
import spins.promela.compiler.ltsmin.matrix.LTSminGuard;
import spins.promela.compiler.ltsmin.matrix.ModelMatrixGenerator;
import spins.promela.compiler.ltsmin.matrix.RWMatrix;
import spins.promela.compiler.ltsmin.matrix.RWMatrix.RWDepRow;
import spins.promela.compiler.ltsmin.model.GuardInfo;
import spins.promela.compiler.ltsmin.model.LTSminModel;
import spins.promela.compiler.ltsmin.model.LTSminTransition;
import spins.promela.compiler.ltsmin.model.ResetProcessAction;
import spins.promela.compiler.ltsmin.util.LTSminDebug;
import spins.promela.compiler.ltsmin.util.LTSminProgress;
import spins.promela.compiler.ltsmin.util.SimplePredicate;
import spins.promela.compiler.parser.ParseException;
import spins.promela.compiler.parser.PromelaTokenManager;
import spins.promela.compiler.variable.ChannelType;
import spins.promela.compiler.variable.Variable;

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

	static LTSminProgress report;
	
	/**
	 * Adds all guards labels and generates the guards matrices for POR
	 * @param model
	 * @param debug
	 */
	static void generateGuardInfo(LTSminModel model, Options opts,
	                              LTSminDebug debug) {
		if(model.getGuardInfo()==null)
			model.setGuardInfo(new GuardInfo(model));
		GuardInfo guardInfo = model.getGuardInfo();
        report = new LTSminProgress(debug);

        if (opts.no_gm) {
            debug.say_indent--;
            debug.say("Generating guard dependency matrices done (%s sec)",
                      report.stopTimer().sec()).say("");
            return;
        }

        int nLabels = guardInfo.getNumberOfLabels();
        int nTrans = model.getTransitions().size();

        debug.say("Generating guard dependency matrices (%d guards) ...", nLabels);
        report.resetTimer().startTimer();
        debug.say_indent++;

        generateDepMatrices (model, guardInfo);

        // generate Maybe Coenabled matrix
        report.setTotal(nLabels*nLabels/2);
        int nmce = generateCoenMatrix (model, guardInfo);
        report.overwriteTotals(nmce, "!MCE guards");

        report.setTotal(nTrans*nTrans/2);
        int mct = generateMCtrans (model, guardInfo);
        report.overwriteTotals(mct, "!MCE transitions");

        // generate Maybe Coenabled matrix
        report.setTotal(nLabels*nLabels);
        int nice = generateICoenMatrix (model, guardInfo);
        report.overwriteTotals(nice, "!ICE guards");

        // generate NES matrix
        report.setTotal(nTrans*nLabels);
        int nnes = generateNESMatrix (model, guardInfo);
        report.overwriteTotals(nnes, "!NES guards");

        report.setTotal(nTrans*nTrans);
        int nnet = generateNEStrans (model, guardInfo);
        report.overwriteTotals(nnet, "!NES transitions");

        // generate NDS matrix
        report.setTotal(nTrans*nLabels);
        int nnds = generateNDSMatrix (model, guardInfo);
        report.overwriteTotals(nnds, "!NDS guards");

        // generate MDS matrix
        report.setTotal(nTrans*nLabels);
        int disables = generateMDSMatrix (model, guardInfo);
        report.overwriteTotals(disables, "MDS guards");

        // generate MES matrix
        report.setTotal(nTrans*nLabels);
        int enables = generateMESMatrix (model, guardInfo);
        report.overwriteTotals(enables, "MES guards");

        // generate NDS transitions
        ModelMatrixGenerator.debug = debug;
        report.setTotal(nTrans*nTrans);
        int nndt = generateNDStrans (model, guardInfo);
        report.overwriteTotals(nndt, "!NDS transitions");
        
        // generate Do Not Accord Matrix
        report.setTotal(nTrans*nTrans/2);
        int ndna = generateDoNoAccord (model, guardInfo);
        report.overwriteTotals(ndna, "!DNA transitions");

        // generate Commutes Matrix
        report.setTotal(nTrans*nTrans/2);
        int commuting = generateCommutes (model, guardInfo);
        report.overwriteTotals(commuting, "Commuting actions");
        
		debug.say_indent--;
		debug.say("Generating guard dependency matrices done (%s sec)",
		          report.stopTimer().sec()).say("");
	}

	static final String MCT = "MCT"; 
    private static int generateMCtrans(LTSminModel model, GuardInfo guardInfo) {
        DepMatrix coen = guardInfo.getCoMatrix();
        int nTrans = model.getTransitions().size();
        DepMatrix mct = new DepMatrix(nTrans, nTrans);
        guardInfo.setMatrix(MCT, mct);
        int ce = 0;
        for (int t1 = 0; t1 < nTrans; t1++) {
            mct.setDependent(t1, t1);
            for (int t2 = t1+1; t2 < nTrans; t2++) {
guard_loop:     for (int g1 : guardInfo.getTransMatrix().get(t1)) { 
                    for (int g2 : guardInfo.getTransMatrix().get(t2)) {  
                        if (coen.isDependent(g1, g2)) {
                            mct.setDependent(t1, t2);
                            mct.setDependent(t2, t1);
                            ce++;
                            break guard_loop;
                        }
                    }
                }
                report.updateProgress();
            }
        }
        return report.getTotal() - ce;
    }

    static final String NDT = "NDT"; 
    private static int generateNDStrans(LTSminModel model, GuardInfo guardInfo) {
        DepMatrix nds = guardInfo.getNDSMatrix();
        int nTrans = model.getTransitions().size();
        DepMatrix ndt = new DepMatrix(nTrans, nTrans);
        guardInfo.setMatrix(NDT, ndt);
        int ndts = 0;
        for (int t1 = 0; t1 < nTrans; t1++) {
            for (int t2 = 0; t2 < nTrans; t2++) {
guard_loop:     for (int g2 : guardInfo.getTransMatrix().get(t2)) { 
                    if (nds.isDependent(g2, t1)) {
                        ndt.setDependent(t1, t2);
                        ndts++;
                        break guard_loop;
                    }
                }
                report.updateProgress();
            }
        }
        return report.getTotal() - ndts;
    }

    static final String NET = "NET"; 
    private static int generateNEStrans(LTSminModel model, GuardInfo guardInfo) {
        DepMatrix nes = guardInfo.getNESMatrix();
        int nTrans = model.getTransitions().size();
        DepMatrix net = new DepMatrix(nTrans, nTrans);
        guardInfo.setMatrix(NET, net);
        int nets = 0;
        for (int t1 = 0; t1 < nTrans; t1++) {
            for (int t2 = 0; t2 < nTrans; t2++) {
guard_loop:     for (int g2 : guardInfo.getTransMatrix().get(t2)) { 
                    if (nes.isDependent(g2, t1)) {
                        net.setDependent(t1, t2);
                        nets++;
                        break guard_loop;
                    }
                }
                report.updateProgress();
            }
        }
        return report.getTotal() - nets;
    }

    static final String T2G = "T2G";
    static final String G2G = "G2G"; // guard reads from guard
    static final String T2T = "T2T"; // transitions excluding guards, including atomic (write dep)
    private static void generateDepMatrices(LTSminModel model,
                                            GuardInfo guardInfo) {
        int nTrans = model.getTransitions().size();
        int nLabels = model.getGuardInfo().getNumberOfLabels();
        DepMatrix g2s = guardInfo.getDepMatrix();

        DepMatrix g2g = new DepMatrix(nLabels, nLabels);
        guardInfo.setMatrix(G2G, g2g);
        report.setTotal(nLabels * nLabels / 2);
        int num = 0;
        for (int g1 = 0; g1 < nLabels; g1++) {
            g2g.setDependent(g1, g1);
            for (int g2 = g1 + 1; g2 < nLabels; g2++) {
                if (g2s.rowsDepenendent(g1, g2)) {
                    g2g.setDependent(g1, g2);
                    g2g.setDependent(g2, g1);
                    num++;
                }
                report.updateProgress();
            }
        }
        report.overwriteTotals(num, "Guard/guard dependencies");

        RWMatrix deps = model.getDepMatrix();
        DepMatrix t2g = new DepMatrix(nTrans, nLabels);
        guardInfo.setMatrix(T2G, t2g);
        report.setTotal(nTrans * nLabels);
        num = 0;
        for (int t = 0; t < nTrans; t++) {
            for (int g = 0; g < nLabels; g++) {
                if (deps.getRow(t).mayWrites(g2s.getRow(g))) {
                    t2g.setDependent(t, g);
                    num++;
                }
                report.updateProgress();
            }
        }
        report.overwriteTotals(num, "Transition/guard writes");

        DepMatrix t2t = new DepMatrix(nTrans, nTrans);
        guardInfo.setMatrix(T2T, t2t);
        RWMatrix trans = model.getAtomicDepMatrix();
        num = 0;
        report.setTotal(nTrans * nTrans);
        for (int t1 = 0; t1 < nTrans; t1++) {
            RWDepRow t1Row = trans.getRow(t1);
            for (int t2 = 0; t2 < nTrans; t2++) {
                if (t1Row.mayWrites(trans.getRow(t2))) {
                    t2t.setDependent(t1, t2);
                    num++;
                }
            }
        }
        report.overwriteTotals(num, "Transition/transition writes");
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
        DepMatrix t2g = model.getGuardInfo().getMatrix(T2G);
		for (int g = 0; g <  nds.getNrRows(); g++) {
            LTSminGuard guard = (LTSminGuard) guardInfo.get(g);

            if (guard.getExpr() instanceof TimeoutExpression) {
                for (LTSminTransition trans : model.getTransitions()) {
                    if (trans.isTimeout()) { // guard only disabled by timeout trans
                        nds.setDependent(g, trans.getGroup()); 
                    } else {
                        notNDS++;
                    }
                }
                continue;
            }
            for (LTSminTransition trans : model.getTransitions()) {
                report.updateProgress ();
                if (!t2g.isDependent(trans.getGroup(), g)) {
                    notNDS += 1;
                    continue;
                }

                boolean ce = true;
                for (int g1 : guardInfo.getTransMatrix().get(trans.getGroup())) {  
                    if (!coen.isDependent(g, g1)) ce = false;
                }
                
                if (ce && atomicNES(model, guardInfo, guard, trans, true)) {
                    nds.setDependent(g, trans.getGroup());
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
        DepMatrix t2g = model.getGuardInfo().getMatrix(T2G);
		for (int g = 0; g <  nes.getNrRows(); g++) {
            LTSminGuard guard = (LTSminGuard) guardInfo.get(g);
			for (LTSminTransition trans : model.getTransitions()) {
			    report.updateProgress ();
			    if (!t2g.isDependent(trans.getGroup(), g)) {
                    notNES += 1;
			        continue;
			    }

		        boolean ice = true;
		        for (int g1 : guardInfo.getTransMatrix().get(trans.getGroup())) {
		            if (!icoen.isDependent(g, g1)) ice = false;
		        }
			    
                if (ice && atomicNES(model, guardInfo, guard, trans, false)) {
                    nes.setDependent(g, trans.getGroup());
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
                                     LTSminGuard guard, LTSminTransition trans,
                                     boolean invert) {
        if (!limitMCE(model, guardInfo, trans.getGroup(), guard, !invert)) {
            return false;
        }

        if (enables(model, trans, guard.getExpr(), invert)) {
            return true;
        }

        for (LTSminTransition atomic : trans.getTransitions()) {
            if (enables(model, atomic, guard.getExpr(), invert)) {
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
                                    int t, LTSminGuard guard, boolean invert) {
        RWMatrix rw = model.getDepMatrix();
        DepMatrix g2s = model.getGuardInfo().getDepMatrix();
        for (int gg : guardInfo.getTransMatrix().get(t)) {
            LTSminGuard gguard = guardInfo.get(gg);
            Expression gge = gguard.getExpr();            
            
            RWDepRow row = rw.getRow(t);
            if (row.mayWrites(g2s.getRow(gg))) {              
                Boolean coenabled = MCE(model, guard.getExpr(), gge, invert, false, row.mayWrite, null);
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
									Expression e, boolean invert) {
        if (e instanceof EvalExpression) {
            EvalExpression eval = (EvalExpression)e;
            return enables(model, t, eval.getExpression(), invert);
        } else if (e instanceof BooleanExpression) {
            BooleanExpression ce = (BooleanExpression)e;
            if (ce.getToken().kind == PromelaTokenManager.BNOT ||
                ce.getToken().kind == PromelaTokenManager.LNOT) {
                return enables(model, t, ce.getExpr1(), !invert);
            } else {
                return enables(model, t, ce.getExpr1(), invert) ||
                       enables(model, t, ce.getExpr2(), invert);
            }
        } else {
            List<SimplePredicate> sps = new ArrayList<SimplePredicate>();
            boolean missed = extract_conjunct_predicates(model, sps, e, invert);
            if (missed) {
                RWMatrix deps = model.getDepMatrix();
                DepMatrix temp = new DepMatrix(1, model.sv.size());
                LTSminDMWalker.walkOneGuard(model, temp, e, 0);
                return deps.getRow(t.getGroup()).mayWrites(temp.getRow(0));
            }
            for (SimplePredicate sp : sps) {
                if (invert)
                    sp = sp.invert();

                if (agrees(model, t, sp)) {
                    return true;
                }
            }
            return false;
        }
	}

    private static boolean agrees (LTSminModel model,
                                   LTSminTransition t, SimplePredicate sp) {
        DepMatrix testSet = new DepMatrix(1, model.sv.size());
        RWMatrix a2s = model.getActionDepMatrix();

        for (Action a : t.getActions()) {
            testSet.clear();
            LTSminDMWalker.walkOneGuard(model, testSet, sp.e, 0);
            RWDepRow writeSet = a2s.getRow(a.getIndex());
            if (!writeSet.mayWrites(testSet.getRow(0)))
                continue;

            boolean conflicts = sp.conflicts(model, a, false);
            if (!conflicts) {
                return true;
        	}
        }
        return false;
    }
    
    /**
     * MUST DISABLE
     */
    static final String MDS = "dm_must_disable";
    private static int generateMDSMatrix(LTSminModel model, GuardInfo guardInfo) {
        int nlabels = guardInfo.getNumberOfLabels();
        DepMatrix mds = new DepMatrix(nlabels, model.getTransitions().size());
        guardInfo.setMatrix(MDS, mds, true);
        int disables = 0;
        for (int g = 0; g <  mds.getNrRows(); g++) {
            LTSminGuard guard = (LTSminGuard) guardInfo.get(g);
            for (LTSminTransition trans : model.getTransitions()) {
                report.updateProgress ();
                if (disables(model, trans, guard.getExpr(), g, false)) {
                    mds.setDependent(g, trans.getGroup());
                    disables += 1;
                }
            }
        }
        return disables;
    }

    /**
     * MUST ENABLE
     */
    static final String MES = "dm_must_enable";
    private static int generateMESMatrix(LTSminModel model, GuardInfo guardInfo) {
        int nlabels = guardInfo.getNumberOfLabels();
        DepMatrix mes = new DepMatrix(nlabels, model.getTransitions().size());
        guardInfo.setMatrix(MES, mes, true);
        int disables = 0;
        for (int g = 0; g <  mes.getNrRows(); g++) {
            LTSminGuard guard = (LTSminGuard) guardInfo.get(g);
            for (LTSminTransition trans : model.getTransitions()) {
                report.updateProgress ();
                if (disables(model, trans, guard.getExpr(), g, true)) {
                    mes.setDependent(g, trans.getGroup());
                    disables += 1;
                }
            }
        }
        return disables;
    }

    /**
     * Underestimates disabling
     * @return true of trans definitely disables the guard, else false
     */
    private static boolean disables (LTSminModel model,
                                     LTSminTransition t,
                                     Expression e, int g,
                                     boolean invert) {
        if (e instanceof EvalExpression) {
            EvalExpression eval = (EvalExpression)e;
            return disables(model, t, eval.getExpression(), g,invert);
        } else if (e instanceof BooleanExpression) {
            BooleanExpression ce = (BooleanExpression)e;
            if (ce.getToken().kind == PromelaTokenManager.BNOT ||
                ce.getToken().kind == PromelaTokenManager.LNOT) {
                return disables(model, t, ce.getExpr1(), g, !invert);
            } else {
                return disables(model, t, ce.getExpr1(), g,invert) &&
                       disables(model, t, ce.getExpr2(), g,invert);
            }
        } else {
            List<SimplePredicate> sps = new ArrayList<SimplePredicate>();
            extract_conjunct_predicates(model, sps, e, invert);
            for (SimplePredicate sp : sps) {
                if (invert) sp = sp.invert();
                if (disables(model, t, g, sp)) return true;
            }
            return false;
        }
    }

    /**
     * Return true, only if one action disables the guard!
     */
    private static boolean disables (LTSminModel model,
                                   LTSminTransition t, int g,
                                   SimplePredicate sp) {
        DepMatrix testSet = new DepMatrix(1, model.sv.size());
        RWMatrix a2s = model.getActionDepMatrix();

        for (Action a : t.getActions()) {
            testSet.clear();
            LTSminDMWalker.walkOneGuard(model, testSet, sp.e, 0);
            RWDepRow writeSet = a2s.getRow(a.getIndex());
            if (!writeSet.mayWrites(testSet.getRow(0)))
                continue;

            boolean conflicts = sp.conflicts(model, a, false);
            if (conflicts) { // one action disables guard!
                return true; // assume actions do not contradict each other
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
     */
    private static int generateDoNoAccord(LTSminModel model, GuardInfo guardInfo) {
        int nTrans = model.getTransitions().size();
        DepMatrix nda = new DepMatrix(nTrans, nTrans);
        guardInfo.setDNAMatrix(nda);
        int neverDNA = 0;
        DepMatrix mct = guardInfo.getMatrix(MCT);
        DepMatrix net = guardInfo.getMatrix(NET);
        DepMatrix ndt = guardInfo.getMatrix(NDT);
        DepMatrix t2t = model.getGuardInfo().getMatrix(T2T);
        for (int t1 = 0; t1 < nTrans; t1++) {
            nda.setDependent(t1, t1);
            for (int t2 = t1+1; t2 < nTrans; t2++) {
                if (transDNA(model, guardInfo, mct, net, ndt, t2t, t1, t2)) {
                    nda.setDependent(t1, t2);
                    nda.setDependent(t2, t1);
                } else {
                    neverDNA++;
                }
                report.updateProgress ();
            }
        }
        return neverDNA;
    }

    private static int generateCommutes(LTSminModel model, GuardInfo guardInfo) {
        int nTrans = model.getTransitions().size();
        DepMatrix commutes = new DepMatrix(nTrans, nTrans);
        guardInfo.setCommutesMatrix(commutes);
        DepMatrix net = guardInfo.getMatrix(NET);
        DepMatrix ndt = guardInfo.getMatrix(NDT);
        DepMatrix t2t = model.getGuardInfo().getMatrix(T2T);
        int commute = 0;
        for (int t1 = 0; t1 < nTrans; t1++) {
            for (int t2 = t1; t2 < nTrans; t2++) {
                if (!transNotCommute(model, net, ndt, t2t, t1, t2)) {
                    commutes.setDependent(t1, t2);
                    commutes.setDependent(t2, t1);
                } else {
                    commute++;
                }
                report.updateProgress ();
            }
        }
        return commute;
    }

    private static boolean transNotCommute (LTSminModel model,
                                             DepMatrix net, DepMatrix ndt,
                                             DepMatrix t2t, int t1, int t2) {

         if (!t2t.isDependent(t1, t2) && !t2t.isDependent(t2, t1)) {
             return false;
         }
    
         // check commutativity
         if (!actionsCommute(model, t1, t2, false)) {
             return true;
         }
    
         if ( atomicDNA(model, net, ndt, t1, t2) ||
              atomicDNA(model, net, ndt, t2, t1)) {
             return true;
         }
         
         return false;
    }
    
   /**
    * Roughly, transitions do not accord if:
    * - they are never coenabled
    * - they disable each other (and are maybe coenabled as implied by DNS)
    * - their actions do commute (see is_commuting_assignment)
    */
    private static boolean transDNA (LTSminModel model, GuardInfo guardInfo,
                                     DepMatrix mct, DepMatrix net, DepMatrix ndt,
                                     DepMatrix t2t, int t1, int t2) {

        // check for co-enabledness
        if (!mct.isDependent(t1, t2)) {
            return false;
        }

        // check not mutually disabling
        if ( ndt.isDependent(t1, t2) ||
             ndt.isDependent(t2, t1)) {
            return true;
        }

        // check independent actions
        if (!t2t.isDependent(t1, t2) && !t2t.isDependent(t2, t1)) {
            return false;
        }

        // check commutativity
        if (!actionsCommute(model, t1, t2, false)) {
            return true;
        }

        if ( atomicDNA(model, net, ndt, t1, t2) ||
             atomicDNA(model, net, ndt, t2, t1)) {
            return true;
        }
        
        return false;
    }

    /**
     * check all atomic steps
     */
    private static boolean atomicDNA(LTSminModel model,
                                     DepMatrix net, DepMatrix ndt,
                                     int t1, int t2) {
        List<LTSminTransition> transitions = model.getTransitions();
        for (LTSminTransition atomic : transitions.get(t1).getTransitions()) {
            int a = atomic.getGroup();
            if (internalDNA(model, net, ndt, a, t2)) {
                return true;
            }
    
            for (LTSminTransition atomic2 : transitions.get(t2).getTransitions()) {
                int b = atomic2.getGroup();
                if (internalDNA(model, net, ndt, a, b)) {
                    return true;
                }   
            }
        }
        return false;
    }

    /**
     * For atomic transitions (internal steps),
     * we require a more stringent condition
     * the atomic steps may neither be disabled nor enabled so
     * that the outcome of the group is not influenced
     */
    private static boolean internalDNA (LTSminModel model,
                                        DepMatrix net, DepMatrix ndt,
                                        int a, int t2) {
        // NO maybe coenabled check, as the internal guards are invisible
        // for t2!

        // internal atomic action disabled / enabled by t2
        // (we may assume t2 to be enabled during atomic sequence;
        //  if it isn't the nds check below should fail)
        if ( ndt.isDependent(t2, a) ||
             net.isDependent(t2, a)) {
            return true;
        }

        // internal action disables t2
        if ( ndt.isDependent(a, t2) ) {
            return true;
        }
        
        if (!actionsCommute(model, a, t2, true)) {
            return true;
        }
        
        return false;
    }

    /**
     * Gathers all mutual r/w dependent actions and checks their commutativity.
     * Channels can be considered commuting if one transition reads and the
     * other writes, and both are enabled. IF the latter condition does not hold
     * set nochan = true;
     */
    private static boolean actionsCommute(LTSminModel model, int t1, int t2,
                                          boolean nochan) {
        
        LTSminTransition trans1 = model.getTransitions().get(t1);
        LTSminTransition trans2 = model.getTransitions().get(t2);
        RWMatrix a2s = model.getActionDepMatrix();

        // check for non-commuting actions
        for (Action acta: trans1.getActions()) {
            RWDepRow depsA = a2s.getRow(acta.getIndex()); 
     
            for (Action actb : trans2.getActions()) {
                RWDepRow depsB = a2s.getRow(actb.getIndex());

                if (!depsB.mayWrites(depsA) && !depsA.mayWrites(depsB)) {
                    continue;
                }

                // extract simple predicates for the actions
                List<SimplePredicate> allA, allB;
                try {
                    allA = allAssigns (model, acta, depsB);
                    allB = allAssigns (model, actb, depsA);
                } catch (ParseException e) {
                    return false;
                } 
                
                for (SimplePredicate spa : allA) {
                    for (SimplePredicate spb : allB) {
                        if (!is_commuting_assignment(model, spa, spb, nochan)) {
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
     * - read and write to a channel (provided both are enabled).
     */
    private static boolean is_commuting_assignment(LTSminModel model,
                                                   SimplePredicate p1,
                                                   SimplePredicate p2,
                                                   boolean nochan) {
        if (!compareID(p1.id, p2.id)) {
            return true;
        } else if (p1.comparison == EQ && p2.comparison == EQ) {
            return p1.constant == p2.constant;
        } else if ((p1.comparison == INCR || p1.comparison == DECR) &&
                   (p2.comparison == INCR || p2.comparison == DECR)) {
            return true;
        } else if ( !nochan &&
               (p1.comparison == CH_READ && p2.comparison == LNOT) ||
               (p1.comparison == LNOT && p2.comparison == CH_READ)) {
            return true;
        }
        return false;
    }

    /**
     * Checks possibility of overlapping IDs.
     * an ID can be incomplete in which case true is returned if the other
     * ID points to the same variable (or a subvariable in it).
     * Dynamic variable access is threaded as "*".  
     */
    private static boolean compareID (Identifier id1, Identifier id2) {
        Variable var1 = id1.getVariable();
        Variable var2 = id2.getVariable();
        if (var1.isHidden() || var2.isHidden()) throw new RuntimeException();

        if (!var1.getName().equals(var2.getName()))
            return false;
        
        Expression ar1 = id1.getArrayExpr();
        Expression ar2 = id2.getArrayExpr();
        
        if (null != ar1) { 
            try {
                int c1 = ar1.getConstantValue();
                int c2 = ar2.getConstantValue();
                if (c1 != c2) {
                    return false;
                }
            } catch (ParseException pe) {}
        }

        if (id1.getSub() != null && id2.getSub() != null)
            return compareID(id1.getSub(), id1.getSub());

        return true;
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
     * 
     */
    private static List<SimplePredicate> allAssigns (LTSminModel model,
                                                     Action a,
                                                     RWDepRow deps)
                                                         throws ParseException {
        List<SimplePredicate> sps = new ArrayList<SimplePredicate>();
        if (a instanceof AssignAction) {
            SimplePredicate sp1 = new SimplePredicate();
            AssignAction ae = (AssignAction)a;
            if (!depCheck(model, ae.getIdentifier(), deps) &&
                !depCheck(model, ae.getExpr(), deps.mayWrite))
                return sps;
            sp1.id = ae.getIdentifier();
            switch (ae.getToken().kind) {
                case ASSIGN:
                    sp1.comparison = EQ;
                    sp1.constant = ae.getExpr().getConstantValue();
                    break;
                case INCR:   sp1.comparison = INCR; break;
                case DECR:   sp1.comparison = DECR; break;
                default: throw new AssertionError("unknown assignment type");
            }
            sps.add(sp1);
        } else if (a instanceof ResetProcessAction) {
            ResetProcessAction rpa = (ResetProcessAction)a;
            Action end = assign(rpa.getProcess().getPC(), -1);
            sps.addAll(allAssigns(model, end, deps));
            Action procs = decr(id(_NR_PR));
            sps.addAll(allAssigns(model, procs, deps));
            /*for (LTSminSlot slot : model.sv) { //TODO: get from slot to Id
                LTSminVariable v = slot.getVariable();
                if (v.getName().equals(C_STATE_PROC_COUNTER)) continue;
                ProcInstance owner = (ProcInstance)v.getVariable().getOwner();
                if (owner == null) continue;
                if ( !owner.getName().equals(rpa.getProcess().getName()) )
                    continue;
                Expression e = v.getInitExpr();
                if (e == null)
                    e = constant(0); 
                Action init = assign(slot.getIdentifier(), e);
                sps.addAll(allAssigns(model, init, writes));
            }*/
        } else if (a instanceof ExprAction) {
            Expression expr = ((ExprAction)a).getExpression();
            if (expr.getSideEffect() == null) return sps; // simple expressions are guards
            
            RunExpression re = (RunExpression)expr;
            Action procs = incr(id(_NR_PR));
            sps.addAll(allAssigns(model, procs, deps));

            for (Proctype p : re.getInstances()) {
                for (ProcInstance instance : re.getInstances()) { // sets a pc to 0
                    Action step = assign(instance.getPC(), 0);
                    sps.addAll(allAssigns(model, step, deps));
                }
                //write to the arguments of the target process
                Iterator<Expression> rei = re.getExpressions().iterator();
                for (Variable v : p.getArguments()) {
                    Expression param = rei.next();
                    if (v.getType() instanceof ChannelType || v.isStatic())
                        continue; //passed by reference or already in state vector
                    Action arg = assign(v, param);
                    sps.addAll(allAssigns(model, arg, deps));
                }
            }
            for (Action rea : re.getInitActions()) {
                sps.addAll(allAssigns(model, rea, deps));
            }
        } else if(a instanceof OptionAction) { // options in a d_step sequence
            // assume dependence check has already been done before call to allAssigns
            throw new ParseException();
        } else if(a instanceof ChannelSendAction) {
            ChannelSendAction csa = (ChannelSendAction)a;
            Identifier id = csa.getIdentifier();

            for (Expression e : csa.getExprs()) {
                if (depCheck(model, e, deps.mayWrite))
                    throw new ParseException();
            }

            if (!depCheck(model, chanLength(id), deps))
                return sps;

            if (csa.isSorted()) {
                throw new ParseException();
            }

            SimplePredicate send = new SimplePredicate();
            send.comparison = LNOT;
            send.id = chanLength(id);
            sps.add(send);
        } else if(a instanceof ChannelReadAction) {
            ChannelReadAction cra = (ChannelReadAction)a;
            Identifier id = cra.getIdentifier();
            
            for (Expression e : cra.getExprs()) {
                if ( e instanceof Identifier &&
                     depCheck(model, e, deps))
                    throw new ParseException();
            }

            if (!depCheck(model, chanLength(id), deps))
                return sps;
            
            if (cra.isRandom()) {
                throw new ParseException();
            }
                
            SimplePredicate read = new SimplePredicate();
            read.comparison = CH_READ;
            read.id = chanLength(id);
            sps.add(read);
        } else {
            throw new ParseException();
        }
        return sps;
    }
    
	/**************
	 * MCE
	 * ************/

	private static int generateCoenMatrix(LTSminModel model, GuardInfo guardInfo) {
	    int nlabels = guardInfo.getNumberOfLabels();
		DepMatrix co = new DepMatrix(nlabels, nlabels);
		guardInfo.setCoMatrix(co);
		int neverCoEnabled = 0;
        DepMatrix g2g = model.getGuardInfo().getMatrix(G2G);
		for (int g1 = 0; g1 < nlabels; g1++) {

            co.setDependent(g1, g1);

            Expression ge1 = guardInfo.get(g1).getExpr();
            for (int g2 = g1+1; g2 < nlabels; g2++) {
	
                report.updateProgress ();

                if (isTimeout(model, co, g1, g2) || isTimeout(model, co, g2, g1)) {
                    neverCoEnabled += co.isDependent(g1,  g2) ? 0 : 1;
                    continue;   
                }

                if (!g2g.isDependent(g1, g2)) { // independent
                    co.setDependent(g1, g2);
                    co.setDependent(g2, g1);
                    continue;
                }
                
                Expression ge2 = guardInfo.get(g2).getExpr();
                Boolean coenabled = MCE(model, ge1, ge2, false, false, null, null);
                if (coenabled == null || coenabled) {
                    co.setDependent(g1, g2);
                    co.setDependent(g2, g1);
                } else {
                    neverCoEnabled++;
                }
			}
		}
		return neverCoEnabled;
	}

    private static boolean isTimeout(LTSminModel model, DepMatrix m,
                                     int g1, int g2) {
        GuardInfo guardInfo = model.getGuardInfo();
        LTSminGuard guard1 = guardInfo.get(g1);
        if (guard1.getExpr() instanceof TimeoutExpression) {
            for (int t2 : guardInfo.getGuardMatrix().get(g2)) {
                if (model.getTransitions().get(t2).isTimeout()) {
                    m.setDependent(g1, g2);
                    m.setDependent(g2, g1);
                    break;
                }
            }
            return true;
        }
        return false;
    }

    private static int generateICoenMatrix(LTSminModel model, GuardInfo guardInfo) {
        int nlabels = guardInfo.getNumberOfLabels();
        int neverICoEnabled = 0;
        DepMatrix ico = new DepMatrix(nlabels, nlabels);
        guardInfo.setICoMatrix(ico);
        DepMatrix g2g = model.getGuardInfo().getMatrix(G2G);
        for (int g1 = 0; g1 < nlabels; g1++) {
            Expression ge1 = guardInfo.get(g1).getExpr();
            for (int g2 = 0; g2 < nlabels; g2++) {
                report.updateProgress ();
                if (!g2g.isDependent(g1, g2)) { // independent
                    ico.setDependent(g1, g2);
                    ico.setDependent(g2, g1);
                    continue;
                }
                Expression ge2 = guardInfo.get(g2).getExpr();
                
                Boolean icoenabled = MCE(model, ge1, ge2, true, false, null, null);
                if (icoenabled == null || icoenabled) {
                    ico.setDependent(g1, g2);
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
                LTSminDMWalker.walkOneGuard(model, testSet, e, 0);
                if (!testSet.getRow(0).isDependent(limit)) return null;
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
                               SimplePredicate a, boolean invert) {
        List<SimplePredicate> gb_sp = new ArrayList<SimplePredicate>();
        boolean missed = extract_conjunct_predicates(model, gb_sp, e2, invert);
        if (invert) {
            if (missed)
                return true; // don't know
            for(SimplePredicate b : gb_sp) {
                b = b.invert();
                if (!a.is_conflict_predicate(model, b)) {
                    return true;
                }
            }
            return false;
        } else {
            for(SimplePredicate b : gb_sp) {
                if (a.is_conflict_predicate(model, b)) {
                    return false;
                }
            }
            return true;
        }
    }
}

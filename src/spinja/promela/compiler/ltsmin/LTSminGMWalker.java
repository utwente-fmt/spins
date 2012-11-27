package spinja.promela.compiler.ltsmin;

import static spinja.promela.compiler.ltsmin.model.LTSminUtil.assign;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.chanContentsGuard;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.chanLength;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.channelBottom;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.channelNext;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.compare;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.constant;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.decr;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.id;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.incr;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.not;
import static spinja.promela.compiler.parser.PromelaConstants.ASSIGN;
import static spinja.promela.compiler.parser.PromelaConstants.DECR;
import static spinja.promela.compiler.parser.PromelaConstants.INCR;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import spinja.promela.compiler.ProcInstance;
import spinja.promela.compiler.Proctype;
import spinja.promela.compiler.actions.Action;
import spinja.promela.compiler.actions.AssignAction;
import spinja.promela.compiler.actions.ChannelReadAction;
import spinja.promela.compiler.actions.ChannelSendAction;
import spinja.promela.compiler.actions.ExprAction;
import spinja.promela.compiler.actions.OptionAction;
import spinja.promela.compiler.expression.BooleanExpression;
import spinja.promela.compiler.expression.ChannelLengthExpression;
import spinja.promela.compiler.expression.ChannelOperation;
import spinja.promela.compiler.expression.ChannelReadExpression;
import spinja.promela.compiler.expression.CompareExpression;
import spinja.promela.compiler.expression.Expression;
import spinja.promela.compiler.expression.Identifier;
import spinja.promela.compiler.expression.RemoteRef;
import spinja.promela.compiler.expression.RunExpression;
import spinja.promela.compiler.ltsmin.LTSminPrinter.ExprPrinter;
import spinja.promela.compiler.ltsmin.matrix.DepMatrix;
import spinja.promela.compiler.ltsmin.matrix.GuardInfo;
import spinja.promela.compiler.ltsmin.matrix.LTSminGuard;
import spinja.promela.compiler.ltsmin.matrix.LTSminGuardAnd;
import spinja.promela.compiler.ltsmin.matrix.LTSminGuardBase;
import spinja.promela.compiler.ltsmin.matrix.LTSminGuardContainer;
import spinja.promela.compiler.ltsmin.matrix.LTSminGuardNand;
import spinja.promela.compiler.ltsmin.matrix.LTSminGuardNor;
import spinja.promela.compiler.ltsmin.matrix.LTSminGuardOr;
import spinja.promela.compiler.ltsmin.matrix.LTSminLocalGuard;
import spinja.promela.compiler.ltsmin.matrix.LTSminPCGuard;
import spinja.promela.compiler.ltsmin.model.LTSminIdentifier;
import spinja.promela.compiler.ltsmin.model.LTSminModel;
import spinja.promela.compiler.ltsmin.model.LTSminTransition;
import spinja.promela.compiler.ltsmin.model.ResetProcessAction;
import spinja.promela.compiler.ltsmin.state.LTSminPointer;
import spinja.promela.compiler.ltsmin.state.LTSminStateVector;
import spinja.promela.compiler.parser.ParseException;
import spinja.promela.compiler.parser.PromelaConstants;
import spinja.promela.compiler.parser.PromelaTokenManager;
import spinja.promela.compiler.variable.ChannelType;
import spinja.promela.compiler.variable.Variable;
import spinja.promela.compiler.variable.VariableType;

/**
 * A container for boolean state labels (part of which are guards), guard
 * matrices and state label matrix 
 * 
 * TODO: assumes that different actions do not cancel each other out!
 * 
 * @author FIB, Alfons Laarman
 */
public class LTSminGMWalker {

	public enum Aggressivity {
		Weak,
		Low,
		Normal,
		High
	}

	static Aggressivity aggressivity = Aggressivity.High;
	static final boolean NO_NES = false;
	static final boolean NO_NDS = false;

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
	static void generateGuardInfo(LTSminModel model,
	                              Map<String, LTSminGuard> labels,
	                              LTSminDebug debug) {
		debug.say("Generating guard information ...");
		debug.say_indent++;
		
		if(model.getGuardInfo()==null)
			model.setGuardInfo(new GuardInfo(model.getTransitions().size()));
		GuardInfo guardInfo = model.getGuardInfo();
		Params params = new Params(model, guardInfo, debug);

		// extact guards
		generateTransitionGuardLabels (params);

		//int nguards = guardInfo.getNumberOfGuards();

		// add the normal labels
        for (Map.Entry<String, LTSminGuard> label : labels.entrySet())
            guardInfo.addLabel(label.getKey(), label.getValue());

        // We extend the NES and NDS matrices to include all labels
        // The special labels, e.g. progress and valid end, can then be used in
        // LTL properties with precise (in)visibility information.
        int nlabels = guardInfo.getNumberOfLabels();
        
		// generate Maybe Coenabled matrix
		int nmce = generateCoenMatrix (model, guardInfo);
		int mceSize = nlabels*nlabels/2;
		params.debug.say("Found "+ nmce +"/"+ mceSize +" !MCE gurads.");
    
		// generate NES matrix
		int nnes = generateNESMatrix (model, guardInfo);
		int nesSize = nlabels*model.getTransitions().size();
		params.debug.say("Found "+ nnes +"/"+ nesSize +" !NES guards.");

		// generate NDS matrix
		int nnds = generateNDSMatrix (model, guardInfo);
		params.debug.say("Found "+ nnds +"/"+ nesSize +" !NDS guards.");

        generateLabelMatrix(model);
		
		debug.say_indent--;
		debug.say("Generating guard information done");
		debug.say("");
	}

	private static void generateLabelMatrix(LTSminModel model) {
        GuardInfo guardInfo = model.getGuardInfo();
		int nlabels = guardInfo.getNumberOfLabels();
	    DepMatrix dm = new DepMatrix(nlabels, model.sv.size());
		guardInfo.setDepMatrix(dm);
		for (int i = 0; i < nlabels; i++) {
			LTSminDMWalker.walkOneGuard(model, dm, guardInfo.get(i), i);
		}
	}

	/**************
	 * NDS
	 * ************/

	private static int generateNDSMatrix(LTSminModel model, GuardInfo guardInfo) {
        int nlabels = guardInfo.getNumberOfLabels();
		DepMatrix nds = new DepMatrix(nlabels, model.getTransitions().size());
		guardInfo.setNDSMatrix(nds);
		int notNDS = 0;
		for (int i = 0; i <  nds.getRows(); i++) {
			for (int j = 0; j < nds.getRowLength(); j++) {
				LTSminTransition trans = model.getTransitions().get(j);
				LTSminGuard g = (LTSminGuard) guardInfo.get(i);
				if (NO_NDS || is_nds_guard(model, g, trans)) {
					nds.incRead(i, j);
				} else {
					notNDS++;
				}
			}
		}
		return notNDS;
	}

	private static boolean is_nds_guard(LTSminModel model, LTSminGuard guard,
										LTSminTransition transition) {
		switch (aggressivity) {
		case Weak:
		case Low:
			return is_nds_guard(model, guard.getExpr(), transition);
		case Normal:
			return is_nds_guard_strong(model, guard.getExpr(), transition);
		case High:
			return is_nds_guard_stronger(model, guard.getExpr(), transition);
		default:
			throw new AssertionError("Unimplemented aggressivity level: "+ aggressivity);
		}
	}

	/**
	 * Determine NDS over conjuctions: NDS holds for ex1 and trans iff 
	 * it holds for one e,t in conjuctions(ex1) X {trans}
	 */
	private static boolean is_nds_guard_stronger(LTSminModel model, Expression ex1,
												 LTSminTransition t) {
        List<Expression> ga_ex = new ArrayList<Expression>();
        extract_conjunctions (ga_ex, ex1);
        for (Expression e : ga_ex) {
            if (!is_nds_guard_strong(model, e, t)) {
            	return false;
            }
        }
        return true;
	}
	
	/**
	 * Determine NDS over disjuctions: NDS holds for ex1 and trans iff 
	 * it holds for all e,t in disjuctions(ex1) X {trans}
	 */
	private static boolean is_nds_guard_strong(LTSminModel model, Expression ex1,
											   LTSminTransition t) {
        List<Expression> ga_ex = new ArrayList<Expression>();
        extract_disjunctions (ga_ex, ex1);
        for (Expression e : ga_ex) {
            if (is_nds_guard(model, e, t)) {
            	return true;
            }
        }
        return false;
	}

	private static boolean is_nds_guard(LTSminModel model, Expression guard,
										LTSminTransition transition) {
        List<SimplePredicate> sps = new ArrayList<SimplePredicate>();
		extract_predicates(sps, guard, true, true); // strict, because we compare future and past state vectors
												 	// conj, since to disable the guard, one conjunction needs to be disabled
		for (SimplePredicate sp2 : sps) {
		    // PC guards can only be disabled by the next transition
		    if (sp2.id.isPC()) { // so exclude the others:
		        for (LTSminPCGuard g : transition.getPCGuards()) {
		            if (!mayBeCoenabled(model, sp2.e, g.getExpr())) return false;
		        }
		    }
			for (Action a : transition.getActions()) { 
				if (is_conflicting(model, sp2, a, true /* INVERT */)) {
					return false; // NOT conflicting!
				}
			}
		}
		return true;
	}

	/**************
	 * NES
	 * ************/
	
	private static int generateNESMatrix(LTSminModel model, GuardInfo guardInfo) {
        int nlabels = guardInfo.getNumberOfLabels();
		DepMatrix nes = new DepMatrix(nlabels, model.getTransitions().size());
		guardInfo.setNESMatrix(nes);
		int notNES = 0;
		for (int i = 0; i <  nes.getRows(); i++) {
			for (int j = 0; j < nes.getRowLength(); j++) {
				LTSminTransition trans = model.getTransitions().get(j);
                LTSminGuard g = (LTSminGuard) guardInfo.get(i);
				if (NO_NES || is_nes_guard(model, g, trans)) {
					nes.incRead(i, j);
				} else {
					notNES++;
				}
			}
		}
		return notNES;
	}

	private static boolean is_nes_guard(LTSminModel model, LTSminGuard guard,
										LTSminTransition transition) {
		switch (aggressivity) {
		case Weak:
		case Low:
			return is_nes_guard(model, guard.getExpr(), transition);
		case Normal:
			return is_nes_guard_strong(model, guard.getExpr(), transition);
		case High:
			return is_nes_guard_stronger(model, guard.getExpr(), transition);
		default:
			throw new AssertionError("Unimplemented aggressivity level: "+ aggressivity);
		}
	}

	/**
	 * Determine NES over disjuctions: NES holds for ex1 and trans iff 
	 * it holds for all e,t in disjuctions(ex1) X {trans}
	 */
	private static boolean is_nes_guard_stronger(LTSminModel model, Expression ex1,
												 LTSminTransition t) {
        List<Expression> ga_ex = new ArrayList<Expression>();
        extract_disjunctions (ga_ex, ex1);
        for (Expression e : ga_ex) {
            if (!is_nes_guard_strong(model, e, t)) {
            	return false;
            }
        }
        return true;
	}
	
	/**
	 * Determine NES over conjuctions: NES holds for ex1 and trans iff 
	 * it holds for one e,t in conjuctions(ex1) X {trans}
	 */
	private static boolean is_nes_guard_strong(LTSminModel model, Expression ex1,
											   LTSminTransition t) {
        List<Expression> ga_ex = new ArrayList<Expression>();
        extract_conjunctions (ga_ex, ex1);
        for (Expression e : ga_ex) {
            if (is_nes_guard(model, e, t)) {
            	return true;
            }
        }
        return false;
	}

	private static boolean is_nes_guard(LTSminModel model, Expression guard,
										LTSminTransition transition) {
        List<SimplePredicate> sps = new ArrayList<SimplePredicate>();
		extract_predicates(sps, guard, true, false); // strict, because we compare future and past state vectors
													 // disj, since to re-enable the guard, one disjunction needs to be re-enabled
		for (SimplePredicate sp2 : sps) {
			for (Action a : transition.getActions()) {
				if (is_conflicting(model, sp2, a, false)) {
					return false;
				}
			}
		}
		return true;
	}

	/**************
	 * MCE
	 * ************/
	
	private static int generateCoenMatrix(LTSminModel model, GuardInfo guardInfo) {
	    int nlabels = guardInfo.getNumberOfLabels();
		DepMatrix co = new DepMatrix(nlabels, nlabels);
		guardInfo.setCoMatrix(co);
		int neverCoEnabled = 0;
		for (int i = 0; i < nlabels; i++) {
			// same guard is always coenabled:
			co.incRead(i, i);
			for (int j = i+1; j < nlabels; j++) {
                LTSminGuard g1 = (LTSminGuard) guardInfo.get(i);
                LTSminGuard g2 = (LTSminGuard) guardInfo.get(j);
				if (mayBeCoenabled(model, g1, g2)) {
					co.incRead(i, j);
					co.incRead(j, i);
				} else {
					neverCoEnabled++;
				}
			}
		}
		return neverCoEnabled;
	}
	
	private static boolean mayBeCoenabled(LTSminModel model, LTSminGuard g1, LTSminGuard g2) {
        switch (aggressivity) {
            case Weak:
            case Low:
                return mayBeCoenabled(model, g1.expr, g2.expr);
            case Normal:
                return mayBeCoenabledStrong(model, g1.expr, g2.expr);
            case High:
                return mayBeCoenabledStronger(model, g1.expr, g2.expr);
            default:
                throw new AssertionError("Unimplemented aggressivity level: "+ aggressivity);
        }
	}

	/**
	 * Determine MCE over disjuctions: MCE holds for ex1 and ex2 iff 
	 * it holds over for all d1,d2 in conjuctions(ex1) X conjunctions(ex2)
	 */
	private static boolean mayBeCoenabledStronger(LTSminModel model, Expression ex1, Expression ex2) {
        List<Expression> ga_ex = new ArrayList<Expression>();
        List<Expression> gb_ex = new ArrayList<Expression>();
        extract_conjunctions (ga_ex, ex1);
        extract_conjunctions (gb_ex, ex2);
        for(Expression a : ga_ex) {
            for(Expression b : gb_ex) {
                if (!mayBeCoenabledStrong(model, a, b)) {
                	return false;
                }
            }
        }
        return true;
	}

	/**
	 * Extracts all conjuctions until disjunctions or arithmicExpr are encountered
	 */
	private static void extract_conjunctions (List<Expression> ds, Expression e) {
		if(e instanceof BooleanExpression) {
			BooleanExpression ce = (BooleanExpression)e;
			if (ce.getToken().kind == PromelaTokenManager.BAND ||
				ce.getToken().kind == PromelaTokenManager.LAND) {
				extract_disjunctions (ds, ce.getExpr1());
				extract_disjunctions (ds, ce.getExpr2());
			} else {
				ds.add(e);
			}
		} else {
			ds.add(e);
		}
	}	
	
	/**
	 * Determine MCE over disjuctions: MCE holds for ex1 and ex2 iff 
	 * it holds for one d1,d2 in disjuctions(ex1) X disjunctions(ex2)
	 */
	private static boolean mayBeCoenabledStrong(LTSminModel model, Expression ex1, Expression ex2) {
        List<Expression> ga_ex = new ArrayList<Expression>();
        List<Expression> gb_ex = new ArrayList<Expression>();
        extract_disjunctions (ga_ex, ex1);
        extract_disjunctions (gb_ex, ex2);
        for(Expression a : ga_ex) {
            for(Expression b : gb_ex) {
                if (mayBeCoenabled(model, a, b)) {
                	return true;
                }
            }
        }
        return false;
	}

	/**
	 * Extracts all disjuctions until conjunctions or arithmicExpr are encountered
	 */
	private static void extract_disjunctions (List<Expression> ds, Expression e) {
		if(e instanceof BooleanExpression) {
			BooleanExpression ce = (BooleanExpression)e;
			if (ce.getToken().kind == PromelaTokenManager.BOR ||
				ce.getToken().kind == PromelaTokenManager.LOR) {
				extract_disjunctions (ds, ce.getExpr1());
				extract_disjunctions (ds, ce.getExpr2());
			} else {
				ds.add(e);
			}
		} else {
			ds.add(e);
		}
	}

	static class SimplePredicate {
		public SimplePredicate() {}
		public SimplePredicate(Expression e, Identifier id, int c) {
			comparison = e.getToken().kind;
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
	}

	/**
	 * Determine MCE over conjunctions: MCE holds for ex1 and ex2 iff 
	 * all sp1,sp2 in simplePreds(ex1) X simplePreds(ex2) do no conflict
	 */
	private static boolean mayBeCoenabled(LTSminModel model, Expression ex1, Expression ex2) {
        List<SimplePredicate> ga_sp = new ArrayList<SimplePredicate>();
        List<SimplePredicate> gb_sp = new ArrayList<SimplePredicate>();
        extract_predicates(ga_sp, ex1, false, true); // non-strict, since MCE holds for the same state
        extract_predicates(gb_sp, ex2, false, true); // conj, since only one conj has to conflict for the guards to conflict
        for(SimplePredicate a : ga_sp) {
            for(SimplePredicate b : gb_sp) {
                if (is_conflict_predicate(model, a, b)) {
                	return false;
                }
            }
        }
        return true;
	}

    /**
     * Returns whether an action CONFLICTS with a simple predicate, e.g.:
     * x := 5 && x == 4
     * 
     * This is an under-estimation! Therefore, a negative result is useless. For
     * For testing on the negation, use the invert flag.
     * 
     * @param model
     * @param sp2 the simple predicate (x == 4)
     * @param a the action
     * @param invert if true: the action is inverted: x := 5 --> x := !5
     * @return true if conflict is found, FALSE IF UNKNOWN
     */
    private static boolean is_conflicting(LTSminModel model, SimplePredicate sp2,
                                          Action a, boolean invert) {
        SimplePredicate sp1 = new SimplePredicate();
        if (a instanceof AssignAction) {
            AssignAction ae = (AssignAction)a;
            try {
                sp1.id = getConstantId(ae.getIdentifier(), true); // strict
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
                    sp1.comparison = invert ? PromelaConstants.NEQ : PromelaConstants.EQ;
                    if (is_conflict_predicate(model, sp1, sp2))
                        return true;
                    break;
                case INCR:
                    if (sp1.getRef(model).equals(sp2.getRef(model)))
                        if (invert ? gt(sp2) : lt(sp2))
                            return true;
                    break;
                case DECR:
                    if (sp1.getRef(model).equals(sp2.getRef(model)))
                        if (invert ? lt(sp2) : gt(sp2))
                            return true;
                    break;
                default:
                    throw new AssertionError("unknown assignment type");
            }
        } else if (a instanceof ResetProcessAction) {
            ResetProcessAction rpa = (ResetProcessAction)a;
            Variable pc = model.sv.getPC(rpa.getProcess());
            if (is_conflicting(model, sp2, assign(pc, -1), invert))
                return true;
            return is_conflicting(model, sp2, decr(id(LTSminStateVector._NR_PR)), invert);
        } else if (a instanceof ExprAction) {
            Expression expr = ((ExprAction)a).getExpression();
            if (expr.getSideEffect() == null) return false; // simple expressions are guards
            RunExpression re = (RunExpression)expr;
            
            if (is_conflicting(model, sp2, incr(id(LTSminStateVector._NR_PR)), invert))
                return true;

            for (Proctype p : re.getInstances()) {
                for (ProcInstance instance : re.getInstances()) { // sets a pc to 0
                    Variable pc = model.sv.getPC(instance);
                    if (is_conflicting(model, sp2, assign(pc, 0), invert)) {
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
                        if (is_conflicting(model, sp2, assign(v, val), invert)) {
                            return true;
                        }
                    } catch (ParseException e) {}
                }
            }
            for (Action rea : re.getActions()) {
                if (is_conflicting(model, sp2,  rea, invert)) {
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
                    if (is_conflicting(model, sp2, assign(next, constant(val)), invert)) {
                        return true;
                    }
                } catch (ParseException e) {}
            }
            return is_conflicting(model, sp2, incr(chanLength(id)), invert);
        } else if(a instanceof OptionAction) { // options in a d_step sequence
            //OptionAction oa = (OptionAction)a;
            //for (Sequence seq : oa) {
                //Action act = seq.iterator().next(); // guaranteed by parser
                //if (act instanceof ElseAction)
            //}
        } else if(a instanceof ChannelReadAction) {
            ChannelReadAction cra = (ChannelReadAction)a;
            Identifier id = cra.getIdentifier();
            if (!cra.isPoll()) {
                return is_conflicting(model, sp2, decr(chanLength(id)), invert);
            }
        }
        return false;
    }

    private static boolean lt(SimplePredicate sp2) {
        return sp2.comparison == PromelaConstants.LT || 
            sp2.comparison == PromelaConstants.LTE;
    }

    private static boolean gt(SimplePredicate sp2) {
        return sp2.comparison == PromelaConstants.GT || 
            sp2.comparison == PromelaConstants.GTE;
    }

	/**
	 * Collects all simple predicates in an expression e.
	 * SimplePred ::= cvarref <comparison> constant | constant <comparison> cvarref
	 * where cvarref is a reference to a singular (channel) variable or a
	 * constant index in array variable.
	 * 
	 * @param strict indicates whether we look for strictly constant variables:
	 * ie. non-array variables or array variables with constant index
	 */
	private static void extract_predicates(List<SimplePredicate> sp, Expression e,
										   boolean strict, boolean conj) {
		int c;
    	if (e instanceof CompareExpression) {
    		CompareExpression ce1 = (CompareExpression)e;
    		Identifier id;
    		try {
    			id = getConstantId(ce1.getExpr1(), strict);
    			c = ce1.getExpr2().getConstantValue();
        		sp.add(new SimplePredicate(e, id, c));
    		} catch (ParseException pe) {
        		try {
        			id = getConstantId(ce1.getExpr2(), strict);
        			c = ce1.getExpr1().getConstantValue();
            		sp.add(new SimplePredicate(e, id, c));
        		} catch (ParseException pe2) {}
    		}
		} else if (e instanceof ChannelReadExpression) { //TODO: isRandom (disjunction of conjunctions)
			ChannelReadExpression cre = (ChannelReadExpression)e;
			Identifier id = cre.getIdentifier();
			extract_predicates(sp, chanContentsGuard(id), strict, conj);
			List<Expression> exprs = cre.getExprs();
			for (int i = 0; i < exprs.size(); i++) {
				try { // this is a conjunction of matchings
					int val = exprs.get(i).getConstantValue();
					Identifier read = channelBottom(id, i);
					CompareExpression compare = compare(PromelaConstants.EQ,
														read, constant(val));
					extract_predicates(sp, compare, strict, conj);
		    	} catch (ParseException pe2) {}
			}
    	} else if (e instanceof ChannelOperation) {
			ChannelOperation co = (ChannelOperation)e;
			String name = co.getToken().image;
			Identifier id = (Identifier)co.getExpression();
			if (((ChannelType)id.getVariable().getType()).isRendezVous())
				return; // Spin returns true in this case (see garp model)
			VariableType type = id.getVariable().getType();
			int buffer = ((ChannelType)type).getBufferSize();
			Expression left = chanLength(id);
			Expression right = null;
			int op = -1;
			if (name.equals("empty")) {
				op = PromelaConstants.EQ;
				right = constant (0);
			} else if (name.equals("nempty")) {
				op = PromelaConstants.NEQ;
				right = constant (0);
			} else if (name.equals("full")) {
				op = PromelaConstants.EQ;
				right = constant (buffer);
			} else if (name.equals("nfull")) {
				op = PromelaConstants.NEQ;
				right = constant (buffer);
			}
			extract_predicates(sp, compare(op, left, right), strict, conj);
		} else if (e instanceof RemoteRef) {
			RemoteRef rr = (RemoteRef)e;
			Variable pc = rr.getPC(null);
			int num = rr.getLabelId();
			Expression comp = compare(PromelaConstants.EQ, id(pc), constant(num));
			extract_predicates(sp, comp, strict, conj);
    	} else if (e instanceof BooleanExpression) {
    		BooleanExpression ce = (BooleanExpression)e;
    		if (conj) {
	    		if (ce.getToken().kind == PromelaTokenManager.BAND ||
	    			ce.getToken().kind == PromelaTokenManager.LAND) {
	    			extract_predicates (sp, ce.getExpr1(), strict, conj);
	    			extract_predicates (sp, ce.getExpr2(), strict, conj);
	    		}
    		} else { // disjunctions
	    		if (ce.getToken().kind == PromelaTokenManager.BOR ||
	    			ce.getToken().kind == PromelaTokenManager.LOR) {
	    			extract_predicates (sp, ce.getExpr1(), strict, conj);
	    			extract_predicates (sp, ce.getExpr2(), strict, conj);
	    		}
    		}
		}
	}

	/**
	 * Tries to parse an expression as a reference to a singular (channel)
	 * variable or a constant index in array variable (a cvarref).
	 */
	private static Identifier getConstantId(Expression e, boolean strict) throws ParseException {
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
					if (strict) throw new ParseException();
				} // non-strict: do nothing. See getRef().
			}
			Identifier sub = null;
			if (null != id.getSub())
				sub = getConstantId(id.getSub(), strict);
			return id(var, ar, sub);
		} else if (e instanceof ChannelLengthExpression)  {
			ChannelLengthExpression cle = (ChannelLengthExpression)e;
			Identifier id = (Identifier)cle.getExpression();
			return getConstantId(chanLength(id), strict);
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
	            case PromelaConstants.LT:
	                // no conflict if one of these cases
	                no_conflict =
	                (p2.constant < p1.constant - 1) ||
	                (p2.constant == p1.constant - 1 && p2.comparison != PromelaConstants.GT) ||
	                (lt(p2) || p2.comparison == PromelaConstants.NEQ);
	                break;
	            case PromelaConstants.LTE:
	                // no conflict if one of these cases
	                no_conflict =
	                (p2.constant < p1.constant) ||
	                (p2.constant == p1.constant && p2.comparison != PromelaConstants.GT) ||
	                (lt(p2) || p2.comparison == PromelaConstants.NEQ);
	                break;
	            case PromelaConstants.EQ:
	                // no conflict if one of these cases
	                no_conflict =
	                (p2.constant == p1.constant && (p2.comparison == PromelaConstants.EQ || p2.comparison == PromelaConstants.LTE || p2.comparison == PromelaConstants.GTE)) ||
	                (p2.constant != p1.constant && p2.comparison == PromelaConstants.NEQ) ||
	                (p2.constant < p1.constant && p2.comparison == PromelaConstants.GT || p2.comparison == PromelaConstants.GTE) ||
	                (p2.constant > p1.constant && lt(p2));
	                break;
	            case PromelaConstants.NEQ:
	                // no conflict if one of these cases
	                no_conflict =
	                (p2.constant != p1.constant) ||
	                (p2.constant == p1.constant && p2.comparison != PromelaConstants.EQ);
	                break;
	            case PromelaConstants.GT:
	                // no conflict if one of these cases
	                no_conflict =
	                (p2.constant > p1.constant + 1) ||
	                (p2.constant == p1.constant + 1 && p2.comparison != PromelaConstants.LT) ||
	                (gt(p2) || p2.comparison == PromelaConstants.NEQ);
	                break;
	            case PromelaConstants.GTE:
	                // no conflict if one of these cases
	                no_conflict =
	                (p2.constant > p1.constant) ||
	                (p2.constant == p1.constant && p2.comparison != PromelaConstants.LT) ||
	                (gt(p2) || p2.comparison == PromelaConstants.NEQ);
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
			params.guardMatrix.addGuard(t.getGroup(), (LTSminGuard)guard);
		} else if (guard instanceof LTSminGuardAnd) {
			for(LTSminGuardBase gb : (LTSminGuardContainer)guard)
				walkGuard(params, t, gb);
        } else if (guard instanceof LTSminGuardNand) {
            LTSminGuardNand g = (LTSminGuardNand)guard;
            if (!g.iterator().hasNext()) return;
            Expression e = g.getExpression();
            params.guardMatrix.addGuard(t.getGroup(), not(e));
		} else if (guard instanceof LTSminGuardNor) { // DeMorgan
			for (LTSminGuardBase gb : (LTSminGuardContainer)guard)
                walkGuard(params, t, gb.negate());
		} else if (guard instanceof LTSminGuardOr) {
		    LTSminGuardOr g = (LTSminGuardOr)guard;
			if (!g.iterator().hasNext()) return;
			Expression e = g.getExpression();
			params.guardMatrix.addGuard(t.getGroup(), e);
		} else {
			throw new AssertionError("UNSUPPORTED: " + guard.getClass().getSimpleName());
		}
	}
}

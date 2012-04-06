package spinja.promela.compiler.ltsmin;

import static spinja.promela.compiler.ltsmin.model.LTSminUtil.calc;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.chanContentsGuard;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.chanLength;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.chanRead;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.channelTop;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.compare;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.constant;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.id;
import static spinja.promela.compiler.ltsmin.state.LTSminTypeChanStruct.bufferVar;
import static spinja.promela.compiler.ltsmin.state.LTSminTypeChanStruct.elemVar;
import static spinja.promela.compiler.parser.Promela.C_TYPE_PROC_COUNTER;
import static spinja.promela.compiler.parser.PromelaConstants.ASSIGN;
import static spinja.promela.compiler.parser.PromelaConstants.DECR;
import static spinja.promela.compiler.parser.PromelaConstants.INCR;

import java.util.ArrayList;
import java.util.List;

import spinja.promela.compiler.actions.Action;
import spinja.promela.compiler.actions.AssignAction;
import spinja.promela.compiler.actions.ChannelReadAction;
import spinja.promela.compiler.actions.ChannelSendAction;
import spinja.promela.compiler.actions.ElseAction;
import spinja.promela.compiler.actions.ExprAction;
import spinja.promela.compiler.actions.OptionAction;
import spinja.promela.compiler.expression.BooleanExpression;
import spinja.promela.compiler.expression.ChannelLengthExpression;
import spinja.promela.compiler.expression.ChannelOperation;
import spinja.promela.compiler.expression.ChannelReadExpression;
import spinja.promela.compiler.expression.CompareExpression;
import spinja.promela.compiler.expression.Expression;
import spinja.promela.compiler.expression.Identifier;
import spinja.promela.compiler.ltsmin.LTSminPrinter.ExprPrinter;
import spinja.promela.compiler.ltsmin.matrix.DepMatrix;
import spinja.promela.compiler.ltsmin.matrix.GuardInfo;
import spinja.promela.compiler.ltsmin.matrix.LTSminGuard;
import spinja.promela.compiler.ltsmin.matrix.LTSminGuardAnd;
import spinja.promela.compiler.ltsmin.matrix.LTSminGuardBase;
import spinja.promela.compiler.ltsmin.matrix.LTSminGuardNand;
import spinja.promela.compiler.ltsmin.matrix.LTSminGuardOr;
import spinja.promela.compiler.ltsmin.matrix.LTSminLocalGuard;
import spinja.promela.compiler.ltsmin.model.ChannelTopExpression;
import spinja.promela.compiler.ltsmin.model.LTSminIdentifier;
import spinja.promela.compiler.ltsmin.model.LTSminModel;
import spinja.promela.compiler.ltsmin.model.LTSminTransition;
import spinja.promela.compiler.ltsmin.model.LTSminTransitionCombo;
import spinja.promela.compiler.ltsmin.model.ResetProcessAction;
import spinja.promela.compiler.ltsmin.state.LTSminPointer;
import spinja.promela.compiler.parser.ParseException;
import spinja.promela.compiler.parser.PromelaConstants;
import spinja.promela.compiler.parser.PromelaTokenManager;
import spinja.promela.compiler.variable.ChannelType;
import spinja.promela.compiler.variable.ChannelVariable;
import spinja.promela.compiler.variable.Variable;
import spinja.promela.compiler.variable.VariableType;

/**
 *
 * @author FIB, Alfons Laarman
 */
public class LTSminGMWalker {

	static public class Params {
		public final LTSminModel model;
		public final GuardInfo guardMatrix;
		public int trans;
		public int guards;
		public LTSminDebug debug;

		public Params(LTSminModel model, GuardInfo guardMatrix, LTSminDebug debug) {
			this.model = model;
			this.guardMatrix = guardMatrix;
			this.trans = 0;
			this.guards = 0;
			this.debug = debug;
		}
	}

	static void walkModel(LTSminModel model, LTSminDebug debug) {
		debug.say("Generating guard information ...");
		debug.say_indent++;
		
		if(model.getGuardInfo()==null)
			model.setGuardInfo(new GuardInfo(model.getTransitions().size()));
		GuardInfo guardInfo = model.getGuardInfo();
		Params params = new Params(model, guardInfo, debug);
		// extact guards bases
		walkTransitions(params);

		// generate guard DM
		generateGuardMatrix(model, guardInfo);
		
		// generate Maybe Coenabled matrix
		int nmce = generateCoenMatrix (model, guardInfo);
		int mceSize = guardInfo.size()*guardInfo.size()/2;
		params.debug.say("Found "+ nmce +"/"+ mceSize +" !MCE gurads.");
		
		// generate NES matrix
		int nnes = generateNESMatrix (model, guardInfo);
		int nesSize = guardInfo.size()*model.getTransitions().size();
		params.debug.say("Found "+ nnes +"/"+ nesSize +" !NES guards.");

		// generate NDS matrix
		int nnds = generateNDSMatrix (model, guardInfo);
		params.debug.say("Found "+ nnds +"/"+ nesSize +" !NDS guards.");
		
		debug.say_indent--;
		debug.say("Generating guard information done");
		debug.say("");
	}

	private static void generateGuardMatrix(LTSminModel model, GuardInfo guardInfo) {
		DepMatrix dm = new DepMatrix(guardInfo.size(), model.sv.size());
		guardInfo.setDepMatrix(dm);
		for (int i = 0; i < guardInfo.size(); i++) {
			LTSminDMWalker.walkOneGuard(model, dm, guardInfo.get(i), i);
		}
	}

	private static int generateNDSMatrix(LTSminModel model, GuardInfo guardInfo) {
		DepMatrix nds = new DepMatrix(guardInfo.size(), model.getTransitions().size());
		guardInfo.setNDSMatrix(nds);
		for (int i = 0; i <  nds.getRows(); i++) {
			for (int j = 0; j < nds.getRowLength(); j++) {
				nds.incRead(i, j);
			}
		}
		return 0;
	}

	private static int generateNESMatrix(LTSminModel model, GuardInfo guardInfo) {
		DepMatrix nes = new DepMatrix(guardInfo.size(), model.getTransitions().size());
		guardInfo.setNESMatrix(nes);
		int notNES = 0;
		for (int i = 0; i <  nes.getRows(); i++) {
			for (int j = 0; j < nes.getRowLength(); j++) {
				if (is_nes_guard(guardInfo.get(i), model.getTransitions().get(j))) {
					nes.incRead(i, j);
				} else {
					notNES++;
				}
			}
		}
		return notNES;
	}

	private static boolean is_nes_guard(LTSminGuard guard,
										LTSminTransition transition) {
		return is_nes_guard(guard.getExpr(), (LTSminTransition)transition);
	}
	
	private static boolean is_nes_guard(Expression g, LTSminTransition transition) {
		for (Action a : ((LTSminTransition)transition).getActions()) {
			if (a instanceof AssignAction) {
				AssignAction ae = (AssignAction)a;
				Identifier id = ae.getIdentifier();
				switch (ae.getToken().kind) {
					case ASSIGN:
						if (id.getVariable().getType().equals(C_TYPE_PROC_COUNTER)) {
					        List<SimplePredicate> sps = new ArrayList<SimplePredicate>();
							extract_predicates(sps, g);
							for (SimplePredicate sp : sps) {
								//if (sp.var.endsWith(suffix))
							}
						}
						break;
					case INCR:
						break;
					case DECR:
						break;
					default:
						throw new AssertionError("unknown assignment type");
				}
			} else if(a instanceof ResetProcessAction) {
				//w.appendLine(nr_pr +"--;");
			} else if(a instanceof ExprAction) {
				ExprAction ea = (ExprAction)a;
				try {
					String sideEffect = ea.getExpression().getSideEffect();
					if (sideEffect != null) {
						//RunExpression re = (RunExpression)ea.getExpression();
						
					}
				} catch (ParseException e) {}
			} else if(a instanceof ChannelSendAction) {
				//ChannelSendAction csa = (ChannelSendAction)a;
			} else if(a instanceof OptionAction) { // options in a d_step sequence
				//OptionAction oa = (OptionAction)a;
				//for (Sequence seq : oa) {
					//Action act = seq.iterator().next(); // guaranteed by parser
					//if (act instanceof ElseAction)
				//}
			} else if(a instanceof ChannelReadAction) {
				//ChannelReadAction cra = (ChannelReadAction)a;
			} else if(a instanceof ElseAction) {
				throw new AssertionError("ElseAction outside OptionAction!");
			} 
		}
		return true;
	}

	/**************
	 * MCE
	 * ************/
	
	private static int generateCoenMatrix(LTSminModel model, GuardInfo gm) {
		DepMatrix co = new DepMatrix(gm.size(), gm.size());
		gm.setCoMatrix(co);
		int neverCoEnabled = 0;
		for (int i = 0; i < gm.size(); i++) {
			// same guard is always coenabled:
			co.incRead(i, i);
			for (int j = i+1; j < gm.size(); j++) {
				if (mayBeCoenabled(model, gm.get(i),gm.get(j))) {
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
		return mayBeCoenabledStronger (model, g1.expr, g2.expr);
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
		public SimplePredicate(int kind, Identifier id, int c) {
			comparison = kind;
			this.id = id;
			this.constant = c;
		}

		public int comparison;
		public Identifier id;
		public String ref = null;
		public int constant;
		
		public String getRef(LTSminModel model) {
			if (null!= ref)
				return ref;
			LTSminPointer svp = new LTSminPointer(model.sv, "");
			ExprPrinter p = new ExprPrinter(svp);
			return ref = p.print(id);
		}
	}

	/**
	 * Determine MCE over conjunctions: MCE holds for ex1 and ex2 iff 
	 * all sp1,sp2 in simplePreds(ex1) X simplePreds(ex2) do no conflict
	 */
	private static boolean mayBeCoenabled(LTSminModel model, Expression ex1, Expression ex2) {
        List<SimplePredicate> ga_sp = new ArrayList<SimplePredicate>();
        List<SimplePredicate> gb_sp = new ArrayList<SimplePredicate>();
        extract_predicates(ga_sp, ex1);
        extract_predicates(gb_sp, ex2);
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
	 * Collects all simple predicates in an expression e.
	 * SimplePred ::= cvarref <comparison> constant | constant <comparison> cvarref
	 * where cvarref is a reference to a singular (channel) variable or a
	 * constant index in array variable.
	 */
	private static void extract_predicates(List<SimplePredicate> sp, Expression e) {
		int c;
		Identifier var;
    	if (e instanceof CompareExpression) {
    		CompareExpression ce1 = (CompareExpression)e;
    		try {
    			var = getConstantVar(ce1.getExpr1());
    			c = ce1.getExpr2().getConstantValue();
        		sp.add(new SimplePredicate(e.getToken().kind, var, c));
    		} catch (ParseException pe) {
        		try {
        			var = getConstantVar(ce1.getExpr2());
        			c = ce1.getExpr1().getConstantValue();
            		sp.add(new SimplePredicate(e.getToken().kind, var, c));
        		} catch (ParseException pe2) {}
    		}
		} else if (e instanceof ChannelReadExpression) {
			ChannelReadExpression cre = (ChannelReadExpression)e;
			Identifier id = cre.getIdentifier();
			extract_predicates(sp, chanContentsGuard(id));
			List<Expression> exprs = cre.getExprs();
			for (int i = 0; i < exprs.size(); i++) {
				try { // this is a conjunction of matchings
					final Expression expr = exprs.get(i);
					extract_predicates(sp, compare(PromelaConstants.EQ,
							channelTop(id, i), constant(expr.getConstantValue())));
		    	} catch (ParseException pe2) {}
			}
    	} else if (e instanceof ChannelOperation) {
			ChannelOperation co = (ChannelOperation)e;
			String name = co.getToken().image;
			Identifier id = (Identifier)co.getExpression();
			VariableType type = id.getVariable().getType();
			int buffer = ((ChannelType)type).getBufferSize();
			Expression left;
			try {
				left = new ChannelLengthExpression(null, id);
			} catch (ParseException e1) { throw new AssertionError(); }
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
			extract_predicates(sp, compare(op, left, right));
		} else if (e instanceof BooleanExpression) {
    		BooleanExpression ce = (BooleanExpression)e;
    		if (ce.getToken().kind == PromelaTokenManager.BAND ||
    			ce.getToken().kind == PromelaTokenManager.LAND) {
    			extract_predicates (sp, ce.getExpr1());
    			extract_predicates (sp, ce.getExpr2());
    		}
		}
	}

	/**
	 * Tries to parse an expression as a reference to a singular (channel)
	 * variable or a constant index in array variable (a cvarref).
	 */
	private static Identifier getConstantVar(Expression e) throws ParseException {
		if (e instanceof LTSminIdentifier) {
		} else if (e instanceof Identifier) {
			Identifier id = (Identifier)e;
			Variable var = id.getVariable();			
			Expression ar = id.getArrayExpr();
			if ((null == ar) != (-1 == var.getArraySize()))
				throw new AssertionError("Invalid array semantics in expression: "+ id);
			if (null != ar) { 
				try {
					ar = constant(ar.getConstantValue());
				} catch (ParseException pe) {} // do nothing. See getRef().
			}
			Identifier sub = null;
			if (null != id.getSub())
				sub = getConstantVar(id.getSub());
			return id(var, ar, sub);
		} else if (e instanceof ChannelLengthExpression)  {
			ChannelLengthExpression cle = (ChannelLengthExpression)e;
			Identifier id = (Identifier)cle.getExpression();
			return getConstantVar(chanLength(id));
		} else if (e instanceof ChannelTopExpression) {
			ChannelTopExpression cte = (ChannelTopExpression)e;
			Identifier id = cte.getChannelReadAction().getIdentifier();
			ChannelVariable cv = (ChannelVariable)id.getVariable();
			int size = cv.getType().getBufferSize();
			Expression sum = calc(PromelaConstants.PLUS, chanLength(id), chanRead(id));
			Expression mod = calc(PromelaConstants.MODULO, sum, constant(size));
			Identifier elem = id(elemVar(cte.getElem()));
			Identifier buf = id(bufferVar(cv), mod, elem);
			Identifier top = new Identifier(id, buf);
			return getConstantVar(top);
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
	    	throw new AssertionError("Serializing of expression "+ p1.id +" or "+ p2.id +" failed: "+ ae);
	    }
		if (ref1.equals(ref2)) { // syntactic matching
	        switch(p1.comparison) {
	            case PromelaConstants.LT:
	                // no conflict if one of these cases
	                no_conflict =
	                (p2.constant < p1.constant - 1) ||
	                (p2.constant == p1.constant - 1 && p2.comparison != PromelaConstants.GT) ||
	                (p2.comparison == PromelaConstants.LT || p2.comparison == PromelaConstants.LTE || p2.comparison == PromelaConstants.NEQ);
	                break;
	            case PromelaConstants.LTE:
	                // no conflict if one of these cases
	                no_conflict =
	                (p2.constant < p1.constant) ||
	                (p2.constant == p1.constant && p2.comparison != PromelaConstants.GT) ||
	                (p2.comparison == PromelaConstants.LT || p2.comparison == PromelaConstants.LTE || p2.comparison == PromelaConstants.NEQ);
	                break;
	            case PromelaConstants.EQ:
	                // no conflict if one of these cases
	                no_conflict =
	                (p2.constant == p1.constant && (p2.comparison == PromelaConstants.EQ || p2.comparison == PromelaConstants.LTE || p2.comparison == PromelaConstants.GTE)) ||
	                (p2.constant != p1.constant && p2.comparison == PromelaConstants.NEQ) ||
	                (p2.constant < p1.constant && p2.comparison == PromelaConstants.GT || p2.comparison == PromelaConstants.GTE) ||
	                (p2.constant > p1.constant && (p2.comparison == PromelaConstants.LT || p2.comparison == PromelaConstants.LTE));
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
	                (p2.comparison == PromelaConstants.GT || p2.comparison == PromelaConstants.GTE || p2.comparison == PromelaConstants.NEQ);
	                break;
	            case PromelaConstants.GTE:
	                // no conflict if one of these cases
	                no_conflict =
	                (p2.constant > p1.constant) ||
	                (p2.constant == p1.constant && p2.comparison != PromelaConstants.LT) ||
	                (p2.comparison == PromelaConstants.GT || p2.comparison == PromelaConstants.GTE || p2.comparison == PromelaConstants.NEQ);
	                break;
	        }
	    }
	    return !no_conflict;
	}

	static void walkTransitions(Params params) {
		for(LTSminTransition t : params.model.getTransitions()) {
			walkTransition(params,t);
			params.trans++;
		}
	}

	static void walkTransition(	Params params, LTSminTransition transition) {
		if(transition instanceof LTSminTransition) {
			LTSminTransition t = (LTSminTransition)transition;
			List<LTSminGuardBase> guards = t.getGuards();
			for(LTSminGuardBase g: guards)
				walkGuard(params, g);
		} else if (transition instanceof LTSminTransitionCombo) {
			LTSminTransitionCombo t = (LTSminTransitionCombo)transition;
			for(LTSminTransition tb : t.transitions)
				walkTransition(params,tb);
		} else {
			throw new AssertionError("UNSUPPORTED: " + transition.getClass().getSimpleName());
		}
	}

	static void walkGuard(Params params, LTSminGuardBase guard) {
		if(guard instanceof LTSminLocalGuard) { //Nothing
		} else if(guard instanceof LTSminGuard) {
			LTSminGuard g = (LTSminGuard)guard;
			params.guardMatrix.addGuard(params.trans, g);
		} else if(guard instanceof LTSminGuardNand) {
			LTSminGuardNand g = (LTSminGuardNand)guard;
			for(LTSminGuardBase gb : g.guards)
				walkGuard(params, gb);
		} else if(guard instanceof LTSminGuardAnd) {
			LTSminGuardAnd g = (LTSminGuardAnd)guard;
			for(LTSminGuardBase gb : g.guards)
				walkGuard(params,gb);
		} else if(guard instanceof LTSminGuardOr) {
			LTSminGuardOr g = (LTSminGuardOr)guard;
			for(LTSminGuardBase gb : g.guards)
				walkGuard(params,gb);
		} else {
			throw new AssertionError("UNSUPPORTED: " + guard.getClass().getSimpleName());
		}
	}
}

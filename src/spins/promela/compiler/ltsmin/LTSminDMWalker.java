package spins.promela.compiler.ltsmin;

import static spins.promela.compiler.ltsmin.state.LTSminStateVector._NR_PR;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.chanLength;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.channelBottom;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.channelIndex;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.channelNext;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.compare;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.constant;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.id;

import java.util.List;

import spins.promela.compiler.Proctype;
import spins.promela.compiler.actions.Action;
import spins.promela.compiler.actions.AssertAction;
import spins.promela.compiler.actions.AssignAction;
import spins.promela.compiler.actions.BreakAction;
import spins.promela.compiler.actions.ChannelReadAction;
import spins.promela.compiler.actions.ChannelSendAction;
import spins.promela.compiler.actions.ElseAction;
import spins.promela.compiler.actions.ExprAction;
import spins.promela.compiler.actions.GotoAction;
import spins.promela.compiler.actions.OptionAction;
import spins.promela.compiler.actions.PrintAction;
import spins.promela.compiler.actions.Sequence;
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
import spins.promela.compiler.expression.MTypeReference;
import spins.promela.compiler.expression.RemoteRef;
import spins.promela.compiler.expression.RunExpression;
import spins.promela.compiler.expression.TimeoutExpression;
import spins.promela.compiler.ltsmin.matrix.DepMatrix;
import spins.promela.compiler.ltsmin.matrix.LTSminGuard;
import spins.promela.compiler.ltsmin.matrix.LTSminGuardAnd;
import spins.promela.compiler.ltsmin.matrix.LTSminGuardBase;
import spins.promela.compiler.ltsmin.matrix.LTSminGuardContainer;
import spins.promela.compiler.ltsmin.matrix.LTSminLocalGuard;
import spins.promela.compiler.ltsmin.model.LTSminIdentifier;
import spins.promela.compiler.ltsmin.model.LTSminModel;
import spins.promela.compiler.ltsmin.model.LTSminTransition;
import spins.promela.compiler.ltsmin.model.ResetProcessAction;
import spins.promela.compiler.ltsmin.state.LTSminSlot;
import spins.promela.compiler.ltsmin.state.LTSminStateVector;
import spins.promela.compiler.ltsmin.state.LTSminSubVector;
import spins.promela.compiler.ltsmin.util.LTSminDebug;
import spins.promela.compiler.ltsmin.util.LTSminRendezVousException;
import spins.promela.compiler.parser.PromelaConstants;
import spins.promela.compiler.variable.ChannelType;
import spins.promela.compiler.variable.Variable;
import spins.promela.compiler.variable.VariableType;

/**
 * Traverses LTSminModel structure and records read/write dependencies of
 * variables (slots in LTSmin state vector) in a matrix.
 * 
 * The code heavily depends on the state vector's ability to be navigated upon
 * its type structure and subdivided into sub vectors.
 * 
 * @see LTSminSubVector
 * 
 * @see Blom, van de Pol, Weber - Bridging the Gap between Enumerative and Symbolic Model Checkers 
 *
 * @author FIB, Alfons Laarman
 */
public class LTSminDMWalker {

	// An expression without constant value, to be used as unknow array index: 
	static final Identifier STAR = new LTSminIdentifier(new Variable(VariableType.INT, "STAR", -1));
	
	static public class Params {
		public final LTSminModel model;
		public final LTSminStateVector sv;
		public final DepMatrix depMatrix;
		public int trans;
		public boolean inTimeOut = false;

		public Params(LTSminModel model, DepMatrix depMatrix, int trans) {
			this.model = model;
			this.depMatrix = depMatrix;
			this.trans = trans;
			this.sv = model.sv;
		}
	}

	public static void walkOneGuard(LTSminModel model, DepMatrix dm,
									LTSminGuardBase g, int num) {
		Params p = new Params(model, dm, num); 
		walkGuard (p, g);
	}
	
    public static void walkOneAction(LTSminModel model, DepMatrix dm,
                                     Action a, int num) {
         Params p = new Params(model, dm, num); 
         walkAction (p, a);
    }
	
	static void walkModel(LTSminModel model, LTSminDebug debug) {
		debug.say("Generating DM information ...");
		debug.say_indent++;

		if(model.getDepMatrix()==null) {
			model.setDepMatrix(new DepMatrix(model.getTransitions().size(), model.sv.size()));
		}
		Params params = new Params(model, model.getDepMatrix(), 0);
		walkTransitions(params);

		debug.say_indent--;
		debug.say("Generating DM information done");
		debug.say("");
	}

	static void walkTransitions(Params params) {
		for(LTSminTransition t: params.model.getTransitions()) {
			params.trans = t.getGroup();
			walkTransition(params,t);
		}
	}

	static void walkTransition(Params params, LTSminTransition t) {
		for(LTSminGuardBase g : t.getGuards())
			walkGuard(params,g);
		for(Action a : t.getActions())
			walkAction(params,a);
		for(LTSminTransition atomic : t.getTransitions()) {
			for(LTSminGuardBase g : atomic.getGuards())
				walkGuard(params,g);
			for(Action a : atomic.getActions()) {
				walkAction(params,a);
			}
		}
	}

	static void walkGuard(Params params, LTSminGuardBase guard) {
		if (guard instanceof LTSminLocalGuard) { // Nothing
		} else if(guard instanceof LTSminGuard) {
			LTSminGuard g = (LTSminGuard)guard;
			walkExpression(params, g.expr, MarkAction.READ);
		} else if(guard instanceof LTSminGuardContainer) {
			LTSminGuardContainer g = (LTSminGuardContainer)guard;
			for(LTSminGuardBase gb : g) {
				walkGuard(params,gb);
			}
		} else {
			throw new AssertionError("UNSUPPORTED: " + guard.getClass().getSimpleName());
		}
	}

	static void walkAction(Params params, Action a) {
		if(a instanceof AssignAction) {
			AssignAction as = (AssignAction)a;
			Identifier id = as.getIdentifier();
			walkExpression(params, id, MarkAction.WRITE); // read
			switch (as.getToken().kind) {
				case PromelaConstants.ASSIGN:
					walkExpression(params, as.getExpr(), MarkAction.READ); // read
					break;
				case PromelaConstants.DECR:
				case PromelaConstants.INCR:
					walkExpression(params, id, MarkAction.READ);
					break;
				default:
					throw new AssertionError("unknown assignment type");
			}
		} else if(a instanceof ResetProcessAction) {
			ResetProcessAction rpa = (ResetProcessAction)a;
			DMIncWritePC(params, rpa.getProcess());
			DMIncWrite(params, _NR_PR);
			DMIncRead(params, _NR_PR); // also done by guard, just to be sure
			DMIncWriteEntire(params, params.model.sv.sub(rpa.getProcess()));
		} else if(a instanceof AssertAction) {
			AssertAction as = (AssertAction)a;
			Expression e = as.getExpr();
			walkExpression(params,e, MarkAction.READ);
		} else if(a instanceof PrintAction) {
			PrintAction pa = (PrintAction)a;
			List<Expression> exprs = pa.getExprs();
			for (final Expression expr : exprs) {
				walkExpression(params,expr, MarkAction.READ);
			}
		} else if(a instanceof ExprAction) {
			ExprAction ea = (ExprAction)a;
			Expression expr = ea.getExpression();
			if (expr.getSideEffect() == null) return; // simple expressions are guards
			//a RunExpression has side effects... yet it does not block if less than 255 processes are started atm
			assert (expr instanceof RunExpression);
			DMIncWrite(params, _NR_PR);
			DMIncRead(params, _NR_PR);
			RunExpression re = (RunExpression)expr;
			for (Proctype p : re.getInstances()) {
				DMIncWritePC(params, p);
				DMIncReadPC(params, p); // we also read to check multiple instantiations
				DMIncWritePID(params, p);
				//write to the arguments of the target process
				for (Variable v : p.getArguments()) {
					if (v.getType() instanceof ChannelType || v.isStatic())
						continue; //passed by reference or in initial state 
					DMIncWrite(params, v);
				}
			}
			for (Action rea : re.getActions()) {
				walkAction(params, rea);
			}
		} else if(a instanceof OptionAction) { // options in a d_step sequence
			OptionAction oa = (OptionAction)a;
			LTSminGuardAnd orc = new LTSminGuardAnd();
			for (Sequence seq : oa) {
				Action act = seq.iterator().next();
				try {
                    LTSminTreeWalker.createEnabledGuard(act, orc);
                } catch (LTSminRendezVousException e) {
                    throw new AssertionError(e);
                }
				for (Action sa : seq) {
					walkAction(params, sa);
				}
			}
			walkGuard(params, orc);
		} else if(a instanceof BreakAction) {
			// noop
		} else if(a instanceof ElseAction) {
			// noop
		} else if(a instanceof GotoAction) { // only in d_steps (not a transition)
			// noop
		} else if(a instanceof ChannelSendAction) {
			ChannelSendAction csa = (ChannelSendAction)a;			
			Identifier id = csa.getIdentifier();
			int m = 0;
			for (Expression e : csa.getExprs()) {
				Expression top = channelNext(id, m++);
				walkExpression(params, top, MarkAction.EWRITE);
				walkExpression(params, e, MarkAction.EREAD);
			}
			walkExpression(params, chanLength(id), MarkAction.BOTH);
		} else if(a instanceof ChannelReadAction) {
			ChannelReadAction cra = (ChannelReadAction)a;			
			Identifier id = cra.getIdentifier();
			int m = 0;
			// the read action itself:
			for (Expression e : cra.getExprs()) {
				Expression read = cra.isRandom() ? channelIndex(id, STAR, m) :
									channelBottom(id, m);
				if (e instanceof Identifier) { // otherwise it is a guard!
					walkExpression(params, e, MarkAction.EWRITE);
					walkExpression(params, read, MarkAction.EREAD);
				}
				m++;
			}
			if (!cra.isPoll()) {
				walkExpression(params, chanLength(id), MarkAction.BOTH);
				// the replacement action:
				m = 0;
				for (@SuppressWarnings("unused") Expression e : cra.getExprs()) {
					Expression buf = channelIndex(id, STAR, m++);
					walkExpression(params, buf, MarkAction.EBOTH);
				}
			}
		} else { // Handle not yet implemented action
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+a.getClass().getName());
		}
	}
	
	/**
	 * Uses Identifier expressions to walk over LTSmin sub vectors. Complete
	 * identifiers, i.e. those that point to (an index of) a native type,
	 * will be marked in the DM using the associated MarkAction. Incomplete ones
	 * throw an AssertionError.
	 */
	public static class IdMarker {
		MarkAction mark;
		Params params;
		public IdMarker(Params params, MarkAction mark) {
			this.params = params;
			this.mark = mark;
		}
		public IdMarker(IdMarker idMarker, MarkAction mark) {
			this.params = idMarker.params;
			this.mark = mark;
		}

		public void mark(Expression e) {
			if (null == e) return;
			if (e instanceof LTSminIdentifier) {
			} else if (e instanceof Identifier) {
				Identifier id = (Identifier)e;
				if (id.getVariable().isHidden())
					return;
				LTSminSubVector sub = params.sv.sub(id.getVariable());
				try {
					sub.mark(this, id);
				} catch (AssertionError ae) {
					throw new AssertionError("Marking expression "+ id +" failed with: "+ ae);
				}
			} else {
				walkExpression(params, e, mark);
			}
		}

		public void doMark(LTSminSubVector sub) {
			mark.DMIncMark(params, sub);
		}
        public boolean isStrict() {
            return mark.strict;
        }
	}

	/**
	 * A marker for SLOTS in the state vector: READ, WRITE or BOTH.
	 */
	public static enum	MarkAction {
	    
		READ(true),
		WRITE(true),
		BOTH(true),
		EREAD(false),
        EWRITE(false),
        EBOTH(false);

		boolean strict;
        MarkAction(boolean strict) {
            this.strict = strict;
        }
        
		private void DMIncMark(Params params, LTSminSubVector sub) {
			switch (this) {
			case EREAD:
			case READ: DMIncRead (params, sub); break;
			case EWRITE:
			case WRITE:DMIncWrite(params, sub); break;
			case EBOTH:
			case BOTH: DMIncRead (params, sub);
					   DMIncWrite(params, sub); break;
			default: throw new AssertionError("Not implemented "+ this);
			}
		}
	}

	static void walkExpression(Params params, Expression e, MarkAction mark) {
		if ((mark == MarkAction.WRITE || mark == MarkAction.EWRITE) && !(e instanceof Identifier))
			throw new AssertionError("Only identifiers and TopExpressions can be written to!");
		if(e instanceof LTSminIdentifier) { //nothing
		} else if(e instanceof Identifier) {
			new IdMarker(params, mark).mark(e);
		} else if(e instanceof AritmicExpression) {
			AritmicExpression ae = (AritmicExpression)e;
			Expression ex1 = ae.getExpr1();
			Expression ex2 = ae.getExpr2();
			Expression ex3 = ae.getExpr3();
			if (ex2 == null) {
				walkExpression(params,ex1, mark);
			} else if (ex3 == null) {
				if (ae.getToken().image.equals("%")) {
					// Modulo takes a special notation to make sure that it
					// returns a positive value
					walkExpression(params,ex1, mark);
					walkExpression(params,ex2, mark);
				} else {
					walkExpression(params,ex1, mark);
					walkExpression(params,ex2, mark);
				}
			} else {
				walkExpression(params,ex1, mark);
				walkExpression(params,ex2, mark);
				walkExpression(params,ex3, mark);
			}
		} else if(e instanceof BooleanExpression) {
			BooleanExpression be = (BooleanExpression)e;
			Expression ex1 = be.getExpr1();
			Expression ex2 = be.getExpr2();
			if (ex2 == null) {
				walkExpression(params,ex1, mark);
			} else {
				walkExpression(params,ex1, mark);
				walkExpression(params,ex2, mark);
			}
		} else if(e instanceof CompareExpression) {
			CompareExpression ce = (CompareExpression)e;
			walkExpression(params,ce.getExpr1(), mark);
			walkExpression(params,ce.getExpr2(), mark);
		} else if(e instanceof ChannelLengthExpression) {
			ChannelLengthExpression cle = (ChannelLengthExpression)e;
			Identifier id = (Identifier)cle.getExpression();
			walkExpression(params, chanLength(id), mark);
		} else if(e instanceof ChannelReadExpression) {
			ChannelReadExpression cre = (ChannelReadExpression)e;
			Identifier id = cre.getIdentifier();
			// mark variables as read
			int m = 0;
			for (Expression expr : cre.getExprs()) {
				Expression read = cre.isRandom() ? channelIndex(id, STAR, m) :
													channelBottom(id, m);
				walkExpression(params, read, mark);
				walkExpression(params, expr, mark);
				m++;
			}
			walkExpression(params, chanLength(id), mark);
		} else if(e instanceof ChannelOperation) {
			ChannelOperation co = (ChannelOperation)e;
			Identifier id = (Identifier)co.getExpression();
			ChannelType ct = (ChannelType)id.getVariable().getType();
			if (!ct.isRendezVous()) // chanops on rendez-vous return true
				walkExpression(params, chanLength(id), mark);
		} else if(e instanceof RunExpression) {
			DMIncRead(params, _NR_PR); // only the guard!
		} else if(e instanceof MTypeReference) {
		} else if(e instanceof ConstantExpression) {
        } else if (e instanceof TimeoutExpression) {
            throw new AssertionError("TimeoutExpression not implemented");
            /*
            if (params.inTimeOut) return; // avoid recursion
            params.inTimeOut = true;
            for (LTSminTransition t : params.model.getTransitions()) {
                if (t.getGroup() == params.trans) continue;
                for (LTSminGuardBase g : t.getGuards()) {
                    walkGuard(params, g);
                }
            }
            params.inTimeOut = false;*/
		} else if (e instanceof RemoteRef) {
			RemoteRef rr = (RemoteRef)e;
			Variable pc = rr.getPC(params.model);
			int num = rr.getLabelId();
			Expression comp = compare(PromelaConstants.EQ, id(pc), constant(num));
			walkExpression(params, comp, mark);
		} else if(e instanceof EvalExpression) {
			EvalExpression eval = (EvalExpression)e;
			walkExpression(params, eval.getExpression(), mark);
		} else if(e instanceof TimeoutExpression) {
			throw new AssertionError("LTSminDMWalker: Not yet implemented: "+e.getClass().getName());
		} else {
			throw new AssertionError("LTSminDMWalker: Not yet implemented: "+e.getClass().getName());
		}
	}

	static void DMIncWrite(Params params, LTSminSubVector sub) {
		if (!(sub instanceof LTSminSlot))
			throw new AssertionError("Variable is not a native type: "+ sub);
		int offset = ((LTSminSlot)sub).getIndex();
		params.depMatrix.incWrite(params.trans, offset);
	}

	static void DMIncRead(Params params, LTSminSubVector sub) {
		if (!(sub instanceof LTSminSlot))
			throw new AssertionError("Variable is not a native type: "+ sub);
		int offset = ((LTSminSlot)sub).getIndex();
		params.depMatrix.incRead(params.trans, offset);
	}

	static void DMIncWriteEntire(Params params, LTSminSubVector sub) {
		for (LTSminSlot slot : sub) {
			DMIncWrite(params, slot);
		}
	}

	private static void DMIncReadPC(Params params, Proctype p) {
		Variable pc = params.sv.getPC(p);
		DMIncRead(params, pc);
	}

	private static void DMIncWritePC(Params params, Proctype p) {
		Variable pc = params.sv.getPC(p);
		DMIncWrite(params, pc);
	}

	private static void DMIncWritePID(Params params, Proctype p) {
		Variable pc = params.sv.getPID(p);
		DMIncWrite(params, pc);
	}
	
	private static void DMIncRead(Params params, Variable var) {
		walkExpression(params, id(var), MarkAction.READ);
	}

	private static void DMIncWrite(Params params, Variable var) {
		walkExpression(params, id(var), MarkAction.WRITE);
	}
}

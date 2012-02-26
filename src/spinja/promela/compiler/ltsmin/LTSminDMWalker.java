package spinja.promela.compiler.ltsmin;

import static spinja.promela.compiler.ltsmin.LTSminStateVector._NR_PR;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import spinja.promela.compiler.Proctype;
import spinja.promela.compiler.actions.Action;
import spinja.promela.compiler.actions.AssertAction;
import spinja.promela.compiler.actions.AssignAction;
import spinja.promela.compiler.actions.ChannelReadAction;
import spinja.promela.compiler.actions.ChannelSendAction;
import spinja.promela.compiler.actions.ElseAction;
import spinja.promela.compiler.actions.ExprAction;
import spinja.promela.compiler.actions.OptionAction;
import spinja.promela.compiler.actions.PrintAction;
import spinja.promela.compiler.actions.Sequence;
import spinja.promela.compiler.expression.AritmicExpression;
import spinja.promela.compiler.expression.BooleanExpression;
import spinja.promela.compiler.expression.ChannelLengthExpression;
import spinja.promela.compiler.expression.ChannelOperation;
import spinja.promela.compiler.expression.ChannelReadExpression;
import spinja.promela.compiler.expression.CompareExpression;
import spinja.promela.compiler.expression.CompoundExpression;
import spinja.promela.compiler.expression.ConstantExpression;
import spinja.promela.compiler.expression.EvalExpression;
import spinja.promela.compiler.expression.Expression;
import spinja.promela.compiler.expression.Identifier;
import spinja.promela.compiler.expression.MTypeReference;
import spinja.promela.compiler.expression.RunExpression;
import spinja.promela.compiler.expression.TimeoutExpression;
import spinja.promela.compiler.ltsmin.instr.ChannelSizeExpression;
import spinja.promela.compiler.ltsmin.instr.ChannelTopExpression;
import spinja.promela.compiler.ltsmin.instr.DepMatrix;
import spinja.promela.compiler.ltsmin.instr.ResetProcessAction;
import spinja.promela.compiler.parser.ParseException;
import spinja.promela.compiler.parser.PromelaConstants;
import spinja.promela.compiler.variable.ChannelType;
import spinja.promela.compiler.variable.ChannelVariable;
import spinja.promela.compiler.variable.Variable;

/**
 *
 * @author FIB
 */
public class LTSminDMWalker {

	static public class Params {
		public final LTSminModel model;
		public final LTSminStateVector sv;
		public final DepMatrix depMatrix;
		public int trans;

		public Params(LTSminModel model, DepMatrix depMatrix, int trans) {
			this.model = model;
			this.depMatrix = depMatrix;
			this.trans = trans;
			this.sv = model.sv;
		}
	}

	static void walkModel(LTSminModel model) {
		if(model.getDepMatrix()==null) {
			model.setDepMatrix(new DepMatrix(model.getTransitions().size(), model.sv.size()));
		}
		Params params = new Params(model, model.getDepMatrix(), 0);
		walkTransitions(params);
	}

	static void walkTransitions(Params params) {
		for(LTSminTransitionBase t: params.model.getTransitions()) {
			walkTransition(params,t);
			params.trans++;
		}
	}

	static void walkTransition(	Params params, LTSminTransitionBase transition) {
		if(transition instanceof LTSminTransition) {
			LTSminTransition t = (LTSminTransition)transition;
			List<LTSminGuardBase> guards = t.getGuards();
			for(LTSminGuardBase g: guards) {
				walkGuard(params,g);
			}
			List<Action> actions = t.getActions();
			for(Action a: actions) {
				walkAction(params,a);
			}
		} else if (transition instanceof LTSminTransitionCombo) {
			LTSminTransitionCombo t = (LTSminTransitionCombo)transition;
			for(LTSminTransitionBase tb: t.transitions) {
				walkTransition(params,tb);
			}
		} else {
			throw new AssertionError("UNSUPPORTED: " + transition.getClass().getSimpleName());
		}
	}

	static void walkGuard(Params params, LTSminGuardBase guard) {
		if (guard instanceof LTSminLocalGuard) { // Nothing
		} else if(guard instanceof LTSminGuard) {
			LTSminGuard g = (LTSminGuard)guard;
			walkExpression(params, g.expr);
		} else if(guard instanceof LTSminGuardNand) {
			LTSminGuardNand g = (LTSminGuardNand)guard;
			for(LTSminGuardBase gb: g.guards) {
				walkGuard(params,gb);
			}
		} else if(guard instanceof LTSminGuardAnd) {
			LTSminGuardAnd g = (LTSminGuardAnd)guard;
			for(LTSminGuardBase gb: g.guards) {
				walkGuard(params,gb);
			}
		} else if(guard instanceof LTSminGuardOr) {
			LTSminGuardOr g = (LTSminGuardOr)guard;
			for(LTSminGuardBase gb: g.guards) {
				walkGuard(params,gb);
			}
		} else {
			throw new AssertionError("UNSUPPORTED: " + guard.getClass().getSimpleName());
		}
	}

	/**
	 * Also sets constantValues to identifiers, where due.
	 * 
	 * @param params
	 * @param a
	 */
	static void walkAction(Params params, Action a) {
		if(a instanceof AssignAction) {
			AssignAction as = (AssignAction)a;
			Identifier id = as.getIdentifier();
			DMAssign(params,id);
			switch (as.getToken().kind) {
				case PromelaConstants.ASSIGN:
					walkExpression(params,as.getExpr()); // read
					break;
				case PromelaConstants.DECR:
				case PromelaConstants.INCR:
					walkExpression(params,id);
					break;
				default:
					throw new AssertionError("unknown assignment type");
			}
		} else if(a instanceof ResetProcessAction) {
			ResetProcessAction rpa = (ResetProcessAction)a;
			DMIncWrite(params, rpa.getProcVar(),0);
			DMIncWrite(params, _NR_PR, 0);
			DMIncRead(params, _NR_PR, 0); // also done by guard, just to be sure
			for(Variable v : rpa.getProcess().getVariables()) {
				DMIncWriteEntire(params, v);
			}
		} else if(a instanceof AssertAction) {
			AssertAction as = (AssertAction)a;
			Expression e = as.getExpr();
			walkExpression(params,e);
		} else if(a instanceof PrintAction) {
			PrintAction pa = (PrintAction)a;
			List<Expression> exprs = pa.getExprs();
			for (final Expression expr : exprs) {
				walkExpression(params,expr);
			}
		} else if(a instanceof ExprAction) {
			ExprAction ea = (ExprAction)a;
			Expression expr = ea.getExpression();
			String sideEffect = null;
			try {
				sideEffect = expr.getSideEffect();
			} catch (ParseException e) {
				e.printStackTrace();
			}
			if (sideEffect != null) {
				//a RunExpression has side effects... yet it does not block if less than 255 processes are started atm
				assert (expr instanceof RunExpression);
				DMIncWrite(params, _NR_PR, 0);
				DMIncRead(params, _NR_PR, 0);
				RunExpression re = (RunExpression)expr;
				Iterator<Expression> rei = re.getExpressions().iterator();
				Proctype p = re.getSpecification().getProcess(re.getId());
				DMIncWrite(params,params.sv.getPC(p),0);
				DMIncRead(params,params.sv.getPC(p),0); // we also read to check multiple instantiations
				DMIncWrite(params,params.sv.getPID(p),0);
				//write to the arguments of the target process
				for (Variable v : p.getArguments()) {
					Expression next = rei.next();
					if (v.getType() instanceof ChannelType) continue; //passed by reference
					DMIncWrite(params, v, 0);
					if (v.isNotAssignedTo()) {
						try {
							v.setConstantValue(next.getConstantValue());
						} catch (ParseException e) {} // expected
					}
				}
			} else {
				// simple expressions are guards
			}
		} else if(a instanceof OptionAction) { // options in a d_step sequence
			OptionAction oa = (OptionAction)a;
			for (Sequence seq : oa) {
				Action ea = seq.iterator().next();
				try {
					walkExpression(params, ((ExprAction)ea).getExpression());
				} catch (ClassCastException e) {
					assert (ea instanceof ElseAction);
				}
				for (Action act : seq) {
					if (act != ea) {
						walkAction(params, act);
					}
				}
			}
		} else if(a instanceof ElseAction) {
			// noop
		} else if(a instanceof ChannelSendAction) {
			ChannelSendAction csa = (ChannelSendAction)a;			
			Identifier id = csa.getIdentifier();

			// mark variables as read
			for (Expression e : csa.getExprs())
				walkExpression(params, e);

			markChannel(params, id, BufferAction.SEND);
		} else if(a instanceof ChannelReadAction) {
			ChannelReadAction cra = (ChannelReadAction)a;			
			Identifier id = cra.getIdentifier();

			// mark variables as write
			markLHS(params, cra.getExprs(), (ChannelVariable)id.getVariable());

			markChannel(params, id, BufferAction.READ);
		} else { // Handle not yet implemented action
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+a.getClass().getName());
		}
	}

	public static void walkOneGuard(LTSminModel model, DepMatrix dm,
									LTSminGuard g, int num) {
		Params p = new Params(model, dm, num); 
		walkExpression(p, g.expr);
	}
	
	static void walkExpression(Params params, Expression e) {
		if(e instanceof LTSminIdentifier) { //nothing
		} else if(e instanceof Identifier) {
			Identifier id = (Identifier)e;
			Variable var = id.getVariable();
			Expression arrayExpr = id.getArrayExpr();
			if (arrayExpr != null) {
				walkExpression(params,arrayExpr);
				try {
					int i = arrayExpr.getConstantValue();
					DMIncRead(params, var, i);
				} catch(ParseException pe) {
					for(int i=0; i<var.getArraySize(); ++i) {
						DMIncRead(params, var, i);
					}
				}
			} else {
				DMIncRead(params, var, 0);
			}
		} else if(e instanceof AritmicExpression) {
			AritmicExpression ae = (AritmicExpression)e;
			Expression ex1 = ae.getExpr1();
			Expression ex2 = ae.getExpr2();
			Expression ex3 = ae.getExpr3();
			if (ex2 == null) {
				walkExpression(params,ex1);
			} else if (ex3 == null) {
				if (ae.getToken().image.equals("%")) {
					// Modulo takes a special notation to make sure that it
					// returns a positive value
					walkExpression(params,ex1);
					walkExpression(params,ex2);
				} else {
					walkExpression(params,ex1);
					walkExpression(params,ex2);
				}
			} else {
				walkExpression(params,ex1);
				walkExpression(params,ex2);
				walkExpression(params,ex3);
			}
		} else if(e instanceof BooleanExpression) {
			BooleanExpression be = (BooleanExpression)e;
			Expression ex1 = be.getExpr1();
			Expression ex2 = be.getExpr2();
			if (ex2 == null) {
				walkExpression(params,ex1);
			} else {
				walkExpression(params,ex1);
				walkExpression(params,ex2);
			}
		} else if (e instanceof ChannelSizeExpression) {
			ChannelSizeExpression cse = (ChannelSizeExpression)e;
			markChannel(params, cse.getIdentifier(), BufferAction.NONE);
		} else if(e instanceof ChannelLengthExpression) {
			ChannelLengthExpression cle = (ChannelLengthExpression)e;
			Identifier id = (Identifier)cle.getExpression();
			markChannel(params, id, BufferAction.NONE);
		} else if(e instanceof ChannelReadExpression) {
			ChannelReadExpression cre = (ChannelReadExpression)e;
			// mark variables as read
			for (Expression expr : cre.getExprs())
				walkExpression(params, expr);
			markChannel(params, cre.getIdentifier(), BufferAction.SCAN);
		} else if(e instanceof ChannelOperation) {
			ChannelOperation co = (ChannelOperation)e;
			markChannel(params, (Identifier)co.getExpression(), BufferAction.NONE);
		} else if(e instanceof CompareExpression) {
			CompareExpression ce = (CompareExpression)e;
			walkExpression(params,ce.getExpr1());
			walkExpression(params,ce.getExpr2());
		} else if(e instanceof RunExpression) {
			DMIncRead(params, _NR_PR, 0);
		} else if(e instanceof CompoundExpression) {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else if(e instanceof MTypeReference) {
		} else if(e instanceof ConstantExpression) {
		} else if(e instanceof ChannelTopExpression) {
			ChannelTopExpression cte = (ChannelTopExpression)e;
			markChannel(params, cte.getChannelReadAction().getIdentifier(), BufferAction.SCAN); //safe overapproximation, since on topExpr is made for each each element.
		} else if(e instanceof EvalExpression) {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else if(e instanceof RunExpression) {
			throw new AssertionError("LTSMinPrinter: Not yet implemented as expression: "+e.getClass().getName());
		} else if(e instanceof TimeoutExpression) {
			DMIncReadAll(params); // should be optimized
		} else {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		}
	}

	public enum BufferAction {
		NONE,
		SEND,
		READ,
		SCAN
	}
	
	private static void markChannel(Params params, Identifier id,
									BufferAction action) {
		ChannelVariable var = (ChannelVariable)id.getVariable();
		Expression arrayExpr = id.getArrayExpr();
		try {
			if (arrayExpr != null)
				walkExpression(params, arrayExpr);
			else
				throw new ParseException();
			int i = arrayExpr.getConstantValue();
			// mark channel meta data
			DMIncRead (params, var, i);
			if (BufferAction.NONE != action) {
				// mark channel meta data write
				if (BufferAction.SCAN != action)
					DMIncWrite(params, var, i);
				// mark entire buffer as read (no way to establish current loc)
				markBuffer(params, var, i, BufferAction.SEND == action);
			}
		} catch(ParseException pe) {
			for(int i=0; i<var.getArraySize(); ++i) {
				// mark channel meta data
				DMIncRead (params, var, i);
				if (BufferAction.NONE != action) {
					// mark channel meta data write
					if (BufferAction.SCAN != action)
						DMIncWrite(params, var, i);
					// mark entire buffer as read (no way to establish current loc)
					markBuffer(params, var, i, BufferAction.SEND == action);
				}
			}
		}
	}

	private static void markBuffer(Params params, ChannelVariable var, int idx,
									boolean send) {
		int offset = var.getArraySize(); // channel's meta info
		int bsize = var.getType().getBufferSize();
		int tsize = var.getType().getTypes().size();
		for (int i = 0; i < bsize; i++) {
			for (int j = 0; j < tsize; j++) {
				if (send) {
					DMIncWrite (params,var, idx*bsize*tsize + i*tsize + j + offset);
				} else {
					DMIncRead (params,var, idx*bsize*tsize + i*tsize + j + offset);
				}
			}
		}
	}

	private static void markLHS(Params params, Collection<Expression> exprs,
								ChannelVariable var) {
		for (Expression expr : exprs) {
			if (expr instanceof Identifier) {
				DMAssign (params, (Identifier)expr);
			}
		}
	}

	static void DMIncWrite(Params params, Variable var, int offset) {
		Integer i = params.model.getVariables().get(var);
		if(i==null) {
			System.out.println("For some reason var is null, " + var.getName());
			System.out.println("Vars: " + params.model.getVariables().toString());
		} else {
			params.depMatrix.incWrite(params.trans, i+offset);
		}
	}

	static void DMIncRead(Params params, Variable var, int offset) {
		Integer i = params.model.getVariables().get(var);
		if(i==null) {
			System.out.println("For some reason var is null, " + var.getName());
			System.out.println("Vars: " + params.model.getVariables().toString());
		} else {
			params.depMatrix.incRead(params.trans, i+offset);
		}
	}

	static void DMIncReadAll(Params params) {
		for(int i=params.model.sv.size(); i-->0;) {
			params.depMatrix.incRead(params.trans, i);
		}
	}

	static void DMIncWriteEntire(Params params, Variable var) {
		if (var.getArraySize() > 1) {
			for(int i=0; i<var.getArraySize(); ++i) {
				DMIncWrite(params,var,i);
			}
		} else {
			DMIncWrite(params,var,0);
		}
	}

	static void DMAssign(Params params, Identifier id) {
		Variable var = id.getVariable();
		Expression arrayExpr = id.getArrayExpr();
		if (var.getArraySize() > 1) {
			if (arrayExpr != null) {
				walkExpression(params, arrayExpr);
				try {
					int i = arrayExpr.getConstantValue();
					DMIncWrite(params,var,i);
				} catch(ParseException pe) {
					DMIncWriteEntire (params, var);
				}
			} else {
				DMIncWrite(params,var,0);
			}
		} else {
			DMIncWrite(params,var,0);
		}
	}
}

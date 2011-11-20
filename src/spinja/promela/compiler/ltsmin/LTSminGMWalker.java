package spinja.promela.compiler.ltsmin;

import java.util.List;

import spinja.promela.compiler.Proctype;
import spinja.promela.compiler.actions.Action;
import spinja.promela.compiler.actions.AssertAction;
import spinja.promela.compiler.actions.AssignAction;
import spinja.promela.compiler.actions.ChannelReadAction;
import spinja.promela.compiler.actions.ChannelSendAction;
import spinja.promela.compiler.actions.ExprAction;
import spinja.promela.compiler.actions.PrintAction;
import spinja.promela.compiler.expression.AritmicExpression;
import spinja.promela.compiler.expression.BooleanExpression;
import spinja.promela.compiler.expression.ChannelLengthExpression;
import spinja.promela.compiler.expression.ChannelOperation;
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
import spinja.promela.compiler.ltsmin.instr.GuardMatrix;
import spinja.promela.compiler.ltsmin.instr.PCExpression;
import spinja.promela.compiler.ltsmin.instr.PCIdentifier;
import spinja.promela.compiler.ltsmin.instr.PriorityExpression;
import spinja.promela.compiler.ltsmin.instr.PriorityIdentifier;
import spinja.promela.compiler.ltsmin.instr.ResetProcessAction;
import spinja.promela.compiler.parser.ParseException;
import spinja.promela.compiler.parser.PromelaConstants;
import spinja.promela.compiler.parser.Token;
import spinja.promela.compiler.variable.ChannelVariable;
import spinja.promela.compiler.variable.Variable;
import spinja.promela.compiler.variable.VariableAccess;
import spinja.promela.compiler.ltsmin.LTSMinPrinter.*;

/**
 *
 * @author FIB
 */
public class LTSminGMWalker {

	static public class Params {
		public final LTSminModel model;
		public final GuardMatrix guardMatrix;
		public final DepMatrix depMatrix;
		public final int trans;
		public final int guard;

		public Params(LTSminModel model, GuardMatrix guardMatrix, DepMatrix depMatrix, int trans, int guard) {
			this.model = model;
			this.guardMatrix = guardMatrix;
			this.depMatrix = depMatrix;
			this.trans = trans;
			this.guard = guard;
		}
	}

	static void walkModel(LTSminModel model) {
		if(model.getGuardMatrix()==null) {
			model.setGuardMatrix(new GuardMatrix(model.getStateVector().size()));
		}
		walkTransitions(model.getGuardMatrix(),model);
	}

	static void walkTypeDef(LTSminType type) {
	}
	static void walkType(LTSminType type) {
	}

	static void walkHeader(LTSminModel model) {
	}

	static void walkStateCount(LTSminModel model) {
	}

	static private void walkIsAtomic() {
	}

	static void walkTransitionCount(LTSminModel model) {
	}

	static void walkForwardDeclarations() {
	}

	static void walkInitialState(LTSminModel model) {
	}
	static void walkGetAll(LTSminModel model) {
	}
	static void walkTransitions(GuardMatrix guardMatrix, LTSminModel model) {
		List<LTSminTransitionBase> transitions = model.getTransitions();
		int trans = 0;
		DepMatrix dm = new DepMatrix(1,model.getStateVector().size());
		guardMatrix.setDepMatrix2(dm);
		for(LTSminTransitionBase t: transitions) {
			walkTransition(new Params(model,guardMatrix,dm,trans,-1),t);
			++trans;
		}

	}

	static void walkTransition(	Params params, LTSminTransitionBase transition) {
		if(transition instanceof LTSminTransition) {
			LTSminTransition t = (LTSminTransition)transition;
			List<LTSminGuardBase> guards = t.getGuards();
			for(LTSminGuardBase g: guards) {
				walkGuard(params,g);
			}
//			List<Action> actions = t.getActions();
//			for(Action a: actions) {
//				walkAction(params,a);
//			}
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
		if(guard instanceof LTSminGuard) {
			int gidx = params.guard>=0 ? params.guard : params.guardMatrix.addGuard(params.trans, guard);
			params.depMatrix.ensureSize(gidx+1);
			System.out.println("Now handling1 " + gidx);
			LTSminGuard g = (LTSminGuard)guard;
			walkBoolExpression(new Params(params.model, params.guardMatrix, params.depMatrix, params.trans, gidx), g.expr);
		} else if(guard instanceof LTSminGuardNand) {
			int gidx = params.guard>=0 ? params.guard : params.guardMatrix.addGuard(params.trans, guard);
			params.depMatrix.ensureSize(gidx+1);
			System.out.println("Now handling2 " + gidx);
			LTSminGuardNand g = (LTSminGuardNand)guard;
			for(LTSminGuardBase gb: g.guards) {
				walkGuard(new Params(params.model, params.guardMatrix, params.depMatrix, params.trans, gidx),gb);
			}
		} else if(guard instanceof LTSminGuardAnd) {
			LTSminGuardAnd g = (LTSminGuardAnd)guard;
			for(LTSminGuardBase gb: g.guards) {
				walkGuard(params,gb);
			}
		} else if(guard instanceof LTSminGuardOr) {
			int gidx = params.guard>=0 ? params.guard : params.guardMatrix.addGuard(params.trans, guard);
			params.depMatrix.ensureSize(gidx+1);
			System.out.println("Now handling3 " + gidx);
			LTSminGuardOr g = (LTSminGuardOr)guard;
			for(LTSminGuardBase gb: g.guards) {
				walkGuard(new Params(params.model, params.guardMatrix, params.depMatrix, params.trans, gidx),gb);
			}
		} else {
			throw new AssertionError("UNSUPPORTED: " + guard.getClass().getSimpleName());
		}
	}
	static void walkAction(Params params, Action a) {
		// Handle assignment action
		if(a instanceof AssignAction) {
			AssignAction as = (AssignAction)a;
			Identifier id = as.getIdentifier();
			final String mask = id.getVariable().getType().getMask();
			switch (as.getToken().kind) {
				case PromelaConstants.ASSIGN:
					try {
						int value = as.getExpr().getConstantValue();
						walkIntExpression(params,id); // assign
					} catch (ParseException ex) {
						// Could not get Constant value
						walkIntExpression(params,id); // assign
						walkIntExpression(params,as.getExpr()); // read
					}
					break;
				case PromelaConstants.INCR:
					if (mask == null) {
						walkIntExpression(params,id); // assign and read
					} else {
						walkIntExpression(params,id); // assign
						walkIntExpression(params,id); // read
					}
					break;
				case PromelaConstants.DECR:
					if (mask == null) {
						walkIntExpression(params,id); // assign and read
					} else {
						walkIntExpression(params,id); // assign
						walkIntExpression(params,id); // read
					}
					break;
				default:
					throw new AssertionError("unknown assignment type");
			}
			DMAssign(params,id);

		} else if(a instanceof ResetProcessAction) {
			ResetProcessAction rpa = (ResetProcessAction)a;
			DMIncWrite(params, rpa.getProcVar(),0);
			for(Variable v: rpa.getProcess().getVariables()) {
				DMIncWriteEntire(params, v);
			}

		} else if(a instanceof ResetProcessAction) {
			ResetProcessAction rpa = (ResetProcessAction)a;
			rpa.getProcess();

		// Handle assert action
		} else if(a instanceof AssertAction) {
			AssertAction as = (AssertAction)a;
			Expression e = as.getExpr();

			walkBoolExpression(params,e);

		// Handle print action
		} else if(a instanceof PrintAction) {
			PrintAction pa = (PrintAction)a;
			String string = pa.getString();
			List<Expression> exprs = pa.getExprs();
			for (final Expression expr : exprs) {
				walkIntExpression(params,expr);
			}

		// Handle expression action
		} else if(a instanceof ExprAction) {
			ExprAction ea = (ExprAction)a;
			Expression expr = ea.getExpression();
			String sideEffect;
			try {
				sideEffect = expr.getSideEffect();
				if (sideEffect != null) {
					//a RunExpression has side effects... yet it does not block if less than 255 processes are started atm
					assert (expr instanceof RunExpression);
					DMIncWrite(params, LTSMinPrinter._NR_PR, 0);
					RunExpression re = (RunExpression)expr;
				
					//write to the arguments of the target process
					for (VariableAccess va: re.readVariables()) {
						Variable v = va.getVar();
						DMIncWrite(params, v, 0);
					}
				}
			} catch (ParseException e) {
				e.printStackTrace();
			}
		// Handle channel send action
		} else if(a instanceof ChannelSendAction) {
			ChannelSendAction csa = (ChannelSendAction)a;
			ChannelVariable var = (ChannelVariable)csa.getVariable();

			if(var.getType().getBufferSize()>0) {
				// Dependency matrix: channel variable
				DMIncRead(params,var,0);
				DMIncWrite(params,var,0);

				List<Expression> exprs = csa.getExprs();
				for (int i = 0; i < exprs.size(); i++) {
					final Expression expr = exprs.get(i);
					walkIntExpression(params, expr);

					// Dependency matrix: channel variable
					DMIncWrite(params,var,i+1);
				}

			} else {
				throw new AssertionError("Trying to actionise rendezvous send!");
			}

		// Handle a channel read action
		} else if(a instanceof ChannelReadAction) {
			ChannelReadAction cra = (ChannelReadAction)a;
			ChannelVariable var = (ChannelVariable)cra.getVariable();

			if(var.getType().getBufferSize()>0) {

				// Dependency matrix: channel variable
				DMIncRead(params, var, 0);
				DMIncWrite(params, var, 0);

				List<Expression> exprs = cra.getExprs();
				for (int i = 0; i < exprs.size(); i++) {
					final Expression expr = exprs.get(i);
					if (expr instanceof Identifier) {
						walkIntExpression(params,expr);
						// Dependency matrix: channel variable
						//dep_matrix.incRead(trans, state_var_offset.get(var)+i+1);
						DMIncRead(params, var, i+1);
						DMAssign(params,expr);
					}
				}

			} else {
				throw new AssertionError("Trying to actionise rendezvous receive!");
			}

		// Handle not yet implemented action
		} else {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+a.getClass().getName());
		}
	}

	static void walkIntExpression(Params params, Expression e) {
		if(e instanceof PCExpression) {
			throw new AssertionError("hopefully this is never reached");
		} else if(e instanceof PriorityExpression) {
			throw new AssertionError("hopefully this is never reached");
		} else if(e instanceof PCIdentifier) {
			PCIdentifier pc = (PCIdentifier)e;
			DMIncRead(params,pc.getVariable(),0);
		} else if(e instanceof PriorityIdentifier) {
			PriorityIdentifier pi = (PriorityIdentifier)e;
			DMIncRead(params,pi.getVariable(),0);
		} else if(e instanceof ChannelSizeExpression) {
			ChannelSizeExpression cse = (ChannelSizeExpression)e;
			DMIncRead(params,cse.getVariable(),0);
		} else if(e instanceof Identifier) {
			Identifier id = (Identifier)e;
			Variable var = id.getVariable();
			Expression arrayExpr = id.getArrayExpr();
			if (var.getArraySize() > 1) {
				if (arrayExpr != null) {
					//w.append(var.getName());
					walkIntExpression(params,arrayExpr);

					try {
						int i = arrayExpr.getConstantValue();
						//if(trans<dep_matrix.getRows()) dep_matrix.incRead(trans, state_var_offset.get(var)+i);
						DMIncRead(params, var, i);
					} catch(ParseException pe) {
						for(int i=0; i<var.getArraySize(); ++i) {
							//if(trans<dep_matrix.getRows()) dep_matrix.incRead(trans, state_var_offset.get(var)+i);
							DMIncRead(params, var, i);
						}
					}
				} else {
					//if(trans<dep_matrix.getRows()) dep_matrix.incRead(trans, state_var_offset.get(var));
					DMIncRead(params, var, 0);
				}
			} else {
				//if(trans<dep_matrix.getRows()) dep_matrix.incRead(trans, state_var_offset.get(var));
				DMIncRead(params, var, 0);
			}
		} else if(e instanceof AritmicExpression) {
			AritmicExpression ae = (AritmicExpression)e;
			Expression ex1 = ae.getExpr1();
			Expression ex2 = ae.getExpr2();
			Expression ex3 = ae.getExpr3();
			if (ex2 == null) {
				walkIntExpression(params,ex1);
			} else if (ex3 == null) {
				if (ae.getToken().image.equals("%")) {
					// Modulo takes a special notation to make sure that it
					// returns a positive value
					walkIntExpression(params,ex1);
					walkIntExpression(params,ex2);
				} else {
					walkIntExpression(params,ex1);
					walkIntExpression(params,ex2);
				}
			} else {
				walkBoolExpression(params,ex1);
				walkIntExpression(params,ex2);
				walkIntExpression(params,ex3);
			}
		} else if(e instanceof BooleanExpression) {
			BooleanExpression be = (BooleanExpression)e;
			Expression ex1 = be.getExpr1();
			Expression ex2 = be.getExpr2();
			if (ex2 == null) {
				walkIntExpression(params,ex1);
			} else {
				walkIntExpression(params,ex1);
				walkBoolExpression(params,ex2);
			}
		} else if(e instanceof ChannelLengthExpression) {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else if(e instanceof ChannelOperation) {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else if(e instanceof CompareExpression) {
			CompareExpression ce = (CompareExpression)e;
			walkIntExpression(params,ce.getExpr1());
			walkIntExpression(params,ce.getExpr2());
		} else if(e instanceof RunExpression) {
			walkIntExpression(params, new Identifier(new Token(), LTSMinPrinter._NR_PR));
		} else if(e instanceof CompoundExpression) {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else if(e instanceof ConstantExpression) {
		} else if(e instanceof ChannelTopExpression) {
			ChannelTopExpression cte = (ChannelTopExpression)e;
			ChannelReadAction cra =cte.getChannelReadAction();
			ChannelVariable var = (ChannelVariable)cra.getVariable();

			// read top most element: needs dependency on entire channel
			for(int i=1; i<=var.getType().getBufferSize(); ++i) {
				DMIncRead(params, var, i);
			}

		} else if(e instanceof EvalExpression) {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else if(e instanceof MTypeReference) {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else if(e instanceof TimeoutExpression) {
			DMIncReadAll(params); // should be optimized
		} else {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		}

	}
	static void walkBoolExpression(Params params, Expression e) {
		if(e instanceof Identifier) {
			walkIntExpression(params,e);
		} else if(e instanceof AritmicExpression) {
			AritmicExpression ae = (AritmicExpression)e;
			Expression ex1 = ae.getExpr1();
			Expression ex2 = ae.getExpr2();
			Expression ex3 = ae.getExpr3();
			if (ex2 == null) {
				walkIntExpression(params,ex1);
			} else if (ex3 == null) {
				walkIntExpression(params,ex1);
				walkIntExpression(params,ex2);
			} else { // Can only happen with the x?1:0 expression
				walkBoolExpression(params,ex1);
				walkBoolExpression(params,ex2);
				walkBoolExpression(params,ex3);
			}
		} else if(e instanceof BooleanExpression) {
			BooleanExpression be = (BooleanExpression)e;
			Expression ex1 = be.getExpr1();
			Expression ex2 = be.getExpr2();
			if (ex2 == null) {
				walkBoolExpression(params,ex1);
			} else {
				walkBoolExpression(params,ex1);
				walkBoolExpression(params,ex2);
			}
		} else if(e instanceof ChannelLengthExpression) {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else if(e instanceof ChannelOperation) {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else if(e instanceof CompareExpression) {
			CompareExpression ce = (CompareExpression)e;
			walkIntExpression(params,ce.getExpr1());
			walkIntExpression(params,ce.getExpr2());
		} else if(e instanceof RunExpression) {
			walkBoolExpression(params, new Identifier(new Token(), LTSMinPrinter._NR_PR));
		} else if(e instanceof CompoundExpression) {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else if(e instanceof ConstantExpression) {
		} else if(e instanceof EvalExpression) {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else if(e instanceof MTypeReference) {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else if(e instanceof TimeoutExpression) {
			DMIncReadAll(params); // should be optimized
		} else {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		}
	}

	static void DMIncWrite(Params params, Variable var, int offset) {
		Integer i = params.model.getVariables().get(var);
		if(i==null) {
			System.out.println("For some reason var is null, " + var.getName());
			System.out.println("Vars: " + params.model.getVariables().toString());
		} else {
			params.depMatrix.incWrite(params.guard, i+offset);
		}
	}

	static void DMDecrWrite(Params params, Variable var, int offset) {
		Integer i = params.model.getVariables().get(var);
		if(i==null) {
			System.out.println("For some reason var is null, " + var.getName());
			System.out.println("Vars: " + params.model.getVariables().toString());
		} else {
			params.depMatrix.decrWrite(params.guard, i+offset);
		}
	}

	static void DMIncRead(Params params, Variable var, int offset) {
		Integer i = params.model.getVariables().get(var);
		if(i==null) {
			System.out.println("For some reason var is null, " + var.getName());
			System.out.println("Vars: " + params.model.getVariables().toString());
		} else {
			System.out.println("Did something for " + params.guard);
			params.depMatrix.incRead(params.guard, i+offset);
		}
	}

	static void DMIncReadAll(Params params) {
		for(int i=params.model.getStateVector().size(); i-->0;) {
		}
	}

	static void DMDecrRead(Params params, Variable var, int offset) {
		Integer i = params.model.getVariables().get(var);
		if(i==null) {
			System.out.println("For some reason var is null, " + var.getName());
			System.out.println("Vars: " + params.model.getVariables().toString());
		} else {
			params.depMatrix.decrRead(params.guard,i+offset);
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

	static void DMAssign(Params params, Expression e) {
		if(e instanceof Identifier) {
			Identifier id = (Identifier)e;
			Variable var = id.getVariable();
			Expression arrayExpr = id.getArrayExpr();
			if (var.getArraySize() > 1) {
				if (arrayExpr != null) {
					try {
						int i = arrayExpr.getConstantValue();
						DMDecrRead(params,var,i);
						DMIncWrite(params,var,i);
					} catch(ParseException pe) {
						for(int i=0; i<var.getArraySize(); ++i) {
							DMDecrRead(params,var,i);
							DMIncWrite(params,var,i);
						}
					}
				} else {
					DMDecrRead(params,var,0);
					DMIncWrite(params,var,0);
//					for(int i=0; i<var.getArraySize(); ++i) {
//						DMDecrRead(params,var,i);
//						DMIncWrite(params,var,i);
//					}
				}
			} else {
				DMDecrRead(params,var,0);
				DMIncWrite(params,var,0);
			}
		} else {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		}
	}
}

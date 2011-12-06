package spinja.promela.compiler.ltsmin;

import static spinja.promela.compiler.ltsmin.LTSminStateVector._NR_PR;

import java.util.List;

import spinja.promela.compiler.actions.ChannelReadAction;
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
import spinja.promela.compiler.parser.ParseException;
import spinja.promela.compiler.parser.Token;
import spinja.promela.compiler.variable.ChannelVariable;
import spinja.promela.compiler.variable.Variable;

/**
 *
 * @author FIB
 */
public class LTSminGMWalker {

	static public class Params {
		public final LTSminModel model;
		public final GuardMatrix guardMatrix;
		public final DepMatrix depMatrix;
		public int trans;
		public int guard;

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
			model.setGuardMatrix(new GuardMatrix(model.sv.size()));
		}
		DepMatrix dm = new DepMatrix(1,model.sv.size());
		GuardMatrix guardMatrix = model.getGuardMatrix();
		Params params = new Params(model,guardMatrix,dm,0,-1);
		guardMatrix.setDepMatrix2(dm);
		walkTransitions(params);
	}

	static void walkTransitions(Params params) {
		for(LTSminTransitionBase t : params.model.getTransitions()) {
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
		if(guard instanceof LTSminLocalGuard) { //Nothing
		} else if(guard instanceof LTSminGuard) {
			int gidx = params.guard>=0 ? params.guard : params.guardMatrix.addGuard(params.trans, guard);
			params.depMatrix.ensureSize(gidx+1);
			//System.out.println("Now handling1 " + gidx);
			LTSminGuard g = (LTSminGuard)guard;
			walkBoolExpression(new Params(params.model, params.guardMatrix, params.depMatrix, params.trans, gidx), g.expr);
		} else if(guard instanceof LTSminGuardNand) {
			int gidx = params.guard>=0 ? params.guard : params.guardMatrix.addGuard(params.trans, guard);
			params.depMatrix.ensureSize(gidx+1);
			//System.out.println("Now handling2 " + gidx);
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
			//System.out.println("Now handling3 " + gidx);
			LTSminGuardOr g = (LTSminGuardOr)guard;
			for(LTSminGuardBase gb: g.guards) {
				walkGuard(new Params(params.model, params.guardMatrix, params.depMatrix, params.trans, gidx),gb);
			}
		} else {
			throw new AssertionError("UNSUPPORTED: " + guard.getClass().getSimpleName());
		}
	}
	
	static void walkIntExpression(Params params, Expression e) {
		if(e instanceof ChannelSizeExpression) {
			ChannelSizeExpression cse = (ChannelSizeExpression)e;
			DMIncRead(params,cse.getVariable(),0);
		} else if(e instanceof LTSminIdentifier) { //nothing
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
			ChannelLengthExpression cle = (ChannelLengthExpression)e;
			Identifier id = (Identifier)cle.getExpression();
			DMIncRead(params,id.getVariable(),0); //filled and nextread is first
		} else if(e instanceof ChannelOperation) {
			ChannelOperation co = (ChannelOperation)e;
			Identifier id = (Identifier)co.getExpression();
			DMIncRead(params,id.getVariable(),0); //filled and nextread is first
		} else if(e instanceof CompareExpression) {
			CompareExpression ce = (CompareExpression)e;
			walkIntExpression(params,ce.getExpr1());
			walkIntExpression(params,ce.getExpr2());
		} else if(e instanceof RunExpression) {
			throw new AssertionError("LTSMinPrinter: Not yet implemented as expression: "+e.getClass().getName());
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
			ChannelLengthExpression cle = (ChannelLengthExpression)e;
			Identifier id = (Identifier)cle.getExpression();
			DMIncRead(params,id.getVariable(),0); //filled and nextread is first
		} else if(e instanceof ChannelOperation) {
			ChannelOperation co = (ChannelOperation)e;
			Identifier id = (Identifier)co.getExpression();
			DMIncRead(params,id.getVariable(),0); //filled and nextread is first
		} else if(e instanceof CompareExpression) {
			CompareExpression ce = (CompareExpression)e;
			walkIntExpression(params,ce.getExpr1());
			walkIntExpression(params,ce.getExpr2());
		} else if(e instanceof RunExpression) {
			walkBoolExpression(params, new Identifier(new Token(), _NR_PR));
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
			//System.out.println("Did something for " + params.guard);
			params.depMatrix.incRead(params.guard, i+offset);
		}
	}

	static void DMIncReadAll(Params params) {
		for(int i=params.model.sv.size(); i-->0;) {
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

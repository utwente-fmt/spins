package spinja.promela.compiler.ltsmin;

import java.util.ArrayList;
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
import spinja.promela.compiler.ltsmin.LTSMinPrinter.GuardMatrix;
import spinja.promela.compiler.ltsmin.LTSMinPrinter.PCIdentifier;
import spinja.promela.compiler.ltsmin.LTSMinPrinter.PriorityIdentifier;
import spinja.promela.compiler.parser.ParseException;
import spinja.promela.compiler.parser.PromelaConstants;
import spinja.promela.compiler.parser.Token;
import spinja.promela.compiler.variable.ChannelVariable;
import spinja.promela.compiler.variable.Variable;
import spinja.util.StringWriter;

/**
 *
 * @author FIB
 */
public class LTSminPrinter2 {

	public static final String IN_VAR = "in";
	public static final String MEMBERACCESS = "->";
	public static final String TMP_ACCESS = LTSMinPrinter.C_STATE_TMP + "->";
	public static final String IN_ACCESS = IN_VAR + "->";
	public static final String TMP_ACCESS_GLOBALS = TMP_ACCESS + LTSMinPrinter.C_STATE_GLOBALS + ".";
	public static final String ACCESS_PRIORITY = LTSMinPrinter.C_STATE_GLOBALS +"."+ LTSMinPrinter.C_STATE_PRIORITY;

	static void generateModel(StringWriter w, LTSminModel model) {
		generateHeader(w,model);
		LTSMinPrinter.generateTypeStructs(w);
		for(LTSminType t: model.getTypes()) {
			generateTypeDef(w, t);
		}
		generateForwardDeclarations(w);
		generateIsAtomic(w);
		generateStateCount(w,model);
		generateInitialState(w,model);
		generateGetNext(w,model);
		generateGetAll(w,model);
		generateTransitionCount(w,model);
		generateDepMatrix(w,model);
		generateStateDescriptors(w,model);
		generateGuardMatrix(w,model);
	}

	static void generateTypeDef(StringWriter w, LTSminType type) {
		w.appendLine("typedef ");
		generateType(w,type);
		w.appendLine(type.getName(),";");
	}
	static void generateType(StringWriter w, LTSminType type) {
		if(type instanceof LTSminTypeStruct) {
			LTSminTypeStruct t = (LTSminTypeStruct)type;
			w.appendLine("struct ",t.getName()," {");
			w.indent();
			for(LTSminType m: t.members) {
				generateType(w, m);
			}
			w.outdent();
			w.appendLine("}");
		} else if(type instanceof LTSminTypeBasic) {
			LTSminTypeBasic t = (LTSminTypeBasic)type;
			w.appendPrefix();
			w.append(t.type_name).append(" ").append(t.name);
			if(t.size>1) w.append("[").append(t.size).append("]");
			w.append(";");
			w.appendPostfix();
		} else {
		}
	}

	static void generateHeader(StringWriter w, LTSminModel model) {

		w.appendLine("/** Generated LTSmin model - ",model.getName());
		String preprefix = w.getPrePrefix();
		String prefix = w.getPrefix();
		w.setPrePrefix(" * ");
		w.setPrefix("  ");

		w.appendLine("State size:  ",model.getStateVector().size()," elements (",model.getStateVector().size()*LTSMinPrinter.STATE_ELEMENT_SIZE," bytes)");
		w.appendLine("Transitions: ",model.getTransitions().size());

		w.setPrefix(prefix);
		w.setPrePrefix(preprefix);

		w.appendLine(" */");
		w.appendLine("");
		w.appendLine("#include <stdio.h>");
		w.appendLine("#include <string.h>");
		w.appendLine("#include <stdint.h>");
		w.appendLine("#include <stdbool.h>");
		w.appendLine("#include <stdlib.h>");
		w.appendLine("#include <assert.h>");
		w.appendLine("");
		w.appendLine("typedef struct transition_info");
		w.appendLine("{");
		w.indent();
		w.appendLine("int* label;");
		w.appendLine("int  group;");
		w.outdent();
		w.appendLine("} transition_info_t;");
	}

	static void generateStateCount(StringWriter w, LTSminModel model) {
		w.appendLine("int ",LTSMinPrinter.C_STATE_SIZE," = ",model.getStateVector().size(),";");
		w.appendLine("extern int spinja_get_state_size() {");
		w.indent();
		w.appendLine("return ",model.getStateVector().size(),";");
		w.outdent();
		w.appendLine("}");
	}

	static private void generateIsAtomic(StringWriter w) {
		w.appendLine("int spinja_is_atomic(void* model, ",LTSMinPrinter.C_STATE_T,"* ",LTSMinPrinter.C_STATE_TMP,") {");
		w.indent();

		w.appendLine("return ",LTSMinPrinter.C_STATE_TMP,"->",LTSMinPrinter.C_PRIORITY,".var >= 0;");

		w.outdent();
		w.appendLine("}");
	}

	static void generateTransitionCount(StringWriter w, LTSminModel model) {
		w.appendLine("extern int spinja_get_transition_groups() {");
		w.indent();
		w.appendLine("return ",model.getTransitions().size(),";");
		w.outdent();
		w.appendLine("}");
	}

	static void generateForwardDeclarations(StringWriter w) {
		w.appendLine("extern int spinja_get_successor_all( void* model, state_t *in, void (*callback)(void* arg, transition_info_t *transition_info, state_t *out), void *arg );");
		w.appendLine("extern int spinja_get_successor( void* model, int t, state_t *in, void (*callback)(void* arg, transition_info_t *transition_info, state_t *out), void *arg );");
	}

	static void generateInitialState(StringWriter w, LTSminModel model) {
		// Generate initial state
		w.append(LTSMinPrinter.C_STATE_T);
		w.append(" ");
		w.append(LTSMinPrinter.C_STATE_INITIAL);
		w.append(" = ");
		w.append("(");
		w.append(LTSMinPrinter.C_STATE_T);
		w.append("){");
		if(model.getStateVector().size() > 0) {
			int i = 0;

			// Insert initial expression of each state element into initial state struct
			for(;;) {

				// Get the variable for the current element (at position i)
				Variable v = model.getStateVector().get(i).getVariable();

				// If it is null, this location is probably a state descriptor
				// or priorityProcess variable so the initial state is 0
				if(v==null) {
					w.append("0");

				// If not null, then it's a legit variable, global or local,
				// so the initial state will be set to whatever the initial
				// expression is
				} else {
					Expression e = v.getInitExpr();
					if(e==null) {
						w.append("0");
					} else {
						generateIntExpression(w, e, "UNKNOWN_INIT_VALUE");
					}
				}
				if(++i>=model.getStateVector().size()) {
					break;
				}
				w.append(",");
			}
		}
		w.appendLine("};");

		w.appendLine("extern void spinja_get_initial_state( state_t *to )");
		w.appendLine("{");
		w.indent();
		w.appendLine("if(state_size*",LTSMinPrinter.STATE_ELEMENT_SIZE," != sizeof(" + LTSMinPrinter.C_STATE_T + ")) { printf(\"state_t SIZE MISMATCH!: state=%i(%i) globals=%i\",sizeof(state_t),state_size*",LTSMinPrinter.STATE_ELEMENT_SIZE,",sizeof(state_globals_t)); }");
		w.appendLine("memcpy(to, (char*)&",LTSMinPrinter.C_STATE_INITIAL,", sizeof(" + LTSMinPrinter.C_STATE_T + "));");
		w.appendLine("to->",LTSMinPrinter.C_PRIORITY,".var = -1;");
		w.outdent();
		w.appendLine("}");
	}
	
	static void generateACallback(StringWriter w, int trans) {
		w.appendLine("transition_info.group = "+ trans +";");
		w.appendLine("callback(arg,&transition_info,tmp);");
		w.appendLine("++states_emitted;");
	}

	static class PCGuardTuple {
		public PCGuardTuple(Proctype p, int linenum) {
			this.linenum = linenum;
			this.p = p;
		}
		public boolean equals(Object o) {
			if (o == null)
				return false;
			if (!(o instanceof PCGuardTuple))
				return false;
			PCGuardTuple other = (PCGuardTuple)o;
			return other.p == p && other.linenum == linenum;
		}
		Proctype p;
		int linenum;
	}
	
	static boolean isAtomicGuard(LTSminGuardBase g) {
		if (!(g instanceof LTSminGuard))
			return false;
		LTSminGuard gg = (LTSminGuard)g;
		if (!(gg.expr instanceof CompareExpression || gg.expr instanceof BooleanExpression))
			return false;
		if (gg.expr instanceof CompareExpression) {
			CompareExpression ce = (CompareExpression)gg.expr;
			return ce.getExpr1() instanceof PriorityIdentifier;
		}
		BooleanExpression be = (BooleanExpression)gg.expr;
		return isAtomicGuard(new LTSminGuard(0, be.getExpr1())) &&
			   isAtomicGuard(new LTSminGuard(0, be.getExpr2()));
	}
	
	static PCGuardTuple lastPCG = null;
	static LTSminGuard lastAG = null;
	
	static PCGuardTuple getPCGuard(LTSminGuardBase g) {
		if (!(g instanceof LTSminGuard))
			return null;
		LTSminGuard gg = (LTSminGuard)g;
		if (!(gg.expr instanceof CompareExpression))
			return null;
		CompareExpression ce = (CompareExpression)gg.expr;
		if (!(ce.getExpr1() instanceof PCIdentifier))
			return null;
		PCIdentifier pi = (PCIdentifier)ce.getExpr1();
		
		if (!(ce.getExpr2() instanceof ConstantExpression))
			return null;
		ConstantExpression constant = (ConstantExpression)ce.getExpr2();
		int l = constant.getNumber();
		
		return new PCGuardTuple(pi.getProcess(), l);
	}
	
	static void generateGetAll(StringWriter w, LTSminModel model) {
		w.appendLine("int spinja_get_successor_all( void* model, state_t *in, void (*callback)(void* arg, transition_info_t *transition_info, state_t *out), void *arg) {");
		w.indent();
		w.appendLine("transition_info_t transition_info = { NULL, -1 };");
		w.appendLine("(void)model; // ignore model");
		w.appendLine("int states_emitted = 0;");
		w.appendLine("register int pos;");
		w.appendLine(LTSMinPrinter.C_STATE_T," local_state;");
		w.appendLine(LTSMinPrinter.C_STATE_T,"* ",LTSMinPrinter.C_STATE_TMP," = &local_state;");
		w.appendLine();
		w.appendLine("static int n_losses = 0;");
		w.appendLine("static int n_atomics = 0;");
		w.appendLine();
		
		// Count atomic states
		w.appendLine("#ifdef COUNTATOMIC").indent();
		w.appendLine(	"if(spinja_is_atomic(model,in))printf(\"handled atomic statements so far: %i\\n\",++n_atomics);").outdent();
		w.appendLine("#endif");

		List<LTSminTransitionBase> transitions = model.getTransitions();
		int trans = 0;
		lastPCG = null;
		lastAG = null;
		for(LTSminTransitionBase t: transitions) {
			generateATransition(w, t, trans);
			++trans;
		}
		w.outdent();
		w.appendLine("}");
		w.outdent();
		w.appendLine("}");
		
		w.appendLine("return states_emitted;");
		w.outdent();
		w.appendLine("}");
		w.appendLine();
	}

	static void generateATransition(StringWriter w, LTSminTransitionBase transition, int trans) {
		if(transition instanceof LTSminTransition) {
			LTSminTransition t = (LTSminTransition)transition;

			assert (t.getGuards().size() > 1); //assume there are two guards
			LTSminGuardBase gg = t.getGuards().get(0);
			PCGuardTuple curPCG = getPCGuard(gg);
			assert (curPCG != null); //assume the first guard is always the PC guard
			
			assert (isAtomicGuard(t.getGuards().get(1)));
			LTSminGuard curAG = (LTSminGuard)t.getGuards().get(1);
			
			boolean boundary = false;
			if (!curAG.equals(lastAG)) {
				if (lastAG != null) {
					w.outdent().appendLine("}");
					w.outdent().appendLine("}");
				}
				w.appendPrefix().append("if(");
				generateGuard(w, curAG);
				w.append(") {").appendPostfix().indent();
				lastAG = curAG;
				boundary = true;
			}
			
			if (boundary || !curPCG.equals(lastPCG)) {
				if (!boundary && lastPCG != null)
					w.outdent().appendLine("}");
				w.appendPrefix().append("if(");
				generateGuard(w, gg);
				w.append(") {").appendPostfix().indent();
				lastPCG = curPCG;
			}

			w.appendPrefix().append("if (true");
			//t.generateNonPCGuardsC(w);
			int count = 0;
			for(LTSminGuardBase g: t.getGuards()) {
				++count;
				if (count < 3) // 0 == PCGuard && 1 == PriorityGuard
					continue;
				w.appendPostfix().appendPrefix().append("&&");
				generateGuard(w, g);
			}
			w.appendLine(") {");
			w.indent();
			w.appendLine("memcpy(", LTSMinPrinter.C_STATE_TMP,", ", IN_VAR , ", sizeof(", LTSMinPrinter.C_STATE_T,"));");
			List<Action> actions = t.getActions();
			for(Action a: actions) {
				generateAction(w,a);
			}
			generateACallback(w,trans);
			w.outdent();
			w.appendLine("}");
		} else if (transition instanceof LTSminTransitionCombo) {
			LTSminTransitionCombo t = (LTSminTransitionCombo)transition;
			for(LTSminTransitionBase tb: t.transitions) {
				generateATransition(w, tb, trans);
			}
		} else {
			w.appendLine("/** UNSUPPORTED: ",transition.getClass().getSimpleName()," **/");
		}
	}
	
	static void generateGetNext(StringWriter w, LTSminModel model) {
		w.appendLine("int spinja_get_successor( void* model, int t, state_t *in, void (*callback)(void* arg, transition_info_t *transition_info, state_t *out), void *arg) {");
		w.indent();

		w.appendLine("transition_info_t transition_info = { NULL, t };");
		w.appendLine("(void)model; // ignore model");
		w.appendLine("register int pos;");
		w.appendLine(LTSMinPrinter.C_STATE_T," local_state;");
		w.appendLine(LTSMinPrinter.C_STATE_T,"* ",LTSMinPrinter.C_STATE_TMP," = &local_state;");
		w.appendLine();

		w.appendLine("static int n_losses = 0;");
		w.appendLine("static int n_atomics = 0;");
		w.appendLine();

		// Count atomic states
		w.appendLine("#ifdef COUNTATOMIC");
		w.indent();
		w.appendLine("if(t==1 && spinja_is_atomic(model,in)) printf(\"handled atomic statements so far: %i\\n\",++n_atomics);");
		w.outdent();
		w.appendLine("#endif");

		w.appendLine("switch(t) {");

		List<LTSminTransitionBase> transitions = model.getTransitions();
		int trans = 0;
		for(LTSminTransitionBase t: transitions) {
			w.appendLine("case ",trans,": { // ",t.getName());
			w.indent();
			generateTransition(w, t);
			w.appendLine("break;");
			w.outdent();
			w.appendLine("}");
			++trans;
		}
		w.appendLine("}");
		w.appendLine("return 0;");

		w.outdent();
		w.appendLine("}");
		w.appendLine();
	}

	static void generateTransition(StringWriter w, LTSminTransitionBase transition) {
		if(transition instanceof LTSminTransition) {
			LTSminTransition t = (LTSminTransition)transition;
			List<LTSminGuardBase> guards = t.getGuards();
			w.appendPrefix().append("if( true ");
			for(LTSminGuardBase g: guards) {
				w.appendPostfix().appendPrefix().append(" && ");
				generateGuard(w,g);
			}
			w.append(" ) {").appendPostfix();
			w.indent();
			w.appendLine("memcpy(", LTSMinPrinter.C_STATE_TMP, ", ", IN_VAR, ", sizeof(", LTSMinPrinter.C_STATE_T,"));");
			List<Action> actions = t.getActions();
			for(Action a: actions) {
				generateAction(w,a);
			}
			generateCallback(w);
			w.outdent();
			w.appendLine("}");
		} else if (transition instanceof LTSminTransitionCombo) {
			LTSminTransitionCombo t = (LTSminTransitionCombo)transition;
			for(LTSminTransitionBase tb: t.transitions) {
				generateTransition(w, tb);
			}
		} else {
			w.appendLine("/** UNSUPPORTED: ",transition.getClass().getSimpleName()," **/");
		}
	}

	static void generateGuard(StringWriter w, LTSminGuardBase guard) {
		if(guard instanceof LTSminGuard) {
			LTSminGuard g = (LTSminGuard)guard;
			generateBoolExpression(w,g.expr, IN_ACCESS);
		} else if(guard instanceof LTSminGuardNand) {
			LTSminGuardNand g = (LTSminGuardNand)guard;
			w.append("!( true");
			for(LTSminGuardBase gb: g.guards) {
				w.append(" && ");
				generateGuard(w,gb);
			}
			w.append(")");
		} else if(guard instanceof LTSminGuardAnd) {
			LTSminGuardAnd g = (LTSminGuardAnd)guard;
			w.append("( true");
			for(LTSminGuardBase gb: g.guards) {
				w.append(" && ");
				generateGuard(w,gb);
			}
			w.append(")");
		} else if(guard instanceof LTSminGuardOr) {
			LTSminGuardOr g = (LTSminGuardOr)guard;
			w.append("( false");
			for(LTSminGuardBase gb: g.guards) {
				w.append(" || ");
				generateGuard(w,gb);
			}
			w.append(")");
		} else {
			w.appendLine("/** UNSUPPORTED: ",guard.getClass().getSimpleName()," **/");
		}
	}

	static void generateAction(StringWriter w, Action a) {
		// Handle assignment action
		if(a instanceof AssignAction) {
			AssignAction as = (AssignAction)a;
			Identifier id = as.getIdentifier();
			final String mask = id.getVariable().getType().getMask();
			switch (as.getToken().kind) {
				case PromelaConstants.ASSIGN:
					try {
						int value = as.getExpr().getConstantValue();
						w.appendPrefix();
						generateIntExpression(w, id, TMP_ACCESS);
						w.append(" = ").append(value & id.getVariable().getType().getMaskInt()).append(";");
						w.appendPostfix();
					} catch (ParseException ex) {
						// Could not get Constant value
						w.appendPrefix();
						generateIntExpression(w, id, TMP_ACCESS);
						w.append(" = ");
						generateIntExpression(w, as.getExpr(), IN_ACCESS);
						w.append((mask == null ? "" : " & " + mask));
						w.append(";");
						w.appendPostfix();
					}
					break;
				case PromelaConstants.INCR:
					if (mask == null) {
						w.appendPrefix();
						generateIntExpression(w, id, TMP_ACCESS);
						w.append("++;");
						w.appendPostfix();
					} else {
						w.appendPrefix();
						generateIntExpression(w, id, TMP_ACCESS);
						w.append(" = (");
						generateIntExpression(w, id, IN_ACCESS);
						w.append(" + 1) & ").append(mask).append(";");
						w.appendPostfix();
					}
					break;
				case PromelaConstants.DECR:
					if (mask == null) {
						w.appendPrefix();
						generateIntExpression(w, id, TMP_ACCESS);
						w.append("--;");
						w.appendPostfix();
					} else {
						w.appendPrefix();
						generateIntExpression(w, id, TMP_ACCESS);
						w.appendLine(" = (");
						generateIntExpression(w, id, IN_ACCESS);
						w.append(" - 1) & ");
						w.append(mask);
						w.append(";");
						w.appendPostfix();
					}
					break;
				default:
					throw new AssertionError("unknown assignment type");
			}

		} else if(a instanceof LTSMinPrinter.ResetProcessAction) {
			LTSMinPrinter.ResetProcessAction rpa = (LTSMinPrinter.ResetProcessAction)a;
			rpa.getProcess();
			String name = LTSMinPrinter.wrapName(rpa.getProcess().getName());
			w.appendLine("#ifndef NORESETPROCESS");
			w.appendLine("memset(&",TMP_ACCESS,name,",-1,sizeof(state_",name,"_t));");
			w.appendLine("#endif");

		// Handle assert action
		} else if(a instanceof AssertAction) {
			AssertAction as = (AssertAction)a;
			Expression e = as.getExpr();

			w.appendPrefix();
			w.append("if(!");
			generateBoolExpression(w, e, IN_ACCESS);
			w.append(") {");
			w.appendPostfix();
			w.indent();
			w.appendLine("printf(\"Assertion violated: ",as.getExpr().toString(), "\\n\");");
			w.appendLine("print_state(",LTSMinPrinter.C_STATE_TMP,");");
			w.outdent();
			w.appendLine("}");

		// Handle print action
		} else if(a instanceof PrintAction) {
			PrintAction pa = (PrintAction)a;
			String string = pa.getString();
			List<Expression> exprs = pa.getExprs();
			w.appendPrefix().append("printf(").append(string);
			for (final Expression expr : exprs) {
				w.append(", ");
				generateIntExpression(w, expr, IN_ACCESS);
			}
			w.append(");").appendPostfix();

		// Handle expression action
		} else if(a instanceof ExprAction) {
			ExprAction ea = (ExprAction)a;
			Expression expr = ea.getExpression();
//			final String sideEffect = expr.getSideEffect();
//			if (sideEffect != null) {
//				throw new AssertionError("This is probably wrong...");
//				//w.appendLine(sideEffect, "; // POSSIBLY THIS IS WRONG");
//			}

		// Handle channel send action
		} else if(a instanceof ChannelSendAction) {
			ChannelSendAction csa = (ChannelSendAction)a;
			ChannelVariable var = (ChannelVariable)csa.getVariable();

			if(var.getType().getBufferSize()>0) {
				String access;
				String access_buffer;
				if(var.getOwner()==null) {
					access = TMP_ACCESS_GLOBALS + LTSMinPrinter.wrapNameForChannelDesc(var.getName());
					access_buffer = TMP_ACCESS_GLOBALS + LTSMinPrinter.wrapNameForChannelBuffer(var.getName()) + "[pos]";
				} else {
					access = TMP_ACCESS + var.getOwner().getName() + "." + LTSMinPrinter.wrapNameForChannelDesc(var.getName());
					access_buffer = TMP_ACCESS + var.getOwner().getName() + "." + LTSMinPrinter.wrapNameForChannelBuffer(var.getName()) + "[pos]";
				}

				w.appendLine("pos = (" + access + ".nextRead + "+access+".filled) % "+var.getType().getBufferSize() + ";");

				// Dependency matrix: channel variable
				//dep_matrix.incRead(trans, state_var_offset.get(var));
				//dep_matrix.incWrite(trans, state_var_offset.get(var));

				List<Expression> exprs = csa.getExprs();
				for (int i = 0; i < exprs.size(); i++) {
					final Expression expr = exprs.get(i);
					w.appendPrefix();
					w.append(access_buffer).append(".m").append(i).append(".var = ");
					generateIntExpression(w, expr, IN_ACCESS);
					w.append(";");
					w.appendPostfix();

					// Dependency matrix: channel variable
					//dep_matrix.incWrite(trans, state_var_offset.get(var)+i+1);
				}

				w.appendLine("++",access, ".filled;");
			} else {
				throw new AssertionError("Trying to actionise rendezvous send!");
			}

		// Handle a channel read action
		} else if(a instanceof ChannelReadAction) {
			ChannelReadAction cra = (ChannelReadAction)a;
			ChannelVariable var = (ChannelVariable)cra.getVariable();

			if(var.getType().getBufferSize()>0) {
				String access;
				String access_buffer;
				if(var.getOwner()==null) {
					access = TMP_ACCESS_GLOBALS + LTSMinPrinter.wrapNameForChannelDesc(var.getName());
					access_buffer = TMP_ACCESS_GLOBALS + LTSMinPrinter.wrapNameForChannelBuffer(var.getName()) + "[pos]";
				} else {
					access = TMP_ACCESS + var.getOwner().getName() + "." + LTSMinPrinter.wrapNameForChannelDesc(var.getName());
					access_buffer = TMP_ACCESS + var.getOwner().getName() + "." + LTSMinPrinter.wrapNameForChannelBuffer(var.getName()) + "[pos]";
				}
				w.appendLine("pos = (" + access + ".nextRead + "+access+".filled) % "+var.getType().getBufferSize() + ";");

				// Dependency matrix: channel variable
				//dep_matrix.incRead(trans, state_var_offset.get(var));
				//dep_matrix.incWrite(trans, state_var_offset.get(var));

				List<Expression> exprs = cra.getExprs();
				for (int i = 0; i < exprs.size(); i++) {
					final Expression expr = exprs.get(i);
					if (expr instanceof Identifier) {
						w.appendPrefix();
						generateIntExpression(w, expr, TMP_ACCESS);
						w.append(" = ").append(access_buffer).append(".m").append(i).append(".var");
						w.append(";");
						w.appendPostfix();
						w.appendPrefix();
						w.append(access_buffer).append(".m").append(i).append(".pad = 0;");
						w.appendPostfix();


						// Dependency matrix: channel variable
						//dep_matrix.incRead(trans, state_var_offset.get(var)+i+1);
					}
				}

				w.appendLine(access,".nextRead = (",access,".nextRead+1)%"+var.getType().getBufferSize()+";");
				w.appendLine("--",access, ".filled;");
			} else {
				throw new AssertionError("Trying to actionise rendezvous receive!");
			}

		// Handle not yet implemented action
		} else {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+a.getClass().getName());
		}
	}

	static void generateCallback(StringWriter w) {
		w.appendLine("callback(arg,&transition_info,tmp);");
		w.appendLine("return ",1,";");
	}

	static void generateIntExpression(StringWriter w, Expression e, String access) {
		//w.append("|").append(e.getClass().getSimpleName()).append("|");
		if(e instanceof LTSMinPrinter.PCExpression) {
			LTSMinPrinter.PCExpression pc = (LTSMinPrinter.PCExpression)e;
			w.append(access).append(pc.getProcessName()).append(".pc.var");
		} else if(e instanceof LTSMinPrinter.PriorityExpression) {
			w.append(access + ACCESS_PRIORITY).append(".var");
		} else if(e instanceof LTSMinPrinter.PCIdentifier) {
			LTSMinPrinter.PCIdentifier pc = (LTSMinPrinter.PCIdentifier)e;
			w.append(access).append(LTSMinPrinter.wrapName(pc.getProcess().getName())).append(".pc.var");
		} else if(e instanceof LTSMinPrinter.PriorityIdentifier) {
			w.append(access + ACCESS_PRIORITY).append(".var");
		} else if(e instanceof LTSMinPrinter.ChannelSizeExpression) {
			LTSMinPrinter.ChannelSizeExpression cse = (LTSMinPrinter.ChannelSizeExpression)e;
			Variable var = cse.getVariable();
			w.append(access);
			if(var.getOwner()==null) {
				w.append("globals.");
			} else {
				w.append(LTSMinPrinter.wrapName(var.getOwner().getName()));
				w.append(".");
			}
			w.append(LTSMinPrinter.wrapName(cse.getVariable().getName())).append(".filled");
		} else if(e instanceof Identifier) {
			Identifier id = (Identifier)e;
			Variable var = id.getVariable();
			Expression arrayExpr = id.getArrayExpr();
			if (var.getArraySize() > 1) {
				if (arrayExpr != null) {
					//w.append(var.getName());
					w.append(access);
					if(var.getOwner()==null) {
						w.append("globals.");
					} else {
						w.append(LTSMinPrinter.wrapName(var.getOwner().getName()));
						w.append(".");
					}
					w.append(LTSMinPrinter.wrapName(var.getName()));
					w.append("[");
					generateIntExpression(w,arrayExpr,access);
					w.append("].var");

					try {
						int i = arrayExpr.getConstantValue();
						//if(trans<dep_matrix.getRows()) dep_matrix.incRead(trans, state_var_offset.get(var)+i);
					} catch(ParseException pe) {
//						for(int i=0; i<var.getArraySize(); ++i) {
//							if(trans<dep_matrix.getRows()) dep_matrix.incRead(trans, state_var_offset.get(var)+i);
//						}
					}
				} else {
					w.append(access);
					if(var.getOwner()==null) {
						w.append("globals.");
					} else {
						w.append(LTSMinPrinter.wrapName(var.getOwner().getName()));
						w.append(".");
					}
					w.append(LTSMinPrinter.wrapName(var.getName()));
					w.append("[0].var");
					//if(trans<dep_matrix.getRows()) dep_matrix.incRead(trans, state_var_offset.get(var));
				}
			} else {
				w.append(access);
				if(var.getOwner()==null) {
					w.append("globals.");
				} else {
					w.append(LTSMinPrinter.wrapName(var.getOwner().getName()));
					w.append(".");
				}
				w.append(LTSMinPrinter.wrapName(var.getName()));
				w.append(".var");
				//if(trans<dep_matrix.getRows()) dep_matrix.incRead(trans, state_var_offset.get(var));
			}
		} else if(e instanceof AritmicExpression) {
			AritmicExpression ae = (AritmicExpression)e;
			Expression ex1 = ae.getExpr1();
			Expression ex2 = ae.getExpr2();
			Expression ex3 = ae.getExpr3();
			if (ex2 == null) {
				w.append("(").append(ae.getToken().image);
				generateIntExpression(w, ex1, access);
				w.append(")");
			} else if (ex3 == null) {
				if (ae.getToken().image.equals("%")) {
					// Modulo takes a special notation to make sure that it
					// returns a positive value
					w.append("abs(");
					generateIntExpression(w, ex1, access);
					w.append(" % ");
					generateIntExpression(w, ex2, access);
					w.append(")");
				} else {

					w.append("(");
					generateIntExpression(w, ex1, access);
					w.append(" ").append(ae.getToken().image).append(" ");
					generateIntExpression(w, ex2, access);
					w.append(")");
				}
			} else {
				w.append("(");
				generateBoolExpression(w, ex1, access);
				w.append(" ? ");
				generateIntExpression(w, ex2, access);
				w.append(" : ");
				generateIntExpression(w, ex3, access);
				w.append(")");
			}
		} else if(e instanceof BooleanExpression) {
			BooleanExpression be = (BooleanExpression)e;
			Expression ex1 = be.getExpr1();
			Expression ex2 = be.getExpr2();
			if (ex2 == null) {
				w.append("(").append(be.getToken().image);
				generateIntExpression(w, ex1, access);
				w.append( " ? 1 : 0)");
			} else {
				w.append("(");
				generateIntExpression(w, ex1, access);
				w.append(" ").append(be.getToken().image).append(" ");
				generateBoolExpression(w,ex2, access);
				w.append(" ? 1 : 0)");
			}
		} else if(e instanceof ChannelLengthExpression) {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else if(e instanceof ChannelOperation) {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else if(e instanceof CompareExpression) {
			CompareExpression ce = (CompareExpression)e;
			w.append("(");
			generateIntExpression(w, ce.getExpr1(), access);
			w.append(" ").append(ce.getToken().image).append(" ");
			generateIntExpression(w, ce.getExpr2(), access);
			w.append(" ? 1 : 0)");
		} else if(e instanceof CompoundExpression) {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else if(e instanceof ConstantExpression) {
			ConstantExpression ce = (ConstantExpression)e;
			switch (ce.getToken().kind) {
				case PromelaConstants.TRUE:
					w.append("1");
					break;
				case PromelaConstants.FALSE:
					w.append("0");
					break;
				case PromelaConstants.SKIP_:
					w.append("1");
					break;
				case PromelaConstants.NUMBER:
					w.append(Integer.toString(ce.getNumber()));
					break;
				default:
					w.append("1");
					break;
			}
		} else if(e instanceof LTSMinPrinter.ChannelTopExpression) {
			LTSMinPrinter.ChannelTopExpression cte = (LTSMinPrinter.ChannelTopExpression)e;
			ChannelReadAction cra =cte.getChannelReadAction();
			ChannelVariable var = (ChannelVariable)cra.getVariable();
			String chan_access = TMP_ACCESS_GLOBALS + LTSMinPrinter.wrapNameForChannelDesc(var.getName());
			String access_buffer = TMP_ACCESS_GLOBALS + LTSMinPrinter.wrapNameForChannelBuffer(var.getName()) + "[" + chan_access + ".nextRead]";
			w.append(access_buffer).append(".m").append(cte.getElem()).append(".var");
		} else if(e instanceof EvalExpression) {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else if(e instanceof MTypeReference) {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else if(e instanceof RunExpression) {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else if(e instanceof TimeoutExpression) {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		}
	}
	
	static void generateBoolExpression(StringWriter w, Expression e, String access) {
		if(e instanceof Identifier) {
			w.append("(");
			generateIntExpression(w, e, access);
			w.append(" != 0 )");
		} else if(e instanceof AritmicExpression) {
			AritmicExpression ae = (AritmicExpression)e;
			Expression ex1 = ae.getExpr1();
			Expression ex2 = ae.getExpr2();
			Expression ex3 = ae.getExpr3();
			if (ex2 == null) {
				w.append("(").append(ae.getToken().image);
				generateIntExpression(w, ex1, access);
				w.append(" != 0)");
			} else if (ex3 == null) {
				w.append("(");
				generateIntExpression(w, ex1, access);
				w.append(" ").append(ae.getToken().image).append(" ");
				generateIntExpression(w, ex2, access);
				w.append(" != 0)");
			} else { // Can only happen with the x?1:0 expression
				w.append("(");
				generateBoolExpression(w, ex1, access);
				w.append(" ? ");
				generateBoolExpression(w, ex2, access);
				w.append(" : ");
				generateBoolExpression(w, ex3, access);
				w.append(")");
			}
		} else if(e instanceof BooleanExpression) {
			BooleanExpression be = (BooleanExpression)e;
			Expression ex1 = be.getExpr1();
			Expression ex2 = be.getExpr2();
			if (ex2 == null) {
				w.append("(").append(be.getToken().image);
				generateBoolExpression(w, ex1, access);
				w.append(")");
			} else {
				w.append("(");
				generateBoolExpression(w, ex1, access);
				w.append(" ").append(be.getToken().image).append(" ");
				generateBoolExpression(w, ex2, access);
				w.append(")");
			}
		} else if(e instanceof ChannelLengthExpression) {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else if(e instanceof ChannelOperation) {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else if(e instanceof CompareExpression) {
			CompareExpression ce = (CompareExpression)e;
			w.append("(");
			generateIntExpression(w, ce.getExpr1(), access);
			w.append(" ").append(ce.getToken().image).append(" ");
			generateIntExpression(w, ce.getExpr2(), access);
			w.append(")");
		} else if(e instanceof CompoundExpression) {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else if(e instanceof ConstantExpression) {
			ConstantExpression ce = (ConstantExpression)e;
			switch (ce.getToken().kind) {
				case PromelaConstants.TRUE:
					w.append("true");
					break;
				case PromelaConstants.FALSE:
					w.append("false");
					break;
				case PromelaConstants.SKIP_:
					w.append("true");
					break;
				case PromelaConstants.NUMBER:
					w.append(ce.getNumber() != 0 ? "true" : "false");
					break;
				default:
					w.append("true");
					break;
			}
		} else if(e instanceof EvalExpression) {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else if(e instanceof MTypeReference) {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else if(e instanceof RunExpression) {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else if(e instanceof TimeoutExpression) {
//			if( timeoutFalse ) {
//				w.append("false /* timeout-false */");
//			} else if (process == spec.getNever()) {
//				w.append("false /* never-timeout */ ");
//			} else {
				// Prevent adding of this transition if it was already seen
				//if(!seenItAll) timeout_transitions.add(new TimeoutTransition(trans, process, lt));
				//w.append("timeout_expression(").append(C_STATE_TMP).append(",").append(trans).append(")");
//				w.append("TIMEOUT_").append(trans).append("()");
//			}
			w.append("TIMEOUT()");
		} else {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		}
	}

	static private void generateDepMatrix(StringWriter w, LTSminModel model) {
		LTSMinPrinter.DepMatrix dm = model.getDepMatrix();

		w.append("int transition_dependency[][2][").append(model.getStateVector().size()).appendLine("] = {");
		w.appendLine("\t// { ... read ...}, { ... write ...}");

		if(dm==null) {
			for(int t=0;t<model.getTransitions().size();++t) {
				w.appendPrefix();
				w.append("\t{{");
				w.append("1");
				for(int s=1; s<model.getStateVector().size(); ++s) {
					w.append(",1");
				}
				w.append("},{");
				w.append("1");
				for(int s=1; s<model.getStateVector().size(); ++s) {
					w.append(",1");
				}
				w.append("}}");

				if(t>=model.getTransitions().size()-1) {
					w.append("  // ").append(t);
				} else {
					w.append(", // ").append(t);
				}
				w.appendPostfix();
			}
			w.appendLine("};");
		} else {

			if(dm.getRows()!=model.getTransitions().size()) throw new AssertionError("DM Rows inconsistent");
			if(dm.getRow(0).getSize()!=model.getStateVector().size()) throw new AssertionError("DM Rows inconsistent");

			// Iterate over all the rows
			int t=0;
			for(;;) {
				w.append("\t{{");
				LTSMinPrinter.DepRow dr = null;
				if(dm!=null) dr = dm.getRow(t);
				int s=0;

				// Insert all read dependencies of the current row
				for(;;) {
					if(dm==null) {
						w.append(1);
					} else {
						w.append(dr.getReadB(s));
					}

					if(++s>=dr.getSize()) {
						break;
					}
					w.append(",");
				}

				// Bridge
				w.append("},{");
				s=0;

				// Insert all write dependencies of the current row
				for(;;) {
					if(dm==null) {
						w.append(1);
					} else {
						w.append(dr.getWriteB(s));
					}
					if(++s>=dr.getSize()) {
						break;
					}
					w.append(",");
				}

				// End the row
				w.append("}}");

				// If this was the last row
				if(t>=dm.getRows()-1) {
					w.appendLine("  // ",t);
					break;
				}
				w.appendLine(", // ",t);
				++t;
			}

			// Close array
			w.appendLine("};");
		}

		// Function to access the dependency matrix
		w.appendLine("");
		w.appendLine("extern const int* spinja_get_transition_read_dependencies(int t)");
		w.appendLine("{");
		w.append("	if (t>=0 && t < ").append(model.getTransitions().size()).appendLine(") return transition_dependency[t][0];");
		w.appendLine("	return NULL;");
		w.appendLine("}");
		w.appendLine("");
		w.appendLine("extern const int* spinja_get_transition_write_dependencies(int t)");
		w.appendLine("{");
		w.append("	if (t>=0 && t < ").append(model.getTransitions().size()).appendLine(") return transition_dependency[t][1];");
		w.appendLine("	return NULL;");
		w.appendLine("}");
	}

	static private void generateStateDescriptors(StringWriter w, LTSminModel model) {

		int state_size = model.getStateVector().size();
		List<LTSminStateElement> state = model.getStateVector();

		// Generate static list of names
		w.appendLine("static const char* var_names[] = {");
		w.indent();

		w.appendPrefix();
		w.append("\"").append(state.get(0).getVariable().toString()).append("\"");
		for(int i=1; i<state_size; ++i) {
			w.append(",");
			w.appendPostfix();
			w.appendPrefix();
			w.append("\"").append(state.get(i).getVariable().toString()).append("\"");
		}
		w.appendPostfix();

		w.outdent();
		w.appendLine("};");

		// Generate static list of types
		List<String> types = new ArrayList<String>();
		int translation[] = new int[state_size];

		int i = 0;
		for(;i<state_size;) {
			Variable var = state.get(i).getVariable();
			if(var==null) {
				int idx = types.indexOf(LTSMinPrinter.C_TYPE_PROC_COUNTER_);
				if(idx<0) {
					types.add(LTSMinPrinter.C_TYPE_PROC_COUNTER_);
					idx = types.size()-1;
				}
				translation[i++] = idx;
			} else if(var.getArraySize()>1) {
				int idx = types.indexOf(LTSMinPrinter.getCTypeOfVar(var).type);
				if(idx<0) {
					types.add(LTSMinPrinter.getCTypeOfVar(var).type);
					idx = types.size()-1;
				}
				for(int end=i+var.getArraySize();i<end;) {
					translation[i++] = idx;
				}
			} else {
				int idx = types.indexOf(LTSMinPrinter.getCTypeOfVar(var).type);
				if(idx<0) {
					types.add(LTSMinPrinter.getCTypeOfVar(var).type);
					idx = types.size()-1;
				}
				translation[i++] = idx;
			}
		}

		w.appendLine("");
		w.appendLine("static const char* var_types[] = {");
		w.indent();

		for(String s: types) {
			w.appendLine("\"",s,"\",");
		}
		w.appendLine("\"\"");

		w.outdent();
		w.appendLine("};");

//		int i = 0;
//		for(;;) {
//
//			w.appendPrefix();
//
//			Variable var = state_vector_var.get(i);
//			if(var==null) {
//				w.append("\"").append(C_TYPE_PROC_COUNTER).append("\"");
//			} else if(var.getArraySize()>1) {
//				w.append("\"").append(getCTypeOfVarReal(var)).append("[0]\"");
//				for(int j=1; i<state_size && j<var.getArraySize(); ++j, ++i) {
//					w.append(",");
//					w.appendPostfix();
//					w.appendPrefix();
//					w.append("\"").append(getCTypeOfVarReal(var)).append("[").append(j).append("]\"");
//				}
//			} else {
//				w.append("\"").append(getCTypeOfVarReal(var)).append("\"");
//			}
//
//			if(++i==state_size) break;
//			w.append(",");
//			w.appendPostfix();
//		}
//		w.appendPostfix();

		w.appendLine("");
		for(String s: types) {
			w.appendLine("static const char* const var_type_",s,"[] = {");
			w.indent();
			w.appendLine("\"\"");
			w.outdent();
			w.appendLine("};");
		}

		w.appendLine("");
		w.appendLine("static const char* const * const var_type_values[] = {");
		w.indent();

		for(String s: types) {
			w.appendLine("var_type_",s,",");
		}
		w.appendLine("NULL");

		w.outdent();
		w.appendLine("};");

		w.appendLine("");
		w.appendLine("extern const char* spinja_get_state_variable_name(unsigned int var) {");
		w.indent();

		w.appendLine("assert(var < ",state_size," && \"spinja_get_state_variable_name: invalid variable\");");
		w.appendLine("return var_names[var];");

		w.outdent();
		w.appendLine("}");
		w.appendLine("");

		w.appendLine("extern int spinja_get_type_count() {");
		w.indent();

		w.appendLine("return ",types.size(),";");

		w.outdent();
		w.appendLine("}");
		w.appendLine("");

		w.appendLine("extern const char* spinja_get_type_name(int type) {");
		w.indent();

		w.appendLine("assert(type < ",types.size()," && \"spinja_get_type_name: invalid type\");");
		w.appendLine("return var_types[type];");

		w.outdent();
		w.appendLine("}");
		w.appendLine("");

		w.appendLine("extern int spinja_get_type_value_count(int type) {");
		w.indent();

		w.appendLine("assert(type < ",types.size()," && \"spinja_get_type_value_count: invalid type\");");
		w.appendLine("return 0;"); /* FIXME */

		w.outdent();
		w.appendLine("}");
		w.appendLine("");

		w.appendLine("extern const char* spinja_get_type_value_name(int type, int value) {");
		w.indent();

		w.appendLine("return var_type_values[type][value];");

		w.outdent();
		w.appendLine("}");
		w.appendLine("");

		w.appendLine("extern int spinja_get_state_variable_type(int var) {");
		w.indent();

		w.appendLine("assert(var < ",state_size," && \"spinja_get_state_variable_type: invalid variable\");");
		w.appendLine("return 0;");

		w.outdent();
		w.appendLine("}");

	}

	static private void generateGuardMatrix(StringWriter w, LTSminModel model) {
		GuardMatrix gm = model.getGuardMatrix();
		if(gm==null) return;

		List<List<Integer>> dp_matrix = gm.getDepMatrix();
		List<List<Integer>> co_matrix = gm.getCoMatrix();
		List<List<Expression>> trans_matrix = gm.getTransMatrix();
		List<Expression> guards = gm.getGuards();
		w.appendLine("/*");
		String old_preprefix = w.getPrePrefix();
		w.setPrePrefix(" * ");

		w.appendLine("");
		w.appendLine("Guard list:");

		for(int g=0; g<guards.size(); ++g) {
			w.appendLine("  - ",guards.get(g).toString());
		}

		w.appendLine("");
		w.appendLine("Guard-Dependency Matrix:");

		for(int g=0; g<dp_matrix.size(); ++g) {
			w.appendPrefix();

			List<Integer> row = dp_matrix.get(g);

			for(int s=0; s<row.size(); ++s) {
				w.append(row.get(s)).append(", ");
			}

			w.appendPostfix();
		}

		w.appendLine("");
		w.appendLine("Co-Enabled Matrix:");

		for(int g=0; g<co_matrix.size(); ++g) {
			w.appendPrefix();

			List<Integer> row = co_matrix.get(g);

			for(int s=0; s<row.size(); ++s) {
				w.append(row.get(s)).append(", ");
			}

			w.appendPostfix();
		}

		w.appendLine("");
		w.appendLine("Transition-Guard Matrix:");
		for(int g=0; g<trans_matrix.size(); ++g) {
			w.appendPrefix();

			List<Expression> row = trans_matrix.get(g);

			for(int s=0; s<row.size(); ++s) {
				w.append(guards.indexOf(row.get(s))).append(", ");
			}

			w.appendPostfix();
		}

		w.setPrePrefix(old_preprefix);

		w.appendLine(" */");
	}

}

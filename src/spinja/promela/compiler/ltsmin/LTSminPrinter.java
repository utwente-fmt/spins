package spinja.promela.compiler.ltsmin;

import static spinja.promela.compiler.ltsmin.LTSminStateVector.C_NUM_PROCS_VAR;
import static spinja.promela.compiler.ltsmin.LTSminStateVector.C_PRIORITY;
import static spinja.promela.compiler.ltsmin.LTSminStateVector.C_STATE_GLOBALS;
import static spinja.promela.compiler.ltsmin.LTSminStateVector.C_STATE_INITIAL;
import static spinja.promela.compiler.ltsmin.LTSminStateVector.C_STATE_PRIORITY;
import static spinja.promela.compiler.ltsmin.LTSminStateVector.C_STATE_PROC_COUNTER;
import static spinja.promela.compiler.ltsmin.LTSminStateVector.C_STATE_SIZE;
import static spinja.promela.compiler.ltsmin.LTSminStateVector.C_STATE_T;
import static spinja.promela.compiler.ltsmin.LTSminStateVector.C_STATE_TMP;
import static spinja.promela.compiler.ltsmin.LTSminStateVector.C_TYPE_CHANNEL;
import static spinja.promela.compiler.ltsmin.LTSminStateVector.C_TYPE_INT1;
import static spinja.promela.compiler.ltsmin.LTSminStateVector.C_TYPE_INT16;
import static spinja.promela.compiler.ltsmin.LTSminStateVector.C_TYPE_INT32;
import static spinja.promela.compiler.ltsmin.LTSminStateVector.C_TYPE_INT8;
import static spinja.promela.compiler.ltsmin.LTSminStateVector.C_TYPE_PROC_COUNTER_;
import static spinja.promela.compiler.ltsmin.LTSminStateVector.C_TYPE_UINT16;
import static spinja.promela.compiler.ltsmin.LTSminStateVector.C_TYPE_UINT32;
import static spinja.promela.compiler.ltsmin.LTSminStateVector.C_TYPE_UINT8;
import static spinja.promela.compiler.ltsmin.LTSminStateVector.STATE_ELEMENT_SIZE;
import static spinja.promela.compiler.ltsmin.LTSminStateVector.getCTypeOfVar;
import static spinja.promela.compiler.ltsmin.LTSminStateVector.wrapNameForChannelDesc;
import static spinja.promela.compiler.parser.PromelaConstants.ASSIGN;
import static spinja.promela.compiler.parser.PromelaConstants.DECR;
import static spinja.promela.compiler.parser.PromelaConstants.FALSE;
import static spinja.promela.compiler.parser.PromelaConstants.INCR;
import static spinja.promela.compiler.parser.PromelaConstants.NUMBER;
import static spinja.promela.compiler.parser.PromelaConstants.SKIP_;
import static spinja.promela.compiler.parser.PromelaConstants.TRUE;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import spinja.promela.compiler.Proctype;
import spinja.promela.compiler.Specification;
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
import spinja.promela.compiler.ltsmin.instr.DepRow;
import spinja.promela.compiler.ltsmin.instr.GuardMatrix;
import spinja.promela.compiler.ltsmin.instr.PCExpression;
import spinja.promela.compiler.ltsmin.instr.PCIdentifier;
import spinja.promela.compiler.ltsmin.instr.PriorityExpression;
import spinja.promela.compiler.ltsmin.instr.PriorityIdentifier;
import spinja.promela.compiler.ltsmin.instr.ResetProcessAction;
import spinja.promela.compiler.parser.ParseException;
import spinja.promela.compiler.parser.Token;
import spinja.promela.compiler.variable.ChannelType;
import spinja.promela.compiler.variable.ChannelVariable;
import spinja.promela.compiler.variable.Variable;
import spinja.promela.compiler.variable.VariableType;
import spinja.util.StringWriter;

/**
 *
 * @author FIB
 */
public class LTSminPrinter {

	public static final String IN_VAR = "in";
	public static final String MEMBERACCESS = "->";
	public static final String TMP_ACCESS = C_STATE_TMP + "->";
	public static final String IN_ACCESS = IN_VAR + "->";
	public static final String TMP_ACCESS_GLOBALS = TMP_ACCESS + C_STATE_GLOBALS + ".";
	public static final String IN_ACCESS_GLOBALS = IN_ACCESS + C_STATE_GLOBALS + ".";
	public static final String TMP_NUM_PROCS = TMP_ACCESS_GLOBALS + C_NUM_PROCS_VAR +".var";
	public static final String IN_NUM_PROCS = IN_ACCESS_GLOBALS + C_NUM_PROCS_VAR +".var";
	public static final String ACCESS_PRIORITY = C_STATE_GLOBALS +"."+ C_STATE_PRIORITY;
	public static final int    PM_MAX_PROCS = 256;

	private static String getPC(String p_name, String access) {
		return access + LTSminTreeWalker.wrapName(p_name) +
				"."+ C_STATE_PROC_COUNTER +".var";
	}

	public static final String DM_NAME = "transition_dependency";
	public static final String GM_DM_NAME = "gm_dm";

	public static String generateCode(Specification spec, String name) {
		LTSminTreeWalker walker = new LTSminTreeWalker(spec,name);
		LTSminModel model = walker.createLTSminModel();
		StringWriter w = new StringWriter();
		LTSminPrinter.generateModel(w, model);
		return w.toString();
	}
	
	private static void generateModel(StringWriter w, LTSminModel model) {
		generateHeader(w,model);
		generateTypeStructs(w);
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
		generateDepMatrix(w,model.getDepMatrix(),DM_NAME);
		generateDMFunctions(w,model.getDepMatrix());
		generateGuardMatrix(w,model);
		generateGuardFunctions(w, model, model.getGuardMatrix());
		generateStateDescriptors(w,model);
	}

	private static void generateTypeDef(StringWriter w, LTSminType type) {
		w.appendLine("typedef ");
		generateType(w,type);
		w.appendLine(type.getName(),";");
		w.appendLine("");
	}

	private static void generateType(StringWriter w, LTSminType type) {
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

	private static void generateHeader(StringWriter w, LTSminModel model) {

		w.appendLine("/** Generated LTSmin model - ",model.getName());
		String preprefix = w.getPrePrefix();
		String prefix = w.getPrefix();
		w.setPrePrefix(" * ");
		w.setPrefix("  ");

		w.appendLine("State size:  ",model.sv.size()," elements (",model.sv.size()*STATE_ELEMENT_SIZE," bytes)");
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

	private static void generateStateCount(StringWriter w, LTSminModel model) {
		w.appendLine("int ",C_STATE_SIZE," = ",model.sv.size(),";");
		w.appendLine("extern int spinja_get_state_size() {");
		w.indent();
		w.appendLine("return ",model.sv.size(),";");
		w.outdent();
		w.appendLine("}");
	}

	private static void generateIsAtomic(StringWriter w) {
		w.appendLine("int spinja_is_atomic(void* model, ",C_STATE_T,"* ",C_STATE_TMP,") {");
		w.indent();

		w.appendLine("return ",C_STATE_TMP,"->",C_PRIORITY,".var >= 0;");

		w.outdent();
		w.appendLine("}");
	}

	private static void generateTransitionCount(StringWriter w, LTSminModel model) {
		w.appendLine("extern int spinja_get_transition_groups() {");
		w.indent();
		w.appendLine("return ",model.getTransitions().size(),";");
		w.outdent();
		w.appendLine("}");
	}

	private static void generateForwardDeclarations(StringWriter w) {
		w.appendLine("extern int spinja_get_successor_all( void* model, state_t *in, void (*callback)(void* arg, transition_info_t *transition_info, state_t *out), void *arg );");
		w.appendLine("extern int spinja_get_successor( void* model, int t, state_t *in, void (*callback)(void* arg, transition_info_t *transition_info, state_t *out), void *arg );");
	}

	private static void generateInitialState(StringWriter w, LTSminModel model) {
		// Generate initial state
		w.append(C_STATE_T +" "+ C_STATE_INITIAL +" = ("+ C_STATE_T +"){");
		
		LTSminStateElement last = model.sv.get(model.sv.size()-1);
		// Insert initial expression of each state element into initial state struct
		for(LTSminStateElement se : model.sv) {
			Variable v = se.getVariable();
			// If it is null, this location is probably a state descriptor
			// or priorityProcess variable so the initial state is 0
			if(v==null) {
				w.append("0");
			// If not null, then it's a legit variable, global or local,
			// so the initial state will be set to whatever the initial
			// expression is
			} else {
				if (v.getOwner() != null && //if this is the program counter of a process: //TODO: could be clearer:
					v.getName().equals(C_STATE_TMP + "." + LTSminTreeWalker.wrapName(v.getOwner().getName()))) {
					switch (v.getOwner().getNrActive()) {
					case 0: w.append("-1"); break; //use run to start this proc
					case 1: w.append("0"); break; //start at the initial state
					default: throw new AssertionError("active[n] not yet supported"); //TODO: extend state vector and introduce anonymous processes
					}
				} else {
					Expression e = v.getInitExpr();
					if(e==null) {
						if (v instanceof ChannelVariable && se.isFirst()) {
							ChannelVariable cv = (ChannelVariable)v;
							//{isRendezVous, nextRead, filled}
							w.append(cv.getType().getBufferSize()==0 ? "1" : "0");
							w.append(",0,0");
						} else {
							w.append("0");
						}
					} else {
						generateIntExpression(w, model, e, "UNKNOWN_INIT_VALUE");
					}
				}
			}
			if (se != last)	w.append(",");
		}
		w.appendLine("};");
		w.appendLine("");
		
		w.appendLine("extern void spinja_get_initial_state( state_t *to )");
		w.appendLine("{");
		w.indent();
		w.appendLine("if(state_size*",STATE_ELEMENT_SIZE," != sizeof(" + C_STATE_T + ")) { printf(\"state_t SIZE MISMATCH!: state=%i(%i) globals=%i\",sizeof(state_t),state_size*",STATE_ELEMENT_SIZE,",sizeof(state_globals_t)); }");
		w.appendLine("memcpy(to, (char*)&",C_STATE_INITIAL,", sizeof(" + C_STATE_T + "));");
		w.appendLine("to->",C_PRIORITY,".var = -1;");
		w.outdent();
		w.appendLine("}");
	}
	
	private static void generateACallback(StringWriter w, int trans) {
		w.appendLine("transition_info.group = "+ trans +";");
		w.appendLine("callback(arg,&transition_info,tmp);");
		w.appendLine("++states_emitted;");
	}

	private static class PCGuardTuple {
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

	private static PCGuardTuple lastPCG = null;
	private static LTSminGuard lastAG = null;
	
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
	
	private static void generateGetAll(StringWriter w, LTSminModel model) {
		w.appendLine("int spinja_get_successor_all( void* model, state_t *in, void (*callback)(void* arg, transition_info_t *transition_info, state_t *out), void *arg) {");
		w.indent();
		w.appendLine("transition_info_t transition_info = { NULL, -1 };");
		w.appendLine("(void)model; // ignore model");
		w.appendLine("int states_emitted = 0;");
		w.appendLine("register int pos;");
		w.appendLine(C_STATE_T," local_state;");
		w.appendLine(C_STATE_T,"* ",C_STATE_TMP," = &local_state;");
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
			generateATransition(w, t, trans, model);
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

	private static void generateATransition(StringWriter w, LTSminTransitionBase transition,
									int trans, LTSminModel model) {
		if(transition instanceof LTSminTransition) {
			LTSminTransition t = (LTSminTransition)transition;

			assert (t.getGuards().size() > 1); //assume there are two guards
			LTSminGuardBase gg = t.getGuards().get(0);
			PCGuardTuple curPCG = getPCGuard(gg);
			assert (curPCG != null); //assume the first guard is always the PC guard
			
			//assert (isAtomicGuard(t.getGuards().get(1))); //we dont need this, equals is flexible
			LTSminGuard curAG = (LTSminGuard)t.getGuards().get(1);
			
			boolean boundary = false;
			if (!curAG.equals(lastAG)) {
				if (lastAG != null) {
					w.outdent().appendLine("}");
					w.outdent().appendLine("}");
				}
				w.appendPrefix().append("if(");
				generateGuard(w, model, curAG);
				w.append(") {").appendPostfix().indent();
				lastAG = curAG;
				boundary = true;
			}
			
			if (boundary || !curPCG.equals(lastPCG)) {
				if (!boundary && lastPCG != null)
					w.outdent().appendLine("}");
				w.appendPrefix().append("if(");
				generateGuard(w, model, gg);
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
				generateGuard(w, model, g);
			}
			w.appendLine(") {");
			w.indent();
			w.appendLine("memcpy(", C_STATE_TMP,", ", IN_VAR , ", sizeof(", C_STATE_T,"));");
			List<Action> actions = t.getActions();
			for(Action a: actions) {
				generateAction(w,a,model);
			}
			generateACallback(w,trans);
			w.outdent();
			w.appendLine("}");
		} else if (transition instanceof LTSminTransitionCombo) {
			LTSminTransitionCombo t = (LTSminTransitionCombo)transition;
			for(LTSminTransitionBase tb: t.transitions) {
				generateATransition(w, tb, trans, model);
			}
		} else {
			w.appendLine("/** UNSUPPORTED: ",transition.getClass().getSimpleName()," **/");
		}
	}
	
	private static void generateGetNext(StringWriter w, LTSminModel model) {
		w.appendLine("int spinja_get_successor( void* model, int t, state_t *in, void (*callback)(void* arg, transition_info_t *transition_info, state_t *out), void *arg) {");
		w.indent();

		w.appendLine("transition_info_t transition_info = { NULL, t };");
		w.appendLine("(void)model; // ignore model");
		w.appendLine("register int pos;");
		w.appendLine(C_STATE_T," local_state;");
		w.appendLine(C_STATE_T,"* ",C_STATE_TMP," = &local_state;");
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
			generateTransition(w, t, model);
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

	public static void generateTransition(StringWriter w, LTSminTransitionBase transition,
								   LTSminModel model) {
		if(transition instanceof LTSminTransition) {
			LTSminTransition t = (LTSminTransition)transition;
			List<LTSminGuardBase> guards = t.getGuards();
			w.appendPrefix().append("if( true ");
			for(LTSminGuardBase g: guards) {
				w.appendPostfix().appendPrefix().append(" && ");
				generateGuard(w,model, g);
			}
			w.append(" ) {").appendPostfix();
			w.indent();
			w.appendLine("memcpy(", C_STATE_TMP, ", ", IN_VAR, ", sizeof(", C_STATE_T,"));");
			List<Action> actions = t.getActions();
			for(Action a: actions) {
				generateAction(w,a, model);
			}
			generateCallback(w);
			w.outdent();
			w.appendLine("}");
		} else if (transition instanceof LTSminTransitionCombo) {
			LTSminTransitionCombo t = (LTSminTransitionCombo)transition;
			for(LTSminTransitionBase tb: t.transitions) {
				generateTransition(w, tb, model);
			}
		} else {
			w.appendLine("/** UNSUPPORTED: ",transition.getClass().getSimpleName()," **/");
		}
	}

	private static void generateGuard(StringWriter w, LTSminModel model, LTSminGuardBase guard) {
		if(guard instanceof LTSminGuard) {
			LTSminGuard g = (LTSminGuard)guard;
			generateBoolExpression(w, model, g.expr, IN_ACCESS);
		} else if(guard instanceof LTSminGuardNand) {
			LTSminGuardNand g = (LTSminGuardNand)guard;
			w.append("!( true");
			for(LTSminGuardBase gb: g.guards) {
				w.append(" && ");
				generateGuard(w,model, gb);
			}
			w.append(")");
		} else if(guard instanceof LTSminGuardAnd) {
			LTSminGuardAnd g = (LTSminGuardAnd)guard;
			w.append("( true");
			for(LTSminGuardBase gb: g.guards) {
				w.append(" && ");
				generateGuard(w,model, gb);
			}
			w.append(")");
		} else if(guard instanceof LTSminGuardOr) {
			LTSminGuardOr g = (LTSminGuardOr)guard;
			w.append("( false");
			for(LTSminGuardBase gb: g.guards) {
				w.append(" || ");
				generateGuard(w,model, gb);
			}
			w.append(")");
		} else {
			w.appendLine("/** UNSUPPORTED: ",guard.getClass().getSimpleName()," **/");
		}
	}

	private static void generateAction(StringWriter w, Action a, LTSminModel model) {
		// Handle assignment action
		if(a instanceof AssignAction) { //TODO: assign + expr + runexp
			AssignAction as = (AssignAction)a;
			Identifier id = as.getIdentifier();
			final String mask = id.getVariable().getType().getMask();
			switch (as.getToken().kind) {
				case ASSIGN:
					try {
						int value = as.getExpr().getConstantValue();
						w.appendPrefix();
						generateIntExpression(w, model, id, TMP_ACCESS);
						w.append(" = ").append(value & id.getVariable().getType().getMaskInt()).append(";");
						w.appendPostfix();
					} catch (ParseException ex) {
						// Could not get Constant value
						w.appendPrefix();
						generateIntExpression(w, model, id, TMP_ACCESS);
						w.append(" = ");
						generateIntExpression(w, model, as.getExpr(), IN_ACCESS);
						w.append((mask == null ? "" : " & " + mask));
						w.append(";");
						w.appendPostfix();
					}
					break;
				case INCR:
					if (mask == null) {
						w.appendPrefix();
						generateIntExpression(w, model, id, TMP_ACCESS);
						w.append("++;");
						w.appendPostfix();
					} else {
						w.appendPrefix();
						generateIntExpression(w, model, id, TMP_ACCESS);
						w.append(" = (");
						generateIntExpression(w, model, id, IN_ACCESS);
						w.append(" + 1) & ").append(mask).append(";");
						w.appendPostfix();
					}
					break;
				case DECR:
					if (mask == null) {
						w.appendPrefix();
						generateIntExpression(w, model, id, TMP_ACCESS);
						w.append("--;");
						w.appendPostfix();
					} else {
						w.appendPrefix();
						generateIntExpression(w, model, id, TMP_ACCESS);
						w.appendLine(" = (");
						generateIntExpression(w, model, id, IN_ACCESS);
						w.append(" - 1) & ");
						w.append(mask);
						w.append(";");
						w.appendPostfix();
					}
					break;
				default:
					throw new AssertionError("unknown assignment type");
			}

		} else if(a instanceof ResetProcessAction) {
			ResetProcessAction rpa = (ResetProcessAction)a;
			rpa.getProcess();
			String name = LTSminTreeWalker.wrapName(rpa.getProcess().getName());
			w.appendLine("#ifndef NORESETPROCESS");
			w.appendLine("memset(&",TMP_ACCESS,name,",-1,sizeof(state_",name,"_t));");
			w.appendLine("#endif");

		// Handle assert action
		} else if(a instanceof AssertAction) {
			AssertAction as = (AssertAction)a;
			Expression e = as.getExpr();

			w.appendPrefix();
			w.append("if(!");
			generateBoolExpression(w, model, e, IN_ACCESS);
			w.append(") {");
			w.appendPostfix();
			w.indent();
			w.appendLine("printf(\"Assertion violated: ",as.getExpr().toString(), "\\n\");");
			w.appendLine("print_state(",C_STATE_TMP,");");
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
				generateIntExpression(w, model, expr, IN_ACCESS);
			}
			w.append(");").appendPostfix();

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
					RunExpression re = (RunExpression)expr;
					Proctype target = re.getSpecification().getProcess(re.getId()); //TODO: anonymous processes (multiple runs on one proctype)

					//only one dynamic process supported atm
					w.appendLine("if (-1 != "+ getPC(target.getName(), IN_ACCESS) +") {");
					w.appendLine("	printf (\"SpinJa only supports a maximum " +
							"one dynamic process creation and only for " +
							"nonactive proctypes.\\n\");");
					w.appendLine("	printf (\"Exiting on '"+ re +"'.\\n\");");
					w.appendLine("	exit (1);");
					w.appendLine("}");
					
					//activate process
					Action ae;
					ae = LTSminTreeWalker.assign(model.sv.procId(target), 0);
					generateAction(w, ae, model);
					w.appendLine("++("+ TMP_NUM_PROCS +");");
					
					List<Variable> args = target.getArguments();
					Iterator<Expression> eit = re.getExpressions().iterator();
					if (args.size() != re.getExpressions().size())
						throw exception("Run expression's parameters do not match the proc's arguments.", re.getToken());
					//write to the arguments of the target process
					for (Variable v : args) {
						Expression e = eit.next();
						//channels are passed by reference: TreeWalker.bindByReferenceCalls 
						if (!(v.getType() instanceof ChannelType)) {
							ae = LTSminTreeWalker.assign(v, e);
							generateAction(w, ae, model);
						}
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
				String access;
				String access_buffer;
				if(var.getOwner()==null) {
					access = TMP_ACCESS_GLOBALS + LTSminTreeWalker.wrapNameForChannelDesc(var.getName());
					access_buffer = TMP_ACCESS_GLOBALS + LTSminTreeWalker.wrapNameForChannelBuffer(var.getName()) + "[pos]";
				} else {
					access = TMP_ACCESS + var.getOwner().getName() + "." + LTSminTreeWalker.wrapNameForChannelDesc(var.getName());
					access_buffer = TMP_ACCESS + var.getOwner().getName() + "." + LTSminTreeWalker.wrapNameForChannelBuffer(var.getName()) + "[pos]";
				}

				w.appendLine("pos = (" + access + ".nextRead + "+access+".filled) % "+var.getType().getBufferSize() + ";");

				List<Expression> exprs = csa.getExprs();
				for (int i = 0; i < exprs.size(); i++) {
					final Expression expr = exprs.get(i);
					w.appendPrefix();
					w.append(access_buffer).append(".m").append(i).append(".var = ");
					generateIntExpression(w, model, expr, IN_ACCESS);
					w.append(";");
					w.appendPostfix();

				}

				w.appendLine("++(",access, ".filled);");
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
					access = TMP_ACCESS_GLOBALS + LTSminTreeWalker.wrapNameForChannelDesc(var.getName());
					access_buffer = TMP_ACCESS_GLOBALS + LTSminTreeWalker.wrapNameForChannelBuffer(var.getName()) + "[pos]";
				} else {
					access = TMP_ACCESS + var.getOwner().getName() + "." + LTSminTreeWalker.wrapNameForChannelDesc(var.getName());
					access_buffer = TMP_ACCESS + var.getOwner().getName() + "." + LTSminTreeWalker.wrapNameForChannelBuffer(var.getName()) + "[pos]";
				}
				w.appendLine("pos = "+ access +".nextRead;");
				List<Expression> exprs = cra.getExprs();
				for (int i = 0; i < exprs.size(); i++) {
					final Expression expr = exprs.get(i);
					if (expr instanceof Identifier) {
						w.appendPrefix();
						generateIntExpression(w, model, expr, TMP_ACCESS);
						w.append(" = ").append(access_buffer).append(".m").append(i).append(".var");
						w.append(";");
						w.appendPostfix();
						w.appendPrefix();
						w.append(access_buffer).append(".m").append(i).append(".pad = 0;");
						w.appendPostfix();
					}
				}

				w.appendLine(access,".nextRead = (",access,".nextRead+1)%"+var.getType().getBufferSize()+";");
				w.appendLine("--(",access, ".filled);");
			} else {
				throw new AssertionError("Trying to actionise rendezvous receive!");
			}

		// Handle not yet implemented action
		} else {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+a.getClass().getName());
		}
	}

	private static ParseException exception(String string, Token token) {
		return new ParseException(string + " At line "+token.beginLine +"column "+ token.beginColumn +".");
	}

	private static void generateCallback(StringWriter w) {
		w.appendLine("callback(arg,&transition_info,tmp);");
		w.appendLine("return ",1,";");
	}

	private static void generateIntExpression(StringWriter w, LTSminModel model, Expression e, String access) {
		//w.append("|").append(e.getClass().getSimpleName()).append("|");
		if(e instanceof PCExpression) {
			PCExpression pc = (PCExpression)e;
			w.append(getPC(pc.getProcessName(), access));
		} else if(e instanceof PriorityExpression) {
			w.append(access + ACCESS_PRIORITY).append(".var");
		} else if(e instanceof PCIdentifier) {
			PCIdentifier pc = (PCIdentifier)e;
			w.append(access).append(LTSminTreeWalker.wrapName(pc.getProcess().getName())).append(".pc.var");
		} else if(e instanceof PriorityIdentifier) {
			w.append(access + ACCESS_PRIORITY).append(".var");
		} else if(e instanceof ChannelSizeExpression) {
			ChannelSizeExpression cse = (ChannelSizeExpression)e;
			Variable var = cse.getVariable();
			w.append(access);
			if(var.getOwner()==null) {
				w.append("globals.");
			} else {
				w.append(LTSminTreeWalker.wrapName(var.getOwner().getName()));
				w.append(".");
			}
			w.append(LTSminTreeWalker.wrapName(cse.getVariable().getName())).append(".filled");
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
						w.append(LTSminTreeWalker.wrapName(var.getOwner().getName()));
						w.append(".");
					}
					w.append(LTSminTreeWalker.wrapName(var.getName()));
					w.append("[");
					generateIntExpression(w,model,arrayExpr, access);
					w.append("].var");
				} else {
					w.append(access);
					if(var.getOwner()==null) {
						w.append("globals.");
					} else {
						w.append(LTSminTreeWalker.wrapName(var.getOwner().getName()));
						w.append(".");
					}
					w.append(LTSminTreeWalker.wrapName(var.getName()));
					w.append("[0].var");
				}
			} else {
				w.append(access);
				if(var.getOwner()==null) {
					w.append("globals.");
				} else {
					w.append(LTSminTreeWalker.wrapName(var.getOwner().getName()));
					w.append(".");
				}
				w.append(LTSminTreeWalker.wrapName(var.getName()));
				w.append(".var");
			}
		} else if(e instanceof AritmicExpression) {
			AritmicExpression ae = (AritmicExpression)e;
			Expression ex1 = ae.getExpr1();
			Expression ex2 = ae.getExpr2();
			Expression ex3 = ae.getExpr3();
			if (ex2 == null) {
				w.append("(").append(ae.getToken().image);
				generateIntExpression(w, model, ex1, access);
				w.append(")");
			} else if (ex3 == null) {
				if (ae.getToken().image.equals("%")) {
					// Modulo takes a special notation to make sure that it
					// returns a positive value
					w.append("abs(");
					generateIntExpression(w, model, ex1, access);
					w.append(" % ");
					generateIntExpression(w, model, ex2, access);
					w.append(")");
				} else {

					w.append("(");
					generateIntExpression(w, model, ex1, access);
					w.append(" ").append(ae.getToken().image).append(" ");
					generateIntExpression(w, model, ex2, access);
					w.append(")");
				}
			} else {
				w.append("(");
				generateBoolExpression(w, model, ex1, access);
				w.append(" ? ");
				generateIntExpression(w, model, ex2, access);
				w.append(" : ");
				generateIntExpression(w, model, ex3, access);
				w.append(")");
			}
		} else if(e instanceof BooleanExpression) {
			BooleanExpression be = (BooleanExpression)e;
			Expression ex1 = be.getExpr1();
			Expression ex2 = be.getExpr2();
			if (ex2 == null) {
				w.append("(").append(be.getToken().image);
				generateIntExpression(w, model, ex1, access);
				w.append( " ? 1 : 0)");
			} else {
				w.append("(");
				generateIntExpression(w, model, ex1, access);
				w.append(" ").append(be.getToken().image).append(" ");
				generateBoolExpression(w,model, ex2, access);
				w.append(" ? 1 : 0)");
			}
		} else if(e instanceof ChannelLengthExpression) {
			ChannelLengthExpression cle = (ChannelLengthExpression)e;
			Identifier id = (Identifier)cle.getExpression();
			Variable var = id.getVariable();
			String name = wrapNameForChannelDesc(model.sv.getDescr(var));
			generateIntExpression(w, model, new ChannelSizeExpression(var,name), access);
		} else if(e instanceof ChannelOperation) {
			ChannelOperation co = (ChannelOperation)e;
			String name = co.getToken().image;
			Identifier id = (Identifier)co.getExpression();
			Variable var = id.getVariable();
			VariableType type = var.getType();
			if (!(type instanceof ChannelType) || ((ChannelType)type).getBufferSize()==-1)
				throw LTSminTreeWalker.error("Unknown channel length of channel "+ var.getName(), co.getToken());
			int buffer = ((ChannelType)type).getBufferSize();
			w.append("(");
			String n = wrapNameForChannelDesc(model.sv.getDescr(var));
			generateIntExpression(w, model, new ChannelSizeExpression(var,n), access);
			if (name.equals("empty")) {
				w.append("== 0");
			} else if (name.equals("nempty")) {
				w.append("!= 0");
			} else if (name.equals("full")) {
				w.append("=="+ buffer);
			} else if (name.equals("nfull")) {
				w.append("!="+ buffer);
			}
			w.append(")");
		} else if(e instanceof CompareExpression) {
			CompareExpression ce = (CompareExpression)e;
			w.append("(");
			generateIntExpression(w, model, ce.getExpr1(), access);
			w.append(" ").append(ce.getToken().image).append(" ");
			generateIntExpression(w, model, ce.getExpr2(), access);
			w.append(" ? 1 : 0)");
		} else if(e instanceof ConstantExpression) {
			ConstantExpression ce = (ConstantExpression)e;
			switch (ce.getToken().kind) {
				case TRUE:
					w.append("1");
					break;
				case FALSE:
					w.append("0");
					break;
				case SKIP_:
					w.append("1");
					break;
				case NUMBER:
					w.append(Integer.toString(ce.getNumber()));
					break;
				default:
					w.append("1");
					break;
			}
		} else if(e instanceof ChannelTopExpression) {
			ChannelTopExpression cte = (ChannelTopExpression)e;
			ChannelReadAction cra =cte.getChannelReadAction();
			ChannelVariable var = (ChannelVariable)cra.getVariable();
			String chan_access = TMP_ACCESS_GLOBALS + LTSminTreeWalker.wrapNameForChannelDesc(var.getName());
			String access_buffer = TMP_ACCESS_GLOBALS + LTSminTreeWalker.wrapNameForChannelBuffer(var.getName()) + "[" + chan_access + ".nextRead]";
			w.append(access_buffer).append(".m").append(cte.getElem()).append(".var");
		} else if(e instanceof EvalExpression) {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else if(e instanceof MTypeReference) {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else if(e instanceof RunExpression) {
			//we define the "instantiation number" as: next_pid+1 (http://spinroot.com/spin/Man/run.html)
			//otherwise the first process can never be started if all proctypes are nonactive.
			w.append("("+ IN_NUM_PROCS +" != "+ (PM_MAX_PROCS-1)
					 	+" ? " + IN_NUM_PROCS +"+1 : 0)");
		} else if(e instanceof CompoundExpression) {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		}  else if(e instanceof TimeoutExpression) {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		}
	}
	
	private static void generateBoolExpression(StringWriter w, LTSminModel model,
											   Expression e, String access) {
		if(e instanceof Identifier) {
			w.append("(");
			generateIntExpression(w, model, e, access);
			w.append(" != 0 )");
		} else if(e instanceof AritmicExpression) {
			AritmicExpression ae = (AritmicExpression)e;
			Expression ex1 = ae.getExpr1();
			Expression ex2 = ae.getExpr2();
			Expression ex3 = ae.getExpr3();
			if (ex2 == null) {
				w.append("(").append(ae.getToken().image);
				generateIntExpression(w, model, ex1, access);
				w.append(" != 0)");
			} else if (ex3 == null) {
				w.append("(");
				generateIntExpression(w, model, ex1, access);
				w.append(" ").append(ae.getToken().image).append(" ");
				generateIntExpression(w, model, ex2, access);
				w.append(" != 0)");
			} else { // Can only happen with the x?1:0 expression
				w.append("(");
				generateBoolExpression(w, model, ex1, access);
				w.append(" ? ");
				generateBoolExpression(w, model, ex2, access);
				w.append(" : ");
				generateBoolExpression(w, model, ex3, access);
				w.append(")");
			}
		} else if(e instanceof BooleanExpression) {
			BooleanExpression be = (BooleanExpression)e;
			Expression ex1 = be.getExpr1();
			Expression ex2 = be.getExpr2();
			if (ex2 == null) {
				w.append("(").append(be.getToken().image);
				generateBoolExpression(w, model, ex1, access);
				w.append(")");
			} else {
				w.append("(");
				generateBoolExpression(w, model, ex1, access);
				w.append(" ").append(be.getToken().image).append(" ");
				generateBoolExpression(w, model, ex2, access);
				w.append(")");
			}
		} else if(e instanceof ChannelLengthExpression) {
			ChannelLengthExpression cle = (ChannelLengthExpression)e;
			Identifier id = (Identifier)cle.getExpression();
			Variable var = id.getVariable();
			w.append("(");
			String name = wrapNameForChannelDesc(model.sv.getDescr(var));
			generateIntExpression(w, model, new ChannelSizeExpression(var, name), access);
			w.append(" != 0)");
		} else if(e instanceof ChannelOperation) {
			generateIntExpression(w, model, e, access);
		} else if(e instanceof CompareExpression) {
			CompareExpression ce = (CompareExpression)e;
			w.append("(");
			generateIntExpression(w, model, ce.getExpr1(), access);
			w.append(" ").append(ce.getToken().image).append(" ");
			generateIntExpression(w, model, ce.getExpr2(), access);
			w.append(")");
		} else if(e instanceof CompoundExpression) {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else if(e instanceof ConstantExpression) {
			ConstantExpression ce = (ConstantExpression)e;
			switch (ce.getToken().kind) {
				case TRUE:
					w.append("true");
					break;
				case FALSE:
					w.append("false");
					break;
				case SKIP_:
					w.append("true");
					break;
				case NUMBER:
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
			w.append("("+ IN_NUM_PROCS +" != "+ (PM_MAX_PROCS-1) +")");
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

	private static void generateDepMatrix(StringWriter w, DepMatrix dm, String name) {
		//LTSMinPrinter.DepMatrix dm = model.getDepMatrix();

		if(dm==null) throw new AssertionError("DM is null!");

		w.appendPrefix().append("int ").append(name).append("[][2][").append(dm.getRowLength()).appendLine("] = {");
		w.indent();
		w.appendLine("// { ... read ...}, { ... write ...}");

		// Iterate over all the rows
		int t=0;
		for(;;) {
			w.appendPrefix();
			w.append("{{");
			DepRow dr = null;
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
				w.append("  // ").append(t).appendPostfix();
				break;
			}
			w.append(", // ").append(t).appendPostfix();
			++t;
		}

		w.outdent();

		// Close array
		w.appendLine("};");

	}

	private static void generateDMFunctions(StringWriter w, DepMatrix dm) {
		// Function to access the dependency matrix
		w.appendLine("");
		w.appendLine("extern const int* spinja_get_transition_read_dependencies(int t)");
		w.appendLine("{");
		w.append("	if (t>=0 && t < ").append(dm.getRows()).appendLine(") return ").append(DM_NAME).append("[t][0];");
		w.appendLine("	return NULL;");
		w.appendLine("}");
		w.appendLine("");
		w.appendLine("extern const int* spinja_get_transition_write_dependencies(int t)");
		w.appendLine("{");
		w.append("	if (t>=0 && t < ").append(dm.getRows()).appendLine(") return ").append(DM_NAME).append("[t][1];");
		w.appendLine("	return NULL;");
		w.appendLine("}");
	}

	private static void generateStateDescriptors(StringWriter w, LTSminModel model) {

		int state_size = model.sv.size();
		List<LTSminStateElement> state = model.sv.getStateVector();

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
				int idx = types.indexOf(C_TYPE_PROC_COUNTER_);
				if(idx<0) {
					types.add(C_TYPE_PROC_COUNTER_);
					idx = types.size()-1;
				}
				translation[i++] = idx;
			} else if(var.getArraySize()>1) {
				int idx = types.indexOf(getCTypeOfVar(var).type);
				if(idx<0) {
					types.add(getCTypeOfVar(var).type);
					idx = types.size()-1;
				}
				for(int end=i+var.getArraySize();i<end;) {
					translation[i++] = idx;
				}
			} else {
				int idx = types.indexOf(getCTypeOfVar(var).type);
				if(idx<0) {
					types.add(getCTypeOfVar(var).type);
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

	private static void generateGuardMatrix(StringWriter w, LTSminModel model) {
		GuardMatrix gm = model.getGuardMatrix();
		if(gm==null) return;

		List<List<Integer>> co_matrix = gm.getCoMatrix();
		List<List<LTSminGuardBase>> trans_matrix = gm.getTransMatrix();
		List<LTSminGuardBase> guards = gm.getGuards();
		
		generateGuardList(w, model, guards);

		w.appendLine("");
		w.appendLine("// Guard-Dependency Matrix:");
//		for(int g=0; g<dp_matrix.size(); ++g) {
//			w.appendPrefix();
//
//			List<Integer> row = dp_matrix.get(g);
//
//			for(int s=0; s<row.size(); ++s) {
//				w.append(row.get(s)).append(", ");
//			}
//
//			w.appendPostfix();
//		}
		LTSminPrinter.generateDepMatrix(w,gm.getDepMatrix2(),GM_DM_NAME);
		w.appendLine("");
		
		generateCoEnabledMatrix(w, co_matrix);
		
		generateTransGuardMatrix(w, trans_matrix, guards);
	}

	private static void generateGuardList(StringWriter w, LTSminModel model,
										  List<LTSminGuardBase> guards) {
		w.appendLine("/*");
		String old_preprefix = w.getPrePrefix();
		w.setPrePrefix(" * ");
		w.appendLine("");
		w.appendLine("Guard list:");
		for(int g=0; g<guards.size(); ++g) {
			w.appendPrefix();
			w.append("  - ");
			w.append(g);
			w.append(" - ");
			LTSminPrinter.generateGuard(w, model, guards.get(g));
			w.appendPostfix();
//			w.appendLine("  - ",
//			w.appendLine("  - ",guards.get(g).toString());
		}
		w.setPrePrefix(old_preprefix);
		w.appendLine(" */");
	}

	private static void generateCoEnabledMatrix(StringWriter w,
												List<List<Integer>> co_matrix) {
		w.appendLine("// Co-Enabled Matrix:");
		w.appendPrefix().append("int gm_coen[][");
		w.append(co_matrix.size());
		w.append("] = {").appendPostfix();
		w.indent();

		for(int g=0; g<co_matrix.size(); ++g) {

			//w.append(String.format("%07d",g));
			//w.append("[] = {");
			w.appendPrefix().append("{");
			
			List<Integer> row = co_matrix.get(g);

			if(row.size()>0) {
				w.append(row.get(0));
				for(int s=1; s<row.size(); ++s) {
					w.append(", ").append(row.get(s));
				}
			}

			w.append(" },").appendPostfix();

		}

		w.appendLine("{0}");
		w.outdent();
		w.appendLine("};");
		w.appendLine("");
	}

	private static void generateTransGuardMatrix(StringWriter w,
			List<List<LTSminGuardBase>> trans_matrix,
			List<LTSminGuardBase> guards) {
		w.appendLine("// Transition-Guard Matrix:");
		w.appendPrefix().append("int* gm_trans[").append(trans_matrix.size()).append("] = {");
		w.appendPostfix();
		for(int g=0; g<trans_matrix.size(); ++g) {
			w.append("((int[]){");
			List<LTSminGuardBase> row = trans_matrix.get(g);

			w.append(" ").append(row.size());

			for(int s=0; s<row.size(); ++s) {
				w.append(", ").append(guards.indexOf(row.get(s)));
			}

			w.append(" })");
			if(g<trans_matrix.size()-1) w.append(",");
			w.append("\t// trans ").append(String.format("%5d",g));
			w.appendPostfix();
		}
		w.appendLine("};");
		w.appendLine("");
	}

	private static void generateGuardFunctions(StringWriter w, LTSminModel model, GuardMatrix gm) {
		List<LTSminGuardBase> guards = gm.getGuards();

		w.appendLine("int spinja_get_guard_count() {");
		w.indent();
		w.appendLine("return ",gm.getGuards().size(),";");
		w.outdent();
		w.appendLine("}");
		w.appendLine("");

		w.appendLine("const int* spinja_get_guards(int t) {");
		w.indent();
		w.appendLine("assert(t < ",gm.getTransMatrix().size()," && \"spinja_get_guards: invalid transition\");");
		w.appendLine("return gm_trans[t];");
		w.outdent();
		w.appendLine("}");
		w.appendLine("");

		w.appendLine("const int*** spinja_get_all_guards() {");
		w.indent();
		w.appendLine("return (const int***)&gm_trans;");
		w.outdent();
		w.appendLine("}");
		w.appendLine("");
		w.appendLine("const int* spinja_get_guard_may_be_coenabled_matrix(int g) {");
		w.indent();
		w.appendLine("assert(g < ",gm.getGuards().size()," && \"spinja_get_guards: invalid guard\");");
		w.appendLine("return gm_coen[g];");
		w.outdent();
		w.appendLine("}");
		w.appendLine("");

		w.appendLine("bool spinja_get_guard(void* model, int g, ",C_STATE_T,"* ",IN_VAR,") {");
		w.indent();
		w.appendLine("assert(g < ",gm.getGuards().size()," && \"spinja_get_guards: invalid guard\");");
		w.appendLine("(void)model;");
		w.appendLine("switch(g) {");
		w.indent();
		for(int g=0; g<guards.size(); ++g) {
			w.appendPrefix();
			w.append("case ").append(g).append(": return ");
			LTSminPrinter.generateGuard(w, model, guards.get(g));
			w.append(";");
			w.appendPostfix();
		}
		w.outdent();
		w.appendLine("}");
		w.appendLine("return false;");
		w.outdent();
		w.appendLine("}");
		w.appendLine("");

		w.appendLine("void spinja_get_guard_all(void* model, ",C_STATE_T,"* ",IN_VAR,", int* guard) {");
		w.indent();
		w.appendLine("(void)model;");
		for(int g=0; g<guards.size(); ++g) {
			w.appendPrefix();
			w.append("guard[").append(g).append("] = ");
			LTSminPrinter.generateGuard(w, model, guards.get(g));
			w.append(";");
			w.appendPostfix();
		}
		w.outdent();
		w.outdent();
		w.appendLine("}");
		w.appendLine("");
	}

	/**
	 * Generates various typedefs for types, to pad the data to
	 * the element size of the state vector.
	 * Model independent.
	 * @param w The StringWriter to which the code is written.
	 */
	private static void generateTypeStructs(StringWriter w) {

		w.appendLine("typedef union ",C_TYPE_INT1," {");
		w.indent();
		w.appendLine("int pad;");
		w.appendLine("unsigned int var:1;");
		w.outdent();
		w.appendLine("} ",C_TYPE_INT1,";");

		w.appendLine("typedef union ",C_TYPE_INT8," {");
		w.indent();
		w.appendLine("int pad;");
		w.appendLine("char var;");
		w.outdent();
		w.appendLine("} ",C_TYPE_INT8,";");

		w.appendLine("typedef union ",C_TYPE_INT16," {");
		w.indent();
		w.appendLine("int pad;");
		w.appendLine("short var;");
		w.outdent();
		w.appendLine("} ",C_TYPE_INT16,";");

		w.appendLine("typedef union ",C_TYPE_INT32," {");
		w.indent();
		w.appendLine("int pad;");
		w.appendLine("int var;");
		w.outdent();
		w.appendLine("} ",C_TYPE_INT32,";");

		w.appendLine("typedef union ",C_TYPE_UINT8," {");
		w.indent();
		w.appendLine("int pad;");
		w.appendLine("unsigned char var;");
		w.outdent();
		w.appendLine("} ",C_TYPE_UINT8,";");

		w.appendLine("typedef union ",C_TYPE_UINT16," {");
		w.indent();
		w.appendLine("int pad;");
		w.appendLine("unsigned short var;");
		w.outdent();
		w.appendLine("} ",C_TYPE_UINT16,";");

		w.appendLine("typedef union ",C_TYPE_UINT32," {");
		w.indent();
		w.appendLine("int pad;");
		w.appendLine("unsigned int var;");
		w.outdent();
		w.appendLine("} ",C_TYPE_UINT32,";");

		w.appendLine("typedef struct ",C_TYPE_CHANNEL," {");
		w.indent();
		w.appendLine("unsigned int isRendezVous: 2;");
		w.appendLine("unsigned int nextRead: 15;");
		w.appendLine("unsigned int filled: 15;");
		w.outdent();
		w.appendLine("} ",C_TYPE_CHANNEL,";");
	}
}

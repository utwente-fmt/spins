package spinja.promela.compiler.ltsmin;

import static spinja.promela.compiler.ltsmin.LTSminStateVector.C_NUM_PROCS_VAR;
import static spinja.promela.compiler.ltsmin.LTSminStateVector.C_STATE_GLOBALS;
import static spinja.promela.compiler.ltsmin.LTSminStateVector.C_STATE_INITIAL;
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
import static spinja.promela.compiler.parser.PromelaConstants.ASSIGN;
import static spinja.promela.compiler.parser.PromelaConstants.DECR;
import static spinja.promela.compiler.parser.PromelaConstants.FALSE;
import static spinja.promela.compiler.parser.PromelaConstants.INCR;
import static spinja.promela.compiler.parser.PromelaConstants.NUMBER;
import static spinja.promela.compiler.parser.PromelaConstants.SKIP_;
import static spinja.promela.compiler.parser.PromelaConstants.TRUE;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.ArrayList;
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
import spinja.promela.compiler.ltsmin.instr.DepRow;
import spinja.promela.compiler.ltsmin.instr.GuardInfo;
import spinja.promela.compiler.ltsmin.instr.ResetProcessAction;
import spinja.promela.compiler.parser.ParseException;
import spinja.promela.compiler.parser.Token;
import spinja.promela.compiler.variable.ChannelType;
import spinja.promela.compiler.variable.ChannelVariable;
import spinja.promela.compiler.variable.Variable;
import spinja.promela.compiler.variable.VariableType;
import spinja.util.StringWriter;

import static spinja.promela.compiler.ltsmin.LTSminTreeWalker.*;

/**
 * Generates C code from the LTSminModel
 * @author FIB, Alfons Laarman
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
	public static final int    PM_MAX_PROCS = 256;

	private static String getPC(Proctype p, String access) {
		return access + p.getName() +"."+ C_STATE_PROC_COUNTER +".var";
	}

	public static final String DM_NAME = "transition_dependency";
	public static final String GM_DM_NAME = "gm_dm";
	public static final String CO_DM_NAME = "co_dm";
	public static final String GM_TRANS_NAME = "gm_trans";

	public static String generateCode(LTSminModel model) {
		StringWriter w = new StringWriter();
		LTSminPrinter.generateModel(w, model);
		return w.toString();
	}
	
	private static void generateModel(StringWriter w, LTSminModel model) {
		generateHeader(w,model);
		generateTypeStructs(w);
		for(LTSminType t : model.getTypes()) {
			generateTypeDef(w, t);
		}
		generateForwardDeclarations(w);
		generateStateCount(w,model);
		generateInitialState(w,model);
		generateLeavesAtomic(w, model);
		generateReach(w, model);
		generateGetNext(w,model);
		generateGetAll(w,model);
		generateTransitionCount(w, model);
		generateDepMatrix(w, model.getDepMatrix(), DM_NAME, true);
		generateDMFunctions(w, model.getDepMatrix());
		generateGuardMatrix(w, model);
		generateGuardFunctions(w, model, model.getGuardInfo());
		generateStateDescriptors(w, model);
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
		generateHashTable(w, model);		
		w.appendLine("");
		w.appendLine("typedef struct transition_info");
		w.appendLine("{");
		w.indent();
		w.appendLine("int* label;");
		w.appendLine("int  group;");
		w.outdent();
		w.appendLine("} transition_info_t;");
	}
	
	public static <T> String readTextFile(Class<T> clazz, String resourceName) throws IOException {
	  	StringBuffer sb = new StringBuffer(1024);
		BufferedInputStream inStream = new BufferedInputStream(clazz.getResourceAsStream(resourceName));
	  	byte[] chars = new byte[1024];
	  	int bytesRead = 0;
	  	while( (bytesRead = inStream.read(chars)) > -1){
	  		//System.out.println(bytesRead);
	  		sb.append(new String(chars, 0, bytesRead));
		}
	  	inStream.close();
	  	return sb.toString();
	}
	
	private static void generateHashTable(StringWriter w, LTSminModel model) {
		try {
			w.appendLine(readTextFile(model.getClass(), "hashtable.c"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void generateReach(StringWriter w, LTSminModel model) {
		try {
			w.appendLine(readTextFile(model.getClass(), "reach.c"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void generateStateCount(StringWriter w, LTSminModel model) {
		w.appendLine("int ",C_STATE_SIZE," = ",model.sv.size(),";");
		w.appendLine("extern int spinja_get_state_size() {");
		w.indent();
		w.appendLine("return ",model.sv.size(),";");
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
		for (LTSminStateElement se : model.sv) {
			Variable v = se.getVariable();
			assert (v!=null);
			Expression e = v.getInitExpr();
			if (e==null) {
				if (v instanceof ChannelVariable && se.isMetaData()) {
					//{nextRead, filled}
					w.append("0,0");
				} else {
					w.append("0");
				}
			} else {
				try {
					w.append(e.getConstantValue());
				} catch (ParseException pe) {
					throw new AssertionError("Cannot parse inital value of state vector: "+ e);
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
		w.outdent();
		w.appendLine("}");
	}
	
	private static void generateACallback(StringWriter w, int trans) {
		w.appendLine("transition_info.group = "+ trans +";");
		w.appendLine("callback(arg,&transition_info,tmp);");
		w.appendLine("++states_emitted;");
	}

	private static void generateLeavesAtomic (StringWriter w, LTSminModel model) {
		List<LTSminTransitionBase> ts = model.getTransitions();
		w.append("char leaves_atomic["+ ts.size() +"] = {");
		if (ts.size() > 0) {
			LTSminTransitionBase last = ts.get(ts.size()-1);
			for (LTSminTransitionBase tb : ts) {
				LTSminTransition t = (LTSminTransition)tb;
				w.append("" + t.leavesAtomic());
				if (tb != last)	w.append(", ");
			}
		}
		w.appendLine("};");
	}
	
	private static void generateGetAll(StringWriter w, LTSminModel model) {
		w.appendLine("int spinja_get_successor_all( void* model, state_t *in, void (*callback)(void* arg, transition_info_t *transition_info, state_t *out), void *arg) {");
		w.indent();
			w.appendLine("state_t out;");
			w.appendLine("spinja_get_successor_all2(model, in, callback, arg, &out, -1);");
		w.outdent();
		w.appendLine("}");
		w.appendLine("");
		w.appendLine("int spinja_get_successor_all2( void* model, state_t *in, void (*callback)(void* arg, transition_info_t *transition_info, state_t *out), void *arg, state_t *tmp, int atomic) {");
		w.indent();
			w.appendLine("transition_info_t transition_info = { NULL, -1 };");
			w.appendLine("int states_emitted = 0;");
			w.appendLine("register int pos;");
			w.appendLine();
			List<LTSminTransitionBase> transitions = model.getTransitions();
			for(LTSminTransitionBase t : transitions) {
				generateATransition(w, t, model);
			}
			w.appendLine("return states_emitted;");
		w.outdent();
		w.appendLine("}");
		w.appendLine();
	}

	public static void generateATransition(StringWriter w, LTSminTransitionBase transition,
										   LTSminModel model) {
		w.appendLine("// "+transition.getName());
		if(transition instanceof LTSminTransition) {
			LTSminTransition t = (LTSminTransition)transition;

			w.appendPrefix().append("if (true");
			for(LTSminGuardBase g: t.getGuards()) {
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
			if (t.isAtomic()) {
				w.appendLine("if (-1!=atomic) {");
					w.indent();
					generateACallback(w,transition.getGroup());
					w.outdent();
				w.appendLine("} else {");
				w.indent();
					String pid = wrapVar(TMP_ACCESS, model.sv.getPID( t.getProcess() ));
					w.appendLine("transition_info.group = "+ transition.getGroup() +";");
					w.appendLine("int count = reach (model, &transition_info, tmp, callback, arg, "+ pid +".var);");
					w.appendLine("states_emitted += count;");
				w.outdent();
				w.appendLine("}");
			} else {
				generateACallback(w,transition.getGroup());
			}
			w.outdent();
			w.appendLine("}");
		} else {
			w.appendLine("/** UNSUPPORTED: ",transition.getClass().getSimpleName()," **/");
		}
	}

	private static void generateGetNext(StringWriter w, LTSminModel model) {
		w.appendLine("int spinja_get_successor( void* model, int t, state_t *in, void (*callback)(void* arg, transition_info_t *transition_info, state_t *out), void *arg) {");
		w.indent();
		w.appendLine("transition_info_t transition_info = { NULL, t };");
		w.appendLine("int states_emitted = 0;");
		w.appendLine("int atomic = -1;");
		w.appendLine("register int pos;");
		w.appendLine(C_STATE_T," local_state;");
		w.appendLine(C_STATE_T,"* ",C_STATE_TMP," = &local_state;");
		w.appendLine();
		w.appendLine("switch(t) {");
		List<LTSminTransitionBase> transitions = model.getTransitions();
		int trans = 0;
		for(LTSminTransitionBase t: transitions) {
			w.appendLine("case ",trans,": { // ",t.getName());
			w.indent();
			generateATransition(w, t, model);
			w.appendLine("return states_emitted;");
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

	private static void generateGuard(StringWriter w, LTSminModel model,
								      LTSminGuardBase guard) {
		if(guard instanceof LTSminGuard) {
			LTSminGuard g = (LTSminGuard)guard;
			generateBoolExpression(w, g.expr, IN_ACCESS);
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
		if(a instanceof AssignAction) { //TODO: assign + expr + runexp
			AssignAction as = (AssignAction)a;
			Identifier id = as.getIdentifier();
			final String mask = id.getVariable().getType().getMask();
			switch (as.getToken().kind) {
				case ASSIGN:
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
						generateIntExpression(w, as.getExpr(), TMP_ACCESS);
						w.append((mask == null ? "" : " & " + mask));
						w.append(";");
						w.appendPostfix();
					}
					break;
				case INCR:
					if (mask == null) {
						w.appendPrefix();
						generateIntExpression(w, id, TMP_ACCESS);
						w.append("++;");
						w.appendPostfix();
					} else {
						w.appendPrefix();
						generateIntExpression(w, id, TMP_ACCESS);
						w.append(" = (");
						generateIntExpression(w, id, TMP_ACCESS);
						w.append(" + 1) & ").append(mask).append(";");
						w.appendPostfix();
					}
					break;
				case DECR:
					if (mask == null) {
						w.appendPrefix();
						generateIntExpression(w, id, TMP_ACCESS);
						w.append("--;");
						w.appendPostfix();
					} else {
						w.appendPrefix();
						generateIntExpression(w, id, TMP_ACCESS);
						w.appendLine(" = (");
						generateIntExpression(w, id, TMP_ACCESS);
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
			
			String name = rpa.getProcess().getName();
			w.appendLine("#ifndef NORESETPROCESS");
			w.appendLine("memcpy(&",TMP_ACCESS,name,",(char*)&(",C_STATE_INITIAL,".",name,"),sizeof(state_",name,"_t));");
			w.appendLine("#endif");
			String nr_pr = wrapVar(TMP_ACCESS, LTSminStateVector._NR_PR)+ ".var";
			w.appendLine(nr_pr +"--;");
			w.appendLine(getPC(rpa.getProcess(), TMP_ACCESS) +" = -1;");
		} else if(a instanceof AssertAction) {
			AssertAction as = (AssertAction)a;
			Expression e = as.getExpr();

			w.appendPrefix();
			w.append("if(!");
			generateBoolExpression(w, e, TMP_ACCESS);
			w.append(") {");
			w.appendPostfix();
			w.indent();
			w.appendLine("printf(\"Assertion violated: ",as.getExpr().toString(), "\\n\");");
			//w.appendLine("print_state(",C_STATE_TMP,");"); //TODO: invalid states!
			w.outdent();
			w.appendLine("}");
		} else if(a instanceof PrintAction) {
			PrintAction pa = (PrintAction)a;
			String string = pa.getString();
			List<Expression> exprs = pa.getExprs();
			w.appendPrefix().append("//printf(").append(string);
			for (final Expression expr : exprs) {
				w.append(", ");
				generateIntExpression(w, expr, TMP_ACCESS);
			}
			w.append(");").appendPostfix();
		} else if(a instanceof ExprAction) {
			ExprAction ea = (ExprAction)a;
			Expression expr = ea.getExpression();
			String sideEffect = null;
			try {
				sideEffect = expr.getSideEffect();
				if (sideEffect != null) {
					//a RunExpression has side effects... yet it does not block if less than 255 processes are started atm
					assert (expr instanceof RunExpression);
					RunExpression re = (RunExpression)expr;
					Proctype target = re.getSpecification().getProcess(re.getId()); //TODO: anonymous processes (multiple runs on one proctype)

					//only one dynamic process supported atm
					w.appendLine("if (-1 != "+ getPC(target, TMP_ACCESS) +") {");
					w.appendLine("	printf (\"SpinJa only supports a maximum " +
							"one dynamic process creation and only for " +
							"nonactive proctypes.\\n\");");
					w.appendLine("	printf (\"Exiting on '"+ re +"'.\\n\");");
					w.appendLine("	exit (1);");
					w.appendLine("}");
					
					//activate process
					Action update_pc = LTSminTreeWalker.assign(model.sv.getPC(target), 0);
					generateAction(w, update_pc, model);
					w.appendLine("++("+ TMP_NUM_PROCS +");");
					
					//set pid
					Action update_pid = LTSminTreeWalker.assign(model.sv.getPID(target),
							LTSminTreeWalker.id(LTSminStateVector._NR_PR));
					generateAction(w, update_pid, model);
					
					List<Variable> args = target.getArguments();
					Iterator<Expression> eit = re.getExpressions().iterator();
					if (args.size() != re.getExpressions().size())
						throw exception("Run expression's parameters do not match the proc's arguments.", re.getToken());
					//write to the arguments of the target process
					for (Variable v : args) {
						Expression e = eit.next();
						//channels are passed by reference: TreeWalker.bindByReferenceCalls 
						if (!(v.getType() instanceof ChannelType)) {
							Action aa = LTSminTreeWalker.assign(v, e);
							generateAction(w, aa, model);
						}
					}
				} else {
					// Simple expressions are guards
				}
			} catch (ParseException e) {
				e.printStackTrace();
			}
		} else if(a instanceof OptionAction) { // options in a d_step sequence
			OptionAction oa = (OptionAction)a;
			if (oa.loops()) {
				w.appendLine("while (true) {");
				w.indent();
			}
			boolean first = true;
			for (Sequence seq : oa) {
				if (!first)
					w.appendPrefix().append("} else if (");
				else
					w.appendPrefix().append("if (");
				Action guardAction = seq.iterator().next();
				if (guardAction instanceof ElseAction) {
					w.append("true");
				} else if (guardAction instanceof AssignAction) {
					w.append("true");
				} else if (guardAction instanceof ExprAction) {
					ExprAction ea = (ExprAction)guardAction;
					generateBoolExpression(w, ea.getExpression(), TMP_ACCESS);
				} else {
					throw new AssertionError("Guard action not implemented for d_step option: "+ guardAction);
				}
				w.append(") {").appendPostfix();
				w.indent();
				for (Action act : seq)
					generateAction(w, act, model);
				w.outdent();
				first = false;
			}
			if (oa.loops()) {
				w.appendPrefix().appendLine("} else { printf(\"Blocking loop in d_step\"); exit(1); }");
				w.appendLine("}");
			} else {
				w.appendLine("}");
			}
		} else if(a instanceof ElseAction) {
			// noop
		} else if(a instanceof ChannelSendAction) {
			ChannelSendAction csa = (ChannelSendAction)a;
			Identifier id = csa.getIdentifier();
			ChannelVariable var = (ChannelVariable)id.getVariable();
			if(var.getType().getBufferSize()>0) {
				List<Expression> exprs = csa.getExprs();
				for (int i = 0; i < exprs.size(); i++) {
					final Expression expr = exprs.get(i);
					w.appendPrefix();
					generateIntExpression(w, channelTop(id,i), TMP_ACCESS);
					w.append(" = ");
					generateIntExpression(w, expr, TMP_ACCESS);
					w.append(";");
					w.appendPostfix();
				}
				w.appendLine("++("+ wrapVarRef(TMP_ACCESS, id, false) +".filled);");
			} else {
				throw new AssertionError("Trying to actionise rendezvous send!");
			}
		} else if(a instanceof ChannelReadAction) {
			ChannelReadAction cra = (ChannelReadAction)a;
			Identifier id = cra.getIdentifier();
			ChannelVariable var = (ChannelVariable)id.getVariable();
			if(var.getType().getBufferSize()>0) {
				List<Expression> exprs = cra.getExprs();
				for (int i = 0; i < exprs.size(); i++) {
					final Expression expr = exprs.get(i);
					if (expr instanceof Identifier) {
						w.appendPrefix();
						generateIntExpression(w, expr, TMP_ACCESS);
						w.append(" = ");
						String chan = wrapVarRef(TMP_ACCESS, id, false);
						String idx = "("+ chan +".nextRead)";
						String chan_access = wrapVarRef(TMP_ACCESS, id, true);
						String access_buffer = chan_access +"["+ idx + "]";
						w.append(access_buffer +".m"+ i +".var");
						w.append(";");
						w.appendPostfix();
					}
				}
				String access = wrapVarRef(TMP_ACCESS, id,false);
				w.appendLine(access,".nextRead = (",access,".nextRead+1)%"+var.getType().getBufferSize()+";");
				w.appendLine("--(",access, ".filled);");
			} else {
				throw new AssertionError("Trying to actionise rendezvous receive!");
			}
		} else {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+a.getClass().getName());
		}
	}

	private static ParseException exception(String string, Token token) {
		return new ParseException(string + " At line "+token.beginLine +"column "+ token.beginColumn +".");
	}

	private static String varPrefix(String access, Variable v) {
		String res = access;
		res += (v.getOwner()==null ? C_STATE_GLOBALS : v.getOwner().getName());
		return res +".";
	}

	private static String wrapVar(String access, Variable var) {
		return varPrefix(access, var) + var.getName();
	}
	
	private static String wrapVarRef(String access, Identifier id, boolean buffer) {
		String base = wrapVar(access, id.getVariable());
		if (buffer)
			base += "_buffer";
		if (id.getVariable().getArraySize() > 1) {
			if (id.getArrayExpr() != null) {
				StringWriter w = new StringWriter();
				generateIntExpression(w,id.getArrayExpr(),access);
				base += "["+ w.toString() +"]";
			} else {
				base += "[0]";
			}
		}
		return base;
	}
	
	private static void generateIntExpression(StringWriter w, Expression e, String access) {
		if(e instanceof LTSminIdentifier) {
			LTSminIdentifier id = (LTSminIdentifier)e;
			w.append(id.getVariable().getName());
		} else if(e instanceof Identifier) {
			Identifier id = (Identifier)e;
			w.append(wrapVarRef(access,id,false) +".var");
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
		} else if(e instanceof ChannelSizeExpression) {
			ChannelSizeExpression cse = (ChannelSizeExpression)e;
			String bufRef = wrapVarRef(access, cse.getIdentifier(), false);
			w.append(bufRef +".filled");
		} else if(e instanceof ChannelLengthExpression) {
			ChannelLengthExpression cle = (ChannelLengthExpression)e;
			Identifier id = (Identifier)cle.getExpression();
			generateIntExpression(w, new ChannelSizeExpression(id), access);
		} else if(e instanceof ChannelReadExpression) {
			ChannelReadExpression cre = (ChannelReadExpression)e;
			generateBoolExpression (w, cre, access);
		} else if(e instanceof ChannelTopExpression) {
			ChannelTopExpression cte = (ChannelTopExpression)e;
			ChannelReadAction cra = cte.getChannelReadAction();
			Identifier id = cra.getIdentifier();
			int size = ((ChannelType)id.getVariable().getType()).getBufferSize();
			String chan = wrapVarRef(access, id, false);
			String idx = "("+ chan +".nextRead + "+ chan +".filled) % "+ size;
			String chan_access = wrapVarRef(access, id, true);
			String access_buffer = chan_access +"["+ idx + "]";
			w.append(access_buffer +".m"+ cte.getElem() +".var");
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
			generateIntExpression(w, new ChannelSizeExpression(id), access);
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
			generateIntExpression(w, ce.getExpr1(), access);
			w.append(" ").append(ce.getToken().image).append(" ");
			generateIntExpression(w, ce.getExpr2(), access);
			w.append(" ? 1 : 0)");
		} else if(e instanceof MTypeReference) {
			MTypeReference ref = (MTypeReference)e;
			w.append(ref.getNumber());
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
		} else if(e instanceof EvalExpression) {
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

	private static void generateBoolExpression(StringWriter w, Expression e,
											   String access) {

		if(e instanceof Identifier) { //also LTSminIdentifier
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
		} else if(e instanceof ChannelTopExpression) {
			ChannelTopExpression cte = (ChannelTopExpression)e;
			w.append("(");
			generateIntExpression(w, cte, access);
			w.append(" != 0 ? 1 : 0)");
		} else if(e instanceof ChannelReadExpression) {
			ChannelReadExpression cre = (ChannelReadExpression)e;
			Identifier id = cre.getIdentifier();
			if (((ChannelType)id.getVariable().getType()).getBufferSize() == 0)
				throw new AssertionError("ChannelReadAction on rendez-vous channel.");
			Expression size = new ChannelSizeExpression(id);
			w.append("(");
			generateIntExpression(w, size, access);
			w.append(" > 0) && ");
			List<Expression> exprs = cre.getExprs();
			for (int i = 0; i < exprs.size(); i++) {
				final Expression expr = exprs.get(i);
				if (expr instanceof Identifier) // always matches
					continue;
				Expression top = channelTop(id, i);
				generateIntExpression(w, top, access);
				w.append(" == ");
				generateIntExpression(w, expr, access);
				if (i != exprs.size() - 1) {
					w.append(" && ");
				}
			}
		} else if(e instanceof ChannelLengthExpression) {
			ChannelLengthExpression cle = (ChannelLengthExpression)e;
			Identifier id = (Identifier)cle.getExpression();
			w.append("(");
			generateIntExpression(w, new ChannelSizeExpression(id), access);
			w.append(" != 0)");
		} else if(e instanceof ChannelOperation) {
			generateIntExpression(w, e, access);
		} else if(e instanceof CompareExpression) {
			CompareExpression ce = (CompareExpression)e;
			w.append("(");
			generateIntExpression(w, ce.getExpr1(), access);
			w.append(" ").append(ce.getToken().image).append(" ");
			generateIntExpression(w, ce.getExpr2(), access);
			w.append(")");
		} else if(e instanceof RunExpression) {
			w.append("("+ IN_NUM_PROCS +" != "+ (PM_MAX_PROCS-1) +")");
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
			w.append("(");
			generateIntExpression(w, e, access);
			w.append(" != 0 ? 1 : 0)");
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

	private static void generateDepMatrix(StringWriter w, DepMatrix dm, String name, boolean rw) {
		String dub = rw ? "[2]" : "";
		w.appendPrefix();
		w.appendLine("int "+ name + "[]"+ dub +"["+ dm.getRowLength() +"] = {");
		w.indent();
		if (rw)
			w.appendLine("// { ... read ...}, { ... write ...}");

		// Iterate over all the rows
		for(int t = 0; t < dm.getRows(); t++) {
			if (t > 0)
				w.append(", // "+ t).appendPostfix();
			w.appendPrefix();

			DepRow dr = dm.getRow(t);
			if (rw) { // both read and write
				w.append("{");
				generateRow(w, dr, true);
				w.append(",");
				generateRow(w, dr, false);			
				w.append("}");
			} else {
				generateRow(w, dr, true);
			}
		}
		w.outdent();
		// Close array
		w.appendLine("};");
	}

	private static void generateRow(StringWriter w, DepRow dr, boolean read) {
		w.append("{");
		
		// Insert all read dependencies of the current row
		for(int s=0; s < dr.getSize(); s++) {
			if(s > 0)
				w.append(",");
			w.append(read ? dr.getReadB(s) : dr.getWriteB(s));
		}

		w.append("}");
	}

	private static void generateDMFunctions(StringWriter w, DepMatrix dm) {
		// Function to access the dependency matrix
		w.appendLine("");
		w.appendLine("extern const int* spinja_get_transition_read_dependencies(int t)");
		w.appendLine("{");
		w.appendLine("	if (t>=0 && t < "+ dm.getRows() +") return "+ DM_NAME +"[t][0];");
		w.appendLine("	return NULL;");
		w.appendLine("}");
		w.appendLine("");
		w.appendLine("extern const int* spinja_get_transition_write_dependencies(int t)");
		w.appendLine("{");
		w.appendLine("	if (t>=0 && t < "+ dm.getRows()+ ") return "+ DM_NAME +"[t][1];");
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

		for(int i = 0; i<state_size;) {
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
		GuardInfo gm = model.getGuardInfo();
		if(gm==null) return;

		DepMatrix co_matrix = gm.getCoMatrix();
		List<List<Integer>> trans_matrix = gm.getTransMatrix();
		List<LTSminGuard> guards = gm.getGuards();
		
		generateGuardList(w, model, guards);

		w.appendLine("");
		w.appendLine("// Guard-Dependency Matrix:");
		generateDepMatrix(w, gm.getDepMatrix(), GM_DM_NAME, false);
		w.appendLine("");
		
		generateDepMatrix(w, co_matrix, CO_DM_NAME, false);
		
		generateTransGuardMatrix(w, trans_matrix, guards);
	}

	private static void generateGuardList(StringWriter w, LTSminModel model,
										  List<LTSminGuard> guards) {
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
			generateGuard(w, model, guards.get(g));
			w.appendPostfix();
//			w.appendLine("  - ",
//			w.appendLine("  - ",guards.get(g).toString());
		}
		w.setPrePrefix(old_preprefix);
		w.appendLine(" */");
	}

	private static void generateTransGuardMatrix(StringWriter w,
												List<List<Integer>> trans_matrix,
												List<LTSminGuard> guards) {
		w.appendLine("// Transition-Guard Matrix:");
		w.appendPrefix().append("int* "+ GM_TRANS_NAME +"[").append(trans_matrix.size()).append("] = {");
		w.appendPostfix();
		for(int g=0; g<trans_matrix.size(); ++g) {
			w.append("((int[]){");
			List<Integer> row = trans_matrix.get(g);

			w.append(" ").append(row.size());

			for(int s=0; s<row.size(); ++s) {
				w.append(", ").append(row.get(s));
			}

			w.append(" })");
			if(g<trans_matrix.size()-1) w.append(",");
			w.append("\t// trans ").append(String.format("%5d",g));
			w.appendPostfix();
		}
		w.appendLine("};");
		w.appendLine("");
	}

	private static void generateGuardFunctions(StringWriter w, LTSminModel model, GuardInfo gm) {
		List<LTSminGuard> guards = gm.getGuards();

		w.appendLine("int spinja_get_guard_count() {");
		w.indent();
		w.appendLine("return ",gm.getGuards().size(),";");
		w.outdent();
		w.appendLine("}");
		w.appendLine("");

		w.appendLine("const int* spinja_get_guards(int t) {");
		w.indent();
		w.appendLine("assert(t < ",gm.getTransMatrix().size()," && \"spinja_get_guards: invalid transition\");");
		w.appendLine("return "+ GM_TRANS_NAME +"[t];");
		w.outdent();
		w.appendLine("}");
		w.appendLine("");

		w.appendLine("const int*** spinja_get_all_guards() {");
		w.indent();
		w.appendLine("return (const int***)&"+ GM_TRANS_NAME +";");
		w.outdent();
		w.appendLine("}");
		w.appendLine("");

		w.appendLine("const int* spinja_get_guard_may_be_coenabled_matrix(int g) {");
		w.indent();
		w.appendLine("assert(g < ",gm.getGuards().size()," && \"spinja_get_guards: invalid guard\");");
		w.appendLine("return "+ CO_DM_NAME +"[g];");
		w.outdent();
		w.appendLine("}");
		w.appendLine("");

		w.appendLine("const int* spinja_get_guard_matrix(int g) {");
		w.indent();
		w.appendLine("assert(g < ",gm.getGuards().size()," && \"spinja_get_guards: invalid guard\");");
		w.appendLine("return "+ GM_DM_NAME +"[g];");
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
			generateGuard(w, model, guards.get(g));
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
			generateGuard(w, model, guards.get(g));
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
		w.appendLine("unsigned int nextRead: 16;");
		w.appendLine("unsigned int filled: 16;");
		w.outdent();
		w.appendLine("} ",C_TYPE_CHANNEL,";");
	}
}

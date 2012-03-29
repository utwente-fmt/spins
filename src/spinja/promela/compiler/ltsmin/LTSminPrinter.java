package spinja.promela.compiler.ltsmin;

import static spinja.promela.compiler.ltsmin.model.LTSminUtil.assign;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.calc;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.chanLength;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.chanRead;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.channelTop;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.constant;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.error;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.exception;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.id;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.printId;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.printPC;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.printPID;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.printVar;
import static spinja.promela.compiler.ltsmin.state.LTSminStateVector.C_STATE;
import static spinja.promela.compiler.ltsmin.state.LTSminStateVector._NR_PR;
import static spinja.promela.compiler.ltsmin.state.LTSminTypeChanStruct.bufferVar;
import static spinja.promela.compiler.ltsmin.state.LTSminTypeChanStruct.elemVar;
import static spinja.promela.compiler.ltsmin.state.LTSminTypeNative.TYPE_BOOL;
import static spinja.promela.compiler.ltsmin.state.LTSminTypeNative.TYPE_INT16;
import static spinja.promela.compiler.ltsmin.state.LTSminTypeNative.TYPE_INT32;
import static spinja.promela.compiler.ltsmin.state.LTSminTypeNative.TYPE_INT8;
import static spinja.promela.compiler.ltsmin.state.LTSminTypeNative.TYPE_UINT16;
import static spinja.promela.compiler.ltsmin.state.LTSminTypeNative.TYPE_UINT32;
import static spinja.promela.compiler.ltsmin.state.LTSminTypeNative.TYPE_UINT8;
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
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import spinja.promela.compiler.Proctype;
import spinja.promela.compiler.actions.Action;
import spinja.promela.compiler.actions.AssertAction;
import spinja.promela.compiler.actions.AssignAction;
import spinja.promela.compiler.actions.BreakAction;
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
import spinja.promela.compiler.ltsmin.matrix.DepMatrix;
import spinja.promela.compiler.ltsmin.matrix.DepRow;
import spinja.promela.compiler.ltsmin.matrix.GuardInfo;
import spinja.promela.compiler.ltsmin.matrix.LTSminGuard;
import spinja.promela.compiler.ltsmin.matrix.LTSminGuardAnd;
import spinja.promela.compiler.ltsmin.matrix.LTSminGuardBase;
import spinja.promela.compiler.ltsmin.matrix.LTSminGuardNand;
import spinja.promela.compiler.ltsmin.matrix.LTSminGuardOr;
import spinja.promela.compiler.ltsmin.model.ChannelSizeExpression;
import spinja.promela.compiler.ltsmin.model.ChannelTopExpression;
import spinja.promela.compiler.ltsmin.model.LTSminIdentifier;
import spinja.promela.compiler.ltsmin.model.LTSminModel;
import spinja.promela.compiler.ltsmin.model.LTSminTransition;
import spinja.promela.compiler.ltsmin.model.ResetProcessAction;
import spinja.promela.compiler.ltsmin.state.LTSminPointer;
import spinja.promela.compiler.ltsmin.state.LTSminSlot;
import spinja.promela.compiler.ltsmin.state.LTSminStateVector;
import spinja.promela.compiler.ltsmin.state.LTSminTypeI;
import spinja.promela.compiler.ltsmin.state.LTSminTypeStruct;
import spinja.promela.compiler.ltsmin.state.LTSminVariable;
import spinja.promela.compiler.parser.ParseException;
import spinja.promela.compiler.parser.PromelaConstants;
import spinja.promela.compiler.variable.ChannelType;
import spinja.promela.compiler.variable.ChannelVariable;
import spinja.promela.compiler.variable.Variable;
import spinja.promela.compiler.variable.VariableType;
import spinja.util.StringWriter;

/**
 * Generates C code from the LTSminModel
 * 
 * @author FIB, Alfons Laarman
 */
public class LTSminPrinter {

	public static final String IN_VAR = "in";
	public static final String OUT_VAR = "tmp";
	public static final String INITIAL_VAR = "initial";

	public static final int    PM_MAX_PROCS = 256;
	public static final String DM_NAME = "transition_dependency";
	public static final String GM_DM_NAME = "gm_dm";
	public static final String CO_DM_NAME = "co_dm";
	public static final String NES_DM_NAME = "nes_dm";
	public static final String NDS_DM_NAME = "nds_dm";
	public static final String GM_TRANS_NAME = "gm_trans";
	public static final int    STATE_ELEMENT_SIZE = 4;

	// first value of assertions indicates a passed assertion
	static List<String> assertions = new ArrayList<String>(Arrays.asList("PASS"));

	public static String generateCode(LTSminModel model) {
		StringWriter w = new StringWriter();
		LTSminPrinter.generateModel(w, model);
		return w.toString();
	}
	
	private static void generateModel(StringWriter w, LTSminModel model) {
		generateHeader(w, model);
		generateNativeTypes(w);
		generateTypeDef(w, model);
		generateForwardDeclarations(w);
		generateStateCount(w, model);
		generateInitialState(w, model);
		generateLeavesAtomic(w, model);
		generateGetNext(w, model);
		generateGetAll(w, model);
		generateTransitionCount(w, model);
		generateDepMatrix(w, model.getDepMatrix(), DM_NAME, true);
		generateDMFunctions(w, model.getDepMatrix());
		generateGuardMatrices(w, model);
		generateGuardFunctions(w, model, model.getGuardInfo());
		generateStateDescriptors(w, model);
		generateEdgeDescriptors(w, model);
		generateHashTable(w, model);
		generateReach(w, model);
	}

	private static void generateTypeDef(StringWriter w, LTSminModel model) {
		for(LTSminTypeStruct struct : model.sv.getTypes()) {
			w.appendLine("typedef struct ", struct.getName()," {");
			w.indent();
			for (LTSminVariable v : struct) {
				LTSminTypeI type = v.getType();
				w.appendPrefix();
				w.append(type +" "+ v.getName());
				if(v.array() > 0)
					w.append("["+ v.array() +"]");
				w.append(";");
				w.appendPostfix();
			}
			w.outdent();
			w.appendLine("} "+ struct.getName() +";");
			w.appendLine("");
		}
		w.appendLine("typedef "+ C_STATE +" state_t;");
		w.appendLine("");
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
		w.appendLine("typedef struct transition_info {");
		w.indent();
		w.appendLine("int* label;");
		w.appendLine("int  group;");
		w.outdent();
		w.appendLine("} transition_info_t;");
		w.appendLine("");
	}
	
	public static <T> String readTextFile(Class<T> clazz, String resourceName) throws IOException {
	  	StringBuffer sb = new StringBuffer(1024);
		BufferedInputStream inStream = new BufferedInputStream(clazz.getResourceAsStream(resourceName));
	  	byte[] chars = new byte[1024];
	  	int bytesRead = 0;
	  	while( (bytesRead = inStream.read(chars)) > -1)
	  		sb.append(new String(chars, 0, bytesRead));
	  	inStream.close();
	  	return sb.toString();
	}
	
	private static void generateHashTable(StringWriter w, LTSminModel model) {
		if (!model.hasAtomic()) return;
		try {
			w.appendLine(readTextFile(new LTSminDebug().getClass(), "hashtable.c"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void generateReach(StringWriter w, LTSminModel model) {
		if (!model.hasAtomic()) return;
		try {
			w.appendLine(readTextFile(new LTSminDebug().getClass(), "reach.c"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void generateStateCount(StringWriter w, LTSminModel model) {
		w.appendLine("extern int spinja_get_state_size() {");
		w.indent();
		w.appendLine("return ",model.sv.size(),";");
		w.outdent();
		w.appendLine("}");
		w.appendLine("");
	}

	private static void generateTransitionCount(StringWriter w, LTSminModel model) {
		w.appendLine("extern int spinja_get_transition_groups() {");
		w.indent();
		w.appendLine("return ",model.getTransitions().size(),";");
		w.outdent();
		w.appendLine("}");
		w.appendLine("");
	}

	private static void generateForwardDeclarations(StringWriter w) {
		w.appendLine("extern inline int reach (void* model, transition_info_t *transition_info, state_t *in, void (*callback)(void* arg, transition_info_t *transition_info, state_t *out), void *arg, int pid);");
		w.appendLine("extern int spinja_get_successor_all( void* model, state_t *in, void (*callback)(void* arg, transition_info_t *transition_info, state_t *out), void *arg );");
		w.appendLine("extern int spinja_get_successor( void* model, int t, state_t *in, void (*callback)(void* arg, transition_info_t *transition_info, state_t *out), void *arg );");
		w.appendLine("static const int numbers[50] = {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49};");
		w.appendLine("");
	}

	private static void generateInitialState(StringWriter w, LTSminModel model) {
		// Generate initial state
		w.append(C_STATE +" "+ INITIAL_VAR +" = ("+ C_STATE +") {");
		w.indent();
		// Insert initial expression of each state element into initial state struct
		for (LTSminSlot slot : model.sv) {
			if (0 != slot.getIndex())
				w.append(", // "+ (slot.getIndex()-1));
			w.appendPostfix().appendPrefix();
			w.append(slot.fullName());
			char[] chars = new char[Math.max(40 - slot.fullName().length(),0)];
			Arrays.fill(chars, ' '); 
			String prefix = new String(chars);
			w.append(prefix +" = ");
			Expression e = slot.getInitExpr();
			if (e==null) {
				w.append("0");
			} else {
				try {
					w.append(e.getConstantValue());
				} catch (ParseException pe) {
					throw new AssertionError("Cannot parse inital value of state vector: "+ e);
				}
			}
			
		}
		w.outdent().appendPostfix();
		w.appendLine("};");
		w.appendLine("");
		
		w.appendLine("extern void spinja_get_initial_state( state_t *to )");
		w.appendLine("{");
		w.indent();
		w.appendLine("if("+ model.sv.size() +"*",STATE_ELEMENT_SIZE," != sizeof(" + C_STATE + "))");
		w.indent();
		w.appendLine("printf(\"state_t SIZE MISMATCH!: state: %i != %i\",sizeof("+ C_STATE +"),"+ model.sv.size() +"*",STATE_ELEMENT_SIZE,");");
		w.outdent();
		w.appendLine("memcpy(to, (char*)&",INITIAL_VAR,", sizeof(" + C_STATE + "));");
		w.outdent();
		w.appendLine("}");
		w.appendLine("");
	}
	
	private static void generateACallback(StringWriter w, int trans) {
		w.appendLine("transition_info.group = "+ trans +";");
		w.appendLine("callback(arg,&transition_info,tmp);");
		w.appendLine("++states_emitted;");
	}

	private static void generateLeavesAtomic (StringWriter w, LTSminModel model) {
		List<LTSminTransition> ts = model.getTransitions();
		w.appendLine("char leaves_atomic["+ ts.size() +"] = {");
		w.indent();
		if (ts.size() > 0) {
			int i = 0;
			for (LTSminTransition tb : ts) {
				if (0 != i) w.append(",\t// "+ i).appendPostfix();
				LTSminTransition t = (LTSminTransition)tb;
				w.appendPrefix();
				w.append("" + t.leavesAtomic());
				i++;
			}
			w.appendPostfix();
		}
		w.outdent();
		w.appendLine("};");
		w.appendLine("");
	}
	
	private static void generateGetAll(StringWriter w, LTSminModel model) {
		w.appendLine("int spinja_get_successor_all( void* model, state_t *in, void (*callback)(void* arg, transition_info_t *transition_info, state_t *out), void *arg) {");
		w.indent();
		w.appendLine("state_t out;");
		w.appendLine("int atomic_process = -1;");
		w.appendLine("spinja_get_successor_all_real(model, in, callback, arg, &out, &atomic_process);");
		w.outdent();
		w.appendLine("}");
		w.appendLine("");
		w.appendLine("int spinja_get_successor_all_real( void* model, state_t *in, void (*callback)(void* arg, transition_info_t *transition_info, state_t *out), void *arg, state_t *tmp, int *atomic) {");
		w.indent();
		w.appendLine("transition_info_t transition_info = { (int *)&numbers[0], -1 };");
		w.appendLine("int states_emitted = 0;");
		w.appendLine();
		List<LTSminTransition> transitions = model.getTransitions();
		for(LTSminTransition t : transitions) {
			generateATransition(w, t, model);
		}
		w.appendLine("return states_emitted;");
		w.outdent();
		w.appendLine("}");
		w.appendLine();
	}

	public static void generateATransition(StringWriter w, LTSminTransition transition,
										   LTSminModel model) {
		if(transition instanceof LTSminTransition) {
			LTSminTransition t = (LTSminTransition)transition;
			Iterator<Action> it = t.getActions().iterator();
			String name = "tau";
			if (it.hasNext()) {
				Action action = it.next();
				StringWriter w2 = new StringWriter();
				generateAction(w2, action, model);
				name = w2.toString().replace("\n", " ");
			}
			w.appendLine("// "+transition.getName() +" "+ name);
			
			w.appendPrefix().append("if (true");
			for(LTSminGuardBase g: t.getGuards()) {
				w.appendPostfix().appendPrefix().append("&&");
				generateGuard(w, model, g);
			}
			w.append(") {").appendPostfix();
			w.indent();
			w.appendLine("memcpy(", OUT_VAR,", ", IN_VAR , ", sizeof(", C_STATE,"));");
			List<Action> actions = t.getActions();
			for(Action a: actions)
				generateAction(w,a,model);
			if (t.isAtomic()) {
				w.appendLine("if (-1 != *atomic) {");
				w.indent();
				if (null != transition.passesControlAtomically()) {
					String pid = printPID(transition.passesControlAtomically(), out(model));
					w.appendLine("*atomic = "+ pid +";");
				}
				generateACallback(w,transition.getGroup());
				w.outdent();
				w.appendLine("} else {");
				w.indent();
				String pid = printPID(transition.getProcess(), out(model));
				w.appendLine("transition_info.group = "+ transition.getGroup() +";");
				w.appendLine("int count = reach (model, &transition_info, tmp, callback, arg, "+ pid +");");
				w.appendLine("states_emitted += count;"); // non-deterministic atomic sequences emit multiple states
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
		w.appendLine("transition_info_t transition_info = { (int *)&numbers[0], t };");
		w.appendLine("int states_emitted = 0;");
		w.appendLine("int minus_one = -1;");
		w.appendLine("int *atomic = &minus_one;");
		w.appendLine(C_STATE," local_state;");
		w.appendLine(C_STATE,"* ",OUT_VAR," = &local_state;");
		w.appendLine();
		w.appendLine("switch(t) {");
		List<LTSminTransition> transitions = model.getTransitions();
		int trans = 0;
		for(LTSminTransition t : transitions) {
			w.appendLine("case ",trans,": {");
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
			generateExpression(w, g.expr, in(model));
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
			switch (as.getToken().kind) {
				case ASSIGN:
					try {
						int value = as.getExpr().getConstantValue();
						w.appendPrefix();
						generateExpression(w, id, out(model));
						w.append(" = ").append(value).append(";");
						w.appendPostfix();
					} catch (ParseException ex) {
						// Could not get Constant value
						w.appendPrefix();
						generateExpression(w, id, out(model));
						w.append(" = ");
						generateExpression(w, as.getExpr(), out(model));
						w.append(";");
						w.appendPostfix();
					}
					break;
				case INCR:
					w.appendPrefix();
					generateExpression(w, id, out(model));
					w.append("++;");
					w.appendPostfix();
					break;
				case DECR:
					w.appendPrefix();
					generateExpression(w, id, out(model));
					w.append("--;");
					w.appendPostfix();
					break;
				default:
					throw new AssertionError("unknown assignment type");
			}
		} else if(a instanceof ResetProcessAction) {
			ResetProcessAction rpa = (ResetProcessAction)a;
			String name = rpa.getProcess().getName();
			String struct_t = model.sv.getMember(rpa.getProcess()).getType().getName();
			w.appendLine("#ifndef NORESETPROCESS");
			w.appendLine("memcpy(&",OUT_VAR,"->"+ name +",(char*)&(",INITIAL_VAR,".",name,"),sizeof("+ struct_t +"));");
			w.appendLine("#endif");
			w.appendLine(printVar(_NR_PR, out(model)) +"--;");
			w.appendLine(printPC(rpa.getProcess(), out(model)) +" = -1;");
		} else if(a instanceof AssertAction) {
			AssertAction as = (AssertAction)a;
			Expression e = as.getExpr();
			StringWriter w2 = new StringWriter();
			generateExpression(w2, e, out(model));
			String expression = w2.toString();
			int index = assertions.indexOf(expression);
			if (-1 == index) {
				assertions.add(expression);
				index = assertions.size() - 1;
				assert (index < 50); // enlarge 'numbers' otherwise
			}
			w.appendPrefix();
			w.append("if(!");
			w.append(expression);
			w.append(") {");
			w.appendPostfix();
			w.indent();
			w.appendLine("transition_info.label = (int *)&numbers["+ index  +"];");
			w.outdent();
			w.appendLine("}");
		} else if(a instanceof PrintAction) {
			PrintAction pa = (PrintAction)a;
			String string = pa.getString();
			List<Expression> exprs = pa.getExprs();
			w.appendPrefix().append("//printf(").append(string);
			for (final Expression expr : exprs) {
				w.append(", ");
				generateExpression(w, expr, out(model));
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
					w.appendLine("if (-1 != "+ printPC(target, out(model)) +") {");
					w.appendLine("	printf (\"SpinJa only supports a maximum " +
							"one dynamic process creation and only for " +
							"nonactive proctypes.\\n\");");
					w.appendLine("	printf (\"Exiting on '"+ re +"'.\\n\");");
					w.appendLine("	exit (1);");
					w.appendLine("}");
					
					//set pid
					Action update_pid = assign(model.sv.getPID(target),
												id(LTSminStateVector._NR_PR));
					generateAction(w, update_pid, model);

					//activate process
					Action update_pc = assign(model.sv.getPC(target), 0);
					generateAction(w, update_pc, model);
					w.appendLine("++("+ printVar(_NR_PR, out(model)) +");");
					
					List<Variable> args = target.getArguments();
					Iterator<Expression> eit = re.getExpressions().iterator();
					if (args.size() != re.getExpressions().size())
						throw exception("Run expression's parameters do not match the proc's arguments.", re.getToken());
					//write to the arguments of the target process
					for (Variable v : args) {
						Expression e = eit.next();
						//channels are passed by reference: TreeWalker.bindByReferenceCalls 
						if (!(v.getType() instanceof ChannelType)) {
							Action aa = assign(v, e);
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
					generateExpression(w, ea.getExpression(), out(model));
				} else {
					throw new AssertionError("Guard action not implemented for d_step option: "+ guardAction);
				}
				w.append(") {").appendPostfix();
				w.indent();
				for (Action act : seq) {
					if (act instanceof BreakAction) {
						OptionAction loop = ((BreakAction)act).getLoop();
						String label = loop.newLabel();
						w.appendLine("goto "+ label +";");
					}
					generateAction(w, act, model);
				}
				w.outdent();
				first = false;
			}
			if (oa.loops()) {
				w.appendPrefix().appendLine("} else { printf(\"Blocking loop in d_step\"); exit(1); }");
				w.appendLine("}");
				String label = oa.getLabel();
				if (null != label) {
					if (!oa.hasSuccessor())
						System.err.println("Warning place skip after loop in d_step for clean break in SPIN.");
					w.append(label +":");
				}
			} else {
				w.appendLine("}");
			}
		} else if(a instanceof BreakAction) {
			// noop
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
					generateExpression(w, channelTop(id,i), out(model));
					w.append(" = ");
					generateExpression(w, expr, out(model));
					w.append(";");
					w.appendPostfix();
				}
				w.appendLine("++("+ printId(chanLength(id), out(model)) +");");
			} else {
				throw new AssertionError("Trying to actionise rendezvous send!");
			}
		} else if(a instanceof ChannelReadAction) {
			ChannelReadAction cra = (ChannelReadAction)a;
			Identifier id = cra.getIdentifier();
			ChannelVariable var = (ChannelVariable)id.getVariable();
			int bufferSize = var.getType().getBufferSize();
			if(bufferSize>0) {
				List<Expression> exprs = cra.getExprs();
				for (int i = 0; i < exprs.size(); i++) {
					final Expression expr = exprs.get(i);
					if (expr instanceof Identifier) {
						w.appendPrefix();
						generateExpression(w, expr, out(model));
						w.append(" = ");
						generateExpression(w, channelTop(id,i), out(model));
						w.append(";");
						w.appendPostfix();
					}
					if (!cra.isPoll()) {
						w.appendPrefix();
						generateExpression(w, channelTop(id,i), out(model));
						w.append(" = ");
						generateExpression(w, constant(0), out(model));
						w.append(";");
						w.appendPostfix();
					}
				}
				if (!cra.isPoll()) {
					String read = printId(chanRead(id), out(model));
					w.appendLine(read," = (", read ,"+1)%"+bufferSize+";");
					String len = printId(chanLength(id), out(model));
					w.appendLine("--(", len, ");");
				}
			} else {
				throw new AssertionError("Trying to actionise rendezvous receive!");
			}
		} else {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+a.getClass().getName());
		}
	}

	public static class ExprPrinter {
		LTSminPointer start;
		public ExprPrinter(LTSminPointer start) {
			this.start = start;
		}
		
		public String print(Expression e) {
			if (null == e)
				return null;
			if (e instanceof Identifier) {
				Identifier id = (Identifier)e;
				return start.printIdentifier(this, id);
			} else {
				StringWriter w = new StringWriter();
				generateExpression(w, e, start);
				return w.toString();
			}
		}
	}

	private static void generateExpression(StringWriter w, Expression e, LTSminPointer state) {
		if(e instanceof LTSminIdentifier) {
			LTSminIdentifier id = (LTSminIdentifier)e;
			w.append("*"+ id.getVariable().getName());
		} else if(e instanceof Identifier) {
			Identifier id = (Identifier)e;
			w.append(printId(id, state));
		} else if(e instanceof AritmicExpression) {
			AritmicExpression ae = (AritmicExpression)e;
			Expression ex1 = ae.getExpr1();
			Expression ex2 = ae.getExpr2();
			Expression ex3 = ae.getExpr3();
			if (ex2 == null) {
				w.append("(").append(ae.getToken().image);
				generateExpression(w, ex1, state);
				w.append(")");
			} else if (ex3 == null) {
				if (ae.getToken().image.equals("%")) {
					// Modulo takes a special notation to make sure that it
					// returns a positive value
					w.append("abs(");
					generateExpression(w, ex1, state);
					w.append(" % ");
					generateExpression(w, ex2, state);
					w.append(")");
				} else {
					w.append("(");
					generateExpression(w, ex1, state);
					w.append(" ").append(ae.getToken().image).append(" ");
					generateExpression(w, ex2, state);
					w.append(")");
				}
			} else {
				w.append("(");
				generateExpression(w, ex1, state);
				w.append(" ? ");
				generateExpression(w, ex2, state);
				w.append(" : ");
				generateExpression(w, ex3, state);
				w.append(")");
			}
		} else if(e instanceof BooleanExpression) {
			BooleanExpression be = (BooleanExpression)e;
			Expression ex1 = be.getExpr1();
			Expression ex2 = be.getExpr2();
			if (ex2 == null) {
				w.append("(").append(be.getToken().image);
				generateExpression(w, ex1, state);
				w.append( ")");
			} else {
				w.append("(");
				generateExpression(w, ex1, state);
				w.append(" ").append(be.getToken().image).append(" ");
				generateExpression(w,ex2, state);
				w.append(")");
			}
		} else if(e instanceof CompareExpression) {
			CompareExpression ce = (CompareExpression)e;
			w.append("(");
			generateExpression(w, ce.getExpr1(), state);
			w.append(" ").append(ce.getToken().image).append(" ");
			generateExpression(w, ce.getExpr2(), state);
			w.append(")");
		} else if(e instanceof ChannelSizeExpression) {
			ChannelSizeExpression cse = (ChannelSizeExpression)e;
			Identifier id = (Identifier)cse.getIdentifier();
			generateExpression(w, chanLength(id), state);
		} else if(e instanceof ChannelLengthExpression) {
			ChannelLengthExpression cle = (ChannelLengthExpression)e;
			Identifier id = (Identifier)cle.getExpression();
			generateExpression(w, chanLength(id), state);
		} else if(e instanceof ChannelReadExpression) {
			ChannelReadExpression cre = (ChannelReadExpression)e;
			Identifier id = cre.getIdentifier();
			if (((ChannelType)id.getVariable().getType()).getBufferSize() == 0)
				throw new AssertionError("ChannelReadAction on rendez-vous channel.");
			Expression size = new ChannelSizeExpression(id);
			w.append("((");
			generateExpression(w, size, state);
			w.append(" > 0)");
			List<Expression> exprs = cre.getExprs();
			for (int i = 0; i < exprs.size(); i++) {
				final Expression expr = exprs.get(i);
				if (expr instanceof Identifier) // always matches
					continue;
				w.append(" && ");
				Expression top = channelTop(id, i);
				generateExpression(w, top, state);
				w.append(" == ");
				generateExpression(w, expr, state);
			}
			w.append(")");
		} else if(e instanceof ChannelTopExpression) {
			ChannelTopExpression cte = (ChannelTopExpression)e;
			ChannelReadAction cra = cte.getChannelReadAction();
			Identifier id = cra.getIdentifier();
			ChannelVariable cv = (ChannelVariable)id.getVariable();
			int size = cv.getType().getBufferSize();
			Expression sum = calc(PromelaConstants.PLUS, chanLength(id), chanRead(id));
			Expression mod = calc(PromelaConstants.MODULO, sum, constant(size));
			Identifier elem = id(elemVar(cte.getElem()));
			Identifier buf = id(bufferVar(cv), mod, elem);
			Identifier top = new Identifier(id, buf);
			generateExpression(w, top, state);
		} else if(e instanceof ChannelOperation) {
			ChannelOperation co = (ChannelOperation)e;
			String name = co.getToken().image;
			Identifier id = (Identifier)co.getExpression();
			Variable var = id.getVariable();
			VariableType type = var.getType();
			if (!(type instanceof ChannelType) || ((ChannelType)type).getBufferSize()==-1)
				throw error("Unknown channel length of channel "+ var.getName(), co.getToken());
			int buffer = ((ChannelType)type).getBufferSize();
			if (0 == buffer) { // chanops on rendez-vous return true
				w.append("true");
				return;
			}
			w.append("(");
			generateExpression(w, chanLength(id), state);
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
		} else if(e instanceof RunExpression) {
			//we define the "instantiation number" as: next_pid+1 (http://spinroot.com/spin/Man/run.html)
			//otherwise the first process can never be started if all proctypes are nonactive.
			w.append("("+ printVar(_NR_PR, state) +" != "+ (PM_MAX_PROCS-1)
					+" ? "+ printVar(_NR_PR, state) +"+1 : 0)");
		} else if(e instanceof EvalExpression) {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else if(e instanceof CompoundExpression) {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		}  else if(e instanceof TimeoutExpression) {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
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
				w.append(", // "+ (t-1)).appendPostfix();
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
		w.appendLine("");
	}

	private static void generateStateDescriptors(StringWriter w, LTSminModel model) {
		int state_size = model.sv.size();

		// Generate static list of names
		w.appendLine("static const char* var_names[] = {");
		w.indent();
		for (LTSminSlot slot : model.sv) {
			if (0 != slot.getIndex())
				w.append(",").appendPostfix();
			w.appendPrefix();
			String fn = slot.fullName();
			fn = fn.substring(1, fn.length() - 4); // .A.B.var --> A.B
			if (fn.startsWith("globals."))
				fn = fn.substring(8); // globals.B --> B
			w.append("\""+ fn +"\"");
		}
		w.outdent().appendPostfix();
		w.appendLine("};");

		// Generate static list of types
		List<String> types = new ArrayList<String>();
		for (LTSminSlot slot : model.sv) {
			LTSminVariable var = slot.getVariable();
			String cType = var.getType().toString();
			if (!types.contains(cType)) {
				types.add(cType);
			}
		}

		w.appendLine("");
		w.appendLine("static const char* var_types[] = {");
		w.indent();
		for (String s: types) {
			w.appendLine("\"",s,"\",");
		}
		w.appendLine("\"\"");
		w.outdent();
		w.appendLine("};");

		w.appendLine("");
		for (String s: types) {
			w.appendLine("static const char* const var_type_",s,"[] = {");
			w.indent();
			w.appendLine("\"\"");
			w.outdent();
			w.appendLine("};");
		}
		w.appendLine("");
		w.appendLine("static const char* const * const var_type_values[] = {");
		w.indent();

		for(String s: types)
			w.appendLine("var_type_",s,",");
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
		w.appendLine("");
	}

	private static void generateEdgeDescriptors(StringWriter w, LTSminModel model) {
		if (assertions.size() == 1) return; // only the passed assertion is present
		
		// Generate static list of names
		w.appendLine("static const char* edge_names[] = {");
		w.indent();
		int i = 0;
		for (String edge : assertions) {
			if (0 != i)
				w.append(",").appendPostfix();
			w.appendPrefix();
			w.append("\""+ edge +"\"");
			i++;
		}
		w.outdent().appendPostfix();
		w.appendLine("};");
		w.appendLine("");

		w.appendLine("extern int spinja_get_edge_count() {");
		w.indent();
		w.appendLine("return ",assertions.size(),";");
		w.outdent();
		w.appendLine("}");
		w.appendLine("");

		w.appendLine("extern const char* spinja_get_edge_name(int type) {");
		w.indent();
		w.appendLine("assert(type < ",assertions.size()," && \"spinja_get_type_name: invalid type\");");
		w.appendLine("return edge_names[type];");
		w.outdent();
		w.appendLine("}");
		w.appendLine("");
	}
	
	private static void generateGuardMatrices(StringWriter w, LTSminModel model) {
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

		w.appendLine("");
		w.appendLine("// Maybe Co-Enabled Matrix:");
		generateDepMatrix(w, co_matrix, CO_DM_NAME, false);
		w.appendLine("");

		w.appendLine("");
		w.appendLine("// Necessary Enabling Matrix:");
		generateDepMatrix(w, gm.getNESMatrix(), NES_DM_NAME, false);
		w.appendLine("");

		w.appendLine("");
		w.appendLine("// Necessary Disabling Matrix:");
		generateDepMatrix(w, gm.getNDSMatrix(), NDS_DM_NAME, false);
		w.appendLine("");
		
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
		w.appendLine("assert(g < ",gm.getGuards().size()," && \"spinja_get_guard_may_be_coenabled_matrix: invalid guard\");");
		w.appendLine("return "+ CO_DM_NAME +"[g];");
		w.outdent();
		w.appendLine("}");
		w.appendLine("");

		w.appendLine("const int* spinja_get_guard_nes_matrix(int g) {");
		w.indent();
		w.appendLine("assert(g < ",gm.getGuards().size()," && \"spinja_get_guard_nes_matrix: invalid guard\");");
		w.appendLine("return "+ NES_DM_NAME +"[g];");
		w.outdent();
		w.appendLine("}");
		w.appendLine("");

		w.appendLine("const int* spinja_get_guard_nds_matrix(int g) {");
		w.indent();
		w.appendLine("assert(g < ",gm.getGuards().size()," && \"spinja_get_guard_nds_matrix: invalid guard\");");
		w.appendLine("return "+ NDS_DM_NAME +"[g];");
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
		
		w.appendLine("bool spinja_get_guard(void* model, int g, ",C_STATE,"* ",IN_VAR,") {");
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

		w.appendLine("void spinja_get_guard_all(void* model, ",C_STATE,"* ",IN_VAR,", int* guard) {");
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
	 */
	private static void generateNativeTypes(StringWriter w) {
		w.appendLine("typedef union ", TYPE_BOOL," {");
		w.indent();
		w.appendLine("int pad;");
		w.appendLine("unsigned int var:1;");
		w.outdent();
		w.appendLine("} ",TYPE_BOOL,";");
		w.appendLine("");

		w.appendLine("typedef union ", TYPE_INT8," {");
		w.indent();
		w.appendLine("int pad;");
		w.appendLine("char var;");
		w.outdent();
		w.appendLine("} ",TYPE_INT8,";");
		w.appendLine("");

		w.appendLine("typedef union ",TYPE_INT16," {");
		w.indent();
		w.appendLine("int pad;");
		w.appendLine("short var;");
		w.outdent();
		w.appendLine("} ",TYPE_INT16,";");
		w.appendLine("");

		w.appendLine("typedef union ",TYPE_INT32," {");
		w.indent();
		w.appendLine("int pad;");
		w.appendLine("int var;");
		w.outdent();
		w.appendLine("} ",TYPE_INT32,";");
		w.appendLine("");

		w.appendLine("typedef union ",TYPE_UINT8," {");
		w.indent();
		w.appendLine("int pad;");
		w.appendLine("unsigned char var;");
		w.outdent();
		w.appendLine("} ",TYPE_UINT8,";");
		w.appendLine("");

		w.appendLine("typedef union ",TYPE_UINT16," {");
		w.indent();
		w.appendLine("int pad;");
		w.appendLine("unsigned short var;");
		w.outdent();
		w.appendLine("} ",TYPE_UINT16,";");
		w.appendLine("");

		w.appendLine("typedef union ",TYPE_UINT32," {");
		w.indent();
		w.appendLine("int pad;");
		w.appendLine("unsigned int var;");
		w.outdent();
		w.appendLine("} ",TYPE_UINT32,";");
		w.appendLine("");
	}

	private static LTSminPointer in(LTSminModel model) {
		return new LTSminPointer(model.sv, IN_VAR);
	}

	private static LTSminPointer out(LTSminModel model) {
		return new LTSminPointer(model.sv, OUT_VAR);
	}
}

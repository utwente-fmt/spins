package spins.promela.compiler.ltsmin;

import static spins.promela.compiler.ltsmin.state.LTSminStateVector.C_STATE;
import static spins.promela.compiler.ltsmin.state.LTSminStateVector._NR_PR;
import static spins.promela.compiler.ltsmin.state.LTSminTypeNative.TYPE_BOOL;
import static spins.promela.compiler.ltsmin.state.LTSminTypeNative.TYPE_INT16;
import static spins.promela.compiler.ltsmin.state.LTSminTypeNative.TYPE_INT32;
import static spins.promela.compiler.ltsmin.state.LTSminTypeNative.TYPE_INT8;
import static spins.promela.compiler.ltsmin.state.LTSminTypeNative.TYPE_UINT16;
import static spins.promela.compiler.ltsmin.state.LTSminTypeNative.TYPE_UINT32;
import static spins.promela.compiler.ltsmin.state.LTSminTypeNative.TYPE_UINT8;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.assign;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.calc;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.chanLength;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.channelBottom;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.channelIndex;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.channelNext;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.compare;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.constant;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.decr;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.eq;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.error;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.id;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.incr;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.not;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.print;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.printPC;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.printVar;
import static spins.promela.compiler.parser.PromelaConstants.ASSIGN;
import static spins.promela.compiler.parser.PromelaConstants.DECR;
import static spins.promela.compiler.parser.PromelaConstants.FALSE;
import static spins.promela.compiler.parser.PromelaConstants.INCR;
import static spins.promela.compiler.parser.PromelaConstants.NUMBER;
import static spins.promela.compiler.parser.PromelaConstants.SKIP_;
import static spins.promela.compiler.parser.PromelaConstants.TRUE;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import spins.promela.compiler.ProcInstance;
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
import spins.promela.compiler.actions.LabelAction;
import spins.promela.compiler.actions.OptionAction;
import spins.promela.compiler.actions.PrintAction;
import spins.promela.compiler.actions.Sequence;
import spins.promela.compiler.expression.AritmicExpression;
import spins.promela.compiler.expression.BooleanExpression;
import spins.promela.compiler.expression.ChannelLengthExpression;
import spins.promela.compiler.expression.ChannelOperation;
import spins.promela.compiler.expression.ChannelReadExpression;
import spins.promela.compiler.expression.CompareExpression;
import spins.promela.compiler.expression.CompoundExpression;
import spins.promela.compiler.expression.ConstantExpression;
import spins.promela.compiler.expression.EvalExpression;
import spins.promela.compiler.expression.Expression;
import spins.promela.compiler.expression.Identifier;
import spins.promela.compiler.expression.MTypeReference;
import spins.promela.compiler.expression.RemoteRef;
import spins.promela.compiler.expression.RunExpression;
import spins.promela.compiler.expression.TimeoutExpression;
import spins.promela.compiler.ltsmin.matrix.DepMatrix;
import spins.promela.compiler.ltsmin.matrix.DepRow;
import spins.promela.compiler.ltsmin.matrix.GuardInfo;
import spins.promela.compiler.ltsmin.matrix.LTSminGuardAnd;
import spins.promela.compiler.ltsmin.matrix.LTSminGuardBase;
import spins.promela.compiler.ltsmin.model.LTSminIdentifier;
import spins.promela.compiler.ltsmin.model.LTSminModel;
import spins.promela.compiler.ltsmin.model.LTSminTransition;
import spins.promela.compiler.ltsmin.model.ResetProcessAction;
import spins.promela.compiler.ltsmin.state.LTSminPointer;
import spins.promela.compiler.ltsmin.state.LTSminSlot;
import spins.promela.compiler.ltsmin.state.LTSminStateVector;
import spins.promela.compiler.ltsmin.state.LTSminTypeI;
import spins.promela.compiler.ltsmin.state.LTSminTypeStruct;
import spins.promela.compiler.ltsmin.state.LTSminVariable;
import spins.promela.compiler.ltsmin.util.LTSminRendezVousException;
import spins.promela.compiler.ltsmin.util.LTSminUtil.Pair;
import spins.promela.compiler.parser.ParseException;
import spins.promela.compiler.parser.PromelaConstants;
import spins.promela.compiler.variable.ChannelType;
import spins.promela.compiler.variable.ChannelVariable;
import spins.promela.compiler.variable.Variable;
import spins.promela.compiler.variable.VariableType;
import spins.util.IndexedSet;
import spins.util.StringWriter;

/**
 * Generates C code from the LTSminModel
 * 
 * @author FIB, Alfons Laarman
 */
public class LTSminPrinter {

	public static final String IN_VAR              = "in";
	public static final String OUT_VAR             = "out";
	public static final String INITIAL_VAR         = "initial";

	public static final int    PM_MAX_PROCS        = 256;
	public static final String DM_NAME             = "transition_dependency";
	public static final String GM_DM_NAME          = "gm_dm";
	public static final String CO_DM_NAME          = "co_dm";
    public static final String VIS_DM_NAME         = "vis_dm";
    public static final String CODIS_DM_NAME       = "codis_dm";
	public static final String NES_DM_NAME         = "nes_dm";
	public static final String NDS_DM_NAME         = "nds_dm";
	public static final String GM_TRANS_NAME       = "gm_trans";
	public static final int    STATE_ELEMENT_SIZE  = 4;
	public static final String SCRATCH_VARIABLE    = "__spins_scratch";

	public static final String VALID_END_STATE_LABEL_NAME       = "end_";
    public static final String ACCEPTING_STATE_LABEL_NAME       = "accept_";
    public static final String NON_PROGRESS_STATE_LABEL_NAME    = "np_";

    public static final String STATEMENT_EDGE_LABEL_NAME        = "statement";
    public static final String ACTION_EDGE_LABEL_NAME           = "action";
    
    public static final String STATEMENT_TYPE_NAME              = "statement";
    public static final String ACTION_TYPE_NAME                 = "action";
    public static final String BOOLEAN_TYPE_NAME                = "bool";

    public static final String NO_ACTION_NAME                   = "";
    public static final String ASSERT_ACTION_NAME               = "assert";
    public static final String PROGRESS_ACTION_NAME             = "progress";

	static int n_active = 0;

	public static String generateCode(LTSminModel model, boolean no_atomic_loops) {
		StringWriter w = new StringWriter();
		LTSminPrinter.generateModel(w, model, no_atomic_loops);
		return w.toString();
	}
	
	private static void generateModel(StringWriter w, LTSminModel model,
	                                  boolean no_atomic_loops) {
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
		generateGuardFunctions(w, model);
		generateStateDescriptors(w, model);
		generateEdgeDescriptors(w, model);
		if (no_atomic_loops) {
		    generateReachNoTable(w, model);
		} else {
		    generateHashTable(w, model);
		    generateReach(w, model);
		}
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
		w.appendLine("");
		w.appendLine("#define skip true");
		w.appendLine("#define EXPECT_FALSE(e) __builtin_expect(e, 0)\n");
		w.appendLine("#define EXPECT_TRUE(e) __builtin_expect(e, 1)");
		w.appendLine(
    		"#ifdef DNDEBUG\n" + 
    		"#define assert(e,...)    ((void)0);\n" + 
    		"#else\n" + 
    		"#define assert(e,...) \\\n" + 
    		"    if (EXPECT_FALSE(!(e))) {\\\n" + 
    		"        char buf[4096];\\\n" + 
    		"        if (#__VA_ARGS__[0])\\\n" + 
    		"            snprintf(buf, 4096, \": \" __VA_ARGS__);\\\n" + 
    		"        else\\\n" + 
    		"            buf[0] = '\\0';\\\n" + 
    		"        printf(\"assertion \\\"%s\\\" failed%s\", #e, buf);\\\n" + 
    		"        exit(-1);\\\n" +
    		"    }\n" + 
    		"#endif"
		);
		w.appendLine("");
		w.appendLine("typedef struct transition_info {");
		w.indent();
		w.appendLine("int* label;");
		w.appendLine("int  group;");
		w.appendLine("int  dummy;"); // just to make sure we don't overwrite POR info
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
		//if (!model.hasAtomic()) return;
		try {
			w.appendLine(readTextFile(new LTSminPrinter().getClass(), "hashtable.c"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

    private static void generateReachNoTable(StringWriter w, LTSminModel model) {
        //if (!model.hasAtomic()) return;
        try {
            w.appendLine(readTextFile(new LTSminPrinter().getClass(), "reach2.c"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

	private static void generateReach(StringWriter w, LTSminModel model) {
		//if (!model.hasAtomic()) return;
		try {
			w.appendLine(readTextFile(new LTSminPrinter().getClass(), "reach.c"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void generateStateCount(StringWriter w, LTSminModel model) {
		w.appendLine("extern int spins_get_state_size() {");
		w.indent();
		w.appendLine("return ",model.sv.size(),";");
		w.outdent();
		w.appendLine("}");
		w.appendLine("");
	}

	private static void generateTransitionCount(StringWriter w, LTSminModel model) {
		w.appendLine("extern int spins_get_transition_groups() {");
		w.indent();
		w.appendLine("return ",model.getTransitions().size(),";");
		w.outdent();
		w.appendLine("}");
		w.appendLine("");
	}

	private static void generateForwardDeclarations(StringWriter w) {
		w.appendLine("extern inline int spins_reach (void* model, transition_info_t *transition_info, state_t *in, void (*callback)(void* arg, transition_info_t *transition_info, state_t *out), void *arg, int pid);");
		w.appendLine("extern int spins_get_successor_all (void* model, state_t *in, void (*callback)(void* arg, transition_info_t *transition_info, state_t *out), void *arg);");
		w.appendLine("extern int spins_get_successor (void* model, int t, state_t *in, void (*callback)(void* arg, transition_info_t *transition_info, state_t *out), void *arg);");
		w.appendLine("extern void spins_atomic_cb (void* arg, transition_info_t *transition_info, state_t *out, int atomic);");
		w.appendLine("static int "+ SCRATCH_VARIABLE +";");
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
					throw new AssertionError("Cannot parse initial value of state vector: "+ e);
				}
			}
			
		}
		w.outdent().appendPostfix();
		w.appendLine("};");
		w.appendLine("");
		
		w.appendLine("extern void spins_get_initial_state( state_t *to )");
		w.appendLine("{");
		w.indent();
		w.appendLine("if("+ model.sv.size() +"*",STATE_ELEMENT_SIZE," != sizeof(" + C_STATE + "))");
		w.indent();
		w.appendLine("printf(\"state_t SIZE MISMATCH!: state: %zu != %i\",sizeof("+ C_STATE +"),"+ model.sv.size() +"*",STATE_ELEMENT_SIZE,");");
		w.outdent();
		w.appendLine("memcpy(to, (char*)&",INITIAL_VAR,", sizeof(" + C_STATE + "));");
		w.outdent();
		w.appendLine("}");
		w.appendLine("");
	}
	
	private static void generateACallback(StringWriter w, LTSminModel model,
	                                      LTSminTransition t) {
        printEdgeLabels(w, model, t);
        w.appendLine("transition_info.group = "+ t.getGroup() +";");
		w.appendLine("callback(arg,&transition_info,"+ OUT_VAR +");");
		w.appendLine("++states_emitted;");
	}

    private static void printEdgeLabels(StringWriter w, LTSminModel model,
                                        LTSminTransition t) {
        int index = model.getEdgeIndex(STATEMENT_EDGE_LABEL_NAME);
        w.appendLine("transition_labels["+ index +"] = "+ t.getGroup() +";"); // index
        if (t.isProgress()) {
            index = model.getEdgeIndex(ACTION_EDGE_LABEL_NAME);
            int value = model.getTypeValueIndex(ACTION_TYPE_NAME, PROGRESS_ACTION_NAME);
            w.appendLine("transition_labels["+ index +"] = "+ value +";"); // index
        }
    }

	private static void generateLeavesAtomic(StringWriter w, LTSminModel model) {
		List<LTSminTransition> ts = model.getTransitions();
		w.appendLine("char leaves_atomic["+ ts.size() +"] = {");
		w.indent();
		if (ts.size() > 0) {
			int i = 0;
			for (LTSminTransition t : ts) {
				if (0 != i) w.append(",\t// "+ (i-1)).appendPostfix();
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

	public static void generateAnAtomicTransition(StringWriter w, LTSminTransition t,
										   LTSminModel model) {
		w.appendLine("// "+ t.getName());
		w.appendPrefix().append("if (");
		int guards = 0;
		for(LTSminGuardBase g: t.getGuards()) {
		    if (g != t.getGuards().get(0))
		        w.appendPostfix().appendPrefix().append("&&");
			guards += generateGuard(w, model, g, in(model));
		}
		if (guards == 0) w.append("true");
		w.append(") {").appendPostfix();
		w.indent();
		w.appendLine("memcpy(", OUT_VAR,", ", IN_VAR , ", sizeof(", C_STATE,"));");
		List<Action> actions = t.getActions();
		for(Action a: actions)
			generateAction(w,a,model, t);
		// No edge labels! They are discarded anyway!
		w.appendLine("transition_info.group = "+ t.getGroup() +";");
		w.appendLine("spins_atomic_cb(arg,&transition_info,"+OUT_VAR+","+ t.getEndId() +");");
		w.appendLine("++states_emitted;");
		w.outdent();
		w.appendLine("}");
	}

	private static void generateGetAll(StringWriter w, LTSminModel model) {
		/* PROMELA specific per-proctype code */
	    if (model.getTransitions().size() == 0) return;
		for (ProcInstance p : model.getTransitions().get(0).getProcess().getSpecification()) {
			w.appendLine("int spins_get_successor_sid"+ p.getID() +"( void* model, state_t *in, void *arg, state_t *"+ OUT_VAR +") {");
			w.indent();
			String edge_array = "";
			for (int i = 0; i < model.getEdges().size(); i++)
			    edge_array += "0, ";
			w.appendLine("int transition_labels["+ model.getEdges().size() +"] = {"+ edge_array +"};");
			w.appendLine("transition_info_t transition_info = { transition_labels, -1 };");
			w.appendLine("int states_emitted = 0;");
			for (Variable local : model.getLocals()) {
				w.appendLine("int "+ local.getName() +";");
			}
			w.appendLine();
			List<LTSminTransition> transitions = model.getTransitions();
			for(LTSminTransition t : transitions) {
				if (t.getProcess() != p) continue;
				generateAnAtomicTransition(w, t, model);
			}
			w.appendLine("return states_emitted;");
			w.outdent();
			w.appendLine("}");
			w.appendLine();
		}
		w.appendLine("int spins_get_successor_sid( void* model, state_t *in, void *arg, state_t *"+ OUT_VAR +", int atomic) {");
		w.indent();
		w.appendLine("switch (atomic) {");
		for (ProcInstance p : model.getTransitions().get(0).getProcess().getSpecification()) {
			w.appendLine("case "+ p.getID() +": return spins_get_successor_sid"+ p.getID() +"(model, in, arg, "+ OUT_VAR +"); break;");
		}
		w.appendLine("default: printf(\"Wrong structural ID\"); exit(-1);");
		w.appendLine("}");
		w.outdent();
		w.appendLine("}");
		w.appendLine();
		/* END PROMELA specific code */

		w.appendLine("int spins_get_successor_all( void* model, state_t *in, void (*callback)(void* arg, transition_info_t *transition_info, state_t *out), void *arg) {");
		w.indent();
		String edge_array = "";
        for (int i = 0; i < model.getEdges().size(); i++)
            edge_array += "0, ";
        w.appendLine("int transition_labels["+ model.getEdges().size() +"] = {"+ edge_array +"};");
		w.appendLine("transition_info_t transition_info = { transition_labels, -1 };");
		w.appendLine("int states_emitted = 0;");
		w.appendLine("state_t tmp;");
		w.appendLine("state_t *"+ OUT_VAR +" = &tmp;");
		for (Variable local : model.getLocals()) {
			w.appendLine("int "+ local.getName() +";");
		}
        generateAssertions(w, model);
		w.appendLine();
		List<LTSminTransition> transitions = model.getTransitions();
		for(LTSminTransition t : transitions) {
			generateATransition(w, t, model, true);
		}
		w.appendLine("return states_emitted;");
		w.outdent();
		w.appendLine("}");
		w.appendLine();
	}

	private static void generateAssertions(StringWriter w, LTSminModel model) {
        for (Pair<Expression, String> p : model.assertions) {
            w.appendPrefix();
            w.append("assert(");
            generateExpression(w, p.left, in(model));
            w.append(", \""+ p.right +"\");");
            w.appendPostfix();
        }
    }

    public static void generateATransition(StringWriter w, LTSminTransition t,
										   LTSminModel model, boolean many) {
		w.appendLine("// "+ t.getName());
        w.appendPrefix().append("if (");
        int guards = 0;
		for(LTSminGuardBase g: t.getGuards()) {
			if (g != t.getGuards().get(0))
			    w.appendPostfix().appendPrefix().append("&&");
			if (many) guards += generateGuardMany(w, model, g, in(model));
			else      guards += generateGuard(w, model, g, in(model));
		}
		if (guards == 0) w.append("true");
		w.append(") {").appendPostfix();
		w.indent();
		w.appendLine("memcpy(", OUT_VAR,", ", IN_VAR , ", sizeof(", C_STATE,"));");
		List<Action> actions = t.getActions();
		for(Action a: actions)
			generateAction(w,a,model, t);
		if (t.isAtomic()) {
		    printEdgeLabels (w, model, t);
			w.appendLine("transition_info.group = "+ t.getGroup() +";");
			w.appendLine("int count = spins_reach (model, &transition_info, "+ OUT_VAR +", callback, arg, "+ t.getEndId() +");");
			w.appendLine("states_emitted += count;"); // non-deterministic atomic sequences emit multiple states
		} else {
			generateACallback(w, model, t);
		}
		w.outdent();
		w.appendLine("}");
	}

	private static void generateGetNext(StringWriter w, LTSminModel model) {
		w.appendLine("int spins_get_successor( void* model, int t, state_t *in, void (*callback)(void* arg, transition_info_t *transition_info, state_t *out), void *arg) {");
		w.indent();
		String edge_array = "";
        for (int i = 0; i < model.getEdges().size(); i++)
            edge_array += "0, ";
        w.appendLine("int transition_labels["+ model.getEdges().size() +"] = {"+ edge_array +"};");
		w.appendLine("transition_info_t transition_info = { transition_labels, t };");
		w.appendLine("int states_emitted = 0;");
		w.appendLine("int minus_one = -1;");
		w.appendLine("int *atomic = &minus_one;");
		w.appendLine(C_STATE," local_state;");
		w.appendLine(C_STATE,"* ",OUT_VAR," = &local_state;");
		for (Variable local : model.getLocals()) {
			w.appendLine("int "+ local.getName() +";");
		}
        generateAssertions(w, model);
		w.appendLine();
		w.appendLine("switch(t) {");
		List<LTSminTransition> transitions = model.getTransitions();
		int trans = 0;
		for(LTSminTransition t : transitions) {
			w.appendLine("case ",trans,": {");
			w.indent();
			generateATransition(w, t, model, false);
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

    private static int generateGuardMany(StringWriter w, LTSminModel model,
                                      LTSminGuardBase guard, LTSminPointer state) {
        if (guard.isDeadlock()) {
            w.append("0 == states_emitted");
        } else {
            return generateGuard(w ,model, guard, state);
        }
        return 1;
    }

	private static int generateGuard(StringWriter w, LTSminModel model,
								      LTSminGuardBase guard, LTSminPointer state) {
        Expression expression = guard.getExpression();
        if (expression == null) return 0;
        generateExpression(w, expression, state);
        return 1;
	}

	private static void generateAction(StringWriter w, Action a,
	                                   LTSminModel model, LTSminTransition t) {
		if(a instanceof AssignAction) { //TODO: assign + expr + runexp
			AssignAction as = (AssignAction)a;
			Identifier id = as.getIdentifier();
			switch (as.getToken().kind) {
				case ASSIGN:
				    /*if (id.getResultType() instanceof CustomVariableType && id.getSub() == null) {
				        CustomVariableType ct = (CustomVariableType) id.getResultType();
				        if (!(as.getExpr() instanceof Identifier))
				            throw new AssertionError("Expected Identifier for struct assignment");
				        Identifier id2 = (Identifier) as.getExpr();
				        AssignAction as2;
				        for (Variable v : ct.getVariableStore().getVariables()) {
				            Identifier sub = new Identifier(id, v);
				            Identifier sub2 = new Identifier(id2, v);
                            as2 = new AssignAction(as.getToken(), sub, sub2);
				            generateAction(w, as2, model);
				        }
				        return;
				    }*/ // TODO: implement copying of structs (structs in channels)
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
			String expression = generateExpression(model, e);
			/*
			int index = assertions.indexOf(expression);
			if (-1 == index) {
				assertions.add(expression);
				index = assertions.size() - 1;
			}
			*/
			w.appendPrefix();
			w.append("if(!");
			w.append(expression);
			w.append(") {");
			w.appendPostfix();
			w.indent();
			if (t.isProgress())
			    System.err.println("Warning: assert action will be overwritten by progress action!");
			int index = model.getEdgeIndex(ACTION_EDGE_LABEL_NAME);
            int value = model.getTypeValueIndex(ACTION_TYPE_NAME, ASSERT_ACTION_NAME);
			w.appendLine("transition_labels["+ index +"] = "+ value +";"); // index
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
			if (expr.getSideEffect() == null) return; // Simple expressions are guards
			//a RunExpression has side effects... yet it does not block if less than 255 processes are started atm
			assert (expr instanceof RunExpression);
			RunExpression re = (RunExpression)expr;
			ProcInstance instance = re.getInstances().get(0); // just take the first!

			//if (re.getInstances().size() > 1) {
				Proctype proc = re.getProctype();
				Expression activeExpr = null;
				for (ProcInstance inst : proc.getInstances()) {
					Variable pc = model.sv.getPC(inst);
					Expression e = compare(PromelaConstants.NEQ, id(pc), constant(-1));
					if (activeExpr == null) {
						activeExpr = e;
					} else {
						activeExpr = calc(PromelaConstants.PLUS, e, activeExpr);
					}
				}
				String activeStr = generateExpression(model, activeExpr);
				++n_active;
				w.appendLine("int __active_" + n_active + " = "+ activeStr +";");

				w.appendLine("if (__active_" + n_active + " >= "+ re.getProctype().getInstances().size() +") {");
				w.appendLine("	printf (\"Error, too many instances for  "+ instance.getTypeName() +": %d.\\n\", __active_" + n_active + ");");
				w.appendLine("	printf (\"Exiting on '"+ re +"'.\\n\");");
				w.appendLine("	exit (1);");
				w.appendLine("}");
			//}

			StringWriter w2 = new StringWriter();
			//only one dynamic process supported atm
			w2.appendLine("if (-1 != "+ printPC(instance, out(model)) +") {");
			w2.appendLine("	printf (\"Instance %d of process "+ instance.getTypeName() +" was already started.\\n\", __active_" + n_active + ");");
			w2.appendLine("	printf (\"Exiting on '"+ re +"'.\\n\");");
			w2.appendLine("	exit (1);");
			w2.appendLine("}");
			
			//set pid
			Action update_pid = assign(model.sv.getPID(instance),
										id(LTSminStateVector._NR_PR));
			generateAction(w2, update_pid, model, t);

			//activate process
			Action update_pc = assign(model.sv.getPC(instance), 0);
			generateAction(w2, update_pc, model, t);
			w2.appendLine("++("+ printVar(_NR_PR, out(model)) +");");
			
			List<Variable> args = instance.getArguments();
			Iterator<Expression> eit = re.getExpressions().iterator();
			if (args.size() != re.getExpressions().size())
				throw error("Run expression's parameters do not match the proc's arguments.", re.getToken());
			// write to the arguments of the target process
			for (Variable v : args) {
				Expression e = eit.next();
				// channels are passed by reference: TreeWalker.bindByReferenceCalls 
				if (!(v.getType() instanceof ChannelType) && !v.isStatic()) {
					Action aa = assign(v, e);
					generateAction(w2, aa, model, t);
				}
			}
			for (Action action: re.getActions())
				generateAction(w2, action, model, t);

			String ccode = w2.toString();
			if (re.getInstances().size() > 1) {
				String struct = model.sv.getMember(instance.getName()).getType().toString();
				ccode = ccode.replace(OUT_VAR +"->"+ instance.getName(), 
					"(("+ struct +"*)&"+ OUT_VAR +"->"+ instance.getName() +")[__active_" + n_active + "]");
			}
			w.append(ccode);
		} else if(a instanceof OptionAction) { // options in a d_step sequence
			OptionAction oa = (OptionAction)a;
			if (oa.loops()) {
				String var = oa.getLabel() +"_var";
				w.appendLine("int "+ var +" = true;");
				w.append(oa.getLabel() +":\t");
				w.append("while ("+ var +") {").appendPostfix();
				w.indent();
			}
			boolean first = true;
			for (Sequence seq : oa) {
				if (!first)
					w.appendPrefix().append("} else if (");
				else
					w.appendPrefix().append("if (");
				Action guardAction = seq.iterator().next();
				LTSminGuardAnd ag = new LTSminGuardAnd();
				try {
                    LTSminTreeWalker.createEnabledGuard(guardAction, ag);
                } catch (LTSminRendezVousException e) {
                    throw new AssertionError(e);
                }
				int count = generateGuard(w, model, ag, out(model));
				if (count == 0) w.append("true");
				w.append(") {").appendPostfix();
				w.indent();
				for (Action act : seq) {
					if (act instanceof BreakAction) {
						OptionAction loop = ((BreakAction)act).getLoop();
						String var = loop.getLabel() +"_var";
						w.appendLine(var +" = false;");
						w.appendLine("goto "+ loop.getLabel() +";");
					}
					generateAction(w, act, model, t);
				}
				w.outdent();
				first = false;
			}
			if (oa.loops()) {
				w.appendLine("} else { assert(false, \"Blocking loop in d_step\"); }");
				w.outdent();
				w.appendLine("}");
			} else {
				w.appendLine("}");
			}
		} else if(a instanceof BreakAction) {
			// noop
		} else if(a instanceof ElseAction) {
			// noop
		} else if(a instanceof LabelAction) {
			w.appendLine(((LabelAction)a).getId() +":");
		} else if(a instanceof GotoAction) {
			w.appendLine("goto "+ ((GotoAction)a).getId() +";");
		} else if(a instanceof ChannelSendAction) {
			ChannelSendAction csa = (ChannelSendAction)a;
			Identifier id = csa.getIdentifier();
			List<Expression> exprs = csa.getExprs();
			for (int e = 0; e < exprs.size(); e++) {
				final Expression expr = exprs.get(e);
				generateAction(w, assign(channelNext(id,e), expr), model, t);
			}
			generateAction(w, incr(chanLength(id)), model, t);
		} else if (a instanceof ChannelReadAction) {
			ChannelReadAction cra = (ChannelReadAction)a;
			Identifier id = cra.getIdentifier();
			ChannelVariable var = (ChannelVariable)id.getVariable();
			int bufferSize = var.getType().getBufferSize();
			List<Expression> exprs = cra.getExprs();
			String len = print(chanLength(id), out(model));
			Identifier jndex = new LTSminIdentifier(model.jndex);
			if (cra.isRandom()) {
				w.appendLine("for (j = 0; j < ", len, "; j++) {");
				w.indent();
	 			for (int e = 0; e < exprs.size(); e++) {
					final Expression expr = exprs.get(e);
					if (expr instanceof Identifier) continue;
					Expression m = channelIndex(id, jndex, e);
					String cond = print(not(eq(m, expr)), out(model));
					w.appendLine("if ("+ cond +") continue;");
				}
			} else {
				w.appendLine("j = 0;");
			}
 			for (int e = 0; e < exprs.size(); e++) {
				final Expression expr = exprs.get(e);
				if (expr instanceof Identifier) {
					Identifier p = (Identifier)expr;
					Expression m = channelBottom(id, e);
					generateAction(w, assign(p, m), model, t);
				}
			}
			if (cra.isRandom()) {
				w.appendLine("break;");
				w.outdent();
				w.appendLine("}");
			} 
			if (!cra.isPoll()) {
				String j = print(jndex, out(model));
				Identifier index = new LTSminIdentifier(model.index);
				Expression pp = calc(PromelaConstants.PLUS, index, constant(1));
				if (bufferSize > 1) { // replacement for canonical state vector (sym. red.)
					w.appendLine("for (i = "+ j +"; i < ", len, " - 1; i++) {");
					w.indent();
					for (int e = 0; e < exprs.size(); e++) {
						Identifier m = channelIndex(id, index, e);
						Identifier mpp = channelIndex(id, pp, e);
						generateAction(w, assign(m, mpp), model, t);
					}
					w.outdent();
					w.appendLine("}");
				}
				generateAction(w, decr(chanLength(id)), model, t);
				for (int e = 0; e < exprs.size(); e++) {
					generateAction(w, assign(channelNext(id,e), constant(0)), model, t);
				}
			}
		} else {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+a.getClass().getName());
		}
	}

	private static String generateExpression(LTSminModel model, Expression e) {
		StringWriter w2 = new StringWriter();
		generateExpression(w2, e, out(model));
		String expression = w2.toString();
		return expression;
	}

	public static class ExprPrinter {
		LTSminPointer start;
		public ExprPrinter(LTSminPointer start) {
			this.start = start;
		}
		
		public String print(Expression e) {
			if (null == e)
				return null;
			if (e instanceof LTSminIdentifier) {
				StringWriter w = new StringWriter();
				generateExpression(w, e, start);
				return w.toString();
			} else if (e instanceof Identifier) {
				Identifier id = (Identifier)e;
				if (id.getVariable().isHidden())
					return SCRATCH_VARIABLE;
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
			if (id.isPointer())
				w.append("*"+ id.getVariable().getName());
			else
				w.append(id.getVariable().getName());
		} else if(e instanceof Identifier) {
			Identifier id = (Identifier)e;
			w.append(print(id, state));
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
		} else if(e instanceof ChannelLengthExpression) {
			ChannelLengthExpression cle = (ChannelLengthExpression)e;
			Identifier id = (Identifier)cle.getExpression();
			generateExpression(w, chanLength(id), state);
		} else if(e instanceof ChannelReadExpression) {
			ChannelReadExpression cre = (ChannelReadExpression)e;
			Identifier id = cre.getIdentifier();
			ChannelType ct = (ChannelType)id.getVariable().getType();
			if (ct.isRendezVous())
				throw new AssertionError("ChannelReadAction on rendez-vous channel.");
			
			w.append("((");
			int max = cre.isRandom() ? ct.getBufferSize() : 1;
			for (int b = 0 ; b < max; b++) {
				if (b > 0) w.append(")||(");
				Expression size = chanLength(id);
				w.append("((");
				generateExpression(w, size, state);
				w.append(" > "+ b +")");
				List<Expression> exprs = cre.getExprs();
				for (int i = 0; i < exprs.size(); i++) {
					final Expression expr = exprs.get(i);
					if (expr instanceof Identifier) // always matches
						continue;
					w.append(" && (");
					Expression read = channelIndex(id, constant(b), i);
					generateExpression(w, read, state);
					w.append(" == ");
					generateExpression(w, expr, state);
					w.append(")");
				}
				w.append(")");
			}
			w.append("))");
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
					w.append("true");
					break;
				case FALSE:
					w.append("false");
					break;
				case SKIP_:
					w.append("skip");
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
			w.append("("+ printVar(_NR_PR, state) +" != "+ (PM_MAX_PROCS-1) +")");
		} else if (e instanceof RemoteRef) {
			RemoteRef rr = (RemoteRef)e;
			Variable pc = rr.getPC(null);
			int num = rr.getLabelId();
			Expression comp = compare(PromelaConstants.EQ, id(pc), constant(num));
			generateExpression(w, comp, state);
		} else if(e instanceof EvalExpression) {
			EvalExpression eval = (EvalExpression)e;
			generateExpression(w, eval.getExpression(), state);
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
		w.appendLine("extern const int* spins_get_transition_read_dependencies(int t)");
		w.appendLine("{");
		w.appendLine("	if (t>=0 && t < "+ dm.getRows() +") return "+ DM_NAME +"[t][0];");
		w.appendLine("	return NULL;");
		w.appendLine("}");
		w.appendLine("");
		w.appendLine("extern const int* spins_get_transition_write_dependencies(int t)");
		w.appendLine("{");
		w.appendLine("	if (t>=0 && t < "+ dm.getRows()+ ") return "+ DM_NAME +"[t][1];");
		w.appendLine("	return NULL;");
		w.appendLine("}");
		w.appendLine("");
	}

	private static void generateStateDescriptors(StringWriter w, LTSminModel model) {
		int state_size = model.sv.size();
        Set<String> types = model.getTypes();

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

		w.appendLine("");
		w.appendLine("static const char* var_types[] = {");
		w.indent();
        for (String s : types) {
			w.appendLine("\"",s,"\",");
		}
		w.appendLine("\"\"");
		w.outdent();
		w.appendLine("};");

		w.appendLine("");
		w.appendLine("static const int var_type[] = {");
		w.indent();
		for (LTSminSlot slot : model.sv) {
			LTSminVariable var = slot.getVariable();
			String cType = var.getVariable().getType().getName();
			int num = model.getTypeIndex(cType);
			w.appendLine(num,",");
		}
		w.appendLine("-1");
		w.outdent();
		w.appendLine("};");

		w.appendLine("");
		w.appendLine("static const int var_type_value_count[] = {");
		w.indent();
		for (IndexedSet<String> vals : model.getTypeValues()) {
		    w.appendLine(vals.size() ,",");
		}
		w.appendLine("-1");
		w.outdent();
		w.appendLine("};");
	
		w.appendLine("");
		for (int i = 0; i < types.size(); i++) {
		    String s = model.getType(i);
			w.appendLine("static const char* const var_type_",s,"[] = {");
			w.indent();
			IndexedSet<String> values = model.getTypeValues(i);
            for (String value : values) {
				w.appendLine("\"",value,"\",");
			}
			if (values.size()> 0) w.appendLine("\"\"");
			w.outdent();
			w.appendLine("};");
			w.appendLine("");
		}
		
		w.appendLine("static const char* const * const var_type_values[] = {");
		w.indent();
		for (String s : types)
			w.appendLine("var_type_",s,",");
		w.appendLine("NULL");
		w.outdent();
		w.appendLine("};");

		w.appendLine("");
		w.appendLine("extern const char* spins_get_state_variable_name(unsigned int var) {");
		w.indent();
		w.appendLine("assert(var < ",state_size,", \"spins_get_state_variable_name: invalid variable index %d\", var);");
		w.appendLine("return var_names[var];");
		w.outdent();
		w.appendLine("}");
		w.appendLine("");

		w.appendLine("extern int spins_get_type_count() {");
		w.indent();
		w.appendLine("return ",types.size(),";");
		w.outdent();
		w.appendLine("}");
		w.appendLine("");

		w.appendLine("extern const char* spins_get_type_name(int type) {");
		w.indent();
		w.appendLine("assert(type > -1 && type < ",types.size(),", \"spins_get_type_name: invalid type index %d\", type);");
		w.appendLine("return var_types[type];");
		w.outdent();
		w.appendLine("}");
		w.appendLine("");

		w.appendLine("extern int spins_get_type_value_count(int type) {");
		w.indent();
		w.appendLine("assert(type > -1 && type < ",types.size(),", \"spins_get_type_value_count: invalid type index %d\", type);");
		w.appendLine("return var_type_value_count[type];");
		w.outdent();
		w.appendLine("}");
		w.appendLine("");

		w.appendLine("extern const char* spins_get_type_value_name(int type, int value) {");
		w.indent();
		w.appendLine("assert(type > -1 && type < ",types.size(),", \"spins_get_type_value_name: invalid type %d\", type);");
		w.appendLine("assert(value <= var_type_value_count[type], \"spins_get_type_value_name: invalid type %d\", value);");
		w.appendLine("return var_type_values[type][value];");
		w.outdent();
		w.appendLine("}");
		w.appendLine("");

		w.appendLine("extern int spins_get_state_variable_type(int var) {");
		w.indent();
		w.appendLine("assert(var > -1 && var < ",state_size,", \"spins_get_state_variable_type: invalid variable %d\", var);");
		w.appendLine("return var_type[var];");
		w.outdent();
		w.appendLine("}");
		w.appendLine("");
	}

	private static void generateEdgeDescriptors(StringWriter w, LTSminModel model) {

        IndexedSet<String> edges = model.getEdges();

		// Generate static list of names
		w.appendLine("static const char* edge_names[] = {");
		w.indent();
        for (String edge : edges) {
			if (edge != edges.getIndex(0))
				w.append(",").appendPostfix();
			w.appendPrefix();
			w.append("\""+ edge +"\"");
		}
		w.outdent().appendPostfix();
		w.appendLine("};");
		w.appendLine("");

		w.appendLine("extern const char* spins_get_edge_name(int index) {");
		w.indent();
		w.appendLine("assert(index < ",edges.size(),", \"spins_get_edge_name: invalid type index %d\", index);");
		w.appendLine("return edge_names[index];");
		w.outdent();
		w.appendLine("}");
		w.appendLine("");

        w.appendLine("static const int edge_type[] = {");
        w.indent();
        for (int i = 0; i < edges.size(); i++) {
            int num = model.getTypeIndex(model.getEdgeType(i));
            w.appendLine(num,",");
        }
        w.appendLine("-1");
        w.outdent();
        w.appendLine("};");
        w .appendLine("");

        w.appendLine("extern int spins_get_edge_count() {");
        w.indent();
        w.appendLine("return "+ edges.size() +";");
        w.outdent();
        w.appendLine("}");
        w.appendLine("");

        w.appendLine("extern int spins_get_edge_type(int edge) {");
        w.indent();
        w.appendLine("assert(edge < ",edges.size(),", \"spins_get_edge_type: invalid type index %d\", edge);");
        w.appendLine("return edge_type[edge];");
        w.outdent();
        w.appendLine("}");
        w.appendLine("");
	}
	
	private static void generateGuardMatrices(StringWriter w, LTSminModel model) {
		GuardInfo gm = model.getGuardInfo();
		if(gm==null) return;

		DepMatrix co_matrix = gm.getCoMatrix();
        DepMatrix codis_matrix = gm.getInverseCoenMatrix();

        w.appendLine("");
        w.appendLine("// Visibility matrix:");
        generateDepMatrix(w, gm.getVisibilityMatrix(), VIS_DM_NAME, false);
        w.appendLine("");

		w.appendLine("");
		w.appendLine("// Label(Guard)-Dependency Matrix:");
		generateDepMatrix(w, gm.getDepMatrix(), GM_DM_NAME, false);
		w.appendLine("");

		w.appendLine("");
		w.appendLine("// Maybe Co-Enabled Matrix:");
		generateDepMatrix(w, co_matrix, CO_DM_NAME, false);
		w.appendLine("");

        w.appendLine("");
        w.appendLine("// Maybe Co-Disabled Matrix:");
        generateDepMatrix(w, codis_matrix, CODIS_DM_NAME, false);
        w.appendLine("");

		w.appendLine("");
		w.appendLine("// Necessary Enabling Matrix:");
		generateDepMatrix(w, gm.getNESMatrix(), NES_DM_NAME, false);
		w.appendLine("");

		w.appendLine("");
		w.appendLine("// Necessary Disabling Matrix:");
		generateDepMatrix(w, gm.getNDSMatrix(), NDS_DM_NAME, false);
		w.appendLine("");

        List<List<Integer>> trans_matrix = gm.getTransMatrix();
		generateTransGuardMatrix(w, trans_matrix);
	}

	private static void generateTransGuardMatrix(StringWriter w,
												List<List<Integer>> trans_matrix) {
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

	private static void generateGuardFunctions(StringWriter w, LTSminModel model) {
	    GuardInfo gm = model.getGuardInfo();

	    w.appendLine("int spins_get_guard_count() {");
		w.indent();
		w.appendLine("return ",gm.getNumberOfGuards(),";");
		w.outdent();
		w.appendLine("}");
		w.appendLine("");

        w.appendLine("int spins_get_label_count() {");
        w.indent();
        w.appendLine("return ",gm.getNumberOfLabels(),";");
        w.outdent();
        w.appendLine("}");
        w.appendLine("");
		
		w.appendLine("const int* spins_get_labels(int t) {");
		w.indent();
		w.appendLine("assert(t < ",gm.getTransMatrix().size(),", \"spins_get_labels: invalid transition index %d\", t);");
		w.appendLine("return "+ GM_TRANS_NAME +"[t];");
		w.outdent();
		w.appendLine("}");
		w.appendLine("");

		w.appendLine("const int*** spins_get_all_labels() {");
		w.indent();
		w.appendLine("return (const int***)&"+ GM_TRANS_NAME +";");
		w.outdent();
		w.appendLine("}");
		w.appendLine("");

		w.appendLine("const int* spins_get_label_may_be_coenabled_matrix(int g) {");
		w.indent();
		w.appendLine("assert(g < ",gm.getNumberOfLabels(),", \"spins_get_label_may_be_coenabled_matrix: invalid guard index %d\", g);");
		w.appendLine("return "+ CO_DM_NAME +"[g];");
		w.outdent();
		w.appendLine("}");
		w.appendLine("");

        w.appendLine("const int* spins_get_label_visiblity_matrix(int g) {");
        w.indent();
        w.appendLine("assert(g < ",gm.getNumberOfLabels(),", \"spins_get_label_visiblity_matrix: invalid guard index %d\", g);");
        w.appendLine("return "+ VIS_DM_NAME +"[g];");
        w.outdent();
        w.appendLine("}");

		w.appendLine("const int* spins_get_label_nes_matrix(int g) {");
		w.indent();
		w.appendLine("assert(g < ",gm.getNumberOfLabels(),", \"spins_get_label_nes_matrix: invalid guard index %d\", g);");
		w.appendLine("return "+ NES_DM_NAME +"[g];");
		w.outdent();
		w.appendLine("}");
		w.appendLine("");

		w.appendLine("const int* spins_get_label_nds_matrix(int g) {");
		w.indent();
		w.appendLine("assert(g < ",gm.getNumberOfLabels(),", \"spins_get_label_nds_matrix: invalid guard index %d\", g);");
		w.appendLine("return "+ NDS_DM_NAME +"[g];");
		w.outdent();
		w.appendLine("}");
		w.appendLine("");
		
		w.appendLine("const int* spins_get_label_matrix(int g) {");
		w.indent();
		w.appendLine("assert(g < ",gm.getNumberOfLabels(),", \"spins_get_label_matrix: invalid guard index %d\", g);");
		w.appendLine("return "+ GM_DM_NAME +"[g];");
		w.outdent();
		w.appendLine("}");
		w.appendLine("");
		
		w.appendLine("int spins_get_label(void* model, int g, ",C_STATE,"* ",IN_VAR,") {");
		w.indent();
        w.appendLine("(void)model;");
		w.appendLine("assert(g < ",gm.getNumberOfLabels(),", \"spins_get_label: invalid guard index %d\", g);");
		w.appendLine("switch(g) {");
		w.indent();
		for(int g=0; g<gm.getNumberOfLabels(); ++g) {
			w.appendPrefix();
			w.append("case ").append(g).append(": return ");
			generateGuard(w, model, gm.getLabel(g), in(model));
			w.append(" != 0;");
			w.appendPostfix();
		}
		w.outdent();
		w.appendLine("}");
		w.appendLine("return false;");
		w.outdent();
		w.appendLine("}");
		w.appendLine("");

        w.appendLine("const char *spins_get_label_name(int g) {");
        w.indent();
        w.appendLine("assert(g < ",gm.getNumberOfLabels(),", \"spins_get_label_name: invalid guard index %d\", g);");
        w.appendLine("switch(g) {");
        w.indent();
        for(int g=0; g<gm.getNumberOfLabels(); ++g) {
            w.appendPrefix();
            w.append("case "+ g +": return \""+ gm.getLabelName(g) +"\";");
            w.appendPostfix();
        }
        w.outdent();
        w.appendLine("}");
        w.appendLine("return \"\";");
        w.outdent();
        w.appendLine("}");
        w.appendLine("");
		
		w.appendLine("void spins_get_labels_all(void* model, ",C_STATE,"* ",IN_VAR,", int* label) {");
		w.indent();
		w.appendLine("(void)model;");
		for(int g=0; g<gm.getNumberOfLabels(); ++g) {
			w.appendPrefix();
			w.append("label[").append(g).append("] = ");
			generateGuard(w, model, gm.getLabel(g), in(model));
			w.append(" != 0;");
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

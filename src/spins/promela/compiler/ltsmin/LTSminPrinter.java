package spins.promela.compiler.ltsmin;

import static spins.promela.compiler.Specification._NR_PR;
import static spins.promela.compiler.ltsmin.LTSminTreeWalker.createCustomIdentifiers;
import static spins.promela.compiler.ltsmin.state.LTSminStateVector.C_STATE;
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
import java.util.Map.Entry;
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
import spins.promela.compiler.expression.TranslatableExpression;
import spins.promela.compiler.ltsmin.LTSminTreeWalker.Options;
import spins.promela.compiler.ltsmin.matrix.DepMatrix;
import spins.promela.compiler.ltsmin.matrix.DepMatrix.DepRow;
import spins.promela.compiler.ltsmin.matrix.LTSminGuardAnd;
import spins.promela.compiler.ltsmin.matrix.LTSminGuardBase;
import spins.promela.compiler.ltsmin.matrix.RWMatrix;
import spins.promela.compiler.ltsmin.matrix.RWMatrix.RWDepRow;
import spins.promela.compiler.ltsmin.model.GuardInfo;
import spins.promela.compiler.ltsmin.model.LTSminIdentifier;
import spins.promela.compiler.ltsmin.model.LTSminModel;
import spins.promela.compiler.ltsmin.model.LTSminTransition;
import spins.promela.compiler.ltsmin.model.ResetProcessAction;
import spins.promela.compiler.ltsmin.state.LTSminPointer;
import spins.promela.compiler.ltsmin.state.LTSminSlot;
import spins.promela.compiler.ltsmin.state.LTSminSubVector;
import spins.promela.compiler.ltsmin.state.LTSminTypeI;
import spins.promela.compiler.ltsmin.state.LTSminTypeNative;
import spins.promela.compiler.ltsmin.state.LTSminTypeStruct;
import spins.promela.compiler.ltsmin.state.LTSminVariable;
import spins.promela.compiler.ltsmin.util.LTSminRendezVousException;
import spins.promela.compiler.ltsmin.util.LTSminUtil.Pair;
import spins.promela.compiler.parser.ParseException;
import spins.promela.compiler.parser.PromelaConstants;
import spins.promela.compiler.variable.ChannelType;
import spins.promela.compiler.variable.ChannelVariable;
import spins.promela.compiler.variable.CustomVariableType;
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
    public static final String DM_ACTIONS_NAME     = "actions_read_dependency";
	public static final String GM_DM_NAME          = "gm_dm";
    public static final String CO_DM_NAME          = "co_dm";
    public static final String DNA_DM_NAME         = "dna_dm";
    public static final String ACT_DM_NAME         = "act_dm";
    public static final String COMMUTES_DM_NAME    = "commutes_dm";
	public static final String NES_DM_NAME         = "nes_dm";
	public static final String NDS_DM_NAME         = "nds_dm";
	public static final String MES_DM_NAME         = "dm_must_enable";
	public static final String ICE_DM_NAME 		   = "dm_mce_invert1";
	public static final String MDS_DM_NAME 		   = "dm_must_disable";
	public static final String GM_TRANS_NAME       = "gm_trans";
	public static final int    STATE_ELEMENT_SIZE  = 4;
	public static final String SCRATCH_VARIABLE    = "__spins_scratch";

	public static final String VALID_END_STATE_LABEL_NAME       = "end_";
    public static final String ACCEPTING_STATE_LABEL_NAME       = "accept_";
    public static final String NON_PROGRESS_STATE_LABEL_NAME    = "np_";
    public static final String PROGRESS_STATE_LABEL_NAME        = "progress_";
    public static final String ASSERT_STATE_LABEL_PREFIX        = "assert_";

    public static final String STATEMENT_EDGE_LABEL_NAME        = "statement";
    public static final String ACTION_EDGE_LABEL_NAME           = "action";

    public static final String STATEMENT_TYPE_NAME              = "statement";
    public static final String ACTION_TYPE_NAME                 = "action";
    public static final String BOOLEAN_TYPE_NAME                = "bool";
    public static final String GUARD_TYPE_NAME                  = "guard";

    public static final String NO_ACTION_NAME                   = "";
    public static final String ASSERT_ACTION_NAME               = "assert";
    public static final String PROGRESS_ACTION_NAME             = "progress";
    
    
    private static  int extra_label             = 0;

	static int n_active = 0;

	public static String generateCode(LTSminModel model, Options opts) {
		StringWriter w = new StringWriter(opts);
		LTSminPrinter.generateModel(w, model);
		return w.toString();
	}

	private static void generateModel(StringWriter w, LTSminModel model) {
		if (w.options.no_gm) {
			w.appendLine("#define SPINS_TEST_CODE").appendLine("");
		}
		
		generateHeader(w, model);
		generateNativeTypes(w);
		generateTypeDef(w, model);
		generateForwardDeclarations(w, model);
		generateStateCount(w, model);
		generateInitialState(w, model);
		generateLeavesAtomic(w, model);
		generateGetActions(w, model);
		generateGetNext(w, model);
		generateGetAll(w, model);
		generateTransitionCount(w, model);
		generateEdgeMatrices(w, model);
        generateDepMatrix(w, model.getAtomicDepMatrix().read, DM_ACTIONS_NAME);
		generateDepMatrix(w, model.getDepMatrix(), DM_NAME);
		generateDMFunctions(w, model.getDepMatrix());
		generateGuardMatrices(w, model, w.options.no_gm);
	    generateOtherMatrices(w, model);
		generateGuardFunctions(w, model, w.options.no_gm);
		generateStateDescriptors(w, model);
		generateEdgeDescriptors(w, model);

        // Used when control flow in atomics is a DAGs, i.e. when  there is no
        // need for duplicate detection
        generateReachNoTable(w, model);

        if (model.hasAtomicCycles) {
		    // control flow in atomics contains cycles
            generateHashTable(w, model);
            generateReach(w, model);
		}
	}

	private static void generateOtherMatrices(StringWriter w, LTSminModel model) {
        GuardInfo gi = model.getGuardInfo();

        w.appendLine("");
        w.appendLine("static const char *matrices[] = {");
        w.indent();
        for (String matrix : gi.exports) {
            w.appendLine("\"",matrix,"\",");
        }
        w.appendLine("\"\"");
        w.outdent();
        w.appendLine("};");

        w.appendLine("");
        w.appendLine("static const int matrix_dimensions[][2] = {");
        w.indent();
        for (String matrix : gi.exports) {
            DepMatrix dm = gi.getMatrix(matrix);
            int rows = dm.getNrRows();
            int cols = dm.getNrCols();
            w.appendLine("{"+ rows +", "+ cols +"},");
        }
        w.appendLine("0");
        w.outdent();
        w.appendLine("};");

        for (String matrix : gi.exports) {
            w.appendLine("");
            generateDepMatrix(w, gi.getMatrix(matrix), matrix);
        }

        w.appendLine("");
        w.appendLine("const int* spins_get_matrix(int m, int x) {");
        w.indent();
        w.appendLine("assert(m < ",gi.getNrExports(), ", \"spins_get_matrix: invalid matrix index %d\", m);");
        w.appendLine("switch(m) {");
        int m = 0;
        for (String matrix : gi.exports) {
            DepMatrix dm = gi.getMatrix(matrix);
            w.appendLine("case ",m,": {");
            w.indent();
            w.appendLine("assert(x < ", dm.getNrRows(), ", \"spins_get_matrix: invalid row index %d\", x);");
            w.appendLine("return "+ matrix +"[x];");
            w.outdent();
            w.appendLine("}");
            ++m;
        }
        w.appendLine("}");
        w.appendLine("return 0;");
        w.outdent();
        w.appendLine("}");

        w.appendLine("");
        w.appendLine("const int spins_get_matrix_count() {");
        w.indent();
        w.appendLine("return "+ gi.getNrExports() +";");
        w.outdent();
        w.appendLine("}");
        w.appendLine();

        w.appendLine("");
        w.appendLine("const char *spins_get_matrix_name(int m) {");
        w.indent();
        w.appendLine("assert(m < ",gi.getNrExports(), ", \"spins_get_matrix: invalid matrix index %d\", m);");
        w.appendLine("return matrices[m];");
        w.outdent();
        w.appendLine("}");

        w.appendLine("");
        w.appendLine("const int spins_get_matrix_row_count(int m) {");
        w.indent();
        w.appendLine("assert(m < ",gi.getNrExports(), ", \"spins_get_matrix: invalid matrix index %d\", m);");
        w.appendLine("return matrix_dimensions[m][0];");
        w.outdent();
        w.appendLine("}");

        w.appendLine("");
        w.appendLine("const int spins_get_matrix_col_count(int m) {");
        w.indent();
        w.appendLine("assert(m < ",gi.getNrExports(), ", \"spins_get_matrix: invalid matrix index %d\", m);");
        w.appendLine("return matrix_dimensions[m][1];");
        w.outdent();
        w.appendLine("}");
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

		w.appendLine("#include <pthread.h>");
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
		w.appendLine("extern const int spins_get_state_size() {");
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

	private static void generateForwardDeclarations(StringWriter w,LTSminModel model) {
            if (model.hasAtomicCycles) {
		w.appendLine("extern inline int spins_reach (void* model, transition_info_t *transition_info, state_t *in, void (*callback)(void* arg, transition_info_t *transition_info, state_t *out, int *cpy), void *arg, int pid, int *cpy);");
            }
		w.appendLine("extern inline int spins_simple_reach (void* model, transition_info_t *transition_info, state_t *in, void (*callback)(void* arg, transition_info_t *transition_info, state_t *out, int *cpy), void *arg, int pid, int *cpy);");
		w.appendLine("extern int spins_get_successor_all (void* model, state_t *in, void (*callback)(void* arg, transition_info_t *transition_info, state_t *out, int *cpy), void *arg);");
		w.appendLine("extern int spins_get_successor (void* model, int t, state_t *in, void (*callback)(void* arg, transition_info_t *transition_info, state_t *out, int *cpy), void *arg);");
		w.appendLine("extern int spins_get_actions (void* model, int t, state_t *in, void (*callback)(void* arg, transition_info_t *transition_info, state_t *out, int *cpy), void *arg);");
        w.appendLine("extern void spins_atomic_cb (void* arg, transition_info_t *transition_info, state_t *out, int atomic, int *cpy);");
        w.appendLine("extern void spins_simple_atomic_cb (void* arg, transition_info_t *transition_info, state_t *out, int atomic, int *cpy);");
        w.appendLine("extern int *spins_get_guards (state_t *in);");
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
			String name = slot.fullName();

            char[] chars = new char[Math.max(40 - name.length(),0)];
            Arrays.fill(chars, ' ');
            String prefix = new String(chars);
            w.append(name.replace(".var", ".pad"));
            w.append(prefix +" = 0,");
            w.appendPostfix().appendPrefix();

            w.append(name);
            w.append(prefix +" = ");
			Expression e = slot.getInitExpr();
			if (e==null) {
				w.append("0");
			} else {
				try {
					w.append(e.getConstantValue());
				} catch (ParseException pe) {
					throw new AssertionError("Cannot parse initial value ["+ e +"] of state vector slot: "+ name);
				}
			}

		}
		w.outdent().appendPostfix();
		w.appendLine("};");
		w.appendLine("");

		w.appendLine("extern void spins_get_initial_state( state_t *to )");
		w.appendLine("{");
		w.indent();
		w.appendLine("assert("+ model.sv.size() +"*",STATE_ELEMENT_SIZE," == "
		                      + "sizeof(" + C_STATE + "),");
		w.appendLine("\t\"state_t SIZE MISMATCH!: state: %zu != %i\", "
		                    + "\n\t\tsizeof("+ C_STATE +"), "
		                    + model.sv.size() +"*",STATE_ELEMENT_SIZE,");");
		w.appendLine("memcpy(to, (char*)&",INITIAL_VAR,", sizeof(" + C_STATE + "));");
		w.outdent();
		w.appendLine("}");
		w.appendLine("");
	}

	private static void generateACallback(StringWriter w, LTSminModel model,
	                                      LTSminTransition t) {
        printEdgeLabels(w, model, t);
        w.appendLine("transition_info.group = "+ t.getGroup() +";");
		w.appendLine("callback(arg,&transition_info,"+ OUT_VAR +",cpy);");
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

	public static void generateAnAtomicTransition(StringWriter w,
	                                              LTSminTransition t,
										          LTSminModel model) {
		w.appendLine("// "+ t.getName());
		w.appendPrefix().append("if (");
		int guards = 0;
		for(LTSminGuardBase g: t.getGuards()) {
		    if (g != t.getGuards().get(0))
		        w.appendPostfix().appendPrefix().append("&&");
			guards += generateGuard(w, model, g, in(model));
		}
		if (guards == 0) w.append("true");	w.append(") {").appendPostfix();
		w.indent();
		w.appendLine("memcpy(", OUT_VAR,", ", IN_VAR , ", sizeof(", C_STATE,"));");
		w.appendLine("int cpy[" + model.sv.size() + "]; memcpy(cpy, cpy_src, sizeof(int[" + model.sv.size() + "]));");
		w.appendPostfix();
		
		List<Action> actions = t.getActions();
		if (w.options.total)
			w.appendLine("while (1) {").indent();
		for(Action a: actions) {
			try {
				generateAction(w,a,model, t);
			} catch (AssertionError ae) {
				throw new AssertionError("Generating action failed for "+ a +"\n"+ ae);
			}
		}
		if (w.options.total) {
			w.appendLine("break;").outdent();
			w.appendLine("}");
		}
		// No edge labels! They are discarded anyway!
		w.appendLine("transition_info.group = "+ t.getGroup() +";");
		if (t.getEnd().liesOnCycle()) {
		    w.appendLine("spins_atomic_cb(arg,&transition_info,"+OUT_VAR+","+ t.getEndId() +",cpy);");
		} else {
		    w.appendLine("spins_simple_atomic_cb(arg,&transition_info,"+OUT_VAR+","+ t.getEndId() +",cpy);");
		}
		w.appendLine("++states_emitted;");
		w.outdent();
		w.appendLine("}");
	}

	private static void generateGetAll(StringWriter w, LTSminModel model) {
		/* PROMELA specific per-proctype code */
	    if (model.getTransitions().size() == 0) return;
		for (ProcInstance p : model.getTransitions().get(0).getProcess().getSpecification()) {
			w.appendLine("int spins_get_successor_sid"+ p.getID() +"( void* model, state_t *in, void *arg, state_t *"+ OUT_VAR +", int *cpy_src) {");
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
				if (!t.isBeginAtomic()) continue;
				generateAnAtomicTransition(w, t, model);
			}
			w.appendLine("return states_emitted;");
			w.outdent();
			w.appendLine("}");
			w.appendLine();
		}
		w.appendLine("int spins_get_successor_sid( void* model, state_t *in, void *arg, state_t *"+ OUT_VAR +", int atomic, int *cpy_src) {");
		w.indent();
		w.appendLine("switch (atomic) {");
		for (ProcInstance p : model.getTransitions().get(0).getProcess().getSpecification()) {
			w.appendLine("case "+ p.getID() +": return spins_get_successor_sid"+ p.getID() +"(model, in, arg, "+ OUT_VAR +", cpy_src); break;");
		}
		w.appendLine("default: printf(\"Wrong structural ID\"); exit(-1);");
		w.appendLine("}");
		w.outdent();
		w.appendLine("}");
		w.appendLine();
		/* END PROMELA specific code */

		w.appendLine("int spins_get_successor_all( void* model, state_t *in, void (*callback)(void* arg, transition_info_t *transition_info, state_t *out, int *cpy), void *arg) {");
		w.indent();
		String edge_array = "";
        for (int i = 0; i < model.getEdges().size(); i++)
            edge_array += "0, ";
        w.appendLine("int transition_labels["+ model.getEdges().size() +"] = {"+ edge_array +"};");
		w.appendLine("transition_info_t transition_info = { transition_labels, -1 };");
		w.appendLine("int states_emitted = 0;");
	    w.appendLine("int *__guards = spins_get_guards(in);");
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
        w.indent();

        int guards = 0;
        if (many) {
		    List<Integer> list = model.getGuardInfo().getTransMatrix().get(t.getGroup());
            for (int g : list) {
                w.append("__guards["+ g +"]");
                if (w.options.total)
                	w.append(" == 1");
                if (list.size() != ++guards) {
                    w.append(" &&").appendPostfix().appendPrefix();
                }
            }
        } else {
	           LTSminGuardBase last = t.getGuards().get(t.getGuards().size() - 1);
            for (LTSminGuardBase g : t.getGuards()) {
    			guards += generateGuard(w, model, g, in(model));
                if (g != last) {
                    w.append(" &&").appendPostfix().appendPrefix();
                }
    		}
		}

		if (guards == 0) w.append("true");
		w.outdent();
		w.append(") {").appendPostfix();
		w.indent();
		//generateActions(w, t, model);
		w.appendLine("states_emitted += spins_get_actions (model, "+ t.getGroup() +", in, callback, arg);");
	    if (many && (hasAssert(t.getActions()) || t.isProgress())) {
		    int index = model.getEdgeIndex(ACTION_EDGE_LABEL_NAME);
		    w.appendLine("transition_labels["+ index +"] = "+ 0 +";");
	    }
		w.outdent();
		w.appendLine("}");
	}

    private static void generateActions(StringWriter w, LTSminTransition t,
                                        LTSminModel model) {
        w.appendLine("memcpy(", OUT_VAR,", ", IN_VAR , ", sizeof(", C_STATE,"));");
		List<Action> actions = t.getActions();
		if (w.options.total)
			w.appendLine("while (1) {").indent();
		for(Action a : actions) {
			try {
				generateAction(w, a, model, t);
			} catch (AssertionError ae) {
				throw ae;//new AssertionError("Generating action failed for "+ a +"\n"+ ae);
			}
		}
		if (w.options.total) {
			w.appendLine("break;").outdent();
			w.appendLine("}");
		}
		if (t.isAtomic()) {
		    printEdgeLabels (w, model, t);
			w.appendLine("transition_info.group = "+ t.getGroup() +";");
			if (t.getEnd().liesOnCycle()) {
			    w.appendLine("int count = spins_reach (model, &transition_info, "+ OUT_VAR +", callback, arg, "+ t.getEndId() +", cpy);");
			} else {
			    w.appendLine("int count = spins_simple_reach (model, &transition_info, "+ OUT_VAR +", callback, arg, "+ t.getEndId() +", cpy);");
			}
			w.appendLine("states_emitted += count;"); // non-deterministic atomic sequences emit multiple states
		} else {
			generateACallback(w, model, t);
		}
    }

    private static boolean hasAssert(List<Action> as) {
        for(Action a : as) {
            if (a instanceof AssertAction) return true;
            if (a instanceof Sequence) return hasAssert (((Sequence)a).getActions());
            if (a instanceof OptionAction) {
                for (Sequence s : ((OptionAction)a)) {
                    if (hasAssert(s.getActions()))
                        return true;
                }
            }
        }
        return false;
    }

	private static void generateGetNext(StringWriter w, LTSminModel model) {
		w.appendLine("int spins_get_successor (void* model, int t, state_t *in, void (*callback)(void* arg, transition_info_t *transition_info, state_t *out, int *cpy), void *arg) {");
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

    private static void generateGetActions(StringWriter w, LTSminModel model) {
        w.appendLine("int spins_get_actions (void* model, int t, state_t *in, void (*callback)(void* arg, transition_info_t *transition_info, state_t *out, int *cpy), void *arg) {");
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
        
        w.appendPrefix();
        w.append("int cpy[" + model.sv.size() + "] = { ");      
        for (int i = 0; i < model.sv.size(); i++) w.append("1,");       
        w.append(" };");
        w.appendPostfix();
        
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
            w.appendLine("// "+ t.getName());
            generateActions (w, t, model);
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

			generateBoundsChecks(w, model, id);
			if (as.getExpr() != null)
				generateBoundsChecks(w, model, as.getExpr());
			
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
			copyAccess(model, w, id);
            
		} else if(a instanceof ResetProcessAction) {
			ResetProcessAction rpa = (ResetProcessAction)a;
			String name = rpa.getProcess().getName();
			String struct_t = model.sv.getMember(rpa.getProcess()).getType().getName();
			w.appendLine("#ifndef NORESETPROCESS");
			w.appendLine("memcpy(&",OUT_VAR,"->"+ name +", (char*)&(",INITIAL_VAR,".",name,"), sizeof("+ struct_t +"));");
            w.appendLine("memset(&((state_t *)cpy)->"+ name +", 0, sizeof("+ struct_t +"));");
			LTSminSubVector sub = model.sv.sub(rpa.getProcess());
			for (LTSminSlot slot : sub) {
		        w.appendPrefix();
		        w.append("cpy["+ slot.getIndex() +"] = 0;");
		        w.appendPostfix();
			}
            w.appendLine("#endif");
			
			w.appendLine(print(_NR_PR, out(model)) +"--;");
			w.appendLine(printPC(rpa.getProcess(), out(model)) +" = -1;");
			copyAccess(model, w, id(_NR_PR));
			copyAccess(model, w, id(rpa.getProcess().getPC()));
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
			for (final Expression expr : exprs) {
				generateBoundsChecks(w, model, expr);
			}
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
					Variable pc = inst.getPC();
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
				if (!w.options.total) {		
					w.appendLine("	printf (\"Error, too many instances for  "+ instance.getTypeName() +": %d.\\n\", __active_" + n_active + ");");
					w.appendLine("	printf (\"Exiting on '"+ re +"'.\\n\");");
					w.appendLine("	exit (1);");
				} else {
					w.appendLine("	return 0;");
				}
				w.appendLine("}");
			//}

			StringWriter w2 = new StringWriter(w);
			//only one dynamic process supported atm
			w2.appendLine("if (-1 != "+ printPC(instance, out(model)) +") {");
			if (!w.options.total) {		
				w2.appendLine("	printf (\"Instance %d of process "+ instance.getTypeName() +" was already started.\\n\", __active_" + n_active + ");");
				w2.appendLine("	printf (\"Exiting on '"+ re +"'.\\n\");");
				w2.appendLine("	exit (1);");
			} else {
				w2.appendLine("	return 0;");
			}
			w2.appendLine("}");

			//set pid
			Action update_pid = assign(instance.getPID(), id(_NR_PR));
			generateAction(w2, update_pid, model, t);

			//activate process
			Action update_pc = assign(instance.getPC(), 0);
			generateAction(w2, update_pc, model, t);
			w2.appendLine("++("+ print(_NR_PR, out(model)) +");");
			copyAccess(model, w2, id(_NR_PR));
			
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
			for (Action action: re.getInitActions())
				generateAction(w2, action, model, t);

			// replace all variable identifiers with the right process instance index
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
				w.append(oa.getLabel() +extra_label+ ":\t");
				w.append("while ("+ var +") {").appendPostfix();
				w.indent();
			}
			for (Sequence seq : oa) {
				Action guardAction = seq.iterator().next();
				LTSminGuardAnd ag = new LTSminGuardAnd();
				try {
                    LTSminTreeWalker.createEnabledGuard(guardAction, ag);
                } catch (LTSminRendezVousException e) {
                    throw new AssertionError(e);
                }
				generateBoundsChecks(w, model, ag.getExpression());
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
				if (ag.getExpression() == null)				
					w.append("true");
				else {
			        generateExpression(w, ag.getExpression(), out(model));
				}
				w.append(") {").appendPostfix();
				w.indent();
				for (Action act : seq) {
					if (act instanceof BreakAction) {
						OptionAction loop = ((BreakAction)act).getLoop();
						String var = loop.getLabel() +"_var";
						w.appendLine(var +" = false;");
						w.appendLine("goto "+ loop.getLabel() +extra_label+";");
					}
					generateAction(w, act, model, t);
				}
				w.outdent();
				first = false;
			}
			if (oa.loops()) {
				// end if:
				w.appendLine("} else { assert(false, \"Blocking loop in d_step\"); }");
				w.outdent();
				w.appendLine("}");	// end while
				extra_label++;
			} else {
				w.appendLine("}"); // end if
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
            ChannelVariable var = (ChannelVariable)id.getVariable();
            int bufferSize = var.getType().getBufferSize();
            List<Expression> exprs = csa.getExprs();
            String len = print(chanLength(id), out(model));
			ChannelVariable cv = (ChannelVariable) id.getVariable();
			ChannelType ct = cv.getType();

            if (csa.isSorted() && bufferSize > 1) {
				Identifier index = new LTSminIdentifier(model.index);
				Expression min = calc(PromelaConstants.MINUS, index, constant(1));
				Identifier m0 = channelIndex(id, min, 0);
				Expression comp = compare(PromelaConstants.LTE, m0, exprs.get(0));
				String test = print(comp, out(model));
				
				w.appendLine("for (i = "+ len +"; i > 0; i--) {");
				w.indent();
				w.appendLine("if ("+ test +") break;");
				for (int e = 0; e < exprs.size(); e++) {
                		Identifier m = channelIndex(id, index, e);
                    	Identifier mmm = channelIndex(id, min, e);
    					VariableType msg = ct.getTypes().get(e);
    					generateChanBufCopy(w, model, t, m, mmm, msg);
                    //generateAction(w, assign(m, mmm), model, t);
				}
				w.outdent();
				w.appendLine("}");

                	for (int e = 0; e < exprs.size(); e++) {
					final Expression expr = exprs.get(e);
					Identifier buf = channelIndex(id,index,e);
					VariableType msg = ct.getTypes().get(e);
					generateChanBufCopy(w, model, t, buf, expr, msg);
					//generateAction(w, assign(buf, expr), model, t);
                	}
            	} else {
	    			for (int e = 0; e < exprs.size(); e++) {
        				final Expression expr = exprs.get(e);
    					Identifier buf = channelNext(id,e);
	    				VariableType msg = ct.getTypes().get(e);
    					generateChanBufCopy(w, model, t, buf, expr, msg);
	                //generateAction(w, assign(buf, expr), model, t);
	    			}
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
			
			ChannelVariable cv = (ChannelVariable) id.getVariable();
			ChannelType ct = cv.getType();
			for (int e = 0; e < exprs.size(); e++) {
				final Expression expr = exprs.get(e);
				if (!(expr instanceof Identifier)) continue;
				Identifier lhs = (Identifier)expr;
				Identifier m = channelBottom(id, e);
				VariableType msg = ct.getTypes().get(e);
				generateChanBufCopy(w, model, t, lhs, m, msg);
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
						VariableType msg = ct.getTypes().get(e);
						generateChanBufCopy(w, model, t, m, mpp, msg);
						//generateAction(w, assign(m, mpp), model, t);
					}
					w.outdent();
					w.appendLine("}");
				}
				generateAction(w, decr(chanLength(id)), model, t);
				for (int e = 0; e < exprs.size(); e++) {
					VariableType msg = ct.getTypes().get(e);
					Identifier buf = channelNext(id,e);
					if (msg instanceof CustomVariableType) {
						List<Identifier> l1 = createCustomIdentifiers(msg, buf);
						for (Identifier id1 : l1) {
							generateAction(w, assign(id1, constant(0)), model, t);
						}
					} else {
						generateAction(w, assign(buf, constant(0)), model, t);
					}
				}
			}
		} else {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+a.getClass().getName());
		}
	}

	private static void generateChanBufCopy(StringWriter w, LTSminModel model, LTSminTransition t,
										   Identifier lhs, Expression rhs, VariableType msg)  {
		if (msg instanceof CustomVariableType) {
			if (!(rhs instanceof Identifier)) throw new AssertionError("Expected identifier in rhs of channel XXX action buffer mod for: "+ lhs +" := "+ rhs);
			Identifier R = (Identifier)rhs;
			List<Identifier> l1 = createCustomIdentifiers(msg, lhs);
			List<Identifier> l2 = createCustomIdentifiers(msg, R);
			Iterator<Identifier> it2 = l2.listIterator();
			for (Identifier id1 : l1) {
				generateAction(w, assign(id1, it2.next()), model, t);
			}
		} else {
			generateAction(w, assign(lhs, rhs), model, t);
		}
	}

    private static void copyAccess(LTSminModel model, StringWriter w, Identifier id) {
		if (id.getVariable().isHidden() || id instanceof LTSminIdentifier) return;
		if (id.isConstant()) return;

		String var = print(id, out(model));
    	
        if (!var.endsWith(".var")) throw new AssertionError(var +" does not end with '.var'");
        String pad = var.substring(0, var.length() - 4) +".pad";

        w.appendPrefix();
        w.append("cpy[((int *)&");
        w.append(pad);
        w.append(" - (int *)"+OUT_VAR+")] = 0;");
        w.appendPostfix();
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
				try {
					return ""+ id.getConstantValue();
				} catch (ParseException e1) {}
				if (id.getVariable().isHidden()) {
					String ctype = LTSminTypeNative.getCType (id.getVariable());
					return  SCRATCH_VARIABLE +"_"+ ctype +".var";
				}
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
			try {
				w.append(print(id, state));
			} catch (AssertionError ae) {
				System.err.println(id);
				ae.printStackTrace();
				new AssertionError("Generating id failed for "+ id +"\n"+ ae);
			}
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
		} else if(e instanceof TranslatableExpression) {
		    TranslatableExpression te = (TranslatableExpression)e;
			generateExpression(w, te.translate(), state);
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
			w.append("("+ print(_NR_PR, state) +" != "+ (PM_MAX_PROCS-1) +")");
		} else if (e instanceof RemoteRef) {
			RemoteRef rr = (RemoteRef)e;
            Expression labelExpr = rr.getLabelExpression(null);
			generateExpression(w, labelExpr, state);
		} else if(e instanceof EvalExpression) {
			EvalExpression eval = (EvalExpression)e;
			generateExpression(w, eval.getExpression(), state);
		}  else if(e instanceof TimeoutExpression) {
		    TimeoutExpression timeout = (TimeoutExpression)e;
            generateExpression(w, timeout.getDeadlock(), state);
        } else if(e instanceof CompoundExpression) {
            throw new AssertionError("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		}
	}

   private static void generateEdgeMatrices(StringWriter w, LTSminModel model) {
       IndexedSet<String> edges = model.getEdges();
       int nrGroups = model.getTransitions().size();

	   int statementID = model.getEdgeIndex(STATEMENT_EDGE_LABEL_NAME);
	   int actionID = model.getEdgeIndex(ACTION_EDGE_LABEL_NAME);
       int noneID = model.getTypeValueIndex(ACTION_TYPE_NAME, NO_ACTION_NAME);
       int assertID = model.getTypeValueIndex(ACTION_TYPE_NAME, ASSERT_ACTION_NAME);
       int progressID = model.getTypeValueIndex(ACTION_TYPE_NAME, PROGRESS_ACTION_NAME);

        w.appendPrefix();
		w.appendLine("int *"+ ACT_DM_NAME + "["+ edges.size() +"]["+ nrGroups +"] = {");
        w.indent();
        for (int i = 0; i < edges.size(); i++) {
        	w.appendLine("{");
        	w.indent();
        	IndexedSet<String> values = model.getTypeValues(i);
            //int typeno = model.getTypeIndex(model.getEdgeType(i));
            for (int g = 0; g < nrGroups; g++) {
	            LTSminTransition T = model.getTransitions().get(g);

	            if (i == actionID) {
		            if (hasAssert(T.getActions())) {
		    			w.appendLine("((int[]){ 1, "+ assertID +" }),");
		            } else if (T.isProgress()) {
		    			w.appendLine("((int[]){ 1, "+ progressID +" }),");
		            } else {
		    			w.appendLine("((int[]){ 1, "+ noneID +" }),");
		            }
		        } else if (i == statementID) {
	    			w.appendLine("((int[]){ 1, "+ g +" }),");
		        } else {
	    			w.append("((int[]){ "+ values.size() +", ");
	            	for (int j = 0 ; j< values.size(); j++) {
	        			w.append(j +", ");
	            	}
	    			w.appendLine(" }),");
		        }
            }
			w.outdent();
			w.appendLine("},");
        }
        w.outdent();
        // Close array
        w.appendLine("};");
        w.appendLine("");

		w.appendLine("");
		w.appendLine("extern const int spins_transition_has_edge(int t, int e, int v)");
		w.appendLine("{");
		w.appendLine("  assert(t < ",nrGroups,", \"spins_transition_has_edge: invalid group index %d\\n\", t);");
		w.appendLine("  assert(e < ",model.getEdges().size(),", \"spins_transition_has_edge: invalid edge label index %d\\n\", e);");
		w.appendLine("	int *ar = "+ ACT_DM_NAME +"[e][t];");
//		w.appendLine("  assert(v < ar[0], \"spins_transition_has_edge: invalid value index %d for edge label %d\\n\", v, e);");
		w.appendLine("	int i;");
		w.appendLine("	for (i = 1; i <= ar[0]; i++) {");
		w.appendLine("	  if (ar[i] == v) return 1;");
		w.appendLine("	}");
		w.appendLine("	return 0;");
		w.appendLine("}");
		w.appendLine("");
    }

   private static void generateDepMatrix(StringWriter w, DepMatrix dm, String name) {
        w.appendPrefix();
        w.appendLine("int "+ name + "[]["+ dm.getNrCols() +"] = {");
        w.indent();

        // Iterate over all the rows
        for(int t = 0; t < dm.getNrRows(); t++) {
            if (t > 0)
                w.append(", // "+ (t-1)).appendPostfix();
            w.appendPrefix();

            DepRow dr = dm.getRow(t);
            generateRow(w, dr);
        }
        w.outdent();
        // Close array
        w.appendLine("};");
    }

	private static void generateDepMatrix(StringWriter w, RWMatrix dm, String name) {
		w.appendPrefix();
		w.appendLine("int "+ name + "[][3]["+ dm.getNrCols() +"] = {");
		w.indent();
		w.appendLine("// { ... read ...}, { ... may write ...}, { ... must write ... }");

		// Iterate over all the rows
		for(int t = 0; t < dm.getNrRows(); t++) {
			if (t > 0)
				w.append(", // "+ (t-1)).appendPostfix();
			w.appendPrefix();

			RWDepRow dr = dm.getRow(t);
			w.append("{");
			generateRow(w, dr.read);
            w.append(",");
            generateRow(w, dr.mayWrite);
            w.append(",");
            generateRow(w, dr.mustWrite);
			w.append("}");
		}
		w.outdent();
		// Close array
		w.appendLine("};");
	}

	private static void generateRow(StringWriter w, DepRow dr) {
		w.append("{");

		// Insert all read dependencies of the current row
		for (int col = 0; col < dr.getNrCols(); col++) {
			if (col > 0)
				w.append(",");
			w.append(dr.intDependent(col));
		}

		w.append("}");
	}

	private static void generateDMFunctions(StringWriter w, RWMatrix dm) {
		// Function to access the dependency matrix
		w.appendLine("");
		w.appendLine("extern const int* spins_get_transition_read_dependencies(int t)");
		w.appendLine("{");
		w.appendLine("	if (t>=0 && t < "+ dm.getNrRows() +") return "+ DM_NAME +"[t][0];");
		w.appendLine("	return NULL;");
		w.appendLine("}");
		w.appendLine("");
        w.appendLine("extern const int* spins_get_transition_may_write_dependencies(int t)");
        w.appendLine("{");
        w.appendLine("  if (t>=0 && t < "+ dm.getNrRows()+ ") return "+ DM_NAME +"[t][1];");
        w.appendLine("  return NULL;");
        w.appendLine("}");
        w.appendLine("");
        w.appendLine("// for backwards compatibility:");
        w.appendLine("extern const int* spins_get_transition_write_dependencies(int t)");
        w.appendLine("{");
        w.appendLine("  if (t>=0 && t < "+ dm.getNrRows()+ ") return "+ DM_NAME +"[t][1];");
        w.appendLine("  return NULL;");
        w.appendLine("}");
        w.appendLine("");
        w.appendLine("extern const int* spins_get_transition_must_write_dependencies(int t)");
        w.appendLine("{");
        w.appendLine("  if (t>=0 && t < "+ dm.getNrRows()+ ") return "+ DM_NAME +"[t][2];");
        w.appendLine("  return NULL;");
        w.appendLine("}");
        w.appendLine("");
        w.appendLine("extern const int* spins_get_actions_read_dependencies(int t)");
        w.appendLine("{");
        w.appendLine("  if (t>=0 && t < "+ dm.getNrRows() +") return "+ DM_ACTIONS_NAME +"[t];");
        w.appendLine("  return NULL;");
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
			if (edge != edges.get(0))
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

	private static void generateGuardMatrices(StringWriter w, LTSminModel model,
	                                          boolean no_gm) {
		GuardInfo gm = model.getGuardInfo();
		if(gm==null) return;

		DepMatrix co_matrix = gm.getCoMatrix();
		DepMatrix dna_matrix = gm.getDNAMatrix();
		DepMatrix commutes_matrix = gm.getCommutesMatrix();

		w.appendLine("");
		w.appendLine("// Label(Guard)-Dependency Matrix:");
		generateDepMatrix(w, gm.getDepMatrix(), GM_DM_NAME);
		w.appendLine("");

        List<List<Integer>> trans_matrix = gm.getTransMatrix();
        generateTransGuardMatrix(w, trans_matrix);

		if (no_gm) return;

		// Optional (POR) matrices:
		w.appendLine("");
		w.appendLine("// Maybe Co-Enabled Matrix:");
		generateDepMatrix(w, co_matrix, CO_DM_NAME);
		w.appendLine("");

        w.appendLine("");
        w.appendLine("// Do Not Accord Matrix:");
        generateDepMatrix(w, dna_matrix, DNA_DM_NAME);
        w.appendLine("");

        w.appendLine("");
        w.appendLine("// Commute Matrix:");
        generateDepMatrix(w, commutes_matrix, COMMUTES_DM_NAME);
        w.appendLine("");

        w.appendLine("");
		w.appendLine("// Necessary Enabling Matrix:");
		generateDepMatrix(w, gm.getNESMatrix(), NES_DM_NAME);
		w.appendLine("");

		w.appendLine("");
		w.appendLine("// Necessary Disabling Matrix:");
		generateDepMatrix(w, gm.getNDSMatrix(), NDS_DM_NAME);
		w.appendLine("");
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

	private static int generateMaybe(StringWriter w, Expression e, LTSminPointer state) {
		if (e == null) return 0;
		if (e instanceof LTSminIdentifier) {
		} else if (e instanceof Identifier) {
			Identifier id = (Identifier) e;
			generateMaybe(w, id.getArrayExpr(), state);
			generateMaybe(w, id.getSub(), state);	
			if (id.getArrayExpr() != null) {
				w.append(" || ");

				generateExpression(w, id.getArrayExpr(), state);
				w.append(" < 0 || ");
				generateExpression(w, id.getArrayExpr(), state);
				w.append(String.format(" >= %d", id.getVariable().getArraySize()));
			} else if (id.getVariable().getArrayIndex() != -1) {
				w.append(String.format(" || %s < 0 || %s >= %d", id.getVariable().getArrayIndex(), id.getVariable().getArrayIndex(), id.getVariable().getArraySize()));
			}
		} else if (e instanceof AritmicExpression) {
			AritmicExpression ae = (AritmicExpression) e;
			Expression ex1 = ae.getExpr1();
			Expression ex2 = ae.getExpr2();
			Expression ex3 = ae.getExpr3();

			if (ae.getToken().kind == PromelaConstants.DIVIDE ||
					ae.getToken().kind == PromelaConstants.MODULO) {
				w.append(" || ");
				generateExpression(w, ex2, state);
				w.append(" == 0");
			}
			
			generateMaybe(w, ex1, state);
			generateMaybe(w, ex2, state);
			generateMaybe(w, ex3, state);
		} else if (e instanceof BooleanExpression) {
			BooleanExpression ae = (BooleanExpression) e;
			Expression ex1 = ae.getExpr1();
			Expression ex2 = ae.getExpr2();
			generateMaybe(w, ex1, state);
			generateMaybe(w, ex2, state);
		} else if (e instanceof CompareExpression) {
			CompareExpression ae = (CompareExpression) e;
			Expression ex1 = ae.getExpr1();
			Expression ex2 = ae.getExpr2();
			generateMaybe(w, ex1, state);
			generateMaybe(w, ex2, state);
		} else if (e instanceof TranslatableExpression) {
			TranslatableExpression te = (TranslatableExpression) e;
			generateMaybe(w, te.translate(), state);
		} else if (e instanceof ChannelReadExpression) {
			ChannelReadExpression cre = (ChannelReadExpression) e;
			for (Expression ex : cre.getExprs()) {
				generateMaybe(w, ex, state);
			}
		} else if (e instanceof MTypeReference) {
		} else if (e instanceof ConstantExpression) {
		} else if (e instanceof RunExpression) {
		} else if (e instanceof RemoteRef) {
		} else if (e instanceof EvalExpression) {
		} else if (e instanceof TimeoutExpression) {
		} else if (e instanceof CompoundExpression) {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "
					+ e.getClass().getName());
		} else {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "
					+ e.getClass().getName());
		}

		return 1;
	}

	private static void generateGuardFunctions(StringWriter w, LTSminModel model,
	                                           boolean no_gm) {
	    GuardInfo gm = model.getGuardInfo();
        int nTrans = gm.getTransMatrix().size();

        w.appendLine("");
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
        w.appendLine("assert(t < ",nTrans,", \"spins_get_labels: invalid transition index %d\", t);");
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

        w.appendLine("int spins_get_label(void* model, int g, ",C_STATE,"* ",IN_VAR,") {");
        w.indent();
        w.appendLine("(void)model;");
        w.appendLine("assert(g < ",gm.getNumberOfLabels(),", \"spins_get_label: invalid state label index %d\", g);");
        w.appendLine("switch(g) {");
        w.indent();
        for (int g = 0; g < gm.getNumberOfGuards(); ++g) {
            w.appendPrefix();
            w.append("case ").append(g).append(": return ");
            generateMaybeGuardText(w, model, g);
        }
        for (int g = gm.getNumberOfGuards(); g < gm.getNumberOfLabels(); ++g) {
            w.appendPrefix();
            w.append("case ").append(g).append(": return ");
            generateExpression(w, gm.getLabel(g).getExpr(), in(model));
            w.append(";");
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
        w.appendLine("assert(g < ",gm.getNumberOfLabels(),", \"spins_get_label_name: invalid state label index %d\", g);");
        w.appendLine("switch(g) {");
        w.indent();
        for (int g = 0; g < gm.getNumberOfLabels(); ++g) {
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

        w.appendLine("void spins_get_labels_many (void* model, ",C_STATE,"* ",IN_VAR,", int* label, bool guards_only) {");
        w.indent();
        w.appendLine("(void)model;");
        for (int g = 0; g < gm.getNumberOfGuards(); ++g) {
            w.appendPrefix();
            w.append("label[").append(g).append("] = ");
            generateMaybeGuardText(w, model, g);
        }
        w.appendLine("if (guards_only) return;");
        for (int g = gm.getNumberOfGuards(); g < gm.getNumberOfLabels(); ++g) {
            w.appendPrefix();
            w.append("label[").append(g).append("] = ");
            generateExpression(w, gm.getLabel(g).getExpr(), in(model));
            w.append(";");
            w.appendPostfix();
        }
        w.outdent();
        w.outdent();
        w.appendLine("}");
        w.appendLine("");

        w.appendLine("void spins_get_labels_all(void* model, ",C_STATE,"* ",IN_VAR,", int* label) {");
        w.indent();
        w.appendLine("(void)model;");
        w.appendLine("spins_get_labels_many(model, "+ IN_VAR +", label, false);");
        w.outdent();
        w.appendLine("}");
        w.appendLine("");

        w.appendLine("void spins_get_guards_all(void* model, ",C_STATE,"* ",IN_VAR,", int* label) {");
        w.indent();
        w.appendLine("(void)model;");
        w.appendLine("spins_get_labels_many(model, "+ IN_VAR +", label, true);");
        w.outdent();
        w.appendLine("}");
        w.appendLine("");

        w.appendLine(
            "static pthread_key_t spins_local_key2;\n" +
            "\n" +
            "__attribute__((constructor)) void spins_initialize_key2() {\n" +
            "    pthread_key_create (&spins_local_key2, NULL);\n" +
            "}\n" +
            "\n" +
            "__attribute__((destructor)) void spins_destroy_key2() {\n" +
            "    pthread_key_delete (spins_local_key2);\n" +
            "}\n" +
            "\n" +
            "static int *spins_get_guards_array () {\n" +
            "    int *array = pthread_getspecific (spins_local_key2);\n" +
            "    if (EXPECT_FALSE(array == NULL)) {\n" +
            "        array = malloc(sizeof(int[spins_get_guard_count()]));\n" +
            "        pthread_setspecific (spins_local_key2, array);\n" +
            "    }\n" +
            "    return array;\n" +
            "}"
        );

        w.appendLine("int *spins_get_guards(",C_STATE,"* ",IN_VAR,") {");
        w.indent();
        w.appendLine("int *guards = spins_get_guards_array();");
        w.appendLine("spins_get_guards_all(NULL, "+ IN_VAR +", guards);");
        w.appendLine("return guards;");
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

        if (no_gm) return;

        w.appendLine("const int* spins_get_trans_commutes_matrix(int t) {");
        w.indent();
        w.appendLine("assert(t < ",nTrans,", \"spins_get_trans_commutes_matrix: invalid trans index %d\", t);");
        w.appendLine("return "+ COMMUTES_DM_NAME +"[t];");
        w.outdent();
        w.appendLine("}");
        w.appendLine("");

        w.appendLine("const int* spins_get_trans_do_not_accord_matrix(int t) {");
        w.indent();
        w.appendLine("assert(t < ",nTrans,", \"spins_get_label_do_not_accord_matrix: invalid trans index %d\", t);");
        w.appendLine("return "+ DNA_DM_NAME +"[t];");
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
	}

	private static void generateMaybeGuardText(StringWriter w, LTSminModel model, int g) {
		GuardInfo gm = model.getGuardInfo();
		StringWriter w2 = new StringWriter(w);
		
		generateMaybe(w2, gm.getLabel(g).getExpr(), in(model));
		String maybe = w2.toString();

		if (maybe.length() != 0) {
			if (w.options.total) {
				w.append("(0");
			} else {
				w.append("(");
				maybe = maybe.replaceFirst(" \\|\\|", "");
			}
			w.append(maybe);
			w.append(") ? 2 :").appendLine();
			w.appendPrefix().appendPrefix().appendPrefix();
		}
        generateExpression(w, gm.getLabel(g).getExpr(), in(model));
		w.append(" != 0;");
		w.appendPostfix();
	}

	private static void generateBoundsChecks(StringWriter w, LTSminModel model, Expression e) {
		StringWriter w2 = new StringWriter(w.options);
		generateMaybe(w2, e, out(model));

		if (w2.length() > 0) {
			if (w.options.total) {
				w.appendPrefix();
				w.append("if ( (0"+ w2.toString()+ ") ) break;");
				w.appendPostfix();
			} else {
				w.appendPrefix();
				w.append("assert( !("+ w2.toString().replaceFirst(" \\|\\|", "") +") );");
			    w.appendPostfix();
			}
		}
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

		for (String t : LTSminTypeNative.types.keySet())
			w.appendLine("static "+ t +" "+ SCRATCH_VARIABLE +"_"+ t +";");
		w.appendLine("");
	}

	private static LTSminPointer in(LTSminModel model) {
		return new LTSminPointer(model.sv, IN_VAR);
	}

	private static LTSminPointer out(LTSminModel model) {
		return new LTSminPointer(model.sv, OUT_VAR);
	}
}

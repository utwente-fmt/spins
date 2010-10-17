/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package spinja.promela.compiler.ltsmin;

import spinja.promela.compiler.Specification;
import spinja.promela.compiler.Proctype;
import spinja.promela.compiler.automaton.Automaton;
import spinja.promela.compiler.automaton.State;
import spinja.promela.compiler.automaton.Transition;
import spinja.promela.compiler.actions.*;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import spinja.promela.compiler.parser.ParseException;
import spinja.promela.compiler.parser.PromelaConstants;
import spinja.promela.compiler.variable.CustomVariableType;
import spinja.promela.compiler.variable.Variable;
import spinja.promela.compiler.variable.VariableStore;
import spinja.promela.compiler.expression.*;
import spinja.util.StringWriter;

/**
 * This class handles the generation of C code for LTSMin.
 * FIXME: handle state vector of length 0
 *
 * @author Freark van der Berg
 */
public class LTSMinPrinter {

//	private List<Variable> globals;
//	private List<Proctype> processes;

	public class TypeDesc {
		public String type;
		public String array;
		public TypeDesc() {
			this("","");
		}
		public TypeDesc(String type) {
			this(type,"");
		}
		public TypeDesc(String type, String array) {
			this.type = type;
			this.array = array;
		}
	}

	/**
	 * This class handles the textual generation of a C struct typedef.
	 */
	public class CStruct {
		private StringWriter s;
		private String name;
		//private List<String> members;

		/**
		 * Create a new struct generator.
		 * @param name The name of the struct to be generated.
		 */
		public CStruct(String name) {
			s = new StringWriter();
			this.name = name;
			s.appendLine("typedef struct ",name," {");
			//members = new ArrayList<String>();
		}

		/**
		 * Adds a member to the struct with type integer.
		 * @param varName The name of the member to add.
		 */
		public void addMember(String varName) {
			addMember(C_TYPE_INT32,varName);
		}

		/**
		 * Adds a member to the struct.
		 * @param type The type of the member to add.
		 * @param varName The name of the member to add.
		 */
		public void addMember(String type, String varName) {
			addMember(new TypeDesc(type,""),varName);
		}

		/**
		 * Adds a member to the struct.
		 * @param type The type of the member to add.
		 * @param varName The name of the member to add.
		 */
		public void addMember(TypeDesc type, String varName) {
			s.indent();
			s.appendLine(type.type," ",varName," ",type.array,";");
			s.outdent();
			//members.add(varName);
		}

		//public List<String> getMembers() {
		//	return members;
		//}

		/**
		 * Return the resulting struct. After this call, new members
		 * can be added and a new result retrieved using this member.
		 * @return The generated typedef struct in C code.
		 */
		public String getCCode() {
			String res = s.toString();
			res += "} " + name + ";\n";
			return res;
		}
	}

	public class DepRow {
		private ArrayList<Integer> reads;
		private ArrayList<Integer> writes;

		DepRow(int trans) {
			reads = new ArrayList(trans);
			writes = new ArrayList(trans);
			for(int i=0;i<trans;++i) {
				reads.add(0);
				writes.add(0);
			}
		}

		public void incRead(int dep) {
			reads.set(dep, reads.get(dep)+1);
		}
		public void incWrite(int dep) {
			writes.set(dep, writes.get(dep)+1);
		}

		public void decrRead(int dep) {
			reads.set(dep, reads.get(dep)-1);
		}
		public void decrWrite(int dep) {
			writes.set(dep, writes.get(dep)-1);
		}

		public int getRead(int state) {
			return reads.get(state);
		}
		public int getWrite(int state) {
			return writes.get(state);
		}
		public int getReadB(int state) {
			return reads.get(state)>0?1:0;
		}
		public int getWriteB(int state) {
			return writes.get(state)>0?1:0;
		}
		public int getSize() {
			return reads.size();
		}

	}

	public class DepMatrix {
		private ArrayList<DepRow> dep_matrix;

		DepMatrix(int trans, int size) {
			dep_matrix = new ArrayList();
			for(int i=0;i<trans;++i) {
				dep_matrix.add(i,new DepRow(size));
			}
		}

		public void incRead(int transition, int dep) {
			DepRow dr = dep_matrix.get(transition);
			assert(dr!=null);
			dr.incRead(dep);
		}
		public void incWrite(int transition, int dep) {
			DepRow dr = dep_matrix.get(transition);
			assert(dr!=null);
			dr.incWrite(dep);
		}

		public void decrRead(int transition, int dep) {
			DepRow dr = dep_matrix.get(transition);
			assert(dr!=null);
			dr.decrRead(dep);
		}
		public void decrWrite(int transition, int dep) {
			DepRow dr = dep_matrix.get(transition);
			assert(dr!=null);
			dr.decrWrite(dep);
		}

		public int getRows() {
			return dep_matrix.size();
		}

		public DepRow getRow(int trans) {
			return dep_matrix.get(trans);
		}
}

	public static final int STATE_ELEMENT_SIZE = 4;

	public static final String C_STATE_T = "state_t";
	public static final String C_STATE_GLOBALS_T = "state_globals_t";
	public static final String C_STATE_GLOBALS = "globals";
	public static final String C_STATE_PROC_COUNTER = "pc";
	public static final String C_STATE_SIZE = "state_size";
	public static final String C_STATE_INITIAL = "initial";
	public static final String C_STATE_TMP = "tmp";
	public static final String C_TYPE_INT1   = "sj_int1";
	public static final String C_TYPE_INT8   = "sj_int8";
	public static final String C_TYPE_INT16  = "sj_int16";
	public static final String C_TYPE_INT32  = "sj_int32";
	public static final String C_TYPE_UINT8  = "sj_uint8";
	public static final String C_TYPE_UINT16 = "sj_uint16";
	public static final String C_TYPE_UINT32 = "sj_uint32";


	private HashMap<Variable,Integer> state_var_offset;
	private HashMap<Variable, String> state_var_desc;
	private HashMap<Proctype,Integer> state_proc_offset;

	private List<Variable> state_vector_var;

	// Textual description of the state vector, per integer
	private List<String> state_vector_desc;

	// List of processes
	private List<Proctype> procs;

	private DepMatrix dep_matrix;
	int current_transition;

	// The specification of which code is to be generated,
	// initialised by constructor
	private final Specification spec;

	// Cached result
	private String c_code;

	// The size of the state vector in bytes
	private int state_size;

	// The CStruct state vector
	private CStruct state;

	/**
	 * Creates a new LTSMinPrinter using the specified Specification.
	 * After this, the generate() member will generate and return C code.
	 * @param spec
	 */
	public LTSMinPrinter(Specification spec) {
		if(spec==null) {
			// error
		}
		this.spec = spec;
		c_code = null;

		state_var_offset = new HashMap<Variable,Integer>();
		state_var_desc = new HashMap<Variable,String>();
		state_proc_offset = new HashMap<Proctype,Integer>();
		state = null;
		state_vector_desc = new ArrayList<String>();
		state_vector_var = new ArrayList<Variable>();
		procs = new ArrayList<Proctype>();

	}

	/**
	 * Generates and returns C code according to the Specification provided
	 * when creating this LTSMinPrinter instance.
	 * @return The C code according to the Specification.
	 */
	public String generate() {

		StringWriter w = new StringWriter();

		// Cache generate() requests
		if(c_code!=null) {
			return c_code;
		}

		// Generate header code
		generateHeader(w);

		// Generate static type structs
		w.appendLine("");
		generateTypeStructs(w);

		// Generate struct describing the state vector
		w.appendLine("");
		generateStateStructs(w);

		// Generate code for initial state and state size
		w.appendLine("");
		generateStateCode(w);

		// Generate code for the transitions
		w.appendLine("");
		generateTransitions(w);

		// Generate Dependency Matrix
		w.appendLine("");
		generateDepMatrix(w,dep_matrix);

		c_code = w.toString();
		return c_code;
	}

	/**
	 * Generates the header of the C code (not the .h!).
	 * Model independent.
	 * @param w The StringWriter to which the code is written.
	 */
	private void generateHeader(StringWriter w) {
		w.appendLine("#include <stdio.h>");
		w.appendLine("#include <string.h>");
		w.appendLine("#include <stdint.h>");
		w.appendLine("");
		w.appendLine("typedef struct transition_info");
		w.appendLine("{");
		w.indent();
		w.appendLine("int* label;");
		w.appendLine("int  group;");
		w.outdent();
		w.appendLine("} transition_info_t;");
	}

	/**
	 * Generates various typedefs for types, to pad the data to
	 * the element size of the state vector.
	 * Model independent.
	 * @param w The StringWriter to which the code is written.
	 */
	private void generateTypeStructs(StringWriter w) {

		w.appendLine("typedef union ",C_TYPE_INT1," {");
		w.indent();
		w.appendLine("int pad;");
		w.appendLine("bool var;");
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

	}

	/**
	 * Generates the C code for the state structs and fills the following
	 * members with accurate data:
	 *   - state_var_offset;
	 *   - state_var_desc;
	 *   - state_proc_offset;
	 *   - state_size;
	 *   - state;
	 *   - state_vector_var;
	 *   - state_vector_desc.
	 * @param w The StringWriter to which the code is written.
	 * @return C code for the state structs.
	 */
	private void generateStateStructs(StringWriter w) {

		// Current offset in the state struct
		int current_offset = 0;

		// List of state structs inside the main state struct
		List<CStruct> state_members = new ArrayList<CStruct>();

		// The main state struct
		state = new CStruct(C_STATE_T);

		// Globals: initialise globals state struct and add to main state struct
		System.out.println("== Globals");
		CStruct sg = new CStruct(C_STATE_GLOBALS_T);
		state.addMember(C_STATE_GLOBALS_T, C_STATE_GLOBALS);

		// Globals: add globals to the global state struct
		VariableStore globals = spec.getVariableStore();
		List<Variable> vars = globals.getVariables();
		for(Variable var: vars) {

			// Add global to the global state struct and fix the offset
			current_offset = handleVariable(sg,var,C_STATE_GLOBALS+".",current_offset);

		}

		// Add global state struct to main state struct
		state_members.add(sg);

		// Processes:
		System.out.println("== Processes");
		Iterator<Proctype> it = spec.iterator();
		for(;it.hasNext();) {
			Proctype p = it.next();

			procs.add(p);

			// Process' name
			String name = wrapName(p.getName());

			// Initialise process state struct and add to main state struct
			System.out.println("[Proc] " + name + " @" + current_offset);
			CStruct proc_sg = new CStruct("state_"+name+"_t"); // fix name
			state.addMember("state_"+name+"_t", name); //fix name

			// Add process to Proctype->offset map and add a description
			state_proc_offset.put(p, current_offset);
			state_vector_desc.add(name + "." + C_STATE_PROC_COUNTER);
			state_vector_var.add(null);

			// Add process counter to process state struct
			proc_sg.addMember(C_STATE_PROC_COUNTER);

			//Fix the offset
			current_offset += STATE_ELEMENT_SIZE;

			// Locals: add locals to the process state struct
			List<Variable> proc_vars = p.getVariables();
			for(Variable var: proc_vars) {
				//System.out.println("  " + var.getName() + " @" + current_offset);

//				// Add local to Variable->offset map and add a description
//				state_var_offset.put(var, current_offset);
//				state_vector_desc.add(p.getName() + "." + var.getName());
//
//				// Add local to the main state struct
//				proc_sg.addMember(var.getName());
//
//				// Fix the offset
//				current_offset += STATE_ELEMENT_SIZE;

				current_offset = handleVariable(proc_sg,var,name + ".",current_offset);
			}

			// Add process state struct to main state struct
			state_members.add(proc_sg);
		}

		// Generate all state structs
		for(CStruct sgen: state_members) {
			w.appendLine(sgen.getCCode());
		}
		w.appendLine(state.getCCode());

		state_size = current_offset;

	}

	/**
	 * Generates the C code for the initial state and the state size.
	 * @param w The StringWriter to which the code is written.
	 */
	private void generateStateCode(StringWriter w) {

		// Generate state size related code
		w.appendLine("int ",C_STATE_SIZE," = ",state_size,";");

		w.appendLine("extern \"C\" int spinja_get_state_size() {");
		w.indent();
		w.appendLine("return ",C_STATE_SIZE,"/4;");
		w.outdent();
		w.appendLine("}");

		// Generate initial state
		w.append(C_STATE_T);
		w.append(" ");
		w.append(C_STATE_INITIAL);
		w.append(" = ");
		w.append("(");
		w.append(C_STATE_T);
		w.append("){");
		if(state_size/STATE_ELEMENT_SIZE > 0) {
			int i = 0;

			// Insert initial expression of each state element into initial state struct
			for(;;) {

				// Get the variable for the current element (at position i)
				Variable v = state_vector_var.get(i);

				// If it is null, this location is probably a state descriptor
				// so the initial state is 0
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
						try {
							generateIntExpression(w, null, e);
						} catch(ParseException pe) {
							pe.printStackTrace();
							System.exit(0);
						}
					}
				}
				if(++i>=state_size/STATE_ELEMENT_SIZE) {
					break;
				}
				w.append(",");
			}
		}
		w.appendLine("};");

		w.appendLine("extern \"C\" void spinja_get_initial_state( state_t *to )");
		w.appendLine("{");
		w.indent();
		w.appendLine("memcpy(to, (char*)&",C_STATE_INITIAL,", state_size);");
		w.outdent();
		w.appendLine("}");

		// Generate state printer
		w.appendLine("void print_state(",C_STATE_T," &s) {");
		for(int i=0; i<state_size; i+=4) {
			String v = getStateDescription(i);
			w.appendLine("    printf(\"",v,": %8i\\n\",s.",v,");");
		}
		w.appendLine("}");
		
	}

	/**
	 * Returns the description for the given offset. The specified offset
	 * should be a multiple of STATE_ELEMENT_SIZE.
	 * @param offset The offset of which a desciption is wanted.
	 * @return The description for the given offset.
	 */
	private String getStateDescription(int offset) {
		if(offset%STATE_ELEMENT_SIZE > 0) {
			return "N/A";
		} else if(offset>state_size) {
			return "N/A";
		} else {
			return state_vector_desc.get(offset/4);
		}
	}

	/**
	 * Returns the C typedef name for the given variable. This typedef
	 * has been defined earlier to pad data to STATE_ELEMENT_SIZE.
	 * @param v The Variable of which the C typedef is wanted.
	 * @return The C typedef name for the given variable.
	 */
	private TypeDesc getCTypeOfVar(Variable v) {
		TypeDesc td = new TypeDesc();
		switch(v.getType().getBits()) {
			case 1:
				td.type = C_TYPE_INT1;
				break;
			case 8:
				td.type = C_TYPE_UINT8;
				break;
			case 16:
				td.type = C_TYPE_INT16;
				break;
			case 32:
				td.type = C_TYPE_INT32;
				break;
			default:
				System.out.println("ERROR: Unable to handle: " + v.getName());
				System.exit(0);
				return null;
		}
		
		int size = v.getArraySize();
		if(size>1) {
			td.array = "[" + size + "]";
		}
		return td;
	}

	/**
	 * Returns the C typedef name for the given variable. This typedef
	 * has been defined earlier to pad data to STATE_ELEMENT_SIZE.
	 * @param v The Variable of which the C typedef is wanted.
	 * @return The C typedef name for the given variable.
	 */
	private String getCTypeOfVarReal(Variable v) {
		switch(v.getType().getBits()) {
			case 1:
				return "bool";
			case 8:
				return "uint8_t";
			case 16:
				return "short";
			case 32:
				return "int";
			default:
				System.out.println("ERROR: Unable to handle: " + v.getName());
				System.exit(0);
				return "";
		}
	}

	// Helper functionality for debugging
	int say_indent = 0;
	private void say(String s) {
		for(int n=say_indent; n-->0;) {
			System.out.print("  ");
		}
		System.out.println(s);
	}

	/**
	 * Generates the state transitions.
	 * This calls generateTransitionsFromState() for every state in every process.
	 * @param w The StringWriter to which the code is written.
	 */
	private void generateTransitions(StringWriter w) {

		say("== Automata");
		++say_indent;

		// Generate the start: initialise tmp
		w.appendLine(
			"extern \"C\" int spinja_get_successor( void* model, int t, state_t *in, void (*callback)(void* arg, transition_info_t *transition_info, state_t *out), void *arg )\n",
			"{");
		w.indent();

		w.appendLine("transition_info_t transition_info = { NULL, t };");
		w.appendLine("(void)model; // ignore model");
		w.appendLine("int states_emitted = 0;");
		w.appendLine(C_STATE_T," tmp;");
		w.appendLine("memcpy(&tmp,in,sizeof(",C_STATE_T,"));");
		w.appendLine();

		// Create references for global variables
//		VariableStore globals = spec.getVariableStore();
//		List<Variable> vars = globals.getVariables();
//		for(Variable var: vars) {
//			w.appendLine(getCTypeOfVarReal(var)," &",wrapName(var.getName())," = ",C_STATE_TMP,".globals.",wrapName(var.getName()),".var;");
//		}

		// Jump to switch state
		w.appendLine();
		w.appendLine("goto switch_state;");
		w.appendLine();

		// Calculate total number of transitions
		int max_transitions = 0;
		for(Proctype p: procs) {
			Automaton a = p.getAutomaton();
			Iterator<State> i = a.iterator();
			while(i.hasNext()) {
				State st = i.next();
				if(st.sizeOut()==0) { // FIXME: Is this the correct prerequisite for THE end state of a process?
					++max_transitions;
				} else {
					for(Transition t: st.output) {
						++max_transitions;
					}
				}
			}
		}

		// Init dependency matrix
		dep_matrix = new DepMatrix(max_transitions,state_size/4);

		// Current number of transitions
		int trans = current_transition = 0;

		// Generate the transitions for all processes
		for(Proctype p: procs) {
			say("[Proc] " + p.getName());
			++say_indent;

			Automaton a = p.getAutomaton();

			// Generate transitions for all states in the process
			Iterator<State> i = a.iterator();
			while(i.hasNext()) {
				State st = i.next();
				trans = generateTransitionsFromState(w,p,trans,st);
			}

			--say_indent;
		}

		// From the switch state we jump to the correct transition,
		// according to the transition group 't'
		w.appendLine("switch_state:switch(t) {");
		w.indent();
		for(int n = 0; n<trans; ++n) {
			w.appendLine("case ",n,": goto l",n,";");
		}
		w.outdent();
		w.appendLine("}");

		w.outdent();
		w.appendLine("}");

		// Generate the spinja_get_transition_groups hook
		w.appendLine("extern \"C\" int spinja_get_transition_groups() {");
		w.indent();
		w.appendLine("return ",trans,";");
		w.outdent();
		w.appendLine("}");
		--say_indent;

	}

	/**
	 * Generates all transitions from the given state. This state should be
	 * in the specified process.
	 * @param w The StringWriter to which the code is written.
	 * @param process The state should be in this process.
	 * @param trans Starts generating transitions from this transition ID.
	 * @param state The state of which all outgoing transitions will be
	 * generated.
	 * @return The next free transition ID
	 * ( = old.trans + "#transitions generated" ).
	 */
	private int generateTransitionsFromState(StringWriter w, Proctype process, int trans, State state) {

		assert(state!=null);
		if(state==null) {
			System.out.println("ERR");
			System.exit(-1);
		}

		say(state.toString());

		// Check if it is an ending state
		if(state.sizeOut()==0) { // FIXME: Is this the correct prerequisite for THE end state of a process?

			// In the case of an ending state, generate a transition only
			// changing the process counter to -1.
			w.appendLine("l",trans,": if(",C_STATE_TMP,".",wrapName(process.getName()),".",C_STATE_PROC_COUNTER,".var == ",state.getStateId(),") {");
			w.indent();
			w.appendLine("",C_STATE_TMP,".",wrapName(process.getName()),".",C_STATE_PROC_COUNTER,".var = ",-1,";");
			w.appendLine("callback(arg,&transition_info,&tmp);");
			w.appendLine("return 1;");
			w.outdent();
			w.appendLine("}");
			w.appendLine("return 0;");

			dep_matrix.incWrite(current_transition, state_proc_offset.get(process)/4);
			dep_matrix.incRead(current_transition, state_proc_offset.get(process)/4);

			current_transition = ++trans;

		} else {

			// In the normal case, generate a transition changing the process
			// counter to the next state and any action the transition does.
			++say_indent;
			//int outs = 0;

				if(state.output==null) {
					System.out.println("ERR");
					System.exit(-1);
				}

			for(Transition t: state.output) {

				assert(t!=null);
				if(t==null) {
					System.out.println("ERR");
					System.exit(-1);
				}

				// Guard: process counter
				w.appendLine("l",trans,": if(",C_STATE_TMP,".",wrapName(process.getName()),".",C_STATE_PROC_COUNTER,".var == ",state.getStateId(),") {");
				w.indent();

				// Create references for local variables
//				List<Variable> proc_vars = process.getVariables();
//				for(Variable var: proc_vars) {
//					w.appendLine(getCTypeOfVarReal(var)," &",wrapName(var.getName())," = ",C_STATE_TMP,".",wrapName(process.getName()),".",wrapName(var.getName()),".var;");
//				}

				// Generate action guard
				try {
					Action a = null;
					if(t.getActionCount()>0) {
						a = t.getAction(0);
					}
					if(a!= null && a.getEnabledExpression()!=null) {
						w.appendPrefix();
						w.append("if(");
						generateEnabledExpression(w,process,a);
						w.appendLine(") { //",a.getClass().getName());
					} else {
						w.appendLine("if(true) {");
					}
				} catch(ParseException e) {
					System.out.println("Something bad happened: ");
					e.printStackTrace();
				}
				w.indent();

				assert(t.getTo()!=null);

				// Change process counter
				w.appendLine("",C_STATE_TMP,".",wrapName(process.getName()),".",C_STATE_PROC_COUNTER,".var = ",t.getTo().getStateId(),";");

				// Generate actions for this transition
				generateStateTransition(w,process,t);

				// Generate the callback and the rest
				w.appendLine("callback(arg,&transition_info,&tmp);");
				w.appendLine("return ",1,";");

				w.outdent();
				w.appendLine("}");

				w.outdent();
				w.appendLine("}");
				w.appendLine("return 0;");
				//++outs;

				dep_matrix.incWrite(current_transition, state_proc_offset.get(process)/4);
				dep_matrix.incRead(current_transition, state_proc_offset.get(process)/4);

				current_transition = ++trans;
			}
			--say_indent;
		}

		// Return the next free transition ID
		return trans;

	}

	/**
	 * Generate the actions for the specified transition. The transition should
	 * be in the specified process.
	 * @param w The StringWriter to which the code is written.
	 * @param process The transition should be in this process.
	 * @param t The transition of which to generate the associated actions.
	 */
	private void generateStateTransition(StringWriter w, Proctype process, Transition t) {

		// Handle all the action in the transition
		Iterator<Action> it = t.iterator();
		while(it.hasNext()) {
			Action a = it.next();
			try {

				// Handle the action
				generateAction(w,process,a);

			// Handle parse exceptions
			} catch(ParseException e) {
				e.printStackTrace();
				System.exit(0);
			}
		}

	}

	private void generateDepMatrix(StringWriter w, DepMatrix dm) {
		w.append("int transition_dependency[][2][").append(state_size/4).appendLine("] = {");
		w.appendLine("\t// { ... read ...}, { ... write ...}");
		int t=0;
		for(;;) {
			w.append("\t{{");
			DepRow dr = dm.getRow(t);
			int s=0;
			for(;;) {
				w.append(dr.getReadB(s));
				if(++s>=dr.getSize()) {
					break;
				}
				w.append(",");
			}
			w.append("},{");
			s=0;
			for(;;) {
				w.append(dr.getWriteB(s));
				if(++s>=dr.getSize()) {
					break;
				}
				w.append(",");
			}
			w.append("}}");
			if(++t>=dm.getRows()) {
				w.appendLine("");
				break;
			}
			w.appendLine(",");
		}
		w.appendLine("};");

		w.appendLine("");
		w.appendLine("extern \"C\" const int* spinja_get_transition_read_dependencies(int t)");
		w.appendLine("{");
		w.append("	if (t>=0 && t < ").append(dm.getRows()).appendLine(") return transition_dependency[t][0];");
		w.appendLine("	return NULL;");
		w.appendLine("}");
		w.appendLine("");
		w.appendLine("extern \"C\" const int* spinja_get_transition_write_dependencies(int t)");
		w.appendLine("{");
		w.append("	if (t>=0 && t < ").append(dm.getRows()).appendLine(") return transition_dependency[t][1];");
		w.appendLine("	return NULL;");
		w.appendLine("}");
	}

	private void generateBoolExpression(StringWriter w, Proctype process, Expression e) throws ParseException {
		if(e instanceof Identifier) {
			w.append("(");
			generateIntExpression(w,process,e);
			w.append(" != 0 )");
		} else if(e instanceof AritmicExpression) {
			AritmicExpression ae = (AritmicExpression)e;
			Expression ex1 = ae.getExpr1();
			Expression ex2 = ae.getExpr2();
			Expression ex3 = ae.getExpr3();
			if (ex2 == null) {
				w.append("(").append(ae.getToken().image);
				generateIntExpression(w,process,ex1);
				w.append(" != 0)");
			} else if (ex3 == null) {
				w.append("(");
				generateIntExpression(w,process,ex1);
				w.append(" ").append(ae.getToken().image).append(" ");
				generateIntExpression(w,process,ex2);
				w.append(" != 0)");
			} else { // Can only happen with the x?1:0 expression
				w.append("(");
				generateBoolExpression(w,process,ex1);
				w.append(" ? ");
				generateBoolExpression(w,process,ex2);
				w.append(" : ");
				generateBoolExpression(w,process,ex3);
				w.append(")");
			}
		} else if(e instanceof BooleanExpression) {
			BooleanExpression be = (BooleanExpression)e;
			Expression ex1 = be.getExpr1();
			Expression ex2 = be.getExpr2();
			if (ex2 == null) {
				w.append("(").append(be.getToken().image);
				generateBoolExpression(w,process,ex1);
				w.append(")");
			} else {
				w.append("(");
				generateBoolExpression(w,process,ex1);
				w.append(" ").append(be.getToken().image).append(" ");
				generateBoolExpression(w,process,ex2);
				w.append(")");
			}
		} else if(e instanceof ChannelLengthExpression) {
			throw new ParseException("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else if(e instanceof ChannelOperation) {
			throw new ParseException("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else if(e instanceof CompareExpression) {
			CompareExpression ce = (CompareExpression)e;
			w.append("(");
			generateIntExpression(w,process,ce.getExpr1());
			w.append(" ").append(ce.getToken().image).append(" ");
			generateIntExpression(w,process,ce.getExpr2());
			w.append(")");
		} else if(e instanceof CompoundExpression) {
			throw new ParseException("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
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
			throw new ParseException("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else if(e instanceof MTypeReference) {
			throw new ParseException("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else if(e instanceof RunExpression) {
			throw new ParseException("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else if(e instanceof TimeoutExpression) {
			throw new ParseException("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else {
			System.out.println("WARNING: Possibly using bad expression");
			w.append(e.getIntExpression());
		}
	}

	private void generateIntExpression(StringWriter w, Proctype process, Expression e) throws ParseException {
		//say("Parsing: " + e.toString());
		if(e instanceof Identifier) {
			Identifier id = (Identifier)e;
			Variable var = id.getVariable();
			Expression arrayExpr = id.getArrayExpr();
			if (var.getArraySize() > 1) {
				if (arrayExpr != null) {
					//w.append(var.getName());
					w.append("tmp.");
					w.append(state_var_desc.get(var));
					w.append("[");
					generateIntExpression(w,process,arrayExpr);
					w.append("].var");

					try {
						int i = arrayExpr.getConstantValue();
						dep_matrix.incRead(current_transition, state_var_offset.get(var)/4+i);
					} catch(ParseException pe) {
						for(int i=0; i<var.getArraySize(); ++i) {
							dep_matrix.incRead(current_transition, state_var_offset.get(var)/4+i);
						}
					}
				} else {
					w.append("tmp.");
					w.append(state_var_desc.get(var));
					w.append("[0].var");
					dep_matrix.incRead(current_transition, state_var_offset.get(var)/4);
				}
			} else {
				w.append("tmp.");
				w.append(state_var_desc.get(var));
				w.append(".var");
				dep_matrix.incRead(current_transition, state_var_offset.get(var)/4);
			}
		} else if(e instanceof AritmicExpression) {
			AritmicExpression ae = (AritmicExpression)e;
			Expression ex1 = ae.getExpr1();
			Expression ex2 = ae.getExpr2();
			Expression ex3 = ae.getExpr3();
			if (ex2 == null) {
				w.append("(").append(ae.getToken().image);
				generateIntExpression(w, process, ex1);
				w.append(")");
			} else if (ex3 == null) {
				if (ae.getToken().image.equals("%")) {
					// Modulo takes a special notation to make sure that it
					// returns a positive value
					w.append("Math.abs("); //FIXME: Math.
					generateIntExpression(w,process,ex1);
					w.append(" % ");
					generateIntExpression(w,process,ex2);
					w.append(")");
				} else {

					w.append("(");
					generateIntExpression(w,process,ex1);
					w.append(" ").append(ae.getToken().image).append(" ");
					generateIntExpression(w,process,ex2);
					w.append(")");
				}
			} else {
				w.append("(");
				generateBoolExpression(w,process,ex1);
				w.append(" ? ");
				generateIntExpression(w,process,ex2);
				w.append(" : ");
				generateIntExpression(w,process,ex3);
				w.append(")");
			}
		} else if(e instanceof BooleanExpression) {
			BooleanExpression be = (BooleanExpression)e;
			Expression ex1 = be.getExpr1();
			Expression ex2 = be.getExpr2();
			if (ex2 == null) {
				w.append("(").append(be.getToken().image);
				generateIntExpression(w,process,ex1);
				w.append( " ? 1 : 0)");
			} else {
				w.append("(");
				generateIntExpression(w,process,ex1);
				w.append(" ").append(be.getToken().image).append(" ");
				generateBoolExpression(w,process,ex2);
				w.append(" ? 1 : 0)");
			}
		} else if(e instanceof ChannelLengthExpression) {
			throw new ParseException("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else if(e instanceof ChannelOperation) {
			throw new ParseException("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else if(e instanceof CompareExpression) {
			CompareExpression ce = (CompareExpression)e;
			w.append("(");
			generateIntExpression(w,process,ce.getExpr1());
			w.append(" ").append(ce.getToken().image).append(" ");
			generateIntExpression(w,process,ce.getExpr2());
			w.append(" ? 1 : 0)");
		} else if(e instanceof CompoundExpression) {
			throw new ParseException("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
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
		} else if(e instanceof EvalExpression) {
			throw new ParseException("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else if(e instanceof MTypeReference) {
			throw new ParseException("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else if(e instanceof RunExpression) {
			throw new ParseException("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else if(e instanceof TimeoutExpression) {
			throw new ParseException("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else {
			System.out.println("WARNING: Possibly using bad expression");
			w.append(e.getIntExpression());
		}
	}

	/**
	 * Generate C code for the specified action. The action should be in the
	 * specified process.
	 * @param w The StringWriter to which the code is written.
	 * @param process The action should be in this process.
	 * @param a The action for which C code will be generated.
	 * @throws ParseException
	 */
	private void generateAction(StringWriter w, Proctype process, Action a) throws ParseException {

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
						generateIntExpression(w,process,id);
						w.append(" = ").append(value & id.getVariable().getType().getMaskInt()).append(";");
						w.appendPostfix();
					} catch (ParseException ex) {
						// Could not get Constant value
						w.appendPrefix();
						generateIntExpression(w,process,id);
						w.append(" = ");
						generateIntExpression(w,process,as.getExpr());
						w.append((mask == null ? "" : " & " + mask));
						w.append(";");
						w.appendPostfix();
					}
					break;
				case PromelaConstants.INCR:
					if (mask == null) {
						w.appendPrefix();
						generateIntExpression(w,process,id);
						w.append("++;");
						w.appendPostfix();
					} else {
						w.appendPrefix();
						generateIntExpression(w,process,id);
						w.append(" = (");
						generateIntExpression(w,process,id);
						w.append(" + 1) & ").append(mask).append(";");
						w.appendPostfix();
					}
					break;
				case PromelaConstants.DECR:
					if (mask == null) {
						w.appendPrefix();
						generateIntExpression(w,process,id);
						w.append("--;");
						w.appendPostfix();
					} else {
						w.appendPrefix();
						generateIntExpression(w,process,id);
						w.appendLine(" = (");
						generateIntExpression(w,process,id);
						w.append(" - 1) & ");
						w.append(mask);
						w.append(";");
						w.appendPostfix();
					}
					break;
				default:
					throw new ParseException("unknown assignment type");
			}
			handleAssignDependency(process, current_transition, id);

		// Handle assert action
		} else if(a instanceof AssertAction) {
			AssertAction as = (AssertAction)a;
			Expression e = as.getExpr();

			w.append("if(!");
			generateBoolExpression(w,process,e);
			w.appendLine(") {");
			w.indent();
			w.appendLine("printf(\"Assertion violated: ",as.getExpr().toString(), "\\n\");");
			w.appendLine("print_state(tmp);");
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
				generateIntExpression(w,process,expr);
			}
			w.append(");").appendPostfix();

		// Handle expression action
		} else if(a instanceof ExprAction) {
			ExprAction ea = (ExprAction)a;
			Expression expr = ea.getExpression();
			final String sideEffect = expr.getSideEffect();
			if (sideEffect != null) {
				w.appendLine(sideEffect, "; // POSSIBLY THIS IS WRONG");
			}

		// Handle not yet implemented action
		} else {
			throw new ParseException("LTSMinPrinter: Not yet implemented: "+a.getClass().getName());
		}

	}

	private void generateEnabledExpression(StringWriter w, Proctype process, Action a) throws ParseException {
		// Handle assignment action
		if(a instanceof AssignAction) {
			w.append("true");

		// Handle assert action
		} else if(a instanceof AssertAction) {
			w.append("true");

		// Handle print action
		} else if(a instanceof PrintAction) {
			w.append("true");

			// Handle expression action
		} else if(a instanceof ExprAction) {
			ExprAction ea = (ExprAction)a;
			Expression expr = ea.getExpression();
			generateBoolExpression(w,process,expr);

		// Handle not yet implemented action
		} else {
			throw new ParseException("LTSMinPrinter: Not yet implemented: "+a.getClass().getName());
		}
	}

	/**
	 * Handle a variable by adding it to a CStruct with the correct type and
	 * putting it in the correct position in the state vector.
	 * @param sg The CStruct to add the variable to.
	 * @param var The variable to add.
	 * @param desc The description of the variable, to add to state_vector_desc.
	 * @param current_offset The offset at which the variable should be put.
	 * @return The next free offset position.
	 */
	private int handleVariable(CStruct sg, Variable var, String desc, int current_offset) {

		// Add global to the global state struct
		TypeDesc td = getCTypeOfVar(var);
		sg.addMember(td,var.getName());

		if(var.getType().getJavaName().equals("Type")) {

			// Untested
			CustomVariableType cvt = (CustomVariableType)var.getType();
			for(Variable v: cvt.getVariableStore().getVariables()) {
				current_offset += handleVariable(sg,v,var.getName()+".",current_offset);
			}
		} else if(var.getType().getJavaName().equals("int")) {
			System.out.println("  " + var.getName() + " @" + current_offset);

			if (var.getArraySize() > 1) {
				state_var_offset.put(var, current_offset);
				state_var_desc.put(var, desc + var.getName());
				for(int i=0; i<var.getArraySize(); ++i) {
					// Add global to Variable->offset map and add a description
					state_vector_desc.add(desc + var.getName());
					state_vector_var.add(var);
					current_offset += STATE_ELEMENT_SIZE;
				}
			} else {
				// Add global to Variable->offset map and add a description
				state_var_offset.put(var, current_offset);
				state_var_desc.put(var, desc + var.getName());
				state_vector_desc.add(desc + var.getName());
				state_vector_var.add(var);
				current_offset += STATE_ELEMENT_SIZE;
			}

		} else {
			System.out.println("ERROR: Unable to handle: " + var.getType().getName());
			System.exit(0);
		}

		return current_offset;
	}

	private String wrapName(String name) {
		return name;
	}

	void handleAssignDependency(Proctype process, int trans, Expression e) {
		if(e instanceof Identifier) {
			Identifier id = (Identifier)e;
			Variable var = id.getVariable();
			Expression arrayExpr = id.getArrayExpr();
			if (var.getArraySize() > 1) {
				if (arrayExpr != null) {
					try {
						int i = arrayExpr.getConstantValue();
						dep_matrix.incWrite(current_transition, state_var_offset.get(var)/4+i);
						dep_matrix.decrRead(current_transition, state_var_offset.get(var)/4+i);
					} catch(ParseException pe) {
						for(int i=0; i<var.getArraySize(); ++i) {
							dep_matrix.incWrite(current_transition, state_var_offset.get(var)/4+i);
							dep_matrix.decrRead(current_transition, state_var_offset.get(var)/4+i);
						}
					}
				} else {
					dep_matrix.incWrite(current_transition, state_var_offset.get(var)/4);
					dep_matrix.decrRead(current_transition, state_var_offset.get(var)/4);
				}
			} else {
				dep_matrix.incWrite(current_transition, state_var_offset.get(var)/4);
				dep_matrix.decrRead(current_transition, state_var_offset.get(var)/4);
			}
		} else {
			System.out.println("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
			System.exit(-1);
		}
	}

}

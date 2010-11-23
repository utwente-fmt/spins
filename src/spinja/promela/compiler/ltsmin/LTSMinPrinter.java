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
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import spinja.promela.compiler.automaton.EndTransition;
import spinja.promela.compiler.parser.ParseException;
import spinja.promela.compiler.parser.PromelaConstants;
import spinja.promela.compiler.variable.ChannelType;
import spinja.promela.compiler.variable.ChannelVariable;
import spinja.promela.compiler.variable.CustomVariableType;
import spinja.promela.compiler.variable.Variable;
import spinja.promela.compiler.variable.VariableType;
import spinja.promela.compiler.variable.VariableStore;
import spinja.promela.compiler.expression.*;
import spinja.util.StringWriter;

/**
 * This class handles the generation of C code for LTSMin.
 * FIXME: handle state vector of length 0
 *
 * Contains various subclasses:
 *   - TypeDesc: contains the description of a type, only using name and array
 *   - CStruct: handles the textual generation of a C struct typedef.
 *   - DepRow:  handles a row of the dependency matrix
 *   - DepMatrix: handles the dependency matrix
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
	 * VarDescriptorArray
	 */
	public abstract class VarDescriptor {
		String type;

		public String getType() {
			return type;
		}

		public void setType(String type) {
			this.type = type;
		}
		
		abstract public List<String> extractDescription();
		abstract public String extractDeclaration();
	}

	/**
	 * VarDescriptorArray
	 */
	public class VarDescriptorArray extends VarDescriptor {
		private VarDescriptor child;
		private int length;

		public VarDescriptorArray(VarDescriptor child, int length) {
			this.child = child;
			this.length = length;
		}

		public int getLength() {
			return length;
		}

		public void setLength(int length) {
			this.length = length;
		}

		public void setChild(VarDescriptor child) {
			this.child = child;
		}
		
		public VarDescriptor getChild() {
			return child;
		}

		public List<String> extractDescription() {
			List<String> descs = new ArrayList<String>();
			if(child!=null) {
				for(int i=0; i<length; ++i) {
					List<String> descs_child = child.extractDescription();
					for(String s: descs_child) {
						descs.add(s + "[" + i + "]");
					}
				}
			} else {
				throw new AssertionError("Failed to extract description: childless array");
			}
			return descs;
		}

		public String extractDeclaration() {
			return child.extractDeclaration() + "[" + length + "]";
		}
	}

	/**
	 * VarDescriptorVar
	 */
	public class VarDescriptorVar extends VarDescriptor {
		private String name;

		public VarDescriptorVar(String name) {
			this.name = name;
		}

		public void setChild(VarDescriptor child) {
		}

		public VarDescriptor getChild() {
			return null;
		}

		public List<String> extractDescription() {
			List<String> descs = new ArrayList<String>();
			descs.add(name);
			return descs;
		}

		public String extractDeclaration() {
			return name;
		}
	}

	/**
	 * VarDescriptorVar
	 */
	public class VarDescriptorMember extends VarDescriptor {

		private VarDescriptor struct;
		private List<VarDescriptor> members;

		public VarDescriptorMember(VarDescriptor struct) {
			this.struct = struct;
			this.members = new ArrayList<VarDescriptor>();
		}

		public void setStruct(VarDescriptor struct) {
			this.struct = struct;
		}

		public VarDescriptor setStruct() {
			return struct;
		}

		public void addMember(VarDescriptor member) {
			this.members.add(member);
		}

		public List<String> extractDescription() {
			List<String> descs = new ArrayList<String>();
			if(struct==null) {
				throw new AssertionError("Failed to extract description: invalid struct");
			}
			if(members==null) {
				throw new AssertionError("Failed to extract description: invalid member");
			}
			List<String> descs_struct = struct.extractDescription();
			for(String s_s: descs_struct) {
				for(VarDescriptor member: members) {
					List<String> descs_member = member.extractDescription();
					for(String m_s: descs_member) {
						descs.add(s_s + "." + m_s);
					}
				}
			}
			return descs;
		}

		public String extractDeclaration() {
			return struct.extractDeclaration();
		}

	}

	/**
	 * VarDescriptorArray
	 */
	public class VarDescriptorChannel extends VarDescriptor {
		private VarDescriptor child;
		private int length;

		public VarDescriptorChannel(VarDescriptor child, int length) {
			this.child = child;
			this.length = length;
		}

		public int getLength() {
			return length;
		}

		public void setLength(int length) {
			this.length = length;
		}

		public void setChild(VarDescriptor child) {
			this.child = child;
		}

		public VarDescriptor getChild() {
			return child;
		}

		public List<String> extractDescription() {
			List<String> descs = new ArrayList<String>();
			if(child!=null) {
				for(int i=0; i<length; ++i) {
					List<String> descs_child = child.extractDescription();
					for(String s: descs_child) {
						descs.add(s + ".m" + i);
					}
				}
			} else {
				throw new AssertionError("Failed to extract description: childless array");
			}
			return descs;
		}

		public String extractDeclaration() {
			return child.extractDeclaration();
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
			s.appendLine(type.type," ",varName,type.array,";");
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
		private int row_length;


		DepMatrix(int trans, int size) {
			dep_matrix = new ArrayList();
			row_length = size;
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

		public void ensureSize(int size) {
			for(int i=dep_matrix.size();i<size;++i) {
				dep_matrix.add(i,new DepRow(row_length));
			}
		}

		public int getRows() {
			return dep_matrix.size();
		}

		public DepRow getRow(int trans) {
			return dep_matrix.get(trans);
		}
	}

	public class SendAction {
		public ChannelSendAction csa;
		public Transition t;
		public Proctype p;

		public SendAction(ChannelSendAction csa, Transition t, Proctype p) {
			this.csa = csa;
			this.t = t;
			this.p = p;
		}

	}

	public class ReadAction {
		public ChannelReadAction cra;
		public Transition t;
		public Proctype p;

		public ReadAction(ChannelReadAction cra, Transition t, Proctype p) {
			this.cra = cra;
			this.t = t;
			this.p = p;
		}

	}

	public class ReadersAndWriters {
		public List<SendAction> sendActions;
		public List<ReadAction> readActions;

		public ReadersAndWriters() {
			sendActions = new ArrayList<SendAction>();
			readActions = new ArrayList<ReadAction>();
		}

	}

	public class AtomTransition {
		public int trans;
		public Transition transition;
		public Proctype process;

		public AtomTransition(int trans, Transition transition, Proctype process) {
			this.trans = trans;
			this.transition = transition;
			this.process = process;
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
	public static final String C_TYPE_CHANNEL = "sj_channel";

	private HashMap<Variable,Integer> state_var_offset;
	private HashMap<Variable, String> state_var_desc;
	private HashMap<Proctype,Integer> state_proc_offset;



	private HashMap<ChannelVariable,ReadersAndWriters> channels;

	private List<AtomTransition> atomicTransitions;

	private List<Variable> state_vector_var;

	// Textual description of the state vector, per integer
	private List<String> state_vector_desc;

	// List of processes
	private List<Proctype> procs;

	// Dependency Matrix
	private DepMatrix dep_matrix;

	// The current transition group is currently being generated
	private int current_transition;

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
	 * @param spec The Specification using which C code is generated.
	 */
	public LTSMinPrinter(Specification spec) {
		if(spec==null) {
			// error
		}
		this.spec = spec;
		c_code = null;

//		VarDescriptorVar vs = new VarDescriptorVar("str");
//		VarDescriptorVar vm = new VarDescriptorVar("mem1");
//		VarDescriptorVar vm2 = new VarDescriptorVar("mem2");
//
//		VarDescriptorArray vma = new VarDescriptorArray(vm, 5);
//		VarDescriptorArray vsa = new VarDescriptorArray(vs, 2);
//		VarDescriptorMember v = new VarDescriptorMember(vsa);
//
//		v.addMember(vma);
//		v.addMember(vm2);
//
//		List<String> l = v.extractDescription();
//		for(String s: l) {
//			System.out.println(s);
//		}
//		System.exit(-1);

		state_var_offset = new HashMap<Variable,Integer>();
		state_var_desc = new HashMap<Variable,String>();
		state_proc_offset = new HashMap<Proctype,Integer>();
		state = null;
		state_vector_desc = new ArrayList<String>();
		state_vector_var = new ArrayList<Variable>();
		procs = new ArrayList<Proctype>();

		channels = new HashMap<ChannelVariable,ReadersAndWriters>();
		atomicTransitions = new ArrayList<AtomTransition>();
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

		// Generate structs describing channels and custom structs
		w.appendLine("");
		generateCustomStructs(w);

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
		w.appendLine("#include <stdlib.h>");
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

		w.appendLine("typedef struct ",C_TYPE_CHANNEL," {");
		w.indent();
		w.appendLine("unsigned int isRendezVous: 2;");
		w.appendLine("unsigned int nextRead: 15;");
		w.appendLine("unsigned int filled: 15;");
		w.outdent();
		w.appendLine("} ",C_TYPE_CHANNEL,";");
	}

	private void generateCustomStruct(StringWriter w, Variable var) {
		if(var.getType() instanceof ChannelType) {
			CStruct struct = new CStruct(wrapNameForChannel(var.getName()));

			ChannelVariable cv = (ChannelVariable)var;
			ChannelType ct = cv.getType();
			VariableStore vs = ct.getVariableStore();

			if (ct.getBufferSize() > 0) {
				//for(int i=0; i<ct.getBufferSize(); ++i) {
					int j=0;
					for(Variable v: vs.getVariables()) {
						TypeDesc td = getCTypeOfVar(v);
						struct.addMember(td,"m"+j);
						++j;
					}
				//}
			}

			channels.put(cv,new ReadersAndWriters());

			w.appendLine(struct.getCCode());
		}

	}

	private void generateCustomStructs(StringWriter w) {
		VariableStore globals = spec.getVariableStore();
		List<Variable> vars = globals.getVariables();
		for(Variable var: vars) {
			generateCustomStruct(w,var);
		}

		Iterator<Proctype> it = spec.iterator();
		for(;it.hasNext();) {
			Proctype p = it.next();
			List<Variable> proc_vars = p.getVariables();
			for(Variable var: proc_vars) {
				generateCustomStruct(w,var);
			}
		}
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

		// Globals: add globals to the global state struct
		VariableStore globals = spec.getVariableStore();
		List<Variable> vars = globals.getVariables();
		for(Variable var: vars) {

			// Add global to the global state struct and fix the offset
			current_offset = handleVariable(sg,var,C_STATE_GLOBALS+".",current_offset);

		}

		// Add global state struct to main state struct
		if(!vars.isEmpty()) {
			state_members.add(sg);
			state.addMember(C_STATE_GLOBALS_T, C_STATE_GLOBALS);
		}

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

	// Generate state struct comment
		for(int off=0; off<state_size/4; ++off) {
			w.appendLine("// ",off,"\t",state_vector_desc.get(off));
		}

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
		w.appendLine("if(state_size != sizeof(" + C_STATE_T + ")) { printf(\"state_t SIZE MISMATCH!: state=%i(%i) globals=%i\",sizeof(state_t),state_size,sizeof(state_globals_t)); }");
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
			//if(state_vector_var.get(offset/4).getType() instanceof ChannelType) {
			//	return state_vector_desc.get(offset/4) + "_desc";
			//} else {
				return state_vector_desc.get(offset/4);
			//}
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
		w.appendLine("register int pos;");
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
//		for(Proctype p: procs) {
//			Automaton a = p.getAutomaton();
//			Iterator<State> i = a.iterator();
//			while(i.hasNext()) {
//				State st = i.next();
//				if(st.sizeOut()==0) { // FIXME: Is this the correct prerequisite for THE end state of a process?
//					++max_transitions;
//				} else {
//					for(Transition t: st.output) {
//						++max_transitions;
//					}
//				}
//			}
//		}

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

		// Generate the rendezvous transitions
		for(Map.Entry<ChannelVariable,ReadersAndWriters> e: channels.entrySet()) {
			ChannelVariable cv = e.getKey();
			ReadersAndWriters raw = e.getValue();
			for(SendAction sa: raw.sendActions) {
				for(ReadAction ra: raw.readActions) {
					dep_matrix.ensureSize(current_transition+1);
					generateRendezVousAction(w,sa,ra,trans);
					current_transition = ++trans;
				}
			}
		}


		// From the switch state we jump to the correct transition,
		// according to the transition group 't'
		w.appendLine("switch_state:");

//		w.appendLine("");
//		w.appendLine("static int atoms = 0;");
//		for(AtomTransition at: atomicTransitions) {
//
//			w.appendLine("if(",C_STATE_TMP,".",wrapName(at.process.getName()),".",C_STATE_PROC_COUNTER,".var == ",at.transition.getFrom().getStateId(),") {");
//			w.indent();
//
//			//w.appendLine("goto l",at.trans,";");
//			w.appendLine("if( t != ",at.trans," ) { return 0; } else { printf(\"handled %i atomic expression so far\\n\",++atoms); }");
//
//			w.outdent();
//			w.appendLine("}");
//
//		}

		w.appendLine("switch(t) {");
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

			dep_matrix.ensureSize(current_transition+1);

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

				dep_matrix.ensureSize(current_transition+1);

				System.out.println("Handling trans: " + t.getClass().getName());

				// Skip handling of EndTransition, because they don't go anywhere
				//if(t instanceof EndTransition) {
				//	continue;
				//}

				// Checks
				if(t==null) {
					System.out.println("Transition is NULL!");
					System.exit(-1);
				}
				//if(t.getTo()==null) {
				//	System.out.println("Transition's next state is NULL!");
				//	System.exit(-1);
				//}


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
						generateEnabledExpression(w,process,a,t);
						w.appendLine(") { //",a.getClass().getName());
					} else {
						w.appendLine("if(true) {");
					}
				} catch(ParseException e) {
					System.out.println("Something bad happened: ");
					e.printStackTrace();
				}
				w.indent();

				// Change process counter
				w.appendLine("",C_STATE_TMP,".",
						wrapName(process.getName()),".",
						C_STATE_PROC_COUNTER,".var = ",
						t.getTo()==null?-1:t.getTo().getStateId(),";"
						);

				// Generate actions for this transition
				generateStateTransition(w,process,t);

				// Generate the callback and the rest
				//if(t.getTo()!=null) {
					w.appendLine("callback(arg,&transition_info,&tmp);");
					w.appendLine("return ",1,";");
				//}

				if(state.isInAtomic()) {
					atomicTransitions.add(new AtomTransition(trans, t, process));
				}

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
				generateAction(w,process,a,t);

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
				//w.append(1);
				if(++s>=dr.getSize()) {
					break;
				}
				w.append(",");
			}
			w.append("},{");
			s=0;
			for(;;) {
				w.append(dr.getWriteB(s));
				//w.append(1);
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
					w.append("abs(");
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
	private void generateAction(StringWriter w, Proctype process, Action a, Transition t) throws ParseException {

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

		// Handle channel send action
		} else if(a instanceof ChannelSendAction) {
			ChannelSendAction csa = (ChannelSendAction)a;
			ChannelVariable var = (ChannelVariable)csa.getVariable();

			if(var.getType().getBufferSize()>0) {
				//int offset = state_var_offset.get(var);
				//String access = "(*(" + C_TYPE_CHANNEL + ")&" + C_STATE_T + "[" + offset + "])";
				String access = C_STATE_TMP + "." + wrapNameForChannelDesc(state_var_desc.get(var));

				//String access_buffer = "(*(" + C_TYPE_CHANNEL + ")&" + C_STATE_T + "[(" + access + ".nextRead" + access + ".filled)%" + var.getType().getBufferSize() + "])";
				w.appendLine("pos = (" + access + ".nextRead + "+access+".filled) % "+var.getType().getBufferSize() + ";");
				String access_buffer = C_STATE_TMP + "." + wrapNameForChannelBuffer(state_var_desc.get(var)) + "[pos]";

				List<Expression> exprs = csa.getExprs();
				for (int i = 0; i < exprs.size(); i++) {
					final Expression expr = exprs.get(i);
					w.appendPrefix();
					w.append(access_buffer).append(".m").append(i).append(".var = ");
					generateIntExpression(w, process, expr);
					w.append(";");
					w.appendPostfix();

				}

				//w.appendLine(access,".filled = (",access,".filled+1) * (",access,".filled<=",var.getType().getVariableStore().getVariables().size()+");");
				w.appendLine("++",access, ".filled;");
			} else {
				ReadersAndWriters raw = channels.get(var);
				if(raw==null) {
					throw new AssertionError("Channel not found in list of channels!");
				}

				raw.sendActions.add(new SendAction(csa,t,process));
			}

		// Handle a channel read action
		} else if(a instanceof ChannelReadAction) {
			ChannelReadAction cra = (ChannelReadAction)a;
			ChannelVariable var = (ChannelVariable)cra.getVariable();
			
			if(var.getType().getBufferSize()>0) {
				String access = C_STATE_TMP + "." + wrapNameForChannelDesc(state_var_desc.get(var));
				w.appendLine("pos = (" + access + ".nextRead + "+access+".filled) % "+var.getType().getBufferSize() + ";");
				String access_buffer = C_STATE_TMP + "." + wrapNameForChannelBuffer(state_var_desc.get(var)) + "[pos]";

				List<Expression> exprs = cra.getExprs();
				for (int i = 0; i < exprs.size(); i++) {
					final Expression expr = exprs.get(i);
					if (expr instanceof Identifier) {
						w.appendPrefix();
						generateIntExpression(w, process, expr);
						w.append(" = ").append(access_buffer).append(".m").append(i).append(".var");
						w.append(";");
						w.appendPostfix();
					}
				}

				w.appendLine(access,".nextRead = (",access,".nextRead+1)%"+var.getType().getBufferSize()+";");
				w.appendLine("--",access, ".filled;");
			} else {
				ReadersAndWriters raw = channels.get(var);
				if(raw==null) {
					throw new AssertionError("Channel not found in list of channels!");
				}

				raw.readActions.add(new ReadAction(cra,t,process));
			}

		// Handle not yet implemented action
		} else {
			throw new ParseException("LTSMinPrinter: Not yet implemented: "+a.getClass().getName());
		}

	}

	private void generateEnabledExpression(StringWriter w, Proctype process, Action a, Transition t) throws ParseException {
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

		// Handle a channel send action
		} else if(a instanceof ChannelSendAction) {
			ChannelSendAction csa = (ChannelSendAction)a;
			ChannelVariable var = (ChannelVariable)csa.getVariable();
			if(var.getType().getBufferSize()>0) {

				//int offset = state_var_offset.get(var);
				//String access = "(*(" + C_TYPE_CHANNEL + ")&" + C_STATE_T + "[" + offset + "])";
				String access = C_STATE_TMP + "." + wrapNameForChannelDesc(state_var_desc.get(var));


				//w.appendLine("printf(\"filled=%i buffersize=%i\n\","+access+".filled,"+var.getType().getBufferSize()+") || ");
				w.append("(");
				//w.append(access).append(".isRendezVous && ");
				w.append(access).append(".filled < ");
				w.append(var.getType().getBufferSize()).append(")");

//					return var + " != -1 && !_channels[" + var + "].isRendezVous() && _channels[" + var
//				+ "].canSend()";
			} else {
				w.append("false");
			}

		// Handle a channel read action
		} else if(a instanceof ChannelReadAction) {
			ChannelReadAction cra = (ChannelReadAction)a;
			ChannelVariable var = (ChannelVariable)cra.getVariable();

			if(var.getType().getBufferSize()>0) {

				String access = C_STATE_TMP + "." + wrapNameForChannelDesc(state_var_desc.get(var));
				String access_buffer = C_STATE_TMP + "." + wrapNameForChannelBuffer(state_var_desc.get(var)) + "[" + access + ".nextRead]";

				List<Expression> exprs = cra.getExprs();

				w.append("(");
				//w.append(access).append(".isRendezVous && ");
				w.append(access).append(".filled > 0");

				for (int i = 0; i < exprs.size(); i++) {
					final Expression expr = exprs.get(i);
					if (!(expr instanceof Identifier)) {
						w.append(" && (");
						w.append(access_buffer).append(".m").append(i).append(".var == ");
						generateIntExpression(w, process, expr);
						w.append(")");
					}

				}

				w.append(")");
			} else {
				w.append("false");
			}


		// Handle not yet implemented action
		} else {
			throw new ParseException("LTSMinPrinter: Not yet implemented: "+a.getClass().getName());
		}
	}

	private int insertVariable(CStruct sg, Variable var, String desc, String name, int current_offset) {

//		String name;
//		if(forcedName!=null && !forcedName.equals("")) {
//			name = forcedName;
//		} else {
//			name = var.getName();
//		}

		if(!state_var_offset.containsKey(var)) {

				System.out.println("Adding VARIABLE TO OFFSET: " + var.getName() + " " + var.hashCode());
			// Add global to Variable->offset map and add a description
			state_var_offset.put(var, current_offset);
			state_var_desc.put(var, desc + name);

		}

		state_vector_desc.add(desc + name);
		state_vector_var.add(var);
		current_offset += STATE_ELEMENT_SIZE;

		return current_offset;
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
		return handleVariable(sg,var,desc,"",current_offset, null);
	}
	private int handleVariable(CStruct sg, Variable var, String desc, String forcedName, int current_offset, VarDescriptor vd) {

		String name;
		if(forcedName!=null && !forcedName.equals("")) {
			name = forcedName;
		} else {
			name = var.getName();
		}

		System.out.println("HANDLING VAR: " + var.getType().getClass().getName());

		if(var.getType() instanceof ChannelType) {
			ChannelVariable cv = (ChannelVariable)var;
			ChannelType ct = cv.getType();
//			int size = ct.getBits()/32;
//			System.out.println("  " + name + " @" + current_offset + " (channel of size " + ct.getBufferSize() + ")");
			VariableStore vs = ct.getVariableStore();

			vd = new VarDescriptorVar(wrapNameForChannelBuffer(name));
			if(var.getArraySize()>1) {
				vd = new VarDescriptorArray(vd,var.getArraySize());
			}
			vd = new VarDescriptorArray(vd,ct.getBufferSize());
			System.out.println(var.getName() + " has " + vs.getVariables().size());
			vd = new VarDescriptorChannel(vd,vs.getVariables().size());
			vd.setType(wrapNameForChannel(name));

			if (ct.getBufferSize() > 0) {

//				// Add global to the global state struct
//				sg.addMember(new TypeDesc(wrapNameForChannel(name),"["+ct.getBufferSize()+"]"),name);
//
//				for(int i=0; i<ct.getBufferSize(); ++i) {
//					int j=0;
//					for(Variable v: vs.getVariables()) {
//						current_offset = insertVariable(sg, v, desc+wrapName(var.getName()) + "[" + j + "]" + ".","m"+i, current_offset);
//						//current_offset = handleVariable(sg,v,wrapName(var.getName()) + "[" + j + "]" + ".","m"+i,current_offset);
//						++j;
//					}
//				}

				System.out.println("Adding CHANNEL: " + var.getName() + " " + var.hashCode());
				current_offset = insertVariable(sg, var,desc,wrapNameForChannelDesc(name), current_offset);
				sg.addMember(C_TYPE_CHANNEL,wrapNameForChannelDesc(name));

				for(String s: vd.extractDescription()) {
					current_offset = insertVariable(sg, var, desc, s, current_offset);
				}
				sg.addMember(vd.getType(),vd.extractDeclaration());

			} else {

				System.out.println("Adding CHANNEL: " + var.getName() + " " + var.hashCode());
				current_offset = insertVariable(sg, var,desc,wrapNameForChannelDesc(name), current_offset);
				sg.addMember(C_TYPE_CHANNEL,wrapNameForChannelDesc(name));



				//System.out.println("NOT YET IMPLEMENTED: RENDEZ VOUZ");
				//System.exit(-1);
//				// Add global to Variable->offset map and add a description
//				state_var_offset.put(var, current_offset);
//				state_var_desc.put(var, desc + name);
//				state_vector_desc.add(desc + name);
//				state_vector_var.add(var);
//				current_offset += STATE_ELEMENT_SIZE;
			}

		} else if(var.getType() instanceof VariableType) {
			if(var.getType().getJavaName().equals("int")) {
				System.out.println("  " + name + " @" + current_offset + " (" + var.getType().getName() + ")");

				// Add global to the global state struct
				TypeDesc td = getCTypeOfVar(var);
				sg.addMember(td,name);

				if (var.getArraySize() > 1) {
					for(int i=0; i<var.getArraySize(); ++i) {
						current_offset = insertVariable(sg,var,desc,name,current_offset);
					}
				} else {
					current_offset = insertVariable(sg,var,desc,name,current_offset);
				}

			} else if(var.getType().getJavaName().equals("Type")) {

				// Untested
				CustomVariableType cvt = (CustomVariableType)var.getType();
				for(Variable v: cvt.getVariableStore().getVariables()) {
					current_offset = handleVariable(sg,v,name+".",name,current_offset,vd);
				}

			} else {
				System.out.println("ERROR: Unknown error trying to handle an integer");
				System.exit(0);
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

	private String wrapNameForChannel(String name) {
		return "ch_"+wrapName(name)+"_t";
	}

	private String wrapNameForChannelDesc(String name) {
		return wrapName(name);
	}

	private String wrapNameForChannelBuffer(String name) {
		return wrapName(name)+"_buffer";
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

	void generateRendezVousAction(StringWriter w, SendAction sa, ReadAction ra, int trans) {
		ChannelSendAction csa = sa.csa;
		ChannelReadAction cra = ra.cra;


		if(csa.getVariable() != cra.getVariable()) {
			throw new AssertionError("generateRendezVousAction() called with inconsequent ChannelVariable");
		}
		ChannelVariable var = (ChannelVariable)csa.getVariable();
		if(var.getType().getBufferSize()>0) {
			throw new AssertionError("generateRendezVousAction() called with non-rendezvous channel");
		}

		List<Expression> csa_exprs = csa.getExprs();
		List<Expression> cra_exprs = cra.getExprs();

		if(csa_exprs.size() != cra_exprs.size()) {
			throw new AssertionError("generateRendezVousAction() called with incompatible actions: size mismatch");
		}

		w.appendLine("l",trans,": if(",C_STATE_TMP,".",wrapName(sa.p.getName()),".",C_STATE_PROC_COUNTER,".var == ",sa.t.getFrom().getStateId(),
		                        " && ",C_STATE_TMP,".",wrapName(ra.p.getName()),".",C_STATE_PROC_COUNTER,".var == ",ra.t.getFrom().getStateId(),") {");
		w.indent();

		w.appendPrefix();
		w.append("if( true");
		for (int i = 0; i < cra_exprs.size(); i++) {
			final Expression csa_expr = csa_exprs.get(i);
			final Expression cra_expr = cra_exprs.get(i);
			if (!(cra_expr instanceof Identifier)) {
				w.append(" && (");
				try {
					generateIntExpression(w, null, csa_expr);
				} catch(ParseException e) {
				}
				w.append(" == ");
				try {
					generateIntExpression(w, null, cra_expr);
				} catch(ParseException e) {
				}
				w.append(")");
			}
		}
		w.append(") {");
		w.appendPostfix();

		w.indent();

		// Change process counter of sender
		w.appendLine("",C_STATE_TMP,".",
				wrapName(sa.p.getName()),".",
				C_STATE_PROC_COUNTER,".var = ",
				sa.t.getTo().getStateId(),";"
				);

		// Change process counter of receiver
		w.appendLine("",C_STATE_TMP,".",
				wrapName(ra.p.getName()),".",
				C_STATE_PROC_COUNTER,".var = ",
				ra.t.getTo().getStateId(),";"
				);

		for (int i = 0; i < cra_exprs.size(); i++) {
			final Expression csa_expr = csa_exprs.get(i);
			final Expression cra_expr = cra_exprs.get(i);
			System.out.println("JAAAAAAAAAAAAAAAAAAAAAAAAAAA " + csa_expr.getClass().getName());
			System.out.println("JAAAAAAAAAAAAAAAAAAAAAAAAAAA " + cra_expr.getClass().getName());
			if ((cra_expr instanceof Identifier)) {
			System.out.println("JAAAAAAA----------------------");
				w.appendPrefix();
				try {
					generateIntExpression(w, null, cra_expr);
				} catch(ParseException e) {
					e.printStackTrace();
				}
				w.append(" = ");
				try {
					generateIntExpression(w, null, csa_expr);
				} catch(ParseException e) {
					e.printStackTrace();
				}
				w.append(";");
				w.appendPostfix();
			}
		}

		w.appendLine("callback(arg,&transition_info,&tmp);");
		w.appendLine("return ",1,";");

		w.outdent();
		w.appendLine("}");

		w.outdent();
		w.appendLine("}");
		w.appendLine("return 0;");
	}

}

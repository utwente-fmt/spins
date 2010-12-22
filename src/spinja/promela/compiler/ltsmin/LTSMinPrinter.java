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
import spinja.promela.compiler.automaton.ElseTransition;
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

	/**
	 * A class describing a variable type. It uses two strings to depict the
	 * type name and an optional array component.
	 * Instances are returned by getCTypeOfVar().
	 */
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
	 * VarDescriptor classes are used to describe the access to one or more
	 * variables. Multiple can be combined in a tree-like structure. On a node,
	 * the function extractDescription() can be called to obtain all the
	 * possibilities. The function extractDeclaration() is used to obtain how
	 * all the variables are declared.
	 * For example, using these classes one can describe an array called foo of
	 * 10 elements by using:
	 * (new VarDescriptorArray(new VarDescriptorVar("foo"),10)).setType("int");
	 * The function extractDeclaration() will return:
	 *   "int foo[10]"
	 * The function extractDescription() will return:
	 *   foo[0]
	 *   foo[1]
	 *   ...
	 *   foo[9]
	 */
	public abstract class VarDescriptor {
		protected String type;

		/**
		 * Returns the type of this variable descriptor.
		 * @return The type of this variable descriptor.
		 */
		public String getType() {
			return type;
		}

		/**
		 * Sets the type of this variable descriptor.
		 * @param type The new type of this variable descriptor.
		 */
		public void setType(String type) {
			this.type = type;
		}

		/**
		 * Returns all the possible variable descriptors of the tree starting
		 * at this node.
		 * @return Variable descriptors.
		 */
		abstract public List<String> extractDescription();

		/**
		 * Returns the declaration of the tree starting at this node.
		 * @return Declaration string.
		 */
		abstract public String extractDeclaration();
	}

	/**
	 * VarDescriptorArray
	 * The returned descriptions will look like this:
	 *   child's_descriptions "[" 0..length "]"
	 * The returned declaration will look like this:
	 *   child's_declaration "[" length "]"
	 */
	public class VarDescriptorArray extends VarDescriptor {
		private VarDescriptor child;
		private int length;

		/**
		 * Creates a array descriptor.
		 * @param child The child of this node.
		 * @param length The length of the array to describe.
		 */
		public VarDescriptorArray(VarDescriptor child, int length) {
			this.child = child;
			this.length = length;
		}

		/**
		 * Returns the length of the array being described.
		 * @return The length of the array being described.
		 */
		public int getLength() {
			return length;
		}

		/**
		 * Sets the length of the array being described.
		 * @param length The new length of the array being described.
		 */
		public void setLength(int length) {
			this.length = length;
		}

		/**
		 * Sets the child of this node.
		 * @param child
		 */
		public void setChild(VarDescriptor child) {
			this.child = child;
		}

		/**
		 * Returns the child of this node.
		 * @return The child of this node.
		 */
		public VarDescriptor getChild() {
			return child;
		}

		/**
		 * Returns the descriptions of this node.
		 * The returned descriptions will look like this:
		 *   child's_descriptions "[" 0..length "]"
		 * @return The descriptions.
		 */
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

		/**
		 * Returns the declaration of this node.
		 * The returned declaration will look like this:
		 *   child's_declaration "[" length "]"
		 * @return The declaration.
		 */
		public String extractDeclaration() {
			return child.extractDeclaration() + "[" + length + "]";
		}
	}

	/**
	 * Describes the name of a variable.
	 * The returned descriptions will look like this:
	 *   specified_name
	 * The returned declaration will look like this:
	 *   specified_name
	 */
	public class VarDescriptorVar extends VarDescriptor {
		private String name;

		/**
		 * Creates a new variable name descriptor.
		 * @param name The name of the variable
		 */
		public VarDescriptorVar(String name) {
			this.name = name;
		}


		/**
		 * Returns the descriptions of this node.
		 * The returned descriptions will look like this:
		 *   specified_name
		 * @return The descriptions.
		 */
		public List<String> extractDescription() {
			List<String> descs = new ArrayList<String>();
			descs.add(name);
			return descs;
		}

		/**
		 * Returns the declaration of this node.
		 * The returned declaration will look like this:
		 *   specified_name
		 * @return The declaration.
		 */
		public String extractDeclaration() {
			return name;
		}
	}

	/**
	 * Describes a struct and its members.
	 * The returned descriptions will look like this:
	 *   struct's_descriptions "." members
	 * The returned declaration will look like this:
	 *   struct's_declaration
	 */
	public class VarDescriptorMember extends VarDescriptor {

		private VarDescriptor struct;
		private List<VarDescriptor> members;

		/**
		 * Creates a new struct descriptor.
		 * Add members to the struct with addMember()
		 * @param struct The descriptor for the struct.
		 */
		public VarDescriptorMember(VarDescriptor struct) {
			this.struct = struct;
			this.members = new ArrayList<VarDescriptor>();
		}

		/**
		 * Sets the struct descriptor of this node.
		 * @param child
		 */
		public void setStruct(VarDescriptor struct) {
			this.struct = struct;
		}

		/**
		 * Returns the struct descriptor of this node.
		 * @return The struct descriptor of this node.
		 */
		public VarDescriptor getStruct() {
			return struct;
		}

		/**
		 * Add a member to the list of members.
		 * @param member
		 */
		public void addMember(VarDescriptor member) {
			this.members.add(member);
		}

		/**
		 * Returns the descriptions of this node.
		 * The returned descriptions will look like this:
		 *   struct's_descriptions "." members
		 * @return The descriptions.
		 */
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

		/**
		 * Returns the declaration of this node.
		 * The returned declaration will look like this:
		 *   struct's_declaration
		 * @return The declaration.
		 */
		public String extractDeclaration() {
			return struct.extractDeclaration();
		}

	}

	/**
	 * Describes a channel and its contents.
	 * The returned descriptions will look like this:
	 *   child's_descriptions ".m" 0..length
	 * The returned declaration will look like this:
	 *   child's_declaration
	 */
	public class VarDescriptorChannel extends VarDescriptor {
		private VarDescriptor child;
		private int length;

		public VarDescriptorChannel(VarDescriptor child, int length) {
			this.child = child;
			this.length = length;
		}

		/**
		 * Returns the size of the channel being described.
		 * @return The size of the channel being described.
		 */
		public int getLength() {
			return length;
		}

		/**
		 * Sets the size of the channel being described.
		 * @param length The new length of the channel being described.
		 */
		public void setLength(int length) {
			this.length = length;
		}

		/**
		 * Sets the child of this node.
		 * @param child
		 */
		public void setChild(VarDescriptor child) {
			this.child = child;
		}

		/**
		 * Returns the child of this node.
		 * @return The child of this node.
		 */
		public VarDescriptor getChild() {
			return child;
		}

		/**
		 * Returns the descriptions of this node.
		 * The returned descriptions will look like this:
		 *   child's_descriptions ".m" 0..length
		 * @return The descriptions.
		 */
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

		/**
		 * Returns the declaration of this node.
		 * The returned declaration will look like this:
		 *   child's_declaration
		 * @return The declaration.
		 */
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

		/**
		 * Create a new struct generator.
		 * @param name The name of the struct to be generated.
		 */
		public CStruct(String name) {
			s = new StringWriter();
			this.name = name;
			s.appendLine("typedef struct ",name," {");
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
		}

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

	/**
	 * Describes a row in the dependency matrix. It holds the dependency for
	 * both read and write operations per transition.
	 */
	public class DepRow {
		private ArrayList<Integer> reads;
		private ArrayList<Integer> writes;

		/**
		 * Creates a new dependency row.
		 * @param size The size of the row, the number of variables to keep
		 * track of
		 */
		DepRow(int size) {
			reads = new ArrayList(size);
			writes = new ArrayList(size);
			for(int i=0;i<size;++i) {
				reads.add(0);
				writes.add(0);
			}
		}

		/**
		 * Increase the number of reads of the specified dependency by one.
		 * @param dep The dependency to increase.
		 */
		public void incRead(int dep) {
			reads.set(dep, reads.get(dep)+1);
		}

		/**
		 * Increase the number of writes of the specified dependency by one.
		 * @param dep The dependency to increase.
		 */
		public void incWrite(int dep) {
			writes.set(dep, writes.get(dep)+1);
		}

		/**
		 * Decrease the number of reads of the specified dependency by one.
		 * @param dep The dependency to decrease.
		 */
		public void decrRead(int dep) {
			reads.set(dep, reads.get(dep)-1);
		}

		/**
		 * Decrease the number of writes of the specified dependency by one.
		 * @param dep The dependency to decrease.
		 */
		public void decrWrite(int dep) {
			writes.set(dep, writes.get(dep)-1);
		}

		/**
		 * Returns the number of reads of the given dependency.
		 * @param state The dependency of which the number of reads is wanted.
		 * @return The number of reads of the given dependency.
		 */
		public int getRead(int state) {
			return reads.get(state);
		}

		/**
		 * Returns the number of writes of the given dependency.
		 * @param state The dependency of which the number of writes is wanted.
		 * @return The number of writes of the given dependency.
		 */
		public int getWrite(int state) {
			return writes.get(state);
		}

		/**
		 * Returns whether the number of reads of the given dependency is
		 * higher than 0.
		 * @param state The dependency of which the number of reads is wanted.
		 * @return 1: #reads>0, 0: #reads<=0
		 */
		public int getReadB(int state) {
			return reads.get(state)>0?1:0;
		}

		/**
		 * Returns whether the number of writes of the given dependency is
		 * higher than 0.
		 * @param state The dependency of which the number of writes is wanted.
		 * @return 1: #writes>0, 0: #writes<=0
		 */
		public int getWriteB(int state) {
			return writes.get(state)>0?1:0;
		}

		/**
		 * Returns the size of the row.
		 * @return The size of the row.
		 */
		public int getSize() {
			return reads.size();
		}

	}

	/**
	 *
	 */
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

		/**
		 * Increase the number of reads of the specified dependency by one.
		 * @param dep The dependency to increase.
		 */
		public void incRead(int transition, int dep) {
			DepRow dr = dep_matrix.get(transition);
			assert(dr!=null);
			dr.incRead(dep);
		}

		/**
		 * Increase the number of writes of the specified dependency by one.
		 * @param dep The dependency to increase.
		 */
		public void incWrite(int transition, int dep) {
			DepRow dr = dep_matrix.get(transition);
			assert(dr!=null);
			dr.incWrite(dep);
		}

		/**
		 * Decrease the number of reads of the specified dependency by one.
		 * @param dep The dependency to decrease.
		 */
		public void decrRead(int transition, int dep) {
			DepRow dr = dep_matrix.get(transition);
			assert(dr!=null);
			dr.decrRead(dep);
		}

		/**
		 * Decrease the number of writes of the specified dependency by one.
		 * @param dep The dependency to decrease.
		 */
		public void decrWrite(int transition, int dep) {
			DepRow dr = dep_matrix.get(transition);
			assert(dr!=null);
			dr.decrWrite(dep);
		}

		/**
		 * Ensure the dependency matrix has the specified number of rows.
		 * Existing rows will not be modified.
		 * If the requested size is not higher than the current size, nothing
		 * is done.
		 * @param size The requested new size of the dependency matrix.
		 * @ensures getRows()>=size
		 */
		public void ensureSize(int size) {
			for(int i=dep_matrix.size();i<size;++i) {
				dep_matrix.add(i,new DepRow(row_length));
			}
		}

		/**
		 * Returns the number of rows in the dependency matrix.
		 * @return The number of rows in the dependency matrix.
		 */
		public int getRows() {
			return dep_matrix.size();
		}

		/**
		 * Returns a dependency row in the dependency matrix.
		 * @param trans The index of the dependency row to return.
		 * @return The dependency row at the requested index.
		 */
		public DepRow getRow(int trans) {
			return dep_matrix.get(trans);
		}
	}

	/**
	 * A class containing three variables.
	 * It is used to describe a channel send operation.
	 */
	public class SendAction {

		/// The channel send action.
		public ChannelSendAction csa;

		/// The transition the channel send action is in.
		public Transition t;

		/// The position the channel send action is in.
		public Proctype p;

		/**
		 * Create a new SendAction using the specified variables.
		 */
		public SendAction(ChannelSendAction csa, Transition t, Proctype p) {
			this.csa = csa;
			this.t = t;
			this.p = p;
		}

	}

	/**
	 * A class containing three variables.
	 * It is used to describe a channel read operation.
	 */
	public class ReadAction {
		/// The channel read action.
		public ChannelReadAction cra;

		/// The transition the channel read action is in.
		public Transition t;

		/// The position the channel read action is in.
		public Proctype p;

		/**
		 * Create a new ReadAction using the specified variables.
		 */
		public ReadAction(ChannelReadAction cra, Transition t, Proctype p) {
			this.cra = cra;
			this.t = t;
			this.p = p;
		}

	}

	/**
	 * The ReadersAndWriters class holds a list of SendActions and
	 * ReadActions.
	 */
	public class ReadersAndWriters {
		public List<SendAction> sendActions;
		public List<ReadAction> readActions;

		/**
		 * Create a new ReadersAndWriters.
		 * The two lists will be initialised using an empty list.
		 */
		public ReadersAndWriters() {
			sendActions = new ArrayList<SendAction>();
			readActions = new ArrayList<ReadAction>();
		}

	}

	public class AtomicState {
		public State s;
		public Proctype p;

		public AtomicState(State s, Proctype p) {
			this.s = s;
			this.p = p;
		}
	}

	/// The size of one element in the state struct in bytes.
	public static final int STATE_ELEMENT_SIZE = 4;

	public static final String C_STATE_T = "state_t";
	public static final String C_STATE_GLOBALS_T = "state_globals_t";
	public static final String C_STATE_GLOBALS = "globals";
	public static final String C_STATE_PROC_COUNTER = "pc";
	public static final String C_STATE_SIZE = "state_size";
	public static final String C_STATE_INITIAL = "initial";
	public static final String C_STATE_TMP = "tmp";
	public static final String C_STATE_PRIORITY = "prioritiseProcess";
	public static final String C_PRIORITY = C_STATE_GLOBALS+"."+C_STATE_PRIORITY;
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

	// For each channel, a list of read actions and send actions is kept
	// to later handle these separately
	private HashMap<ChannelVariable,ReadersAndWriters> channels;

	// The variables in the state struct
	private List<Variable> state_vector_var;

	// Textual description of the state vector, per integer
	private List<String> state_vector_desc;

	// List of processes
	private List<Proctype> procs;

	// Dependency Matrix
	private DepMatrix dep_matrix;

	// Atomic states - of these loss of atomicity will be generated
	private List<AtomicState> atomicStates;

	// The specification of which code is to be generated,
	// initialised by constructor
	private final Specification spec;

	// Cached result
	private String c_code;

	// The size of the state vector in bytes
	private int state_size;

	// The CStruct state vector
	private CStruct state;

	// The transition ID of the transition that handles loss of atomicity
	int loss_transition_id;

	// Set to true when all transitions have been parsed.
	// After this, channels and loss of atomicity is handled.
	boolean seenItAll = false;

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

		state_var_offset = new HashMap<Variable,Integer>();
		state_var_desc = new HashMap<Variable,String>();
		state_proc_offset = new HashMap<Proctype,Integer>();
		state = null;
		state_vector_desc = new ArrayList<String>();
		state_vector_var = new ArrayList<Variable>();
		procs = new ArrayList<Proctype>();
		atomicStates = new ArrayList<AtomicState>();

		channels = new HashMap<ChannelVariable,ReadersAndWriters>();
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
		int t = generateTransitions(w);

		// Generate code for all the transitions
		w.appendLine("");
		generateTransitionsAll(w,t);

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

	/**
	 * For the specified variable, generate a custom struct typedef and print
	 * to the StringWriter.
	 * ChannelVariable's are also remembered, for later use. In particular for
	 * rendezvous.
	 * @param w The StringWriter to which the code is written.
	 * @param var The variable of which a custom typedef is requested.
	 */
	private void generateCustomStruct(StringWriter w, Variable var) {

		// Handle the ChannelType variable type
		if(var.getType() instanceof ChannelType) {

			// Create a new C struct generator
			CStruct struct = new CStruct(wrapNameForChannel(var.getName()));

			ChannelVariable cv = (ChannelVariable)var;
			ChannelType ct = cv.getType();
			VariableStore vs = ct.getVariableStore();

			// Only generate members for non-rendezvous channels
			if (ct.getBufferSize() > 0) {
				int j=0;
				for(Variable v: vs.getVariables()) {
					TypeDesc td = getCTypeOfVar(v);
					struct.addMember(td,"m"+j);
					++j;
				}
			}

			// Remember this channel variable, to keep track of
			channels.put(cv,new ReadersAndWriters());

			// Write the typedef to w
			w.appendLine(struct.getCCode());
		}

	}

	/**
	 * Parse all globals and all local variables of processes to generate
	 * custom struct typedefs where needed. Calls generateCustomStruct() for
	 * variable that need it.
	 * @param w
	 */
	private void generateCustomStructs(StringWriter w) {

		// Globals
		VariableStore globals = spec.getVariableStore();
		List<Variable> vars = globals.getVariables();
		for(Variable var: vars) {
			generateCustomStruct(w,var);
		}

		// Locals
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
		say("== Globals");
		CStruct sg = new CStruct(C_STATE_GLOBALS_T);

		// Add priority process
		{
			sg.addMember(C_TYPE_INT32, C_STATE_PRIORITY);
			current_offset+=STATE_ELEMENT_SIZE;
			state_vector_desc.add(C_PRIORITY);
			state_vector_var.add(null);
		}

		// Globals: add globals to the global state struct
		VariableStore globals = spec.getVariableStore();
		List<Variable> vars = globals.getVariables();
		for(Variable var: vars) {

			// Add global to the global state struct and fix the offset
			current_offset = handleVariable(sg,var,C_STATE_GLOBALS+".",current_offset);

		}

		// Add global state struct to main state struct
		// Add it even if there are no global variables, since priorityProcess
		// is a 'global'
		state_members.add(sg);
		state.addMember(C_STATE_GLOBALS_T, C_STATE_GLOBALS);

		// Processes:
		say("== Processes");
		Iterator<Proctype> it = spec.iterator();
		for(;it.hasNext();) {
			Proctype p = it.next();

			procs.add(p);

			// Process' name
			String name = wrapName(p.getName());

			// Initialise process state struct and add to main state struct
			say("[Proc] " + name + " @" + current_offset);
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

		// Generate forward declaration of functions
		w.appendLine("");
		w.appendLine("extern \"C\" int spinja_get_successor_all( void* model, state_t *in, void (*callback)(void* arg, transition_info_t *transition_info, state_t *out), void *arg );");
		w.appendLine("extern \"C\" int spinja_get_successor( void* model, int t, state_t *in, void (*callback)(void* arg, transition_info_t *transition_info, state_t *out), void *arg );");

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
							generateIntExpression(w, null, e,-1);
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
		w.appendLine("to->",C_PRIORITY,".var = -1;");
		w.outdent();
		w.appendLine("}");

		// Generate state printer
		w.appendLine("void print_state(",C_STATE_T,"* s) {");
		w.indent();
		w.appendLine("if(!s) return;");
		for(int i=0; i<state_size; i+=4) {
			String v = getStateDescription(i);
			Variable var = state_vector_var.get(i/4);
			if(var==null) {
				w.appendLine("printf(\"",v,": %i\\n\",s->",v,".var);");
			} else if(var instanceof ChannelVariable) {
				ChannelVariable cv = (ChannelVariable)var;
				if(cv.getType().getBufferSize()==0) {
					w.appendLine("printf(\"[CH] ",v,": rendezvous\\n\");");
				} else {
					w.appendLine("printf(\"[CH] ",v,": nextRead=%i, filled=%i\\n\",s->",v,".nextRead,s->",v,".filled);");
				}
			} else if(var.getArraySize()>1) {
				for(int j=0; i<state_size && j<var.getArraySize(); ++j, i+=4) {
					w.appendLine("printf(\"",v,": %i\\n\",s->",v,"[",j,"].var);");
				}
			} else {
				w.appendLine("printf(\"",v,": %i\\n\",s->",v,".var);");
				//w.appendLine("printf(\"",v,": %i\\n\",s->",v,".var);");
			}
		}
		w.outdent();
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
				throw new AssertionError("ERROR: Unable to handle: " + v.getName());
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
				throw new AssertionError("ERROR: Unable to handle: " + v.getName());
		}
	}

	// Helper functionality for debugging
	int say_indent = 0;
	private void say(String s) {
//		for(int n=say_indent; n-->0;) {
//			System.out.print("  ");
//		}
//		System.out.println(s);
	}

	private void generateTransitionsAll(StringWriter w, int transitions) {
		w.appendLine("extern \"C\" int spinja_get_successor_all( void* model, state_t *in, void (*callback)(void* arg, transition_info_t *transition_info, state_t *out), void *arg) {");
		w.indent();

		w.appendLine("int t=",transitions,",emitted=0;");
		w.appendLine("for(;t--;) {");
		w.indent();
		w.appendLine("emitted += spinja_get_successor(model,t,in,callback,arg);");
		w.outdent();
		w.appendLine("}");
		w.appendLine("return emitted;");
		w.outdent();
		w.appendLine("}");
		w.appendLine();
	}

	/**
	 * Generates the state transitions.
	 * This calls generateTransitionsFromState() for every state in every process.
	 * @param w The StringWriter to which the code is written.
	 */
	private int generateTransitions(StringWriter w) {

		say("== Automata");
		++say_indent;

		// Generate the start: initialise tmp
		w.appendLine("extern \"C\" int spinja_get_successor( void* model, int t, state_t *in, void (*callback)(void* arg, transition_info_t *transition_info, state_t *out), void *arg) {");
		w.indent();

		w.appendLine("transition_info_t transition_info = { NULL, t };");
		w.appendLine("(void)model; // ignore model");
		w.appendLine("int states_emitted = 0;");
		w.appendLine("register int pos;");
		w.appendLine(C_STATE_T," ",C_STATE_TMP,";");
		w.appendLine("memcpy(&",C_STATE_TMP,",in,sizeof(",C_STATE_T,"));");
		w.appendLine();

		// Jump to switch state
		w.appendLine();
		w.appendLine("goto switch_state;");
		w.appendLine();

		// Init dependency matrix
		dep_matrix = new DepMatrix(1,state_size/4);

		// Current number of transitions
		int trans = 0;

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
		seenItAll = true;

		// Generate the rendezvous transitions
		for(Map.Entry<ChannelVariable,ReadersAndWriters> e: channels.entrySet()) {
			ChannelVariable cv = e.getKey();
			ReadersAndWriters raw = e.getValue();
			for(SendAction sa: raw.sendActions) {
				for(ReadAction ra: raw.readActions) {
					//if(state_proc_offset.get(sa.p) != state_proc_offset.get(ra.p)) {
						dep_matrix.ensureSize(trans+1);
						w.appendLine("l",trans,": // ",sa.p.getName(),"[",state_proc_offset.get(sa.p),"] --> ",ra.p.getName(),"[",state_proc_offset.get(ra.p),"]");
						generateRendezVousAction(w,sa,ra,trans);
						++trans;
					//}
				}

			}

		}

		// Create loss of atomicity transition.
		// This is used when a process blocks inside an atomic transition.
		dep_matrix.ensureSize(trans+1);
		loss_transition_id = trans;
		w.appendLine("l",loss_transition_id,": // loss of transitions");
		for(AtomicState as: atomicStates) {
			State s = as.s;
			Proctype process = as.p;
			assert(s.isInAtomic());
			w.appendPrefix();
			//w.append("if( true /* s= ").append(s.getStateId()).append(" */");
			w.append("if( ").append(C_STATE_TMP).append(".").append(wrapName(process.getName())).append(".").append(C_STATE_PROC_COUNTER).append(".var == ").append(s.getStateId());
			w.appendPostfix();
			w.appendPrefix();
			w.append("&&( ").append(C_STATE_TMP).append(".").append(C_PRIORITY).append(".var == ").append(state_proc_offset.get(process)).append(" )");
			for(Transition ot: s.output) {
				w.appendPostfix();
				w.appendPrefix();
				w.append("&&!(");
				generateTransitionGuard(w,process,ot,trans);
				w.append(")");
			}
			w.append(" ) {");
			w.appendPostfix();
			w.indent();
			w.appendLine(C_STATE_TMP,".",C_PRIORITY,".var = ",-1,";");
			w.appendLine("printf(\"[",state_proc_offset.get(process),"] @%i BIG IF - handled %i losses of atomicity so far\\n\",__LINE__,++n_losses);");
			w.appendLine("callback(arg,&transition_info,&tmp);");
			w.appendLine("return ",1,";");
			w.outdent();
			w.appendLine("}");
		}
		w.appendLine("return 0;");
		++trans;

		// From the switch state we jump to the correct transition,
		// according to the transition group 't'
		w.appendLine("switch_state:");

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

		return trans;

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

		if(state==null) {
			throw new AssertionError("State is NULL");
		}

		say(state.toString());

		// Check if it is an ending state
		if(state.sizeOut()==0) { // FIXME: Is this the correct prerequisite for THE end state of a process?

			dep_matrix.ensureSize(trans+1);

			// In the case of an ending state, generate a transition only
			// changing the process counter to -1.
			w.appendLine("l",trans,": // ",process.getName(),"[",state_proc_offset.get(process),"] - end transition");
			w.appendLine("if( ",C_STATE_TMP,".",wrapName(process.getName()),".",C_STATE_PROC_COUNTER,".var == ",state.getStateId());
			if(process.getID()<procs.size()-1) {
				w.appendLine("#ifdef SPINDEATHMODE ");
				w.appendLine(" && ",C_STATE_TMP,".",wrapName(procs.get(process.getID()+1).getName()),".",C_STATE_PROC_COUNTER,".var == -1");
				w.appendLine("#endif");
			}
			w.appendLine(") {");
			w.indent();
			w.appendLine("",C_STATE_TMP,".",wrapName(process.getName()),".",C_STATE_PROC_COUNTER,".var = ",-1,";");
			w.appendLine("callback(arg,&transition_info,&tmp);");
			w.appendLine("return 1;");
			w.outdent();
			w.appendLine("}");
			w.appendLine("return 0;");

			// Dependency matrix: process counter
			dep_matrix.incWrite(trans, state_proc_offset.get(process)/4);
			dep_matrix.incRead(trans, state_proc_offset.get(process)/4);

			// Keep track of the current transition ID
			++trans;

		} else {

			// In the normal case, generate a transition changing the process
			// counter to the next state and any actions the transition does.
			++say_indent;
			//int outs = 0;

			// If this is an atomic state, add it to the list
			if(state.isInAtomic()) {
				atomicStates.add(new AtomicState(state,process));
			}

			if(state.output==null) {
				throw new AssertionError("State's output list is NULL");
			}

			for(Transition t: state.output) {

				// Generate transition
				trans = generateStateTransition(w,process,t,trans);

			}
			--say_indent;
		}

		// Return the next free transition ID
		return trans;

	}

	public int generateStateTransition(StringWriter w, Proctype process, Transition t, int trans) {
		// Checks
		if(t==null) {
			throw new AssertionError("State transition is NULL");
		}

		// DO NOT actionise RENDEZVOUS channel send/read
		// These will be remembered and handled later separately
		{
			Action a = null;
			if(t.getActionCount()>0) {
				a = t.getAction(0);
			}
			if(a!= null && a instanceof ChannelSendAction) {
				ChannelSendAction csa = (ChannelSendAction)a;
				ChannelVariable var = (ChannelVariable)csa.getVariable();
				if(var.getType().getBufferSize()==0) {

					// Remember this rendezvous send action for later...
					ReadersAndWriters raw = channels.get(var);
					if(raw==null) {
						throw new AssertionError("Channel not found in list of channels!");
					}
					raw.sendActions.add(new SendAction(csa,t,process));

					// ...and go to next transition.
					return trans;
				}
			} else if(a!= null && a instanceof ChannelReadAction) {
				ChannelReadAction cra = (ChannelReadAction)a;
				ChannelVariable var = (ChannelVariable)cra.getVariable();
				if(var.getType().getBufferSize()==0) {

					// Remember this rendezvous send action for later...
					ReadersAndWriters raw = channels.get(var);
					if(raw==null) {
						throw new AssertionError("Channel not found in list of channels!");
					}
					raw.readActions.add(new ReadAction(cra,t,process));

					// ...and go to next transition.
					return trans;
				}
			}
		}

		// Ensure the dependency matrix is of adequate size
		dep_matrix.ensureSize(trans+1);

		say("Handling trans: " + t.getClass().getName());

		// Guard: process counter
		w.appendLine("l",trans,": // ",process.getName(),"[",state_proc_offset.get(process),"] - ",t.getFrom().isInAtomic()?"atomic":"normal"," transition");
		w.appendLine("if( ",C_STATE_TMP,".",wrapName(process.getName()),".",C_STATE_PROC_COUNTER,".var == ",t.getFrom().getStateId());
		w.appendLine("&&( ",C_STATE_TMP,".",C_PRIORITY,".var == ",state_proc_offset.get(process)," || ",C_STATE_TMP,".",C_PRIORITY,".var<0"," ) ) {");
		w.indent();

		// Generate action guard using the first action in the list
		// of the transition
		w.appendPrefix();
		w.append("if( ");
		generateTransitionGuard(w,process,t,trans);
		w.append(" ) {");
		w.appendPostfix();
		w.indent();

		// If this is an ElseTransition, all other transitions should not be
		// enabled, so make guards for this
		w.appendPrefix();
		w.append("if( true");
		if(t instanceof ElseTransition) {
			ElseTransition et = (ElseTransition)t;
			for(Transition ot: t.getFrom().output) {
				if(ot!=et) {
					w.appendPostfix();
					w.appendPrefix();
					w.append("&&!(");
					generateTransitionGuard(w,process,ot,trans);
					w.append(")");
				}
			}
		}
		w.append(" ) {");
		w.appendPostfix();
		w.indent();

		// If the target state is null and this is not the last process
		// in the list: produce code that optionally handles death of
		// processes like Spin/SpinJa does: a process can only terminate
		// when all processes that are higher in the list are dead.
		if(t.getTo()==null && process.getID()<procs.size()-1) {
			w.appendLine("#ifdef SPINDEATHMODE ");
			w.appendLine("if(",C_STATE_TMP,".",wrapName(procs.get(process.getID()+1).getName()),".",C_STATE_PROC_COUNTER,".var != -1) {");
			w.indent();
			w.appendLine("return 0;");
			w.outdent();
			w.appendLine("}");
			w.appendLine("#endif");
		}

		// Change process counter to the next state.
		// For end transitions, the PC is changed to -1.
		w.appendLine("",C_STATE_TMP,".",
				wrapName(process.getName()),".",
				C_STATE_PROC_COUNTER,".var = ",
				t.getTo()==null?-1:t.getTo().getStateId(),";"
				);

		// Generate actions for this transition
		generateStateTransitionActions(w,process,t,trans);

		// If this transition is atomic
		if(t.getTo()!=null && t.getTo().isInAtomic()) {

			// Claim priority when taking this transition. It is
			// possible this process had already priority, so nothing
			// changes.
			w.appendLine(C_STATE_TMP,".",C_PRIORITY,".var = ",state_proc_offset.get(process),";");

		// If this transition is not atomic
		} else {

			// Make sure no process has priority. This transition was
			// either executed while having priority and it is now given
			// up, or no process had priority and this remains the same.
			w.appendLine(C_STATE_TMP,".",C_PRIORITY,".var = ",-1,";");

		}

			// Generate the callback and the rest
		w.appendLine("callback(arg,&transition_info,&tmp);");
		w.appendLine("return ",1,";");

		w.outdent();
		w.appendLine("} // else");

		w.outdent();
		w.appendLine("} // guard");

		w.outdent();
		w.appendLine("} // state");

		w.appendLine("return 0;");

		// Dependency matrix: process counter
		dep_matrix.incWrite(trans, state_proc_offset.get(process)/4);
		dep_matrix.incRead(trans, state_proc_offset.get(process)/4);
		return trans+1;
	}

	void generateTransitionGuard(StringWriter w, Proctype process, Transition t, int trans) {
		try {
			Action a = null;
			if(t.getActionCount()>0) {
				a = t.getAction(0);
			}
			if(a!= null && a.getEnabledExpression()!=null) {
				generateEnabledExpression(w,process,a,t,trans);
			} else {
				w.append("true");
			}
		} catch(ParseException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Generate the actions for the specified transition. The transition should
	 * be in the specified process.
	 * @param w The StringWriter to which the code is written.
	 * @param process The transition should be in this process.
	 * @param t The transition of which to generate the associated actions.
	 */
	private void generateStateTransitionActions(StringWriter w, Proctype process, Transition t, int trans) {

		// Handle all the action in the transition
		Iterator<Action> it = t.iterator();
		while(it.hasNext()) {
			Action a = it.next();
			try {

				// Handle the action
				generateAction(w,process,a,t,trans);

			// Handle parse exceptions
			} catch(ParseException e) {
				e.printStackTrace();
				System.exit(0);
			}
		}

	}

	/**
	 * Generates the dependency matrix given the specified dependency matrix.
	 * @param w The StringWriter to which the code is written.
	 * @param dm The dependency matrix to generate
	 */
	private void generateDepMatrix(StringWriter w, DepMatrix dm) {
		w.append("int transition_dependency[][2][").append(state_size/4).appendLine("] = {");
		w.appendLine("\t// { ... read ...}, { ... write ...}");
		int t=0;

		// Iterate over all the rows
		for(;;) {
			w.append("\t{{");
			DepRow dr = dm.getRow(t);
			int s=0;

			// Insert all read dependencies of the current row
			for(;;) {
				w.append(dr.getReadB(s));
				//w.append(1);

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
				w.append(dr.getWriteB(s));
				//w.append(1);
				if(++s>=dr.getSize()) {
					break;
				}
				w.append(",");
			}

			// End the row
			w.append("}}");

			// If this was the last row
			if(++t>=dm.getRows()) {
				w.appendLine("  // ",t);
				break;
			}
			w.appendLine(", // ",t);
		}

		// Close array
		w.appendLine("};");

		// Function to access the dependency matrix
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

	/**
	 * Generates the code for the given expression. The result is a boolean
	 * expression.
	 * @param w The StringWriter to which the code is written.
	 * @param process The process in which the specified expression is in.
	 * @param e The expression of which the code will be generated.
	 * @throws ParseException
	 */
	private void generateBoolExpression(StringWriter w, Proctype process, Expression e, int trans) throws ParseException {
		if(e instanceof Identifier) {
			w.append("(");
			generateIntExpression(w,process,e,trans);
			w.append(" != 0 )");
		} else if(e instanceof AritmicExpression) {
			AritmicExpression ae = (AritmicExpression)e;
			Expression ex1 = ae.getExpr1();
			Expression ex2 = ae.getExpr2();
			Expression ex3 = ae.getExpr3();
			if (ex2 == null) {
				w.append("(").append(ae.getToken().image);
				generateIntExpression(w,process,ex1,trans);
				w.append(" != 0)");
			} else if (ex3 == null) {
				w.append("(");
				generateIntExpression(w,process,ex1,trans);
				w.append(" ").append(ae.getToken().image).append(" ");
				generateIntExpression(w,process,ex2,trans);
				w.append(" != 0)");
			} else { // Can only happen with the x?1:0 expression
				w.append("(");
				generateBoolExpression(w,process,ex1,trans);
				w.append(" ? ");
				generateBoolExpression(w,process,ex2,trans);
				w.append(" : ");
				generateBoolExpression(w,process,ex3,trans);
				w.append(")");
			}
		} else if(e instanceof BooleanExpression) {
			BooleanExpression be = (BooleanExpression)e;
			Expression ex1 = be.getExpr1();
			Expression ex2 = be.getExpr2();
			if (ex2 == null) {
				w.append("(").append(be.getToken().image);
				generateBoolExpression(w,process,ex1,trans);
				w.append(")");
			} else {
				w.append("(");
				generateBoolExpression(w,process,ex1,trans);
				w.append(" ").append(be.getToken().image).append(" ");
				generateBoolExpression(w,process,ex2,trans);
				w.append(")");
			}
		} else if(e instanceof ChannelLengthExpression) {
			throw new ParseException("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else if(e instanceof ChannelOperation) {
			throw new ParseException("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else if(e instanceof CompareExpression) {
			CompareExpression ce = (CompareExpression)e;
			w.append("(");
			generateIntExpression(w,process,ce.getExpr1(),trans);
			w.append(" ").append(ce.getToken().image).append(" ");
			generateIntExpression(w,process,ce.getExpr2(),trans);
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

	/**
	 * Generates the code for the given expression.
	 * @param w The StringWriter to which the code is written.
	 * @param process The process in which the specified expression is in.
	 * @param e The expression of which the code will be generated.
	 * @throws ParseException
	 */
	private void generateIntExpression(StringWriter w, Proctype process, Expression e, int trans) throws ParseException {
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
					generateIntExpression(w,process,arrayExpr,trans);
					w.append("].var");

					try {
						int i = arrayExpr.getConstantValue();
						if(trans<dep_matrix.getRows()) dep_matrix.incRead(trans, state_var_offset.get(var)/4+i);
					} catch(ParseException pe) {
						for(int i=0; i<var.getArraySize(); ++i) {
							if(trans<dep_matrix.getRows()) dep_matrix.incRead(trans, state_var_offset.get(var)/4+i);
						}
					}
				} else {
					w.append("tmp.");
					w.append(state_var_desc.get(var));
					w.append("[0].var");
					if(trans<dep_matrix.getRows()) dep_matrix.incRead(trans, state_var_offset.get(var)/4);
				}
			} else {
				w.append("tmp.");
				w.append(state_var_desc.get(var));
				w.append(".var");
				if(trans<dep_matrix.getRows()) dep_matrix.incRead(trans, state_var_offset.get(var)/4);
			}
		} else if(e instanceof AritmicExpression) {
			AritmicExpression ae = (AritmicExpression)e;
			Expression ex1 = ae.getExpr1();
			Expression ex2 = ae.getExpr2();
			Expression ex3 = ae.getExpr3();
			if (ex2 == null) {
				w.append("(").append(ae.getToken().image);
				generateIntExpression(w, process, ex1,trans);
				w.append(")");
			} else if (ex3 == null) {
				if (ae.getToken().image.equals("%")) {
					// Modulo takes a special notation to make sure that it
					// returns a positive value
					w.append("abs(");
					generateIntExpression(w,process,ex1,trans);
					w.append(" % ");
					generateIntExpression(w,process,ex2,trans);
					w.append(")");
				} else {

					w.append("(");
					generateIntExpression(w,process,ex1,trans);
					w.append(" ").append(ae.getToken().image).append(" ");
					generateIntExpression(w,process,ex2,trans);
					w.append(")");
				}
			} else {
				w.append("(");
				generateBoolExpression(w,process,ex1,trans);
				w.append(" ? ");
				generateIntExpression(w,process,ex2,trans);
				w.append(" : ");
				generateIntExpression(w,process,ex3,trans);
				w.append(")");
			}
		} else if(e instanceof BooleanExpression) {
			BooleanExpression be = (BooleanExpression)e;
			Expression ex1 = be.getExpr1();
			Expression ex2 = be.getExpr2();
			if (ex2 == null) {
				w.append("(").append(be.getToken().image);
				generateIntExpression(w,process,ex1,trans);
				w.append( " ? 1 : 0)");
			} else {
				w.append("(");
				generateIntExpression(w,process,ex1,trans);
				w.append(" ").append(be.getToken().image).append(" ");
				generateBoolExpression(w,process,ex2,trans);
				w.append(" ? 1 : 0)");
			}
		} else if(e instanceof ChannelLengthExpression) {
			throw new ParseException("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else if(e instanceof ChannelOperation) {
			throw new ParseException("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else if(e instanceof CompareExpression) {
			CompareExpression ce = (CompareExpression)e;
			w.append("(");
			generateIntExpression(w,process,ce.getExpr1(),trans);
			w.append(" ").append(ce.getToken().image).append(" ");
			generateIntExpression(w,process,ce.getExpr2(),trans);
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
	private void generateAction(StringWriter w, Proctype process, Action a, Transition t, int trans) throws ParseException {

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
						generateIntExpression(w,process,id,trans);
						w.append(" = ").append(value & id.getVariable().getType().getMaskInt()).append(";");
						w.appendPostfix();
					} catch (ParseException ex) {
						// Could not get Constant value
						w.appendPrefix();
						generateIntExpression(w,process,id,trans);
						w.append(" = ");
						generateIntExpression(w,process,as.getExpr(),trans);
						w.append((mask == null ? "" : " & " + mask));
						w.append(";");
						w.appendPostfix();
					}
					break;
				case PromelaConstants.INCR:
					if (mask == null) {
						w.appendPrefix();
						generateIntExpression(w,process,id,trans);
						w.append("++;");
						w.appendPostfix();
					} else {
						w.appendPrefix();
						generateIntExpression(w,process,id,trans);
						w.append(" = (");
						generateIntExpression(w,process,id,trans);
						w.append(" + 1) & ").append(mask).append(";");
						w.appendPostfix();
					}
					break;
				case PromelaConstants.DECR:
					if (mask == null) {
						w.appendPrefix();
						generateIntExpression(w,process,id,trans);
						w.append("--;");
						w.appendPostfix();
					} else {
						w.appendPrefix();
						generateIntExpression(w,process,id,trans);
						w.appendLine(" = (");
						generateIntExpression(w,process,id,trans);
						w.append(" - 1) & ");
						w.append(mask);
						w.append(";");
						w.appendPostfix();
					}
					break;
				default:
					throw new ParseException("unknown assignment type");
			}
			handleAssignDependency(process, trans, id);

		// Handle assert action
		} else if(a instanceof AssertAction) {
			AssertAction as = (AssertAction)a;
			Expression e = as.getExpr();

			w.append("if(!");
			generateBoolExpression(w,process,e,trans);
			w.appendLine(") {");
			w.indent();
			w.appendLine("printf(\"Assertion violated: ",as.getExpr().toString(), "\\n\");");
			w.appendLine("print_state(&tmp);");
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
				generateIntExpression(w,process,expr,trans);
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
				String access = C_STATE_TMP + "." + wrapNameForChannelDesc(state_var_desc.get(var));

				w.appendLine("pos = (" + access + ".nextRead + "+access+".filled) % "+var.getType().getBufferSize() + ";");
				String access_buffer = C_STATE_TMP + "." + wrapNameForChannelBuffer(state_var_desc.get(var)) + "[pos]";

				List<Expression> exprs = csa.getExprs();
				for (int i = 0; i < exprs.size(); i++) {
					final Expression expr = exprs.get(i);
					w.appendPrefix();
					w.append(access_buffer).append(".m").append(i).append(".var = ");
					generateIntExpression(w, process, expr,trans);
					w.append(";");
					w.appendPostfix();

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
				String access = C_STATE_TMP + "." + wrapNameForChannelDesc(state_var_desc.get(var));
				w.appendLine("pos = (" + access + ".nextRead + "+access+".filled) % "+var.getType().getBufferSize() + ";");
				String access_buffer = C_STATE_TMP + "." + wrapNameForChannelBuffer(state_var_desc.get(var)) + "[pos]";

				List<Expression> exprs = cra.getExprs();
				for (int i = 0; i < exprs.size(); i++) {
					final Expression expr = exprs.get(i);
					if (expr instanceof Identifier) {
						w.appendPrefix();
						generateIntExpression(w, process, expr,trans);
						w.append(" = ").append(access_buffer).append(".m").append(i).append(".var");
						w.append(";");
						w.appendPostfix();
					}
				}

				w.appendLine(access,".nextRead = (",access,".nextRead+1)%"+var.getType().getBufferSize()+";");
				w.appendLine("--",access, ".filled;");
			} else {
				throw new AssertionError("Trying to actionise rendezvous receive!");
			}

		// Handle not yet implemented action
		} else {
			throw new ParseException("LTSMinPrinter: Not yet implemented: "+a.getClass().getName());
		}

	}

	private void generateEnabledExpression(StringWriter w, Proctype process, Action a, Transition t, int trans) throws ParseException {
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
			generateBoolExpression(w,process,expr,trans);

		// Handle a channel send action
		} else if(a instanceof ChannelSendAction) {
			ChannelSendAction csa = (ChannelSendAction)a;
			ChannelVariable var = (ChannelVariable)csa.getVariable();
			if(var.getType().getBufferSize()>0) {

				String access = C_STATE_TMP + "." + wrapNameForChannelDesc(state_var_desc.get(var));

				w.append("(");
				w.append(access).append(".filled < ");
				w.append(var.getType().getBufferSize()).append(")");

			} else if(seenItAll) {
				ReadersAndWriters raw = channels.get(var);
				w.append("false");
				for(ReadAction ra: raw.readActions) {
					List<Expression> csa_exprs = csa.getExprs();
					List<Expression> cra_exprs = ra.cra.getExprs();
					w.appendPostfix();
					w.appendPrefix();
					w.append(" || ( (");
					w.append(C_STATE_TMP).append(".").append(wrapName(ra.p.getName())).append(".").append(C_STATE_PROC_COUNTER).append(".var == ").append(ra.t.getFrom().getStateId());
					w.append(" )");
					for (int i = 0; i < cra_exprs.size(); i++) {
						final Expression csa_expr = csa_exprs.get(i);
						final Expression cra_expr = cra_exprs.get(i);
						if (!(cra_expr instanceof Identifier)) {
							w.append(" && (");
							try {
								generateIntExpression(w, null, csa_expr,trans);
							} catch(ParseException e) {
								e.printStackTrace();
							}
							w.append(" == ");
							try {
								generateIntExpression(w, null, cra_expr,trans);
							} catch(ParseException e) {
								e.printStackTrace();
							}
							w.append(")");
						}
					}
					w.append(")");
				}
			} else {
				throw new AssertionError("Trying to actionise rendezvous send before all others!");
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
				w.append(access).append(".filled > 0");

				for (int i = 0; i < exprs.size(); i++) {
					final Expression expr = exprs.get(i);
					if (!(expr instanceof Identifier)) {
						w.append(" && (");
						w.append(access_buffer).append(".m").append(i).append(".var == ");
						generateIntExpression(w, process, expr,trans);
						w.append(")");
					}

				}

				w.append(")");
			} else if(seenItAll) {
				ReadersAndWriters raw = channels.get(var);
				w.append("false");
				for(SendAction sa: raw.sendActions) {
					List<Expression> csa_exprs = sa.csa.getExprs();
					List<Expression> cra_exprs = cra.getExprs();
					w.appendPostfix();
					w.appendPrefix();
					w.append(" || ( (");
					w.append(C_STATE_TMP).append(".").append(wrapName(sa.p.getName())).append(".").append(C_STATE_PROC_COUNTER).append(".var == ").append(sa.t.getFrom().getStateId());
					w.append(" )");
					for (int i = 0; i < cra_exprs.size(); i++) {
						final Expression csa_expr = csa_exprs.get(i);
						final Expression cra_expr = cra_exprs.get(i);
						if (!(cra_expr instanceof Identifier)) {
							w.append(" && (");
							try {
								generateIntExpression(w, null, csa_expr,trans);
							} catch(ParseException e) {
								e.printStackTrace();
							}
							w.append(" == ");
							try {
								generateIntExpression(w, null, cra_expr,trans);
							} catch(ParseException e) {
								e.printStackTrace();
							}
							w.append(")");
						}
					}
					w.append(")");
				}
			} else {
				throw new AssertionError("Trying to actionise rendezvous receive before all others!");
			}


		// Handle not yet implemented action
		} else {
			throw new ParseException("LTSMinPrinter: Not yet implemented: "+a.getClass().getName());
		}
	}

	private int insertVariable(CStruct sg, Variable var, String desc, String name, int current_offset) {

		if(!state_var_offset.containsKey(var)) {

			say("Adding VARIABLE TO OFFSET: " + var.getName() + " " + var.hashCode());
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

	/**
	 * Handle a variable by adding it to a CStruct with the correct type and
	 * putting it in the correct position in the state vector.
	 * @param sg The CStruct to add the variable to.
	 * @param var The variable to add.
	 * @param desc The description of the variable, to add to state_vector_desc.
	 * @param current_offset The offset at which the variable should be put.
	 * @param vd The VarDescriptor to use for the description and declaration
	 * of the variable.
	 * @return The next free offset position.
	 */
	private int handleVariable(CStruct sg, Variable var, String desc, String forcedName, int current_offset, VarDescriptor vd) {

		String name;
		if(forcedName!=null && !forcedName.equals("")) {
			name = forcedName;
		} else {
			name = var.getName();
		}

		say("HANDLING VAR: " + var.getType().getClass().getName());

		if(var.getType() instanceof ChannelType) {
			ChannelVariable cv = (ChannelVariable)var;
			ChannelType ct = cv.getType();
			VariableStore vs = ct.getVariableStore();

			vd = new VarDescriptorVar(wrapNameForChannelBuffer(name));
			if(var.getArraySize()>1) {
				vd = new VarDescriptorArray(vd,var.getArraySize());
			}
			vd = new VarDescriptorArray(vd,ct.getBufferSize());
			say(var.getName() + " has " + vs.getVariables().size());
			vd = new VarDescriptorChannel(vd,vs.getVariables().size());
			vd.setType(wrapNameForChannel(name));

			if (ct.getBufferSize() > 0) {

				say("Adding CHANNEL: " + var.getName() + " " + var.hashCode());
				current_offset = insertVariable(sg, var,desc,wrapNameForChannelDesc(name), current_offset);
				sg.addMember(C_TYPE_CHANNEL,wrapNameForChannelDesc(name));

				for(String s: vd.extractDescription()) {
					current_offset = insertVariable(sg, var, desc, s, current_offset);
				}
				sg.addMember(vd.getType(),vd.extractDeclaration());

			} else {

				say("Adding CHANNEL: " + var.getName() + " " + var.hashCode());
				current_offset = insertVariable(sg, var,desc,wrapNameForChannelDesc(name), current_offset);
				sg.addMember(C_TYPE_CHANNEL,wrapNameForChannelDesc(name));

			}

		} else if(var.getType() instanceof VariableType) {
			if(var.getType().getJavaName().equals("int")) {
				say("  " + name + " @" + current_offset + " (" + var.getType().getName() + ")");

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
				throw new AssertionError("ERROR: Unknown error trying to handle an integer");
			}
		} else {
			throw new AssertionError("ERROR: Unable to handle: " + var.getType().getName());
		}

		return current_offset;
	}

	/**
	 * Cleans the specified name so it is a valid C name.
	 * @param name The name to clean.
	 * @return The cleaned name.
	 */
	private String wrapName(String name) {
		return name;
	}

	/**
	 * Generates a channel name given the specified name.
	 * Also cleans it.
	 * @param name The name to use to generate a channel name.
	 * @return The generated, clean channel name.
	 */
	private String wrapNameForChannel(String name) {
		return "ch_"+wrapName(name)+"_t";
	}

	/**
	 * Generates a channel descriptor name given the specified name.
	 * Also cleans it.
	 * @param name The name to use to generate a channel descriptor name.
	 * @return The generated, clean channel descriptor name.
	 */
	private String wrapNameForChannelDesc(String name) {
		return wrapName(name);
	}

	/**
	 * Generates a channel buffer name given the specified name.
	 * Also cleans it.
	 * @param name The name to use to generate a channel buffer name.
	 * @return The generated, clean channel buffer name.
	 */
	private String wrapNameForChannelBuffer(String name) {
		return wrapName(name)+"_buffer";
	}

	/**
	 * Checks for any dependency in the specified expression and adds found
	 * dependencies to the dependency matrix.
	 * @param process The process owning the expression.
	 * @param trans The transition ID in which the expression resides.
	 * @param e The expression to check for dependencies.
	 */
	void handleAssignDependency(Proctype process, int trans, Expression e) {
		if(e instanceof Identifier) {
			Identifier id = (Identifier)e;
			Variable var = id.getVariable();
			Expression arrayExpr = id.getArrayExpr();
			if (var.getArraySize() > 1) {
				if (arrayExpr != null) {
					try {
						int i = arrayExpr.getConstantValue();
						dep_matrix.incWrite(trans, state_var_offset.get(var)/4+i);
						dep_matrix.decrRead(trans, state_var_offset.get(var)/4+i);
					} catch(ParseException pe) {
						for(int i=0; i<var.getArraySize(); ++i) {
							dep_matrix.incWrite(trans, state_var_offset.get(var)/4+i);
							dep_matrix.decrRead(trans, state_var_offset.get(var)/4+i);
						}
					}
				} else {
					dep_matrix.incWrite(trans, state_var_offset.get(var)/4);
					dep_matrix.decrRead(trans, state_var_offset.get(var)/4);
				}
			} else {
				dep_matrix.incWrite(trans, state_var_offset.get(var)/4);
				dep_matrix.decrRead(trans, state_var_offset.get(var)/4);
			}
		} else {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		}
	}

	/**
	 * Generate Pre code for a rendezvous couple.
	 */
	private void generatePreRendezVousAction(StringWriter w, SendAction sa, ReadAction ra, int trans) {
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

		w.appendLine("if(",C_STATE_TMP,".",wrapName(sa.p.getName()),".",C_STATE_PROC_COUNTER,".var == ",sa.t.getFrom().getStateId(),
		                        " && ",C_STATE_TMP,".",wrapName(ra.p.getName()),".",C_STATE_PROC_COUNTER,".var == ",ra.t.getFrom().getStateId()," && ");
		w.appendLine("( ",C_STATE_TMP,".",C_PRIORITY,".var == ",state_proc_offset.get(sa.p)," || ",C_STATE_TMP,".",C_PRIORITY,".var == ",state_proc_offset.get(ra.p)," || ",C_STATE_TMP,".",C_PRIORITY,".var<0"," ) ) {");
		w.indent();

		w.appendPrefix();
		w.append("if( true");
		for (int i = 0; i < cra_exprs.size(); i++) {
			final Expression csa_expr = csa_exprs.get(i);
			final Expression cra_expr = cra_exprs.get(i);
			if (!(cra_expr instanceof Identifier)) {
				w.append(" && (");
				try {
					generateIntExpression(w, null, csa_expr,trans);
				} catch(ParseException e) {
					e.printStackTrace();
				}
				w.append(" == ");
				try {
					generateIntExpression(w, null, cra_expr,trans);
				} catch(ParseException e) {
					e.printStackTrace();
				}
				w.append(")");
			}
		}
		w.append(") {");
		w.appendPostfix();

		w.indent();
	}

	/**
	 * Generate Post code for a rendezvous couple.
	 */
	private void generatePostRendezVousAction(StringWriter w, SendAction sa, ReadAction ra) {
		w.outdent();

		if(sa.t.getFrom()!=null && sa.t.getFrom().isInAtomic()) {
			w.appendLine("} else if(",C_STATE_TMP,".",C_PRIORITY,".var == ",state_proc_offset.get(sa.p),") {");
			w.indent();
			w.appendLine(C_STATE_TMP,".",C_PRIORITY,".var = -1;");
			//w.appendLine("printf(\"[",state_proc_offset.get(sa.p),"] handled %i losses of atomicity so far\\n\",++n_losses);");
			w.appendLine("callback(arg,&transition_info,&tmp);");
			w.appendLine("return ",1,";");
			//w.appendLine("return spinja_get_successor_all_advanced(model,&tmp,callback,arg);");
			w.outdent();
			w.appendLine("}");
		} else {
			w.appendLine("}");
		}

		w.outdent();
		w.appendLine("}");
	}

	/**
	 * Generate the transition for one rendezvous couple. The specified
	 * transition ID will be used to identify the generated transition.
	 * @param w The StringWriter to which the code is written.
	 * @param sa The SendAction component.
	 * @param ra The ReadAction component.
	 * @param trans The transition ID to use for the generated transition.
	 */
	private void generateRendezVousAction(StringWriter w, SendAction sa, ReadAction ra, int trans) {
		ChannelSendAction csa = sa.csa;
		ChannelReadAction cra = ra.cra;

		// Pre
		generatePreRendezVousAction(w,sa,ra,trans);

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

		List<Expression> csa_exprs = csa.getExprs();
		List<Expression> cra_exprs = cra.getExprs();
		for (int i = 0; i < cra_exprs.size(); i++) {
			final Expression csa_expr = csa_exprs.get(i);
			final Expression cra_expr = cra_exprs.get(i);
			if ((cra_expr instanceof Identifier)) {
				w.appendPrefix();
				try {
					generateIntExpression(w, null, cra_expr,trans);
				} catch(ParseException e) {
					e.printStackTrace();
				}
				w.append(" = ");
				try {
					generateIntExpression(w, null, csa_expr,trans);
				} catch(ParseException e) {
					e.printStackTrace();
				}
				w.append(";");
				w.appendPostfix();
			}
		}

		if(ra.t.getTo()!=null && ra.t.getTo().isInAtomic()) {
			//w.appendLine(C_STATE_TMP,".",C_PRIORITY,".var = ",process.getID(),";");
			w.appendLine(C_STATE_TMP,".",C_PRIORITY,".var = ",state_proc_offset.get(ra.p),";");
			w.appendLine("printf(\"[",state_proc_offset.get(sa.p),"] rendezvous: transferred atomicity to ",state_proc_offset.get(ra.p)," \\n\");");
			w.appendLine("callback(arg,&transition_info,&tmp);");
			w.appendLine("return ",1,";");
		} else {
			w.appendLine(C_STATE_TMP,".",C_PRIORITY,".var = ",-1,";");
			// Generate the callback and the rest
			//if(t.getTo()!=null) {
				w.appendLine("callback(arg,&transition_info,&tmp);");
				w.appendLine("return ",1,";");
			//}
		}

		// Post
		generatePostRendezVousAction(w,sa,ra);
		
		w.appendLine("return 0;");
	}

}

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
import java.util.Set;
import spinja.promela.compiler.automaton.ElseTransition;
import spinja.promela.compiler.parser.ParseException;
import spinja.promela.compiler.parser.PromelaConstants;
import spinja.promela.compiler.variable.ChannelType;
import spinja.promela.compiler.variable.ChannelVariable;
import spinja.promela.compiler.variable.CustomVariableType;
import spinja.promela.compiler.variable.Variable;
import spinja.promela.compiler.variable.VariableType;
import spinja.promela.compiler.variable.VariableStore;
import spinja.promela.compiler.expression.*;
import spinja.promela.compiler.parser.Token;
import spinja.promela.compiler.variable.VariableAccess;
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
	static public class TypeDesc {
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
	static public class DepRow {
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
	static public class DepMatrix {
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
			if(transition<0 || transition>=dep_matrix.size()) return;
			DepRow dr = dep_matrix.get(transition);
			assert(dr!=null);
			dr.incRead(dep);
		}

		/**
		 * Increase the number of writes of the specified dependency by one.
		 * @param dep The dependency to increase.
		 */
		public void incWrite(int transition, int dep) {
			if(transition<0 || transition>=dep_matrix.size()) return;
			DepRow dr = dep_matrix.get(transition);
			assert(dr!=null);
			dr.incWrite(dep);
		}

		/**
		 * Decrease the number of reads of the specified dependency by one.
		 * @param dep The dependency to decrease.
		 */
		public void decrRead(int transition, int dep) {
			if(transition<0 || transition>=dep_matrix.size()) return;
			DepRow dr = dep_matrix.get(transition);
			assert(dr!=null);
			dr.decrRead(dep);
		}

		/**
		 * Decrease the number of writes of the specified dependency by one.
		 * @param dep The dependency to decrease.
		 */
		public void decrWrite(int transition, int dep) {
			if(transition<0 || transition>=dep_matrix.size()) return;
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

	public class TimeoutTransition {
		public int trans;
		public Proctype p;
		public LTSminTransition lt;

		public TimeoutTransition(int trans, Proctype p, LTSminTransition lt) {
			this.trans = trans;
			this.p = p;
			this.lt = lt;
		}

	}

	public class ElseTransitionItem {
		public int trans;
		public ElseTransition t;
		public Proctype p;

		public ElseTransitionItem(int trans, ElseTransition t, Proctype p) {
			this.trans = trans;
			this.t = t;
			this.p = p;
		}

	}

	static public class GuardMatrix {
		private List<Expression> guards;
		private List< List<Integer> > co_matrix;
		private List< List<Integer> > dep_matrix;
		private List< List<Expression> > trans_matrix;

		private final int width;

		public GuardMatrix(int width) {
			this.width = width;
			guards = new ArrayList<Expression>();
			co_matrix = new ArrayList< List<Integer> >();
			dep_matrix = new ArrayList< List<Integer> >();
			trans_matrix = new ArrayList< List<Expression> >();
		}

		public int addGuard(int trans, Expression g) {

			int idx = getGuard(g);
			if(idx>=0) return idx;

			guards.add(g);
			for(int i=co_matrix.size(); i-->0;) {
				co_matrix.get(i).add(1);
			}

			{
				List<Integer> row = new ArrayList<Integer>(guards.size());
				co_matrix.add(row);
				for(int i=row.size(); i-->0;) {
					row.set(i,1);
				}
			}

			{
				List<Integer> row = new ArrayList<Integer>(width);
				dep_matrix.add(row);
				for(int i=row.size(); i-->0;) {
					row.set(i,1);
				}
			}

			{
				for(int i=trans_matrix.size();i<=trans;++i) {
					trans_matrix.add(i,new ArrayList<Expression>());
				}
				trans_matrix.get(trans).add(g);
			}

			return co_matrix.size()-1;
		}

		public int getGuard(Expression g) {
			for(int i = guards.size(); i-->0;) {
				if(guards.get(i).equals(g)) return i;
			}
			return -1;
		}

		public List< List<Integer> > getDepMatrix() {
			return dep_matrix;
		}

		public List< List<Integer> > getCoMatrix() {
			return co_matrix;
		}

		public List< List<Expression> > getTransMatrix() {
			return trans_matrix;
		}

		public List<Expression> getGuards() {
			return guards;
		}

	}

//	public class Guard {
//		public int trans;
//		public Expression expr;
//
//		public Guard(int trans, Expression expr) {
//			this.trans = trans;
//			this.expr = expr;
//		}
//
//		public Expression getExpr() {
//			return expr;
//		}
//
//		public void setExpr(Expression expr) {
//			this.expr = expr;
//		}
//
//		public int getTrans() {
//			return trans;
//		}
//
//		public void setTrans(int trans) {
//			this.trans = trans;
//		}
//
//	}

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
	public static final String C_STATE_NEVER = "never";
	public static final String C_PRIORITY = C_STATE_GLOBALS+"."+C_STATE_PRIORITY;
	public static final String C_NEVER = C_STATE_GLOBALS+"."+C_STATE_NEVER;
	public static final String C_TYPE_INT1   = "sj_int1";
	public static final String C_TYPE_INT8   = "sj_int8";
	public static final String C_TYPE_INT16  = "sj_int16";
	public static final String C_TYPE_INT32  = "sj_int32";
	public static final String C_TYPE_UINT8  = "sj_uint8";
	public static final String C_TYPE_UINT16 = "sj_uint16";
	public static final String C_TYPE_UINT32 = "sj_uint32";
	public static final String C_TYPE_CHANNEL = "sj_channel";
	public static final String C_TYPE_PROC_COUNTER = C_TYPE_INT32;
	public static final String C_TYPE_PROC_COUNTER_ = "int";

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

	// The size of the state vector in integers (32bit)
	private int state_size;

	// The CStruct state vector
	private CStruct state;

	// The transition ID of the transition that handles loss of atomicity
	int loss_transition_id;

	// The transition ID of the transition that handles total timeout
	int total_timeout_id;
	LTSminTransition lt_total_timeout;

	// State vector offset of the prioritiseProcess variable
	int offset_priority;

	// Set to true when all transitions have been parsed.
	// After this, channels, else, timeout, and loss of atomicity is handled.
	boolean seenItAll = false;

	// List of transition with a TimeoutExpression
	List<TimeoutTransition> timeout_transitions;

	// List of Elsetransitions
	// These will be generated after normal transitions
	List<ElseTransitionItem> else_transitions;

	GuardMatrix guard_matrix;

	private HashMap<Proctype,Identifier> PCIDs;

	private PriorityIdentifier priorityIdentifier;

	// List of all generated transitions
	// These will be valid after generateTransitions()
	//List<LTSminTransition> transitions;

	//List<Guard> guards;

	LTSminModel model;

	/**
	 * Creates a new LTSMinPrinter using the specified Specification.
	 * After this, the generate() member will generate and return C code.
	 * @param spec The Specification using which C code is generated.
	 * @param name The name to give the model.
	 */
	public LTSMinPrinter(Specification spec, String name) {
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
		timeout_transitions = new ArrayList<TimeoutTransition>();
		else_transitions = new ArrayList<ElseTransitionItem>();

//		transitions = new ArrayList<LTSminTransition>();
//		guards = new ArrayList<Guard>();

		model = new LTSminModel(name);

		channels = new HashMap<ChannelVariable,ReadersAndWriters>();
		PCIDs = new HashMap<Proctype,Identifier>();
		priorityIdentifier = new PriorityIdentifier();
	}

	/**
	 * Generates and returns C code according to the Specification provided
	 * when creating this LTSMinPrinter instance.
	 * @return The C code according to the Specification.
	 */
	public String generate() {

		StringWriter header   = new StringWriter();
		StringWriter structs  = new StringWriter();
		StringWriter w        = new StringWriter();

		// Cache generate() requests
		if(c_code!=null) {
			return c_code;
		}

		long start_t = System.currentTimeMillis();

		// Generate header code
		generateHeader(header);

		// Generate static type structs
		structs.appendLine("");
		generateTypeStructs(structs);

		// Generate structs describing channels and custom structs
		structs.appendLine("");
		generateCustomStructs(structs);

		// Generate struct describing the state vector
		structs.appendLine("");
		generateStateStructs(structs);

		// Generate defines describing when a process is allowed to die
		header.appendLine("");
		generateAllowedDeath(header);

		// Generate code for initial state and state size
		w.appendLine("");
		generateStateCode(w);

		// Generate code for the transitions
		w.appendLine("");
		int t = generateTransitions(w);

		// Generate code for all the transitions
		w.appendLine("");
		generateTransitionsAll(w,t);

		// Generate code for timeout expression
		header.appendLine("");
		w.appendLine("");
		generateTimeoutExpression(header,w);

		// Generate code for total time out expression
		if(spec.getNever()!=null) {
		header.appendLine("");
			generateTotalTimeout(header);
		}

		// Generate Dependency Matrix
		w.appendLine("");
		generateDepMatrix(w,dep_matrix);

		// Generate Guard Matrix
		w.appendLine("");
		generateGuardMatrix(w,guard_matrix);

		// Generate state descriptors
		w.appendLine("");
		generateStateDescriptors(w);

		// Generate buchi is accepting
		w.appendLine("");
		generateBuchiIsAccepting(w);

		// Generate is atomic
		w.appendLine("");
		generateIsAtomic(w);

		long end_t = System.currentTimeMillis();

		// Generate statistics
		w.appendLine("");
		generateStatistics(w,start_t,end_t);

		//w.append("/*");
		w.clear();
		LTSminDMWalker.walkModel(model);
		LTSminPrinter2.generateModel(w, model);
		c_code = w.toString();
		return c_code;
		//w.append("*/");
		//c_code = header.toString() + structs.toString() + w.toString();
		//return c_code;
	}

	/**
	 * Generates the header of the C code (not the .h!).
	 * Model independent.
	 * @param w The StringWriter to which the code is written.
	 */
	static private void generateHeader(StringWriter w) {
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

	private void generateAllowedDeath(StringWriter w) {

		if(procs.isEmpty()) throw new AssertionError("generateAllowedDeath: process list is empty, please call after generateStateStructs()");

		w.appendLine("#ifdef SPINDEATHMODE ");
		{
			for(int i=0; i<procs.size()-1; ++i) {
				Proctype process = procs.get(i);
				//if(process.getID() != i) throw new AssertionError("Process ID inconsistent: " + process.getID() + " != " + i);
				w.appendLine("#define ALLOWED_DEATH_",wrapName(process.getName()),"() (",C_STATE_TMP,".",wrapName(procs.get(i+1).getName()),".",C_STATE_PROC_COUNTER,".var == -1)");
			}
			w.appendLine("#define ALLOWED_DEATH_",wrapName(procs.get(procs.size()-1).getName()),"() (1)");
			if(spec.getNever()!=null) w.appendLine("#define ALLOWED_DEATH_",wrapName(spec.getNever().getName()),"() (1)");
		}
		w.appendLine("#else");
		{
			for(int i=0; i<procs.size(); ++i) {
				Proctype process = procs.get(i);
				w.appendLine("#define ALLOWED_DEATH_",wrapName(process.getName()),"() (1)");
			}
			if(spec.getNever()!=null) w.appendLine("#define ALLOWED_DEATH_",wrapName(spec.getNever().getName()),"() (1)");
		}

		w.appendLine("#endif");
	}

	/**
	 * Generates various typedefs for types, to pad the data to
	 * the element size of the state vector.
	 * Model independent.
	 * @param w The StringWriter to which the code is written.
	 */
	static public void generateTypeStructs(StringWriter w) {

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

			LTSminTypeStruct ls = new LTSminTypeStruct(wrapNameForChannel(var.getName()));

			// Only generate members for non-rendezvous channels
			if (ct.getBufferSize() > 0) {
				int j=0;
				for(Variable v: vs.getVariables()) {
					TypeDesc td = getCTypeOfVar(v);
					struct.addMember(td,"m"+j);
					ls.members.add(new LTSminTypeBasic(td.type,"m"+j));
					++j;
				}
			}

			// Remember this channel variable, to keep track of
			channels.put(cv,new ReadersAndWriters());

			model.addType(ls);

			// Write the typedef to w
			w.appendLine(struct.getCCode());
		}

	}

	static Variable priorVar = new Variable(VariableType.INT, C_STATE_TMP + "." + C_PRIORITY, 1);
	class PriorityIdentifier extends Identifier {

		public PriorityIdentifier() {
			super(new Token(PromelaConstants.PRIORITY, C_STATE_TMP + "." + C_PRIORITY),priorVar);
		}
		
		@Override
		public boolean equals(Object o) {
			if (o == null)
				return false;
			return o instanceof PriorityIdentifier;
		}
	}

	class PCIdentifier extends Identifier {
		private Proctype process;

		public Proctype getProcess() {
			return process;
		}

		public PCIdentifier(Proctype process) {
			//super(new Token(PromelaConstants.PC_VALUE, C_STATE_TMP + "." + wrapName(process.getName())),new Variable(VariableType.INT, C_STATE_TMP + "." + wrapName(process.getName()), 1));
			super(new Token(PromelaConstants.PC_VALUE, C_STATE_TMP + "." + wrapName(process.getName())),processIdentifiers.get(process));
			this.process = process;
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
			PCIDs.put(p,new PCIdentifier(p));
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
	 *   - offset_priority
	 *   - procs
	 * @param w The StringWriter to which the code is written.
	 * @return C code for the state structs.
	 */
	public Variable never_var;
	public List<Variable> procs_var = new ArrayList<Variable>();
	public HashMap<Proctype,Variable> processIdentifiers = new HashMap<Proctype, Variable>();
	private void generateStateStructs(StringWriter w) {

		// Current offset in the state struct
		int current_offset = 0;

		// List of state structs inside the main state struct
		List<CStruct> state_members = new ArrayList<CStruct>();

		// The main state struct
		state = new CStruct(C_STATE_T);

		LTSminTypeStruct ls_t = new LTSminTypeStruct(C_STATE_T);

		// Globals: initialise globals state struct and add to main state struct
		say("== Globals");
		CStruct sg = new CStruct(C_STATE_GLOBALS_T);
		LTSminTypeStruct ls_g = new LTSminTypeStruct(C_STATE_GLOBALS_T);
		model.addType(ls_g);

		// Add priority process
		{
			ls_g.members.add(new LTSminTypeBasic(C_TYPE_INT32, C_STATE_PRIORITY));
			sg.addMember(C_TYPE_INT32, C_STATE_PRIORITY);
			offset_priority = current_offset;
			++current_offset;
			state_vector_desc.add(C_PRIORITY);
			state_vector_var.add(null);
			model.addElement(new LTSminStateElement(priorVar));
		}

		// Globals: add globals to the global state struct
		VariableStore globals = spec.getVariableStore();
		List<Variable> vars = globals.getVariables();
		for(Variable var: vars) {

			// Add global to the global state struct and fix the offset
			current_offset = handleVariable(sg,var,C_STATE_GLOBALS+".",current_offset,ls_g);
		}

		// Add global state struct to main state struct
		// Add it even if there are no global variables, since priorityProcess
		// is a 'global'
		state_members.add(sg);
		state.addMember(C_STATE_GLOBALS_T, C_STATE_GLOBALS);

		ls_t.members.add(new LTSminTypeBasic(C_STATE_GLOBALS_T, C_STATE_GLOBALS));

		// Add Never process
		{
			Proctype p = spec.getNever();
			if(p!=null) {
				String name = wrapName(p.getName());

				LTSminTypeStruct ls_p = new LTSminTypeStruct("state_"+name+"_t");
				//ls_t.members.add(ls_p);
				ls_t.members.add(new LTSminTypeBasic("state_"+name+"_t",wrapName(name)));
				CStruct proc_never = new CStruct("state_"+name+"_t");
				state.addMember("state_"+name+"_t", name);

				// Add
				proc_never.addMember(C_TYPE_PROC_COUNTER, C_STATE_PROC_COUNTER);
				ls_p.members.add(new LTSminTypeBasic(C_TYPE_PROC_COUNTER,C_STATE_PROC_COUNTER));

				// Add process to Proctype->offset map and add a description
				state_proc_offset.put(p, current_offset);
				state_vector_desc.add(name + "." + C_STATE_PROC_COUNTER);
				state_vector_var.add(null);

				//Fix the offset
				++current_offset;

				// Add process state struct to main state struct
				state_members.add(proc_never);
				never_var = new Variable(VariableType.INT, C_STATE_TMP + "." + wrapName(p.getName()), 1);
				model.addElement(new LTSminStateElement(never_var));
				model.addType(ls_p);
				processIdentifiers.put(p,never_var);
			}
		}

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

			LTSminTypeStruct ls_p = new LTSminTypeStruct("state_"+name+"_t");
			ls_t.members.add(new LTSminTypeBasic("state_"+name+"_t",wrapName(name)));
			// Add process to Proctype->offset map and add a description
			state_proc_offset.put(p, current_offset);
			state_vector_desc.add(name + "." + C_STATE_PROC_COUNTER);
			state_vector_var.add(null);

			// Add process counter to process state struct
			proc_sg.addMember(C_TYPE_PROC_COUNTER,C_STATE_PROC_COUNTER);
			ls_p.members.add(new LTSminTypeBasic(C_TYPE_PROC_COUNTER,C_STATE_PROC_COUNTER));

			//Fix the offset
			++current_offset;

			{
				Variable var = new Variable(VariableType.INT, C_STATE_TMP + "." + wrapName(p.getName()), 1);
				procs_var.add(var);
				model.addElement(new LTSminStateElement(var,name+"."+var.getName()));
				processIdentifiers.put(p,var);
			}
			
			// Locals: add locals to the process state struct
			List<Variable> proc_vars = p.getVariables();
			for(Variable var: proc_vars) {
				current_offset = handleVariable(proc_sg,var,name + ".",current_offset,ls_p);
			}

			// Add process state struct to main state struct
			state_members.add(proc_sg);
			model.addType(ls_p);

		}

		// Generate all state structs
		for(CStruct sgen: state_members) {
			w.appendLine(sgen.getCCode());
		}
		w.appendLine(state.getCCode());

		state_size = current_offset;
		model.addType(ls_t);

	}

	/**
	 * Generates the C code for the initial state and the state size.
	 * @param w The StringWriter to which the code is written.
	 */
	private void generateStateCode(StringWriter w) {

		// Generate forward declaration of functions
		w.appendLine("");
		w.appendLine("extern int spinja_get_successor_all( void* model, state_t *in, void (*callback)(void* arg, transition_info_t *transition_info, state_t *out), void *arg );");
		w.appendLine("extern int spinja_get_successor( void* model, int t, state_t *in, void (*callback)(void* arg, transition_info_t *transition_info, state_t *out), void *arg );");
		//w.appendLine("static int timeout_expression(",C_STATE_T," ",C_STATE_TMP,", int trans);");
		w.appendLine("int spinja_is_atomic(void* model, ",C_STATE_T,"* ",C_STATE_TMP,");");

		// Generate state struct comment
		for(int off=0; off<state_size; ++off) {
			w.appendLine("// ",off,"\t",state_vector_desc.get(off));
		}

		// Generate state size related code
		w.appendLine("int ",C_STATE_SIZE," = ",state_size,";");

		w.appendLine("extern int spinja_get_state_size() {");
		w.indent();
		w.appendLine("return ",C_STATE_SIZE,";");
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
		if(state_size > 0) {
			int i = 0;

			// Insert initial expression of each state element into initial state struct
			for(;;) {

				// Get the variable for the current element (at position i)
				Variable v = state_vector_var.get(i);

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
						try {
							generateIntExpression(w, null, e,-1, new LTSminTransition());
						} catch(ParseException pe) {
							pe.printStackTrace();
							System.exit(0);
						}
					}
				}
				if(++i>=state_size) {
					break;
				}
				w.append(",");
			}
		}
		w.appendLine("};");

		w.appendLine("extern void spinja_get_initial_state( state_t *to )");
		w.appendLine("{");
		w.indent();
		w.appendLine("if(state_size*",STATE_ELEMENT_SIZE," != sizeof(" + C_STATE_T + ")) { printf(\"state_t SIZE MISMATCH!: state=%i(%i) globals=%i\",sizeof(state_t),state_size*",STATE_ELEMENT_SIZE,",sizeof(state_globals_t)); }");
		w.appendLine("memcpy(to, (char*)&",C_STATE_INITIAL,", sizeof(" + C_STATE_T + "));");
		w.appendLine("to->",C_PRIORITY,".var = -1;");
		w.outdent();
		w.appendLine("}");

		// Generate state printer
		w.appendLine("void print_state(",C_STATE_T,"* s) {");
		w.indent();
		w.appendLine("if(!s) return;");
		for(int i=0; i<state_size; ++i) {
			String v = getStateDescription(i);
			Variable var = state_vector_var.get(i);

			// PC or PriorityProcess
			if(var==null) {
				w.appendLine("printf(\"",v,": %i\\n\",s->",v,".var);");
			} else if(var instanceof ChannelVariable) {
				ChannelVariable cv = (ChannelVariable)var;
				if(cv.getType().getBufferSize()==0) {
					w.appendLine("printf(\"[CH] ",v,": rendezvous\\n\");");
				} else {
					w.appendLine("printf(\"[CH] ",v,": nextRead=%i, filled=%i\\n\",s->",v,".nextRead,s->",v,".filled);");
					++i;
					for(;i<state_size && var==state_vector_var.get(i); ++i) {
						w.appendLine("printf(\"",v,": %i\\n\",s->",getStateDescription(i),".var);");
					}
				}
			} else if(var.getArraySize()>1) {
				for(int j=0; i<state_size && j<var.getArraySize(); ++j, ++i) {
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
	 * is in integers.
	 * @param offset The offset of which a desciption is wanted.
	 * @return The description for the given offset.
	 */
	private String getStateDescription(int offset) {
		if(offset>=state_size) {
			return "N/A";
		} else {
			return state_vector_desc.get(offset);
		}
	}

	private String getStateType(int offset) {
		if(offset>=state_size) {
			return "N/A";
		} else {
			Variable v = state_vector_var.get(offset);
			return v==null?C_TYPE_PROC_COUNTER:getCTypeOfVarReal(v);
		}
	}

	/**
	 * Returns the C typedef name for the given variable. This typedef
	 * has been defined earlier to pad data to STATE_ELEMENT_SIZE.
	 * @param v The Variable of which the C typedef is wanted.
	 * @return The C typedef name for the given variable.
	 */
	static public TypeDesc getCTypeOfVar(Variable v) {
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
		if(v==null) throw new AssertionError("ERROR: Variable is NULL");
		if(v.getType()==null) throw new AssertionError("ERROR: Type is NULL of: " + v.getName());
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
		for(int n=say_indent; n-->0;) {
			System.out.print("  ");
		}
		System.out.println(s);
	}

	private void generateTransitionsAll(StringWriter w, int transitions) {
		w.appendLine("extern int spinja_get_successor_all( void* model, state_t *in, void (*callback)(void* arg, transition_info_t *transition_info, state_t *out), void *arg) {");
		w.indent();

		//w.appendLine("transition_info_t transition_info = { NULL, ",loss_transition_id," };");
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
		generateTransitions_pre(w);
		int trans = generateTransitions_mid(w,0);
		trans = generateTransitions_post(w,trans);
		return trans;
	}

	/**
	 *
	 */
	private void generateTransitions_pre(StringWriter w) {
		say("== Automata");
		++say_indent;

		// Generate the start: initialise tmp
		w.appendLine("extern int spinja_get_successor( void* model, int t, state_t *in, void (*callback)(void* arg, transition_info_t *transition_info, state_t *out), void *arg) {");
		w.indent();

		w.appendLine("transition_info_t transition_info = { NULL, t };");
		w.appendLine("(void)model; // ignore model");
		w.appendLine("int states_emitted = 0;");
		w.appendLine("register int pos;");
		w.appendLine(C_STATE_T," ",C_STATE_TMP,";");
		w.appendLine("memcpy(&",C_STATE_TMP,",in,sizeof(",C_STATE_T,"));");
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

		// Jump to switch state
		w.appendLine();
		w.appendLine("goto switch_state;");
		w.appendLine();

		// Init dependency matrix
		dep_matrix = new DepMatrix(1,state_size);
		guard_matrix = new GuardMatrix(state_size);

	}

	/**
	 * Generates the state transitions.
	 * This calls generateTransitionsFromState() for every state in every process.
	 * @param w The StringWriter to which the code is written.
	 */
	private int generateTransitions_mid(StringWriter w, int trans) {

		// Generate the normal transitions for all processes.
		// This does not include: rendezvous, else, timeout.
		// Loss of atomicity is handled separately as well.
		for(Proctype p: procs) {
			say("[Proc] " + p.getName());
			++say_indent;

			Automaton a = p.getAutomaton();

			// Generate transitions for all states in the process
			Iterator<State> i = a.iterator();
			while(i.hasNext()) {
				State st = i.next();

				Proctype never = spec.getNever();
				if(never!=null) {
					Automaton never_a = never.getAutomaton();
					Iterator<State> never_i = never_a.iterator();

					while(never_i.hasNext()) {
						trans = generateTransitionsFromState(w,p,trans,st,never_i.next());
					}
				} else {
						trans = generateTransitionsFromState(w,p,trans,st,null);
				}
			}

			--say_indent;
		}
		seenItAll = true;

		// Generate Else Transitions
		for(ElseTransitionItem eti: else_transitions) {
			Proctype never = spec.getNever();
			if(never!=null) {
				Automaton never_a = never.getAutomaton();
				Iterator<State> never_i = never_a.iterator();

				while(never_i.hasNext()) {
					State never_state = never_i.next();
					for(Transition never_t: never_state.output) {
						trans = generateStateTransition(w, eti.p, eti.t, trans,never_t);
					}
				}
			} else {
				trans = generateStateTransition(w, eti.p, eti.t, trans, null);
			}
		}

		// Generate the rendezvous transitions
		for(Map.Entry<ChannelVariable,ReadersAndWriters> e: channels.entrySet()) {
			ChannelVariable cv = e.getKey();
			ReadersAndWriters raw = e.getValue();
			for(SendAction sa: raw.sendActions) {
				for(ReadAction ra: raw.readActions) {
					//if(state_proc_offset.get(sa.p) != state_proc_offset.get(ra.p)) {

						// Add transition
						if(model.getTransitions().size() != trans) throw new AssertionError("Transition not set at correct location in the transition array");

						LTSminTransition lt = new LTSminTransition(sa.p);
						model.getTransitions().add(lt);

						dep_matrix.ensureSize(trans+1);
						w.appendLine("l",trans,": // ",sa.p.getName(),"[",state_proc_offset.get(sa.p),"] --> ",ra.p.getName(),"[",state_proc_offset.get(ra.p),"]");

						Proctype never = spec.getNever();
						if(never!=null) {
							Automaton never_a = never.getAutomaton();
							Iterator<State> never_i = never_a.iterator();

							while(never_i.hasNext()) {
								State never_state = never_i.next();
								for(Transition never_t: never_state.output) {
									generateRendezVousAction(w,sa,ra,trans,never_t,lt);
								}
							}
						} else {
							generateRendezVousAction(w,sa,ra,trans,null,lt);
						}

						++trans;
					//}
				}

			}

		}
		return trans;
	}

	private int generateTransitions_post(StringWriter w, int trans) {
		// Create loss of atomicity transition.
		// This is used when a process blocks inside an atomic transition.

		// Add transition
		{
			if(model.getTransitions().size() != trans) throw new AssertionError("Transition not set at correct location in the transition array");
			LTSminTransitionCombo ltc = new LTSminTransitionCombo("loss of atomicity");
			model.getTransitions().add(ltc);

			dep_matrix.ensureSize(trans+1);
			loss_transition_id = trans;
			w.appendLine("l",loss_transition_id,": // loss of transitions");
			for(AtomicState as: atomicStates) {

				LTSminTransition lt = new LTSminTransition(as.p);
				ltc.addTransition(lt);
				
				State s = as.s;
				Proctype process = as.p;
				assert(s.isInAtomic());
				w.appendPrefix();
				//w.append("if( true /* s= ").append(s.getStateId()).append(" */");
				w.append("if( ").append(C_STATE_TMP).append(".").append(wrapName(process.getName())).append(".").append(C_STATE_PROC_COUNTER).append(".var == ").append(s.getStateId());
				w.appendPostfix();
				w.appendPrefix();
				w.append("&&( ").append(C_STATE_TMP).append(".").append(C_PRIORITY).append(".var == ").append(state_proc_offset.get(process)).append(" )");

				// Dependency matrix: process counter, prioritiseProcess
				dep_matrix.incRead(trans, state_proc_offset.get(process));
				dep_matrix.incRead(trans, offset_priority);
				dep_matrix.incWrite(trans, offset_priority);

				guard_matrix.addGuard(trans,makePCGuard(s,process));
				guard_matrix.addGuard(trans,makeAtomicGuard(process));

				lt.addGuard(new LTSminGuard(trans, makePCGuard(s, process)));
				lt.addGuard(new LTSminGuard(trans, makeExclusiveAtomicGuard(process)));


				for(Transition ot: s.output) {
					LTSminGuardNand gnand = new LTSminGuardNand();
					w.appendPostfix();
					w.appendPrefix();
					w.append("&&!(");
					generateTransitionGuard(w,process,ot,trans,gnand);
					w.append(")");

					lt.addGuard(gnand);
				}
				w.append(" ) {");
				w.appendPostfix();
				w.indent();
				w.appendLine(C_STATE_TMP,".",C_PRIORITY,".var = ",-1,";");
				lt.addAction(new AssignAction(
										new Token(PromelaConstants.ASSIGN,"="),
										priorityIdentifier,
										new ConstantExpression(new Token(PromelaConstants.NUMBER,"-1"), -1)));
				w.appendLine("printf(\"[",state_proc_offset.get(process),"] @%i BIG IF - handled %i losses of atomicity so far\\n\",__LINE__,++n_losses);");
				w.appendLine("callback(arg,&transition_info,&tmp);");
				w.appendLine("return ",1,";");
				generateDependencymatrixStats(w,dep_matrix,trans);
				w.outdent();
				w.appendLine("}");
			}
			w.appendLine("return 0;");
			++trans;
		}

		// Add total timeout transition in case of a never claim.
		// This is because otherwise accepting cycles might not be found,
		// although the never claim is violated.
		if(spec.getNever()!=null) {
			{
				// Add transition
				if(model.getTransitions().size() != trans) throw new AssertionError("Transition not set at correct location in the transition array");
				LTSminTransition lt = lt_total_timeout = new LTSminTransition("total timeout");
				model.getTransitions().add(lt);

				dep_matrix.ensureSize(trans+1);
				total_timeout_id = trans;
				w.appendLine("l",total_timeout_id,": // total timeout");

	//			w.appendPrefix().append("if( ");
	//			generateTotalTimeoutExpression(w, total_timeout);
	//			w.append(" ) {").appendPostfix();
				w.appendPrefix();
				w.append("if( TOTAL_TIMEOUT() && ( false");

				LTSminGuardOr gor = new LTSminGuardOr();
				lt.addGuard(gor);

				Iterator<State> i = spec.getNever().getAutomaton().iterator();
				while(i.hasNext()) {
					State s = i.next();
					if(s.isAcceptState()) {
						w.append(" || ").append(C_STATE_TMP).append(".").append(wrapName(spec.getNever().getName())).append(".").append(C_STATE_PROC_COUNTER).append(".var == ").append(s.getStateId());
						gor.addGuard(new LTSminGuard(trans, makePCGuard(s, spec.getNever())));
					}
				}
				w.append(" ) ) {");
				w.appendPostfix();
				w.indent();
				w.appendLine("callback(arg,&transition_info,&tmp);");
				w.appendLine("return ",1,";");
				generateDependencymatrixStats(w,dep_matrix,trans);
				w.outdent();
				w.appendLine("}");

				w.appendLine("return 0;");
				++trans;
			}

			// Add accepting cycle in the end state of never claim
			{
				// Add transition
				if(model.getTransitions().size() != trans) throw new AssertionError("Transition not set at correct location in the transition array");
				LTSminTransition lt = new LTSminTransition("cycle");
				model.getTransitions().add(lt);

				dep_matrix.ensureSize(trans+1);
				w.appendLine("#ifndef NOENDACCEPT");
				w.appendLine("l",trans,": // never claim end transition");
				w.appendPrefix();
				w.append("if( false ");

				LTSminGuardOr gor = new LTSminGuardOr();
				lt.addGuard(gor);

				Iterator<State> i = spec.getNever().getAutomaton().iterator();
				while(i.hasNext()) {
					State s = i.next();
					if(s.isEndingState()) {
						w.append(" || ").append(C_STATE_TMP).append(".").append(wrapName(spec.getNever().getName())).append(".").append(C_STATE_PROC_COUNTER).append(".var == ").append(s.getStateId());
						gor.addGuard(new LTSminGuard(trans,makePCGuard(s, spec.getNever())));
					}
				}
				w.append(" ) {");
				w.appendPostfix();
				w.indent();
				w.appendLine("callback(arg,&transition_info,&tmp);");
				w.appendLine("return ",1,";");
				generateDependencymatrixStats(w,dep_matrix,trans);
				w.outdent();
				w.appendLine("}");
				w.appendLine("return 0;");
				w.appendLine("#endif");
				++trans;
			}

		}

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
		w.appendLine("extern int spinja_get_transition_groups() {");
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
	private int generateTransitionsFromState(StringWriter w, Proctype process, int trans, State state, State never_state) {

		if(state==null) {
			throw new AssertionError("State is NULL");
		}

		say(state.toString());

		// Check if it is an ending state
		if(state.sizeOut()==0) { // FIXME: Is this the correct prerequisite for THE end state of a process?

			// Add transition
			if(model.getTransitions().size() != trans) throw new AssertionError("Transition now set at correct location in the transition array");
			LTSminTransition lt = new LTSminTransition(process);
			model.getTransitions().add(lt);

			dep_matrix.ensureSize(trans+1);

			// Dependency matrix: process counter, prioritiseProcess
			dep_matrix.incWrite(trans, state_proc_offset.get(process));
			dep_matrix.incRead(trans, state_proc_offset.get(process));
			dep_matrix.incWrite(trans, offset_priority);
			dep_matrix.incRead(trans, offset_priority);

			guard_matrix.addGuard(trans,makePCGuard(state,process));
			guard_matrix.addGuard(trans,makeAtomicGuard(process));
			guard_matrix.addGuard(trans,makeAllowedToDie(process));

			lt.addGuard(new LTSminGuard(trans, makePCGuard(state, process)));
			lt.addGuard(new LTSminGuard(trans, makeAtomicGuard(process)));
			lt.addGuard(new LTSminGuard(trans, makeAllowedToDie(process)));

			// In the case of an ending state, generate a transition only
			// changing the process counter to -1.
			w.appendLine("l",trans,": // ",process.getName(),"[",state_proc_offset.get(process),"] - end transition");
			w.appendLine("if( ",C_STATE_TMP,".",wrapName(process.getName()),".",C_STATE_PROC_COUNTER,".var == ",state.getStateId());
			w.appendLine("&&( ",C_STATE_TMP,".",C_PRIORITY,".var == ",state_proc_offset.get(process)," || ",C_STATE_TMP,".",C_PRIORITY,".var<0"," )");
			w.appendLine("&&( ALLOWED_DEATH_",wrapName(process.getName()),"() ) ) {");
			w.indent();
			w.appendLine("",C_STATE_TMP,".",wrapName(process.getName()),".",C_STATE_PROC_COUNTER,".var = ",-1,";");
			w.appendLine("callback(arg,&transition_info,&tmp);");
			w.appendLine("return 1;");
			generateDependencymatrixStats(w,dep_matrix,trans);
			w.outdent();
			w.appendLine("} // state");
			w.appendLine("return 0;");

			lt.addAction(new AssignAction(
									new Token(PromelaConstants.ASSIGN,"="),
									new PCIdentifier(process),
									new ConstantExpression(new Token(PromelaConstants.NUMBER,"-1"),-1)));

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

			if(never_state!=null) {
				for(Transition t: state.output) {
					for(Transition never_t: never_state.output) {

						// Generate transition
						trans = generateStateTransition(w,process,t,trans,never_t);

					}
				}
			} else {
				for(Transition t: state.output) {
					// Generate transition
					trans = generateStateTransition(w,process,t,trans,null);
				}
			}
			--say_indent;
		}

		// Return the next free transition ID
		return trans;

	}

	public int generateStateTransition(StringWriter w, Proctype process, Transition t, int trans, Transition never_t) {
		// Checks
		if(t==null) {
			throw new AssertionError("State transition is NULL");
		}

		// If the from state is atomic, ignore the never transition
		if(t.getFrom().isInAtomic()) never_t = null;

		// DO NOT actionise RENDEZVOUS channel send/read
		// These will be remembered and handled later separately
		// Check only for the normal process, not for the never claim
		// The never claim process is not allowed to contain message passing
		// statements.
		// "This means that a never claim may not contain assignment or message
		// passing statements." @ http://spinroot.com/spin/Man/never.html)
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

		// DO NOT try to generate Else transitions immediately,
		// but buffer it until every state has been visited.
		// This is because during the normal generation, some transitions
		// are not generated (e.g. rendezvous), so their enabledness is
		// unknown.
		//
		{
			if(!seenItAll && t instanceof ElseTransition) {
				else_transitions.add(new ElseTransitionItem(-1,(ElseTransition)t,process));
				return trans;
			}
		}

		// Add transition
		if(model.getTransitions().size() != trans) throw new AssertionError("Transition not set at correct location in the transition array");
		LTSminTransition lt = new LTSminTransition(process);
		model.getTransitions().add(lt);

		// Ensure the dependency matrix is of adequate size
		dep_matrix.ensureSize(trans+1);

		// Dependency matrix: process counter, prioritiseProcess
		dep_matrix.incWrite(trans, state_proc_offset.get(process));
		dep_matrix.incRead(trans, state_proc_offset.get(process));
		dep_matrix.incWrite(trans, offset_priority);
		dep_matrix.incRead(trans, offset_priority);

		if(never_t!=null) {
			say("Handling trans: " + t.getClass().getName() + " || " + never_t.getClass().getName());
		} else {
			say("Handling trans: " + t.getClass().getName());
		}
		// Guard: process counter
		w.appendLine("l",trans,": // ",process.getName(),"[",state_proc_offset.get(process),"] - ",t.getFrom().isInAtomic()?"atomic ":"normal ",t instanceof ElseTransition?"else ":"","transition");
		w.appendLine("if( ",C_STATE_TMP,".",wrapName(process.getName()),".",C_STATE_PROC_COUNTER,".var == ",t.getFrom().getStateId());
		w.appendLine("&&( ",C_STATE_TMP,".",C_PRIORITY,".var == ",state_proc_offset.get(process)," || ",C_STATE_TMP,".",C_PRIORITY,".var<0"," )");

		guard_matrix.addGuard(trans,makePCGuard(t.getFrom(),process));
		guard_matrix.addGuard(trans,makeAtomicGuard(process));

		lt.addGuard(new LTSminGuard(trans, makePCGuard(t.getFrom(), process)));
		lt.addGuard(new LTSminGuard(trans, makeAtomicGuard(process)));

		// Check if the process is allowed to die if the target state is null
		if(t.getTo()==null) {
			w.appendLine("&&( ALLOWED_DEATH_",wrapName(process.getName()),"() )");
//			guard_matrix.addGuard(trans,makeAllowedToDie(process));
//			lt.addGuard(new LTSminGuard(trans, makeAllowedToDie(process)));
		}

		if(never_t!=null) {
			w.appendLine("&&( ",C_STATE_TMP,".",wrapName(spec.getNever().getName()),".",C_STATE_PROC_COUNTER,".var == ",never_t.getFrom().getStateId(),") ) {");
			guard_matrix.addGuard(trans,makePCGuard(never_t.getFrom(),spec.getNever()));
			lt.addGuard(new LTSminGuard(trans, makePCGuard(never_t.getFrom(),spec.getNever())));
		} else {
			w.removePostfix();
			w.append(") {");
			w.appendPostfix();
		}
		w.indent();

		// Generate action guard using the first action in the list
		// of the transition
		w.appendLine("if( true /* Guards */");

		w.appendPrefix();
		w.append("&&( ");
		generateTransitionGuard(w,process,t,trans,lt);
		w.append(")");

		if(never_t != null) {
			w.appendPostfix();
			w.appendPrefix();
			w.append("&&(");
			generateTransitionGuard(w,spec.getNever(),never_t,trans,lt);
			w.append(")");
		}

		w.append(" ) {");
		w.appendPostfix();
		w.indent();

		// If this is an ElseTransition, all other transitions should not be
		// enabled, so make guards for this
		w.appendPrefix();
		w.append("if( true /* Else */");
		if(t instanceof ElseTransition) {
			ElseTransition et = (ElseTransition)t;
			for(Transition ot: t.getFrom().output) {
				if(ot!=et) {
					w.appendPostfix();
					w.appendPrefix();
					w.append("&&!(");
					generateTransitionGuard(w,process,ot,trans,lt);
					w.append(")");
				}
			}
		}
		if(never_t != null && never_t instanceof ElseTransition) {
			ElseTransition et = (ElseTransition)never_t;
			for(Transition ot: t.getFrom().output) {
				if(ot!=et) {
					w.appendPostfix();
					w.appendPrefix();
					w.append("&&!(");
					generateTransitionGuard(w,spec.getNever(),ot,trans,lt);
					w.append(")");
				}
			}
		}
		w.append(" ) {");
		w.appendPostfix();
		w.indent();

		// If there is no never claim or the target state of the never
		// transition is not atomic, then generate action code of the system
		// transition. Otherwise (if there is a claim and the target state is
		// atomic), do not generate system transition code.
		// The dying transition (when never_t.getTo()==null) is not considered
		// atomic
		if(never_t == null || never_t.getTo()==null || !never_t.getTo().isInAtomic()) {
			// Change process counter to the next state.
			// For end transitions, the PC is changed to -1.
			w.appendLine("",C_STATE_TMP,".",
					wrapName(process.getName()),".",
					C_STATE_PROC_COUNTER,".var = ",
					t.getTo()==null?-1:t.getTo().getStateId(),";"
					);

			lt.addAction(new AssignAction(
									new Token(PromelaConstants.ASSIGN,"="),
									new PCIdentifier(process),
									new ConstantExpression(new Token(PromelaConstants.NUMBER,""+(t.getTo()==null?-1:t.getTo().getStateId())),t.getTo()==null?-1:t.getTo().getStateId())));
			if(t.getTo()==null) lt.addAction(new ResetProcessAction(process));

			// Generate actions for this transition
			generateStateTransitionActions(w,process,t,trans,lt);

			// If this transition is atomic
			if(t.getTo()!=null && t.getTo().isInAtomic()) {

				// Claim priority when taking this transition. It is
				// possible this process had already priority, so nothing
				// changes.
				w.appendLine(C_STATE_TMP,".",C_PRIORITY,".var = ",state_proc_offset.get(process),";");

				lt.addAction(new AssignAction(
										new Token(PromelaConstants.ASSIGN,"="),
										priorityIdentifier,
										new ConstantExpression(new Token(PromelaConstants.NUMBER,""+state_proc_offset.get(process)), state_proc_offset.get(process))));
			// If this transition is not atomic
			} else {

				// Make sure no process has priority. This transition was
				// either executed while having priority and it is now given
				// up, or no process had priority and this remains the same.
				w.appendLine(C_STATE_TMP,".",C_PRIORITY,".var = ",-1,";");
				lt.addAction(new AssignAction(
										new Token(PromelaConstants.ASSIGN,"="),
										priorityIdentifier,
										new ConstantExpression(new Token(PromelaConstants.NUMBER,"-1"), -1)));

			}
		}

		// If there is a never claim, generate the PC update code
		if(never_t != null) {
			w.appendLine("",C_STATE_TMP,".",
					wrapName(spec.getNever().getName()),".",
					C_STATE_PROC_COUNTER,".var = ",
					never_t.getTo()==null?-1:never_t.getTo().getStateId(),";"
					);
			lt.addAction(new AssignAction(
									new Token(PromelaConstants.ASSIGN,"="),
									new PCIdentifier(spec.getNever()),
									new ConstantExpression(new Token(PromelaConstants.NUMBER,""+(never_t.getTo()==null?-1:never_t.getTo().getStateId())),never_t.getTo()==null?-1:never_t.getTo().getStateId())));
		}

		// Generate the callback and the rest
		w.appendLine("callback(arg,&transition_info,&tmp);");
		w.appendLine("return ",1,";");

		w.outdent();
		w.appendLine("} // else");

		w.outdent();
		w.appendLine("} // guard");

		generateDependencymatrixStats(w,dep_matrix,trans);

		w.outdent();
		w.appendLine("} // state");

		w.appendLine("return 0;");

		// Dependency matrix: process counter
		dep_matrix.incWrite(trans, state_proc_offset.get(process));
		dep_matrix.incRead(trans, state_proc_offset.get(process));
		return trans+1;
	}

	/**
	 * Generates the guard C code of a transition.
	 * @param w The StringWriter to which the code is written.
	 * @param process The state should be in this process.
	 * @param t The transition of which the guard will be generated.
	 * @param trans The transition group ID to use for generation.
	 */
	void generateTransitionGuard(StringWriter w, Proctype process, Transition t, int trans, LTSminGuardContainer lt) {
		try {

			w.append("(");

			Action a = null;
			if(t.getActionCount()>0) {
				a = t.getAction(0);
			}
			if(a!= null && a.getEnabledExpression()!=null) {
				generateEnabledExpression(w,process,a,t,trans,lt);
			} else {
				w.append("true /* no EE */");
			}
			if(t.getTo()==null) {
				lt.addGuard(new LTSminGuard(trans,makeAllowedToDie(process)));
				w.append(" && ( ALLOWED_DEATH_").append(wrapName(process.getName())).append("() )");
			}

			w.append(")");
			
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
	private void generateStateTransitionActions(StringWriter w, Proctype process, Transition t, int trans, LTSminTransition lt) {

		// Handle all the action in the transition
		Iterator<Action> it = t.iterator();
		while(it.hasNext()) {
			Action a = it.next();
			try {

				// Handle the action
				generateAction(w,process,a,trans,lt);
				lt.addAction(a);

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
		w.append("int transition_dependency[][2][").append(state_size).appendLine("] = {");
		w.appendLine("\t// { ... read ...}, { ... write ...}");
		int t=0;

		// Iterate over all the rows
		for(;;) {
			w.append("\t{{");
			DepRow dr = dm.getRow(t);
			int s=0;

			// Insert all read dependencies of the current row
			for(;;) {
				//w.append(dr.getReadB(s));
				w.append(1);

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
				//w.append(dr.getWriteB(s));
				w.append(1);
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

		// Function to access the dependency matrix
		w.appendLine("");
		w.appendLine("extern const int* spinja_get_transition_read_dependencies(int t)");
		w.appendLine("{");
		w.append("	if (t>=0 && t < ").append(dm.getRows()).appendLine(") return transition_dependency[t][0];");
		w.appendLine("	return NULL;");
		w.appendLine("}");
		w.appendLine("");
		w.appendLine("extern const int* spinja_get_transition_write_dependencies(int t)");
		w.appendLine("{");
		w.append("	if (t>=0 && t < ").append(dm.getRows()).appendLine(") return transition_dependency[t][1];");
		w.appendLine("	return NULL;");
		w.appendLine("}");
	}

	private void generateGuardMatrix(StringWriter w, GuardMatrix gm) {
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

	/**
	 * Generates the code for the given expression. The result is a boolean
	 * expression.
	 * @param w The StringWriter to which the code is written.
	 * @param process The process in which the specified expression is in.
	 * @param e The expression of which the code will be generated.
	 * @throws ParseException
	 */
	private boolean timeoutFalse = false;
	private void generateBoolExpression(StringWriter w, Proctype process, Expression e, int trans, LTSminGuardContainer lt) throws ParseException {
		if(e instanceof Identifier) {
			w.append("(");
			generateIntExpression(w,process,e,trans,lt);
			w.append(" != 0 )");
		} else if(e instanceof AritmicExpression) {
			AritmicExpression ae = (AritmicExpression)e;
			Expression ex1 = ae.getExpr1();
			Expression ex2 = ae.getExpr2();
			Expression ex3 = ae.getExpr3();
			if (ex2 == null) {
				w.append("(").append(ae.getToken().image);
				generateIntExpression(w,process,ex1,trans,lt);
				w.append(" != 0)");
			} else if (ex3 == null) {
				w.append("(");
				generateIntExpression(w,process,ex1,trans,lt);
				w.append(" ").append(ae.getToken().image).append(" ");
				generateIntExpression(w,process,ex2,trans,lt);
				w.append(" != 0)");
			} else { // Can only happen with the x?1:0 expression
				w.append("(");
				generateBoolExpression(w,process,ex1,trans,lt);
				w.append(" ? ");
				generateBoolExpression(w,process,ex2,trans,lt);
				w.append(" : ");
				generateBoolExpression(w,process,ex3,trans,lt);
				w.append(")");
			}
		} else if(e instanceof BooleanExpression) {
			BooleanExpression be = (BooleanExpression)e;
			Expression ex1 = be.getExpr1();
			Expression ex2 = be.getExpr2();
			if (ex2 == null) {
				w.append("(").append(be.getToken().image);
				generateBoolExpression(w,process,ex1,trans,lt);
				w.append(")");
			} else {
				w.append("(");
				generateBoolExpression(w,process,ex1,trans,lt);
				w.append(" ").append(be.getToken().image).append(" ");
				generateBoolExpression(w,process,ex2,trans,lt);
				w.append(")");
			}
		} else if(e instanceof ChannelLengthExpression) {
			throw new ParseException("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else if(e instanceof ChannelOperation) {
			throw new ParseException("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else if(e instanceof CompareExpression) {
			CompareExpression ce = (CompareExpression)e;
			w.append("(");
			generateIntExpression(w,process,ce.getExpr1(),trans,lt);
			w.append(" ").append(ce.getToken().image).append(" ");
			generateIntExpression(w,process,ce.getExpr2(),trans,lt);
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
			if( timeoutFalse ) {
				w.append("false /* timeout-false */");
			} else if (process == spec.getNever()) {
				w.append("false /* never-timeout */ ");
			} else {
				// Prevent adding of this transition if it was already seen
				//if(!seenItAll) timeout_transitions.add(new TimeoutTransition(trans, process, lt));
				//w.append("timeout_expression(").append(C_STATE_TMP).append(",").append(trans).append(")");
				w.append("TIMEOUT_").append(trans).append("()");
			}
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
	private void generateIntExpression(StringWriter w, Proctype process, Expression e, int trans, LTSminGuardContainer lt) throws ParseException {
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
					generateIntExpression(w,process,arrayExpr,trans,lt);
					w.append("].var");

					try {
						int i = arrayExpr.getConstantValue();
						if(trans<dep_matrix.getRows()) dep_matrix.incRead(trans, state_var_offset.get(var)+i);
					} catch(ParseException pe) {
						for(int i=0; i<var.getArraySize(); ++i) {
							if(trans<dep_matrix.getRows()) dep_matrix.incRead(trans, state_var_offset.get(var)+i);
						}
					}
				} else {
					w.append("tmp.");
					w.append(state_var_desc.get(var));
					w.append("[0].var");
					if(trans<dep_matrix.getRows()) dep_matrix.incRead(trans, state_var_offset.get(var));
				}
			} else {
				w.append("tmp.");
				w.append(state_var_desc.get(var));
				w.append(".var");
				if(trans<dep_matrix.getRows()) dep_matrix.incRead(trans, state_var_offset.get(var));
			}
		} else if(e instanceof AritmicExpression) {
			AritmicExpression ae = (AritmicExpression)e;
			Expression ex1 = ae.getExpr1();
			Expression ex2 = ae.getExpr2();
			Expression ex3 = ae.getExpr3();
			if (ex2 == null) {
				w.append("(").append(ae.getToken().image);
				generateIntExpression(w, process, ex1,trans,lt);
				w.append(")");
			} else if (ex3 == null) {
				if (ae.getToken().image.equals("%")) {
					// Modulo takes a special notation to make sure that it
					// returns a positive value
					w.append("abs(");
					generateIntExpression(w,process,ex1,trans,lt);
					w.append(" % ");
					generateIntExpression(w,process,ex2,trans,lt);
					w.append(")");
				} else {

					w.append("(");
					generateIntExpression(w,process,ex1,trans,lt);
					w.append(" ").append(ae.getToken().image).append(" ");
					generateIntExpression(w,process,ex2,trans,lt);
					w.append(")");
				}
			} else {
				w.append("(");
				generateBoolExpression(w,process,ex1,trans,lt);
				w.append(" ? ");
				generateIntExpression(w,process,ex2,trans,lt);
				w.append(" : ");
				generateIntExpression(w,process,ex3,trans,lt);
				w.append(")");
			}
		} else if(e instanceof BooleanExpression) {
			BooleanExpression be = (BooleanExpression)e;
			Expression ex1 = be.getExpr1();
			Expression ex2 = be.getExpr2();
			if (ex2 == null) {
				w.append("(").append(be.getToken().image);
				generateIntExpression(w,process,ex1,trans,lt);
				w.append( " ? 1 : 0)");
			} else {
				w.append("(");
				generateIntExpression(w,process,ex1,trans,lt);
				w.append(" ").append(be.getToken().image).append(" ");
				generateBoolExpression(w,process,ex2,trans,lt);
				w.append(" ? 1 : 0)");
			}
		} else if(e instanceof ChannelLengthExpression) {
			throw new ParseException("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else if(e instanceof ChannelOperation) {
			throw new ParseException("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		} else if(e instanceof CompareExpression) {
			CompareExpression ce = (CompareExpression)e;
			w.append("(");
			generateIntExpression(w,process,ce.getExpr1(),trans,lt);
			w.append(" ").append(ce.getToken().image).append(" ");
			generateIntExpression(w,process,ce.getExpr2(),trans,lt);
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
	public void generateAction(StringWriter w, Proctype process, Action a, int trans, LTSminTransition lt) throws ParseException {

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
						generateIntExpression(w,process,id,trans,lt);
						w.append(" = ").append(value & id.getVariable().getType().getMaskInt()).append(";");
						w.appendPostfix();
					} catch (ParseException ex) {
						// Could not get Constant value
						w.appendPrefix();
						generateIntExpression(w,process,id,trans,lt);
						w.append(" = ");
						generateIntExpression(w,process,as.getExpr(),trans,lt);
						w.append((mask == null ? "" : " & " + mask));
						w.append(";");
						w.appendPostfix();
					}
					break;
				case PromelaConstants.INCR:
					if (mask == null) {
						w.appendPrefix();
						generateIntExpression(w,process,id,trans,lt);
						w.append("++;");
						w.appendPostfix();
					} else {
						w.appendPrefix();
						generateIntExpression(w,process,id,trans,lt);
						w.append(" = (");
						generateIntExpression(w,process,id,trans,lt);
						w.append(" + 1) & ").append(mask).append(";");
						w.appendPostfix();
					}
					break;
				case PromelaConstants.DECR:
					if (mask == null) {
						w.appendPrefix();
						generateIntExpression(w,process,id,trans,lt);
						w.append("--;");
						w.appendPostfix();
					} else {
						w.appendPrefix();
						generateIntExpression(w,process,id,trans,lt);
						w.appendLine(" = (");
						generateIntExpression(w,process,id,trans,lt);
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

			w.appendPrefix();
			w.append("if(!");
			generateBoolExpression(w,process,e,trans,lt);
			w.append(") {");
			w.appendPostfix();
			w.indent();
			w.appendLine("printf(\"Assertion violated: ",as.getExpr().toString(), "\\n\");");
			w.appendLine("print_state(&",C_STATE_TMP,");");
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
				generateIntExpression(w,process,expr,trans,lt);
			}
			w.append(");").appendPostfix();

		// Handle expression action
		} else if(a instanceof ExprAction) {
			ExprAction ea = (ExprAction)a;
			Expression expr = ea.getExpression();
			final String sideEffect = expr.getSideEffect();
			if (sideEffect != null) { ///this is a RunExp TODO: support Process nrActive!=0
				//throw new AssertionError("This is probably wrong...");
				//w.appendLine(sideEffect, "; // POSSIBLY THIS IS WRONG");
			}

		// Handle channel send action
		} else if(a instanceof ChannelSendAction) {
			ChannelSendAction csa = (ChannelSendAction)a;
			ChannelVariable var = (ChannelVariable)csa.getVariable();

			if(var.getType().getBufferSize()>0) {
				String access = C_STATE_TMP + "." + wrapNameForChannelDesc(state_var_desc.get(var));

				w.appendLine("pos = (" + access + ".nextRead + "+access+".filled) % "+var.getType().getBufferSize() + ";");
				String access_buffer = C_STATE_TMP + "." + wrapNameForChannelBuffer(state_var_desc.get(var)) + "[pos]";

				// Dependency matrix: channel variable
				dep_matrix.incRead(trans, state_var_offset.get(var));
				dep_matrix.incWrite(trans, state_var_offset.get(var));

				List<Expression> exprs = csa.getExprs();
				for (int i = 0; i < exprs.size(); i++) {
					final Expression expr = exprs.get(i);
					w.appendPrefix();
					w.append(access_buffer).append(".m").append(i).append(".var = ");
					generateIntExpression(w, process, expr,trans,lt);
					w.append(";");
					w.appendPostfix();

					// Dependency matrix: channel variable
					dep_matrix.incWrite(trans, state_var_offset.get(var)+i+1);
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

				// Dependency matrix: channel variable
				dep_matrix.incRead(trans, state_var_offset.get(var));
				dep_matrix.incWrite(trans, state_var_offset.get(var));

				List<Expression> exprs = cra.getExprs();
				for (int i = 0; i < exprs.size(); i++) {
					final Expression expr = exprs.get(i);
					if (expr instanceof Identifier) {
						handleAssignDependency(process,trans,expr);
						w.appendPrefix();
						generateIntExpression(w, process, expr,trans,lt);
						w.append(" = ").append(access_buffer).append(".m").append(i).append(".var");
						w.append(";");
						w.appendPostfix();

						// Dependency matrix: channel variable
						dep_matrix.incRead(trans, state_var_offset.get(var)+i+1);
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

	/**
	 * Generates the C code denoting when the specified Action is enabled.
	 * The enabledness of rendezvous channel actions can only be determined
	 * after all other transitions have been visited (when seenItAll is true).
	 * @param w The StringWriter to which the code is written.
	 * @param process The action should be in this process.
	 * @param a The action for which C code will be generated.
	 * @param t The transition the action is in.
	 * @param trans The transition group ID to use for generation.
	 * @throws ParseException
	 */
	private void generateEnabledExpression(StringWriter w, Proctype process, Action a, Transition t, int trans, LTSminGuardContainer lt) throws ParseException {
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
			generateBoolExpression(w,process,expr,trans,lt);
			lt.addGuard(new LTSminGuard(trans, expr));

		// Handle a channel send action
		} else if(a instanceof ChannelSendAction) {
			ChannelSendAction csa = (ChannelSendAction)a;
			ChannelVariable var = (ChannelVariable)csa.getVariable();
			if(var.getType().getBufferSize()>0) {

				String access = C_STATE_TMP + "." + wrapNameForChannelDesc(state_var_desc.get(var));

				w.append("(");
				w.append(access).append(".filled < ");
				w.append(var.getType().getBufferSize()).append(")");

				guard_matrix.addGuard(trans,makeChannelUnfilledGuard(var));
				lt.addGuard(new LTSminGuard(trans,makeChannelUnfilledGuard(var)));

				// Dependency matrix: channel variable
				dep_matrix.incRead(trans, state_var_offset.get(var));

			} else if(seenItAll) {
				ReadersAndWriters raw = channels.get(var);
				w.append("(false");
				LTSminGuardOr gor = new LTSminGuardOr();
				lt.addGuard(gor);
				for(ReadAction ra: raw.readActions) {
					List<Expression> csa_exprs = csa.getExprs();
					List<Expression> cra_exprs = ra.cra.getExprs();
					//w.appendPostfix();
					//w.appendPrefix();
					w.append(" || ( (");
					w.append(C_STATE_TMP).append(".").append(wrapName(ra.p.getName())).append(".").append(C_STATE_PROC_COUNTER).append(".var == ").append(ra.t.getFrom().getStateId());
					w.append(" )");

					LTSminGuardAnd gand = new LTSminGuardAnd();
					gor.addGuard(gand);
					gand.addGuard(new LTSminGuard(trans, makePCGuard(ra.t.getFrom(), ra.p)));

					// Dependency matrix: process counter
					dep_matrix.incRead(trans, state_proc_offset.get(ra.p));

					for (int i = 0; i < cra_exprs.size(); i++) {
						final Expression csa_expr = csa_exprs.get(i);
						final Expression cra_expr = cra_exprs.get(i);
						if (!(cra_expr instanceof Identifier)) {
							gand.addGuard(new LTSminGuard(trans,new CompareExpression(new Token(PromelaConstants.EQ,"=="),csa_expr,cra_expr)));
							w.append(" && (");
							try {
								generateIntExpression(w, null, csa_expr,trans,lt);
							} catch(ParseException e) {
								e.printStackTrace();
							}
							w.append(" == ");
							try {
								generateIntExpression(w, null, cra_expr,trans,lt);
							} catch(ParseException e) {
								e.printStackTrace();
							}
							w.append(")");
						}
					}
					w.append(")");
				}
				w.append(")");
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

				guard_matrix.addGuard(trans,makeChannelHasContentsGuard(var));
				lt.addGuard(new LTSminGuard(trans,makeChannelHasContentsGuard(var)));

				// Dependency matrix: channel variable
				dep_matrix.incRead(trans, state_var_offset.get(var));

				for (int i = 0; i < exprs.size(); i++) {
					final Expression expr = exprs.get(i);
					if (!(expr instanceof Identifier)) {

						w.append(" && (");
						w.append(access_buffer).append(".m").append(i).append(".var == ");
						generateIntExpression(w, process, expr,trans,lt);
						w.append(")");
						//throw new AssertionError("add guard addition here");

						lt.addGuard(new LTSminGuard(trans,new CompareExpression(new Token(PromelaConstants.EQ,"=="),new ChannelTopExpression(cra, i),expr)));
					}

				}

				w.append(")");
			} else if(seenItAll) {
				ReadersAndWriters raw = channels.get(var);
				w.append("(false");
				LTSminGuardOr gor = new LTSminGuardOr();
				lt.addGuard(gor);
				for(SendAction sa: raw.sendActions) {
					List<Expression> csa_exprs = sa.csa.getExprs();
					List<Expression> cra_exprs = cra.getExprs();
					//w.appendPostfix();
					//w.appendPrefix();
					w.append(" || ( (");
					w.append(C_STATE_TMP).append(".").append(wrapName(sa.p.getName())).append(".").append(C_STATE_PROC_COUNTER).append(".var == ").append(sa.t.getFrom().getStateId());
					w.append(" )");

					// Dependency matrix: process counter
					dep_matrix.incRead(trans, state_proc_offset.get(sa.p));

					LTSminGuardAnd gand = new LTSminGuardAnd();
					gor.addGuard(gand);
					gand.addGuard(new LTSminGuard(trans, makePCGuard(sa.t.getFrom(), sa.p)));

					for (int i = 0; i < cra_exprs.size(); i++) {
						final Expression csa_expr = csa_exprs.get(i);
						final Expression cra_expr = cra_exprs.get(i);
						if (!(cra_expr instanceof Identifier)) {
							gand.addGuard(new LTSminGuard(trans,new CompareExpression(new Token(PromelaConstants.EQ,"=="),csa_expr,cra_expr)));
							w.append(" && (");
							try {
								generateIntExpression(w, null, csa_expr,trans,lt);
							} catch(ParseException e) {
								e.printStackTrace();
							}
							w.append(" == ");
							try {
								generateIntExpression(w, null, cra_expr,trans,lt);
							} catch(ParseException e) {
								e.printStackTrace();
							}
							w.append(")");
						}
					}
					w.append(")");
				}
				w.append(")");
			} else {
				throw new AssertionError("Trying to actionise rendezvous receive before all others!");
			}


		// Handle not yet implemented action
		} else {
			throw new ParseException("LTSMinPrinter: Not yet implemented: "+a.getClass().getName());
		}
	}

	/**
	 * Generate the timeout expression for the specified TimeoutTransition.
	 * This will generate the expression that NO transition is enabled. The
	 * dependency matrix is fixed accordingly. If tt is null,
	 * @param w The StringWriter to which the code is written.
	 * @param tt The TimeoutTransition to generate code for.
	 */
	public void generateTimeoutExpression(StringWriter w, TimeoutTransition tt) {

		// Loop over all processes
		for(Proctype p: procs) {
			Automaton a = p.getAutomaton();
			Iterator<State> i = a.iterator();

			// Loop over all states of the process
			while(i.hasNext()) {
				State st = i.next();

				// Cull other states of the current process
				//if(tt.p == p && tt.t.getFrom() != st) {
				//	continue;
				//}

				// Check if this state has an ElseTransition
				// If so, skip the transition, because this state
				// always has an active outgoing transition
				boolean hasElse = false;
				for(Transition trans: st.output) {
					if(trans instanceof ElseTransition) {
						hasElse = true;
					}
				}
				if(hasElse) continue;

				// Loop over all transitions of the state
				for(Transition trans: st.output) {

					// Skip transitions with a timeout expression
//					boolean skip = false;
//					for(TimeoutTransition tt2: timeout_transitions) {
//						if(tt2.t == trans) skip = true;
//					}
//					if(skip) continue;

					w.appendPostfix();
					w.appendPrefix();

					dep_matrix.incRead(tt.trans, state_proc_offset.get(p));
					dep_matrix.incRead(tt.trans, offset_priority);

					tt.lt.addGuard(new LTSminGuard(tt.trans,makeAllowedToDie(p)));
					tt.lt.addGuard(new LTSminGuard(tt.trans,makeAtomicGuard(p)));

					// Add the expression that the current ttransition from the
					// current state in the curren process is not enabled.
					w.append("&&!(");
					w.append(C_STATE_TMP).append(".").append(wrapName(p.getName())).append(".").append(C_STATE_PROC_COUNTER).append(".var == ").append(trans.getFrom().getStateId());
					w.append("&&( ").append(C_STATE_TMP).append(".").append(C_PRIORITY).append(".var == ").append(state_proc_offset.get(p)).append(" || ").append(C_STATE_TMP).append(".").append(C_PRIORITY).append(".var<0").append(" )");
					w.append("&&");
					timeoutFalse = true;
					generateTransitionGuard(w,p,trans,tt.trans,tt.lt);
					timeoutFalse = false;
					w.append(")");
				}

			}
		}
	}

	/**
	 *
	 * @param header The StringWriter to which the #define code will be written to.
	 * @param w
	 */
	public void generateTimeoutExpression(StringWriter header, StringWriter w) {
		if(timeout_transitions.isEmpty()) return;

		//w.appendLine("static int timeout_expression(",C_STATE_T," ",C_STATE_TMP,", int trans) {");
		//w.indent();
		//w.appendLine("switch(trans) {");

		for(TimeoutTransition tt: timeout_transitions) {
			//w.appendLine("case ",tt.trans,":");
			//w.indent();
			//w.appendPrefix();
			//w.append("return true ");
			//generateTimeoutExpression(w,tt);
			//w.append(";");
			//w.appendPostfix();
			//w.appendLine("break;");
			//w.outdent();

			header.appendLine("#define TIMEOUT_",tt.trans,"() ( true \\");
			String old_postfix = header.getPostfix();
			header.setPostfix("\\"+old_postfix);
			generateTimeoutExpression(header,tt);
			header.appendPostfix();
			header.setPostfix(old_postfix);
			header.appendLine(")");

		}
		//w.appendLine("default:");
		//w.indent();
		//w.appendLine("printf(\"Error: no timeout expression specified for transition %i\\n\",trans);");
		//w.appendLine("exit(-1);");
		//w.outdent();
		//w.appendLine("}");

		//w.outdent();
		//w.appendLine("}");
		//w.appendLine("");
	}

	/**
	 * Generates #define code for the total timeout expression.
	 * @param w The StringWriter to which the #define code will be written to.
	 */
	public void generateTotalTimeout(StringWriter w) {
		w.appendPrefix().append("#define TOTAL_TIMEOUT() ( true ");
		String old_postfix = w.getPostfix();
		w.setPostfix(" \\"+old_postfix);
		generateTotalTimeoutExpression(w,total_timeout_id, lt_total_timeout);
		w.setPostfix(old_postfix);
		w.append(")").appendPostfix();
	}
	/**
	 * Generate the total timeout expression. This expression is true iff
	 * no transition is enabled, including 'normal' time out transitions.
	 * The dependency matrix is fixed accordingly.
	 * @param w The StringWriter to which the code is written.
	 * @param tt The TimeoutTransition to generate code for.
	 */
	public void generateTotalTimeoutExpression(StringWriter w, int trans, LTSminTransition lt) {

		// Loop over all processes
		for(Proctype p: procs) {
			Automaton a = p.getAutomaton();
			Iterator<State> i = a.iterator();

			// Loop over all states of the process
			while(i.hasNext()) {
				State st = i.next();

				// Cull other states of the current process
				//if(tt.p == p && tt.t.getFrom() != st) {
				//	continue;
				//}

				// Check if this state has an ElseTransition
				// If so, skip the transition, because this state
				// always has an active outgoing transition
				for(Transition t: st.output) {
					if(t instanceof ElseTransition) {
						continue;
					}
				}

				// Loop over all transitions of the state
				for(Transition t: st.output) {

					w.appendPostfix();
					w.appendPrefix();

					dep_matrix.incRead(trans, state_proc_offset.get(p));
					dep_matrix.incRead(trans, offset_priority);

					// Add the expression that the current ttransition from the
					// current state in the curren process is not enabled.
					LTSminGuardNand gnand = new LTSminGuardNand();
					lt.addGuard(gnand);
					gnand.addGuard(new LTSminGuard(trans, makePCGuard(st, p)));
					gnand.addGuard(new LTSminGuard(trans, makeAtomicGuard(p)));
					w.append("&&!(");
					w.append(C_STATE_TMP).append(".").append(wrapName(p.getName())).append(".").append(C_STATE_PROC_COUNTER).append(".var == ").append(t.getFrom().getStateId());
					w.append("&&( ").append(C_STATE_TMP).append(".").append(C_PRIORITY).append(".var == ").append(state_proc_offset.get(p)).append(" || ").append(C_STATE_TMP).append(".").append(C_PRIORITY).append(".var<0").append(" )");
					w.append("&&");
					generateTransitionGuard(w,p,t,trans,gnand);
					w.append(")");
				}

			}
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
		++current_offset;

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
	private int handleVariable(CStruct sg, Variable var, String desc, int current_offset, LTSminTypeStruct ls) {
		return handleVariable(sg,var,desc,"",current_offset, ls, null);
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
	private int handleVariable(CStruct sg, Variable var, String desc, String forcedName, int current_offset, LTSminTypeStruct ls, VarDescriptor vd) {

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
				ls.members.add(new LTSminTypeBasic(C_TYPE_CHANNEL, wrapNameForChannelDesc(name)));
				model.addElement(new LTSminStateElement(var,desc+"."+var.getName()));
				for(String s: vd.extractDescription()) {
					current_offset = insertVariable(sg, var, desc, s, current_offset);
					model.addElement(new LTSminStateElement(var,desc+"."+var.getName()));
				}
				sg.addMember(vd.getType(),vd.extractDeclaration());
				ls.members.add(new LTSminTypeBasic(vd.getType(), vd.extractDeclaration()));

			} else {

				say("Adding CHANNEL: " + var.getName() + " " + var.hashCode());
				current_offset = insertVariable(sg, var,desc,wrapNameForChannelDesc(name), current_offset);
				sg.addMember(C_TYPE_CHANNEL,wrapNameForChannelDesc(name));
				ls.members.add(new LTSminTypeBasic(C_TYPE_CHANNEL, wrapNameForChannelDesc(name)));
				model.addElement(new LTSminStateElement(var,desc+"."+var.getName()));

			}

		} else if(var.getType() instanceof VariableType) {
			if(var.getType().getJavaName().equals("int")) {
				say("  " + name + " @" + current_offset + " (" + var.getType().getName() + ")");

				// Add global to the global state struct
				TypeDesc td = getCTypeOfVar(var);
				sg.addMember(td,name);
				ls.members.add(new LTSminTypeBasic(td.type, name,var.getArraySize()));

				if (var.getArraySize() > 1) {
					for(int i=0; i<var.getArraySize(); ++i) {
						current_offset = insertVariable(sg,var,desc,name,current_offset);
						model.addElement(new LTSminStateElement(var,desc+"."+var.getName()));
					}
				} else {
					current_offset = insertVariable(sg,var,desc,name,current_offset);
					model.addElement(new LTSminStateElement(var,desc+"."+var.getName()));
				}

			} else if(var.getType().getJavaName().equals("Type")) {

				// Untested
				CustomVariableType cvt = (CustomVariableType)var.getType();
				for(Variable v: cvt.getVariableStore().getVariables()) {
					current_offset = handleVariable(sg,v,name+".",name,current_offset,ls,vd);
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
	static public String wrapName(String name) {
		return name;
	}

	/**
	 * Generates a channel name given the specified name.
	 * Also cleans it.
	 * @param name The name to use to generate a channel name.
	 * @return The generated, clean channel name.
	 */
	static public String wrapNameForChannel(String name) {
		return "ch_"+wrapName(name)+"_t";
	}

	/**
	 * Generates a channel descriptor name given the specified name.
	 * Also cleans it.
	 * @param name The name to use to generate a channel descriptor name.
	 * @return The generated, clean channel descriptor name.
	 */
	static public String wrapNameForChannelDesc(String name) {
		return wrapName(name);
	}

	/**
	 * Generates a channel buffer name given the specified name.
	 * Also cleans it.
	 * @param name The name to use to generate a channel buffer name.
	 * @return The generated, clean channel buffer name.
	 */
	static public String wrapNameForChannelBuffer(String name) {
		return wrapName(name)+"_buffer";
	}

	/**
	 * Checks for any dependency in the specified expression and adds found
	 * dependencies to the dependency matrix.
	 * @param process The process owning the expression.
	 * @param trans The transition ID in which the expression resides.
	 * @param e The expression to check for dependencies.
	 */
	private void handleAssignDependency(Proctype process, int trans, Expression e) {
		if(e instanceof Identifier) {
			Identifier id = (Identifier)e;
			Variable var = id.getVariable();
			Expression arrayExpr = id.getArrayExpr();
			if (var.getArraySize() > 1) {
				if (arrayExpr != null) {
					try {
						int i = arrayExpr.getConstantValue();
						dep_matrix.incWrite(trans, state_var_offset.get(var)+i);
						dep_matrix.decrRead(trans, state_var_offset.get(var)+i);
					} catch(ParseException pe) {
						for(int i=0; i<var.getArraySize(); ++i) {
							dep_matrix.incWrite(trans, state_var_offset.get(var)+i);
							dep_matrix.decrRead(trans, state_var_offset.get(var)+i);
						}
					}
				} else {
					dep_matrix.incWrite(trans, state_var_offset.get(var));
					dep_matrix.decrRead(trans, state_var_offset.get(var));
				}
			} else {
				dep_matrix.incWrite(trans, state_var_offset.get(var));
				dep_matrix.decrRead(trans, state_var_offset.get(var));
			}
		} else {
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+e.getClass().getName());
		}
	}

	/**
	 * Generate Pre code for a rendezvous couple.
	 */
	private void generatePreRendezVousAction(StringWriter w, SendAction sa, ReadAction ra, int trans, LTSminTransition lt) {
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

		// Dependency matrix: process counter, prioritiseProcess
		dep_matrix.incRead(trans, state_proc_offset.get(sa.p));
		dep_matrix.incRead(trans, state_proc_offset.get(ra.p));
		dep_matrix.incRead(trans, offset_priority);
		dep_matrix.incWrite(trans, state_proc_offset.get(sa.p));
		dep_matrix.incWrite(trans, state_proc_offset.get(ra.p));
		dep_matrix.incWrite(trans, offset_priority);

		guard_matrix.addGuard(trans,makePCGuard(sa.t.getFrom(), sa.p));
		guard_matrix.addGuard(trans,makePCGuard(ra.t.getFrom(), ra.p));

		lt.addGuard(new LTSminGuard(trans,makePCGuard(sa.t.getFrom(), sa.p)));
		lt.addGuard(new LTSminGuard(trans,makePCGuard(ra.t.getFrom(), ra.p)));

		w.appendLine("if(",C_STATE_TMP,".",wrapName(sa.p.getName()),".",C_STATE_PROC_COUNTER,".var == ",sa.t.getFrom().getStateId(),
		                        " && ",C_STATE_TMP,".",wrapName(ra.p.getName()),".",C_STATE_PROC_COUNTER,".var == ",ra.t.getFrom().getStateId()," && ");
		w.appendLine("( ",C_STATE_TMP,".",C_PRIORITY,".var == ",state_proc_offset.get(sa.p)," || ",C_STATE_TMP,".",C_PRIORITY,".var == ",state_proc_offset.get(ra.p)," || ",C_STATE_TMP,".",C_PRIORITY,".var<0"," ) ) {");
		w.indent();

		w.appendPrefix();
		w.append("if( true /* Channel matches */");
		for (int i = 0; i < cra_exprs.size(); i++) {
			final Expression csa_expr = csa_exprs.get(i);
			final Expression cra_expr = cra_exprs.get(i);
			if (!(cra_expr instanceof Identifier)) {
				lt.addGuard(new LTSminGuard(trans,new CompareExpression(new Token(PromelaConstants.EQ,"=="),csa_expr,cra_expr)));
				w.append(" && (");
				try {
					generateIntExpression(w, null, csa_expr,trans,lt);
				} catch(ParseException e) {
					e.printStackTrace();
				}
				w.append(" == ");
				try {
					generateIntExpression(w, null, cra_expr,trans,lt);
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
	private void generatePostRendezVousAction(StringWriter w, SendAction sa, ReadAction ra, int trans) {
		w.outdent();

//		if(sa.t.getFrom()!=null && sa.t.getFrom().isInAtomic()) {
//			w.appendLine("} else if(",C_STATE_TMP,".",C_PRIORITY,".var == ",state_proc_offset.get(sa.p),") {");
//			w.indent();
//			w.appendLine(C_STATE_TMP,".",C_PRIORITY,".var = -1;");
//			w.appendLine("printf(\"[",state_proc_offset.get(sa.p),"] rendezvous: lost atomicity\\n\");");
//			//w.appendLine("printf(\"[",state_proc_offset.get(sa.p),"] handled %i losses of atomicity so far\\n\",++n_losses);");
//			w.appendLine("callback(arg,&transition_info,&tmp);");
//			w.appendLine("return ",1,";");
//			//w.appendLine("return spinja_get_successor_all_advanced(model,&tmp,callback,arg);");
//			w.outdent();
//			w.appendLine("}");
//		} else {
			w.appendLine("}");
//		}

		generateDependencymatrixStats(w,dep_matrix,trans);

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
	private void generateRendezVousAction(StringWriter w, SendAction sa, ReadAction ra, int trans, Transition never_t, LTSminTransition lt) {
		ChannelSendAction csa = sa.csa;
		ChannelReadAction cra = ra.cra;

		// Pre
		generatePreRendezVousAction(w,sa,ra,trans,lt);

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

		lt.addAction(new AssignAction(
								new Token(PromelaConstants.ASSIGN,"="),
								new PCIdentifier(sa.p),
								new ConstantExpression(new Token(PromelaConstants.NUMBER,""+sa.t.getTo().getStateId()),sa.t.getTo().getStateId())));
		lt.addAction(new AssignAction(
								new Token(PromelaConstants.ASSIGN,"="),
								new PCIdentifier(ra.p),
								new ConstantExpression(new Token(PromelaConstants.NUMBER,""+ra.t.getTo().getStateId()),ra.t.getTo().getStateId())));

		List<Expression> csa_exprs = csa.getExprs();
		List<Expression> cra_exprs = cra.getExprs();
		for (int i = 0; i < cra_exprs.size(); i++) {
			final Expression csa_expr = csa_exprs.get(i);
			final Expression cra_expr = cra_exprs.get(i);
			if ((cra_expr instanceof Identifier)) {
				lt.addAction(new AssignAction(
									new Token(PromelaConstants.ASSIGN,"="),
									(Identifier)cra_expr,
									csa_expr));
				w.appendPrefix();
				try {
					generateIntExpression(w, null, cra_expr,trans,lt);
				} catch(ParseException e) {
					e.printStackTrace();
				}
				w.append(" = ");

				handleAssignDependency(ra.p,trans,cra_expr);
				try {
					generateIntExpression(w, null, csa_expr,trans,lt);
				} catch(ParseException e) {
					e.printStackTrace();
				}
				w.append(";");
				w.appendPostfix();
			}
		}

//		if(ra.t.getFrom()!=null && ra.t.getFrom().isInAtomic()) {
//			w.appendLine("printf(\"handled atomic statements so far: %i\\n\",++n_atomics);");
//		}

		if(ra.t.getTo()!=null && ra.t.getTo().isInAtomic()) {
			//w.appendLine(C_STATE_TMP,".",C_PRIORITY,".var = ",process.getID(),";");
			w.appendLine(C_STATE_TMP,".",C_PRIORITY,".var = ",state_proc_offset.get(ra.p),";");
			lt.addAction(new AssignAction(
									new Token(PromelaConstants.ASSIGN,"="),
									priorityIdentifier,
									new ConstantExpression(new Token(PromelaConstants.NUMBER,""+state_proc_offset.get(ra.p)), state_proc_offset.get(ra.p))));
			w.appendLine("printf(\"[",state_proc_offset.get(sa.p),"] rendezvous: transferred atomicity to ",state_proc_offset.get(ra.p)," \\n\");");
			w.appendLine("callback(arg,&transition_info,&tmp);");
			w.appendLine("return ",1,";");
		} else {
			w.appendLine(C_STATE_TMP,".",C_PRIORITY,".var = ",-1,";");
				lt.addAction(new AssignAction(
										new Token(PromelaConstants.ASSIGN,"="),
										priorityIdentifier,
										new ConstantExpression(new Token(PromelaConstants.NUMBER,"-1"), -1)));
			// Generate the callback and the rest
			//if(t.getTo()!=null) {
				w.appendLine("callback(arg,&transition_info,&tmp);");
				w.appendLine("return ",1,";");
			//}
		}

		// Post
		generatePostRendezVousAction(w,sa,ra,trans);
		
		w.appendLine("return 0;");
	}

	private void generateStateDescriptors(StringWriter w) {

		// Generate static list of names
		w.appendLine("static const char* var_names[] = {");
		w.indent();

		w.appendPrefix();
		w.append("\"").append(getStateDescription(0)).append("\"");
		for(int i=1; i<state_size; ++i) {
			w.append(",");
			w.appendPostfix();
			w.appendPrefix();
			w.append("\"").append(getStateDescription(i)).append("\"");
		}
		w.appendPostfix();

		w.outdent();
		w.appendLine("};");

		// Generate static list of types
		List<String> types = new ArrayList<String>();
		int translation[] = new int[state_size];

		int i = 0;
		for(;i<state_size;) {
			Variable var = state_vector_var.get(i);
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

	/**
	 * Generates some general statistics about the generated model.
	 * @param w
	 * @param start_t Time the generation started.
	 * @param end_t Time the generation ended.
	 */
	private void generateStatistics(StringWriter w, long start_t, long end_t) {
		String old_preprefix = w.getPrePrefix();
		String old_prefix = w.getPrefix();
		w.appendLine("/*");

		w.setPrePrefix(" * ");
		w.setPrefix("  ");

		w.appendLine("Generated in: ",end_t-start_t,"ms");
		w.appendLine("");

		w.appendLine("State vector:");
		w.setPrePrefix(" *  - ");
		for(int i=0; i<state_size; ++i) {
			w.appendPrefix();
			w.append(getStateDescription(i));
			w.append(": ");
			w.append(getStateType(i));
			w.appendPostfix();
		}
		w.setPrePrefix(" * ");

		w.appendLine("");
		model.prettyPrint(w,this);

		w.setPrePrefix(old_preprefix);
		w.setPrefix(old_prefix);

		w.appendLine(" */");
	}

	/**
	 * Generates some statistics about the dependency matrix, written in
	 * comments.
	 * @param w
	 * @param dep_matrix
	 * @param trans
	 */
	private void generateDependencymatrixStats(StringWriter w, DepMatrix dep_matrix, int trans) {
		w.appendPrefix();
		DepRow row = dep_matrix.getRow(trans);
		w.append("// read: ");
		for(int i=0; i<row.getSize(); ++i) {
			w.append(" ").append(row.getReadB(i));
		}
		w.appendPostfix().appendPrefix();
		w.append("// write:");
		for(int i=0; i<row.getSize(); ++i) {
			w.append(" ").append(row.getWriteB(i));
		}
		w.appendPostfix();
	}

	private void generateBuchiIsAccepting(StringWriter w) {
		w.appendLine("int spinja_buchi_is_accepting(void* model, ",C_STATE_T,"* ",C_STATE_TMP,") {");
		w.indent();

		//w.appendLine("printf(\"buchi try!\\n\");");

		if(spec.getNever()!=null) {
			say("Buchi has " + spec.getNever().getAutomaton().size());
			boolean first = true;
			Iterator<State> i = spec.getNever().getAutomaton().iterator();
			while(i.hasNext()) {
				State s = i.next();

				if(s.isAcceptState()||s.isEndingState()) {
					say("Buchi accepts: " + s.getStateId());
					if(!first) {
						if(s.isEndingState()) {w.append("#ifndef NOENDACCEPT").appendPostfix(); w.appendPrefix();}
						else w.removePostfix();
						w.append(" else ");
						first = false;
					} else {
						if(s.isEndingState()) {w.append("#ifndef NOENDACCEPT").appendPostfix(); }
						w.appendPrefix();
					}
					w.append("if( ")
					 .append(C_STATE_TMP)
					 .append("->")
					 .append(wrapName(spec.getNever().getName()))
					 .append(".")
					 .append(C_STATE_PROC_COUNTER)
					 .append(".var == ")
					 .append(s.getStateId())
					 .append(" ) {");
					w.appendPostfix();
					w.indent();
					//w.appendLine("printf(\"buchi accept found!\\n\");");
					w.appendLine("return 1;");
					w.outdent();
					w.appendLine("}");
					if(s.isEndingState()) {w.append("#endif").appendPostfix(); w.appendLine("");}
				} else {
					say("Buchi does NOT accept: " + s.getStateId());
				}
			}
		}
		w.appendLine("return 0;");

		w.outdent();
		w.appendLine("}");
	}

	private void generateIsAtomic(StringWriter w) {
		w.appendLine("int spinja_is_atomic(void* model, ",C_STATE_T,"* ",C_STATE_TMP,") {");
		w.indent();

		w.appendLine("return ",C_STATE_TMP,"->",C_PRIORITY,".var >= 0;");

		w.outdent();
		w.appendLine("}");
	}

	private void generateGuards(StringWriter w) {

		/* function: get_guard_count */

		w.appendLine("int get_guard_count() {");
		w.indent();

		w.appendLine("return ",0,";");

		w.outdent();
		w.appendLine("}");

		/* function: get_guard_matrix */

		w.appendLine("");

		/* function: get_guards */

		/* function: get_all_guards */

		/* function: get_guard */

		/* function: get_guard_all */

	}

	private void extractGuards(Expression e, int trans) {
		if(e instanceof AritmicExpression) {
			AritmicExpression ae = (AritmicExpression)e;
			Expression e1 = ae.getExpr1();
			Expression e2 = ae.getExpr2();
			Expression e3 = ae.getExpr3();
			if(e3!=null) {
			} else if (e2 != null) {
				if(ae.getToken().image.equals("&&")) {
					extractGuards(e1,trans);
					extractGuards(e2,trans);
				}
			} else if (e1 != null) {
			} else {
			}
		}

		System.out.println("Adding guard: " + e.toString());
	}

	class PCExpression extends Expression {
		private String processName;

		public String getProcessName() {
			return processName;
		}

		public void setProcessName(String processName) {
			this.processName = processName;
		}

		public PCExpression(String processName) {
			super(new Token(PromelaConstants.NUMBER,C_STATE_TMP + "." + wrapName(processName) + "." + C_STATE_PROC_COUNTER));
			this.processName = processName;
		}
		public Set<VariableAccess> readVariables() {
			return null;
		}
		public VariableType getResultType() throws ParseException {
			return null;
		}

		@Override
		public String getIntExpression() {
			return C_STATE_TMP + "." + wrapName(processName) + "." + C_STATE_PROC_COUNTER;
		}
	}

	class PriorityExpression extends Expression {

		public PriorityExpression() {
			super(new Token(PromelaConstants.NUMBER,C_STATE_TMP + "." + C_PRIORITY));
		}
		public Set<VariableAccess> readVariables() {
			return null;
		}
		public VariableType getResultType() throws ParseException {
			return null;
		}

		@Override
		public String getIntExpression() {
			return C_STATE_TMP + "." + C_PRIORITY;
		}

	}

	class ChannelSizeExpression extends Expression {
		private ChannelVariable var;

		public ChannelSizeExpression(ChannelVariable var) {
			super(new Token(PromelaConstants.NUMBER,wrapNameForChannelDesc(state_var_desc.get(var))+".filled"));
			this.var = var;
		}
		public Set<VariableAccess> readVariables() {
			return null;
		}
		public VariableType getResultType() throws ParseException {
			return null;
		}

		public Variable getVariable() {
			return var;
		}

		@Override
		public String getIntExpression() {
			return "("+C_STATE_TMP + "." + wrapNameForChannelDesc(state_var_desc.get(var))+".filled)";
		}

	}

	class ChannelTopExpression extends Expression {
		private ChannelReadAction cra;
		private int elem;

		public ChannelTopExpression(ChannelReadAction cra, int elem) {
			super(new Token(PromelaConstants.NUMBER,"[" + wrapNameForChannelDesc(state_var_desc.get(cra.getVariable()))+".nextRead].m"+elem));
			this.cra = cra;
			this.elem = elem;
		}
		public Set<VariableAccess> readVariables() {
			return null;
		}
		public VariableType getResultType() throws ParseException {
			return null;
		}

		public ChannelReadAction getChannelReadAction() {
			return cra;
		}

		public int getElem() {
			return elem;
		}

		@Override
		public String getIntExpression() {
			return "(["+C_STATE_TMP + "." + wrapNameForChannelDesc(state_var_desc.get(cra.getVariable()))+".nextRead].m" + elem + ")";
		}

	}

	class ChannelTailExpression extends Expression {
		private ChannelReadAction cra;
		private int elem;

		public ChannelTailExpression(ChannelReadAction cra, int elem) {
			super(new Token(PromelaConstants.NUMBER,"[" + wrapNameForChannelDesc(state_var_desc.get(cra.getVariable()))+".nextRead].m"+elem));
			this.cra = cra;
			this.elem = elem;
		}
		public Set<VariableAccess> readVariables() {
			return null;
		}
		public VariableType getResultType() throws ParseException {
			return null;
		}

		public ChannelReadAction getChannelReadAction() {
			return cra;
		}

		public int getElem() {
			return elem;
		}

		@Override
		public String getIntExpression() {
			return "(["+C_STATE_TMP + "." + wrapNameForChannelDesc(state_var_desc.get(cra.getVariable()))+".nextRead].m" + elem + ")";
		}

	}

	class ResetProcessAction extends Action {
		private Proctype process;
		private Variable procVar;

		public ResetProcessAction(Proctype process) {
			super(new Token(PromelaConstants.ASSIGN, "=reset="));
			this.process = process;
			this.procVar = processIdentifiers.get(process);
		}

		public Proctype getProcess() {
			return process;
		}

		public Variable getProcVar() {
			return procVar;
		}

		@Override
		public String toString() {
			return "";
		}

		@Override
		public String getEnabledExpression() throws ParseException {
			throw new UnsupportedOperationException("Not supported yet.");
		}

		@Override
		public void printTakeStatement(StringWriter w) throws ParseException {
			throw new UnsupportedOperationException("Not supported yet.");
		}

	}

	private Expression makePCGuard(State s, Proctype p) {
		Expression left = new PCIdentifier(p);
		Expression right = new ConstantExpression(new Token(PromelaConstants.NUMBER,""+s.getStateId()), s.getStateId());
		Expression e = new CompareExpression(new Token(PromelaConstants.EQ,"=="), left, right);
		return e;
	}

	private Expression makePCDeathGuard(Proctype p) {
		Expression left = new PCIdentifier(p);
		Expression right = new ConstantExpression(new Token(PromelaConstants.NUMBER,"-1"), -1);
		Expression e = new CompareExpression(new Token(PromelaConstants.EQ,"=="), left, right);
		return e;
	}
	private Expression makePCDeathGuardN(Proctype p) {
		Expression left = new PCIdentifier(p);
		Expression right = new ConstantExpression(new Token(PromelaConstants.NUMBER,"0"), 0);
		Expression e = new CompareExpression(new Token(PromelaConstants.GTE,">="), left, right);
		return e;
	}

	private Expression makeAllowedToDie(Proctype p) {
		int i = procs.indexOf(p) + 1;
		if(i<procs.size()) {
			return makePCDeathGuard(procs.get(i));
		} else {
			return new ConstantExpression(new Token(PromelaConstants.TRUE,"1"), 1);
		}
	}

	private Expression makeAtomicGuard(Proctype p) {
		Expression left = new PriorityIdentifier();
		Expression right = new ConstantExpression(new Token(PromelaConstants.NUMBER,"0"), 0);
		Expression e = new CompareExpression(new Token(PromelaConstants.LT,"<"), left, right);

		Expression left2 = new PriorityIdentifier();
		Expression right2 = new ConstantExpression(new Token(PromelaConstants.NUMBER,""+state_proc_offset.get(p)), state_proc_offset.get(p));
		Expression e2 = new CompareExpression(new Token(PromelaConstants.EQ,"=="), left2, right2);

		return new BooleanExpression(new Token(PromelaConstants.LOR,"||"), e, e2);
	}

	private Expression makeExclusiveAtomicGuard(Proctype p) {
		Expression left2 = new PriorityIdentifier();
		Expression right2 = new ConstantExpression(new Token(PromelaConstants.NUMBER,""+state_proc_offset.get(p)), state_proc_offset.get(p));
		return new CompareExpression(new Token(PromelaConstants.EQ,"=="), left2, right2);
	}

	private Expression makeChannelUnfilledGuard(ChannelVariable var) {
		Expression left = new ChannelSizeExpression(var);
		Expression right = new ConstantExpression(new Token(PromelaConstants.NUMBER,""+var.getType().getBufferSize()), var.getType().getBufferSize());
		Expression e = new CompareExpression(new Token(PromelaConstants.LT,"<"), left, right);
		return e;
	}

	private Expression makeChannelHasContentsGuard(ChannelVariable var) {
		Expression left = new ChannelSizeExpression(var);
		Expression right = new ConstantExpression(new Token(PromelaConstants.NUMBER,"0"), 0);
		Expression e = new CompareExpression(new Token(PromelaConstants.GT,">"), left, right);
		return e;
	}

}


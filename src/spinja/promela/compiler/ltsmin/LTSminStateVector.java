package spinja.promela.compiler.ltsmin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import spinja.promela.compiler.Proctype;
import spinja.promela.compiler.Specification;
import spinja.promela.compiler.expression.Identifier;
import spinja.promela.compiler.ltsmin.instr.CStruct;
import spinja.promela.compiler.ltsmin.instr.PCIdentifier;
import spinja.promela.compiler.ltsmin.instr.PriorityIdentifier;
import spinja.promela.compiler.ltsmin.instr.TypeDesc;
import spinja.promela.compiler.ltsmin.instr.VarDescriptor;
import spinja.promela.compiler.ltsmin.instr.VarDescriptorArray;
import spinja.promela.compiler.ltsmin.instr.VarDescriptorChannel;
import spinja.promela.compiler.ltsmin.instr.VarDescriptorVar;
import spinja.promela.compiler.parser.Token;
import spinja.promela.compiler.variable.ChannelType;
import spinja.promela.compiler.variable.ChannelVariable;
import spinja.promela.compiler.variable.CustomVariableType;
import spinja.promela.compiler.variable.Variable;
import spinja.promela.compiler.variable.VariableStore;
import spinja.promela.compiler.variable.VariableType;

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
public class LTSminStateVector implements Iterable<LTSminStateElement> {

	/// The size of one element in the state struct in bytes.
	public static final int STATE_ELEMENT_SIZE = 4;

	public static final String C_STATE_T = "state_t";
	public static final String C_STATE_GLOBALS_T = "state_globals_t";
	public static final String C_STATE_GLOBALS = "globals";
	public static final String C_STATE_PROC_COUNTER = "pc";
	public static final String C_NUM_PROCS_VAR = "_nr_pr";
	public static final String C_STATE_SIZE = "state_size";
	public static final String C_STATE_INITIAL = "initial";
	public static final String NUM_PROCS_VAR = "_nr_pr";

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

	public static final Variable _NR_PR = new Variable(VariableType.BYTE, C_NUM_PROCS_VAR, 1);
	
	private HashMap<Variable,Integer> state_var_offset;
	private HashMap<Variable, String> state_var_desc;
	private HashMap<Proctype,Integer> state_proc_offset;

	private List<LTSminStateElement> stateVector;

	// The variables in the state struct
	private List<Variable> state_vector_var;

	// Textual description of the state vector, per integer
	private List<String> state_vector_desc;

	// The CStruct state vector
	private CStruct state;

	private HashMap<Proctype,Identifier> PCIDs;

	private LTSminModel model;

	/**
	 * Creates a new LTSMinPrinter using the specified Specification.
	 * After this, the instrument() member will instrument and return C code.
	 * @param spec The Specification using which C code is instrumentd.
	 * @param model the LTSminModel
	 */
	public LTSminStateVector(LTSminModel model) {
		this.model = model;
		state_var_offset = new HashMap<Variable,Integer>();
		state_var_desc = new HashMap<Variable,String>();
		state_proc_offset = new HashMap<Proctype,Integer>();
		state = null;
		stateVector = new ArrayList<LTSminStateElement>();
		state_vector_desc = new ArrayList<String>();
		state_vector_var = new ArrayList<Variable>();
		PCIDs = new HashMap<Proctype,Identifier>();
	}

	/**
	 * genrates and returns C code according to the Specification provided
	 * when creating this LTSMinPrinter instance.
	 * @return The C code according to the Specification.
	 */
	public void createVectorStructs(Specification spec, LTSminDebug debug) {
		createCustomStructs(spec);
		createStateStructs(spec, debug);
	}

	/**
	 * For the specified variable, instrument a custom struct typedef and print
	 * to the StringWriter.
	 * ChannelVariable's are also remembered, for later use. In particular for
	 * rendezvous.
	 * @param w The StringWriter to which the code is written.
	 * @param var The variable of which a custom typedef is requested.
	 */
	private void buildCustomStruct(Variable var) {

		// Handle the ChannelType variable type
		if(var.getType() instanceof ChannelType) {
			ChannelVariable cv = (ChannelVariable)var;
			ChannelType ct = cv.getType();
			//skip uninitialized channels (ie proc arguments) and rendez-vous channels
			if (ct.getBufferSize() == -1 || ct.getBufferSize() == 0 ) return;
			// Create a new C struct generator
			CStruct struct = new CStruct(wrapNameForChannel(var.getName()));
			VariableStore vs = ct.getVariableStore();
			LTSminTypeStruct ls = new LTSminTypeStruct(wrapNameForChannel(var.getName()));
			// Only instrument members for non-rendezvous channels
			if (ct.getBufferSize() > 0) {
				int j=0;
				for(Variable v: vs.getVariables()) {
					TypeDesc td = getCTypeOfVar(v);
					struct.addMember(td,"m"+j);
					ls.members.add(new LTSminTypeBasic(td.type,"m"+j));
					++j;
				}
			}

			model.addType(ls);
		}

	}

	/**
	 * Parse all globals and all local variables of processes to instrument
	 * custom struct typedefs where needed. Calls instrumentCustomStruct() for
	 * variable that need it.
	 * @param w
	 */
	private void createCustomStructs(Specification spec) {

		// Globals
		VariableStore globals = spec.getVariableStore();
		List<Variable> vars = globals.getVariables();
		for(Variable var: vars) {
			buildCustomStruct(var);
		}

		// Locals
		for(Proctype p : spec) {
			List<Variable> proc_vars = p.getVariables();
			for(Variable var: proc_vars) {
				buildCustomStruct(var);
			}
			PCIDs.put(p, procId(p));
		}
	}

	/**
	 * instruments the C code for the state structs and fills the following
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
	private Variable never_var;
	private List<Variable> procs_var = new ArrayList<Variable>();
	private HashMap<Proctype,Variable> processIdentifiers = new HashMap<Proctype, Variable>();
	private void createStateStructs(Specification spec, LTSminDebug debug) {

		// Current offset in the state struct
		int current_offset = 0;

		// List of state structs inside the main state struct
		List<CStruct> state_members = new ArrayList<CStruct>();

		// The main state struct
		state = new CStruct(C_STATE_T);

		LTSminTypeStruct ls_t = new LTSminTypeStruct(C_STATE_T);

		// Globals: initialise globals state struct and add to main state struct
		debug.say("== Globals");
		CStruct sg = new CStruct(C_STATE_GLOBALS_T);
		LTSminTypeStruct ls_g = new LTSminTypeStruct(C_STATE_GLOBALS_T);
		model.addType(ls_g);

		// Add priority process
		{
			ls_g.members.add(new LTSminTypeBasic(C_TYPE_INT32, C_STATE_PRIORITY));
			sg.addMember(C_TYPE_INT32, C_STATE_PRIORITY);
			++current_offset;
			state_vector_desc.add(C_PRIORITY);
			state_vector_var.add(null);
			addElement(new LTSminStateElement(PriorityIdentifier.priorVar));
		}

		// Globals: add globals to the global state struct
		VariableStore globals = spec.getVariableStore();
		globals.addVariable(_NR_PR);
		List<Variable> vars = globals.getVariables();
		for(Variable var: vars) {
			// Add global to the global state struct and fix the offset
			current_offset = handleVariable(sg,var,C_STATE_GLOBALS+".",current_offset,ls_g, debug);
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
				addElement(new LTSminStateElement(never_var));
				model.addType(ls_p);
				processIdentifiers.put(p,never_var);
			}
		}

		// Processes:
		debug.say("== Processes");
		for(Proctype p : spec) {
			// Process' name
			String name = wrapName(p.getName());

			// Initialise process state struct and add to main state struct
			debug.say("[Proc] " + name + " @" + current_offset);
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
				Variable var = new Variable(VariableType.INT, C_STATE_TMP + "." + wrapName(p.getName()), 1, p);
				procs_var.add(var);
				addElement(new LTSminStateElement(var,name+"."+var.getName()));
				processIdentifiers.put(p,var);
			}
			
			// Locals: add locals to the process state struct
			List<Variable> proc_vars = p.getVariables();
			Set<Variable> args = new HashSet<Variable>(p.getArguments());
			for(Variable var: proc_vars) {
				if (args.contains(var)) {
					if (var.getType() instanceof ChannelType)
						continue; // channel types are passed as reference
								  // the tree walker modifies the AST to make
								  // the argument point directly to the real channel
				}
				current_offset = handleVariable(proc_sg,var,name + ".",current_offset,ls_p, debug);
			}

			// Add process state struct to main state struct
			state_members.add(proc_sg);
			model.addType(ls_p);
		}
		model.addType(ls_t);
	}

	private int insertVariable(CStruct sg, Variable var, String desc, String name, int current_offset) {
		if(!state_var_offset.containsKey(var)) {
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
	private int handleVariable(CStruct sg, Variable var, String desc, int current_offset, LTSminTypeStruct ls, LTSminDebug debug) {
		return handleVariable(sg,var,desc,"",current_offset, ls, debug);
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
	private int handleVariable(CStruct sg, Variable var, String desc, String name, int current_offset, LTSminTypeStruct ls, LTSminDebug debug) {
		if(name==null || name.equals("")) 
			name = var.getName();
		if(var.getType() instanceof ChannelType) {
			ChannelVariable cv = (ChannelVariable)var;
			ChannelType ct = cv.getType();
			if (ct.getBufferSize() == 0) return current_offset; //skip rendez-vous channels
			VariableStore vs = ct.getVariableStore();

			VarDescriptor vd = new VarDescriptorVar(wrapNameForChannelBuffer(name));
			if(var.getArraySize()>1) {
				vd = new VarDescriptorArray(vd,var.getArraySize());
			}
			vd = new VarDescriptorArray(vd,ct.getBufferSize());
			vd = new VarDescriptorChannel(vd,vs.getVariables().size());
			vd.setType(wrapNameForChannel(name));

			debug.say(current_offset +"\t"+ var.getName() + " ["+ ct.getBufferSize() +"] of {"+ vs.getVariables().size() +"}");
			current_offset = insertVariable(sg, var,desc,wrapNameForChannelDesc(name), current_offset);
			sg.addMember(C_TYPE_CHANNEL,wrapNameForChannelDesc(name));
			ls.members.add(new LTSminTypeBasic(C_TYPE_CHANNEL, wrapNameForChannelDesc(name)));
			addElement(new LTSminStateElement(var,desc+"."+var.getName()));
		
			for(String s: vd.extractDescription()) {
				current_offset = insertVariable(sg, var, desc, s, current_offset);
				addElement(new LTSminStateElement(var,desc+"."+var.getName(), false));
			}
			sg.addMember(vd.getType(),vd.extractDeclaration());
			ls.members.add(new LTSminTypeBasic(vd.getType(), vd.extractDeclaration()));

		} else if(var.getType() instanceof VariableType) {
			if(var.getType().getJavaName().equals("int")) {
				debug.say(current_offset +"\t"+ var.getType().getName() +" "+ name);

				// Add global to the global state struct
				TypeDesc td = getCTypeOfVar(var);
				sg.addMember(td,name);
				ls.members.add(new LTSminTypeBasic(td.type, name,var.getArraySize()));
				if (var.getArraySize() > 1) {
					for(int i=0; i<var.getArraySize(); ++i) {
						current_offset = insertVariable(sg,var,desc,name,current_offset);
						addElement(new LTSminStateElement(var,desc+"."+var.getName()));
					}
				} else {
					current_offset = insertVariable(sg,var,desc,name,current_offset);
					addElement(new LTSminStateElement(var,desc+"."+var.getName()));
				}
			} else if(var.getType().getJavaName().equals("Type")) {
				//TODO: Untested
				CustomVariableType cvt = (CustomVariableType)var.getType();
				for(Variable v: cvt.getVariableStore().getVariables()) {
					current_offset = handleVariable(sg,v,name+".",name,current_offset,ls, debug);
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
				throw new AssertionError("ERROR: Unable to handle: " + v.getRealName());
		}
		
		int size = v.getArraySize();
		if(size>1) {
			td.array = "[" + size + "]";
		}
		return td;
	}

	public List<LTSminStateElement> getStateVector() {
		return stateVector;
	}

	public void addElement(LTSminStateElement element) {
		Integer i = model.getVariables().get(element.getVariable());
		if(i!=null) {
			if(stateVector.get(stateVector.size()-1).getVariable()!=element.getVariable()) {
				throw new AssertionError("Adding inconsecutive variable");
			}
		} else {
			i = stateVector.size();
		}
		int s = stateVector.size();
		stateVector.add(i,element);
		if(s+1 != stateVector.size()) throw new AssertionError("Failed to add element");
		model.getVariables().put(element.getVariable(),i);
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
	 * instruments a channel name given the specified name.
	 * Also cleans it.
	 * @param name The name to use to instrument a channel name.
	 * @return The instrumentd, clean channel name.
	 */
	static public String wrapNameForChannel(String name) {
		return "ch_"+wrapName(name)+"_t";
	}

	/**
	 * instruments a channel descriptor name given the specified name.
	 * Also cleans it.
	 * @param name The name to use to instrument a channel descriptor name.
	 * @return The instrumentd, clean channel descriptor name.
	 */
	static public String wrapNameForChannelDesc(String name) {
		return wrapName(name);
	}

	/**
	 * instruments a channel buffer name given the specified name.
	 * Also cleans it.
	 * @param name The name to use to instrument a channel buffer name.
	 * @return The instrumentd, clean channel buffer name.
	 */
	static public String wrapNameForChannelBuffer(String name) {
		return wrapName(name)+"_buffer";
	}

	public static AssertionError error(String string, Token token) {
		return new AssertionError(string + " At line "+token.beginLine +"column "+ token.beginColumn +".");
	}

	public int procOffset(Proctype process) {
		return state_proc_offset.get(process);
	}

	public Variable getProcId(Proctype p) {
		return processIdentifiers.get(p);
	}

	public PCIdentifier procId(Proctype p) {
		return new PCIdentifier(p, processIdentifiers.get(p));
	}

	public String getDescr(Variable variable) {
		return state_var_desc.get(variable);
	}

	@Override
	public Iterator<LTSminStateElement> iterator() {
		return stateVector.iterator();
	}

	public int size() {
		return stateVector.size();
	}

	public LTSminStateElement get(int i) {
		return stateVector.get(i);
	}
}

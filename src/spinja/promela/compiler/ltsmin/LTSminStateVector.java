package spinja.promela.compiler.ltsmin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static spinja.promela.compiler.ltsmin.LTSminTreeWalker.*;

import spinja.promela.compiler.Proctype;
import spinja.promela.compiler.Specification;
import spinja.promela.compiler.ltsmin.instr.CStruct;
import spinja.promela.compiler.ltsmin.instr.TypeDesc;
import spinja.promela.compiler.ltsmin.instr.VarDescriptor;
import spinja.promela.compiler.ltsmin.instr.VarDescriptorArray;
import spinja.promela.compiler.ltsmin.instr.VarDescriptorChannel;
import spinja.promela.compiler.ltsmin.instr.VarDescriptorVar;
import spinja.promela.compiler.parser.ParseException;
import spinja.promela.compiler.parser.Token;
import spinja.promela.compiler.variable.ChannelType;
import spinja.promela.compiler.variable.ChannelVariable;
import spinja.promela.compiler.variable.CustomVariableType;
import spinja.promela.compiler.variable.Variable;
import spinja.promela.compiler.variable.VariableStore;
import spinja.promela.compiler.variable.VariableType;

/**
 * This class handles the generation of the State vector
 * FIXME: handle state vector of length 0
 *
 * @author Freark van der Berg, Alfons Laarman
 */
public class LTSminStateVector implements Iterable<LTSminStateElement> {

	/// The size of one element in the state struct in bytes.
	public static final int STATE_ELEMENT_SIZE = 4;

	public static final String C_STATE_T = "state_t";
	public static final String C_STATE_GLOBALS_T = "state_globals_t";
	public static final String C_STATE_GLOBALS = "globals";
	public static final String C_STATE_PROC_COUNTER = "_pc";
	public static final String C_STATE_PID = "_pid";
	public static final String C_NUM_PROCS_VAR = "_nr_pr";
	public static final String C_STATE_SIZE = "state_size";
	public static final String C_STATE_INITIAL = "initial";
	public static final String NUM_PROCS_VAR = "_nr_pr";

	public static final String C_STATE_TMP = "tmp";
	public static final String C_STATE_NEVER = "never";
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
	public final String C_TYPE_PID;
	public static final String C_TYPE_PROC_COUNTER_ = "int";

	public static final Variable _NR_PR = new Variable(VariableType.BYTE, C_NUM_PROCS_VAR, 1);

	private HashMap<Proctype,Variable> processIdentifier;
	private HashMap<Proctype,Variable> pcs;

	private List<LTSminStateElement> stateVector;

	// The CStruct state vector
	private CStruct state;

	private LTSminModel model;

	/**
	 * Creates a new LTSMinPrinter using the specified Specification.
	 * After this, the instrument() member will instrument and return C code.
	 * @param spec The Specification using which C code is instrumentd.
	 * @param model the LTSminModel
	 */
	public LTSminStateVector(LTSminModel model) {
		this.model = model;
		state = null;
		stateVector = new ArrayList<LTSminStateElement>();
		processIdentifier = new HashMap<Proctype,Variable>();
		pcs = new HashMap<Proctype,Variable>();
		C_TYPE_PID = getCType(C_NUM_PROCS_VAR, VariableType.PID);
	}

	/**
	 * generates and returns C code according to the Specification provided
	 * when creating this LTSMinPrinter instance.
	 * @return The C code according to the Specification.
	 */
	public void createVectorStructs(Specification spec, LTSminDebug debug) {
		createCustomStructs(spec);
		createStateStructs(spec, debug);
	}

	/**
	 * For the specified variable, build a custom struct typedef and print
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
			// Only build members for non-rendezvous channels
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
	 * Parse all globals and all local variables of processes to create
	 * custom struct typedefs where needed. Calls buildCustomStruct() for
	 * variable that need it.
	 * @param w
	 */
	private void createCustomStructs(Specification spec) {

		// Globals
		VariableStore globals = spec.getVariableStore();
		List<Variable> vars = globals.getVariables();
		for (Variable var : vars) {
			buildCustomStruct(var);
		}

		// Locals
		for (Proctype p : spec) {
			List<Variable> proc_vars = p.getVariables();
			for (Variable var : proc_vars) {
				buildCustomStruct(var);
			}
		}
	}

	/**
	 * Creates the C code for the state structs and fills the following
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
	private void createStateStructs(Specification spec, LTSminDebug debug) {

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

		// Globals: add globals to the global state struct
		VariableStore globals = spec.getVariableStore();
		globals.addVariable(_NR_PR);
		
		List<Variable> vars = globals.getVariables();
		for (Variable var : vars) {
			// Add global to the global state struct and fix the offset
			handleVariable(sg, var, C_STATE_GLOBALS+".", ls_g, debug);
		}

		// Add global state struct to main state struct
		// Add it even if there are no global variables, since priorityProcess
		// is a 'global'
		state_members.add(sg);
		state.addMember(C_STATE_GLOBALS_T, C_STATE_GLOBALS);

		ls_t.members.add(new LTSminTypeBasic(C_STATE_GLOBALS_T, C_STATE_GLOBALS));

		// Add Never process
		if(spec.getNever()!=null) {
			Proctype p = spec.getNever();
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


			// Add process state struct to main state struct
			state_members.add(proc_never);
			Variable pc = new Variable(VariableType.INT, "_pc", 0, p);
			try { pc.setInitExpr(constant(0));
			} catch (ParseException e) { assert (false); }
			addElement(new LTSminStateElement(pc));
			pcs.put(p, pc);
			model.addType(ls_p);
		}

		// Processes:
		debug.say("== Processes");
		int nr_active = 0;
		for (Proctype p : spec) {
			nr_active += p.getNrActive();
			
			// Process' name
			String name = wrapName(p.getName());

			Variable pc = new Variable(VariableType.INT, "_pc", 0, p);
			addElement(new LTSminStateElement(pc));
			int initial_pc = (p.getNrActive() == 0 ? -1 : 0);
			try { pc.setInitExpr(constant(initial_pc));
			} catch (ParseException e) { assert (false); }
			pcs.put(p, pc);
			Variable pid = new Variable(VariableType.PID, "_pid", 0, p);
			addElement(new LTSminStateElement(pid));
			try { pid.setInitExpr(constant(p.getID()));
			} catch (ParseException e) { assert (false); }
			processIdentifier.put(p, pid);
			
			// Initialise process state struct and add to main state struct
			debug.say("[Proc] " + name);
			CStruct proc_sg = new CStruct("state_"+name+"_t"); // fix name
			state.addMember("state_"+name+"_t", name); //fix name

			LTSminTypeStruct ls_p = new LTSminTypeStruct("state_"+name+"_t");
			ls_t.members.add(new LTSminTypeBasic("state_"+name+"_t",wrapName(name)));
			// Add process to Proctype->offset map and add a description


			// Add process counter to process state struct
			proc_sg.addMember(C_TYPE_PROC_COUNTER,C_STATE_PROC_COUNTER);
			ls_p.members.add(new LTSminTypeBasic(C_TYPE_PROC_COUNTER,C_STATE_PROC_COUNTER));
			proc_sg.addMember(C_TYPE_PID,C_STATE_PID);
			ls_p.members.add(new LTSminTypeBasic(C_TYPE_PID,C_STATE_PID));
		
			// Locals: add locals to the process state struct
			List<Variable> proc_vars = p.getVariables();
			Set<Variable> args = new HashSet<Variable>(p.getArguments());
			for (Variable var : proc_vars) {
				if (args.contains(var) && var.getType() instanceof ChannelType)
						continue; // channel types are passed as reference
								  // the tree walker modifies the AST to make
								  // the argument point directly to the real channel
				handleVariable(proc_sg,var,name + ".",ls_p, debug);
			}

			p.addVariable(pid);
			p.addVariable(pc);

			// Add process state struct to main state struct
			state_members.add(proc_sg);
			model.addType(ls_p);
		}

		// set number of processes to initial number of active processes.
		try { _NR_PR.setInitExpr(constant(nr_active));
		} catch (ParseException e) {assert (false);}		

		model.addType(ls_t);
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
	private void handleVariable(CStruct sg, Variable var, String desc,  LTSminTypeStruct ls, LTSminDebug debug) {
		handleVariable(sg,var,desc,"", ls, debug);
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
	private void handleVariable(CStruct sg, Variable var, String desc, String name,
								LTSminTypeStruct ls, LTSminDebug debug) {
		if(name==null || name.equals("")) 
			name = var.getName();
		if(var.getType() instanceof ChannelType) {
			ChannelVariable cv = (ChannelVariable)var;
			ChannelType ct = cv.getType();
			if (ct.getBufferSize() == 0) return; //skip rendez-vous channels
			VariableStore vs = ct.getVariableStore();

			// channel meta info
			if (ct.getBufferSize() > 255)
				throw new AssertionError("Channel buffer cannot be larger than "+ 255);
			debug.say("\t"+ var.getName() + "["+ var.getArraySize() +"]" +
					" of {"+ vs.getVariables().size() +"} ["+ ct.getBufferSize() +"]");
			sg.addMember(C_TYPE_CHANNEL,wrapNameForChannelDesc(name));
			ls.members.add(new LTSminTypeBasic(C_TYPE_CHANNEL, wrapNameForChannelDesc(name), var.getArraySize()));
			VarDescriptor chan_info = new VarDescriptorVar(wrapNameForChannelDesc(name));
			if(var.getArraySize()>1)
				chan_info = new VarDescriptorArray(chan_info,var.getArraySize());
			for (@SuppressWarnings("unused") String s : chan_info.extractDescription())
				addElement(new LTSminStateElement(var,desc+"."+var.getName(), true));

			// channel buffer
			VarDescriptor vd = new VarDescriptorVar(wrapNameForChannelBuffer(name));
			if(var.getArraySize()>1)
				vd = new VarDescriptorArray(vd,var.getArraySize());
			vd = new VarDescriptorArray(vd,ct.getBufferSize());
			vd = new VarDescriptorChannel(vd,vs.getVariables().size());
			vd.setType(wrapNameForChannel(name));
			for (@SuppressWarnings("unused") String s : vd.extractDescription())
				addElement(new LTSminStateElement(var,desc+"."+var.getName(), false));
			sg.addMember(vd.getType(),vd.extractDeclaration());
			ls.members.add(new LTSminTypeBasic(vd.getType(), vd.extractDeclaration()));
		} else if(var.getType() instanceof VariableType) {
			if (var.getType().getJavaName().equals("int")) {
				debug.say("\t"+ var.getType().getName() +" "+ name);

				// Add global to the global state struct
				TypeDesc td = getCTypeOfVar(var);
				sg.addMember(td,name);
				ls.members.add(new LTSminTypeBasic(td.type, name,var.getArraySize()));
				if (var.getArraySize() > 1) {
					for (int i=0; i<var.getArraySize(); ++i) {
						addElement(new LTSminStateElement(var,desc+"."+var.getName()));
					}
				} else {
					addElement(new LTSminStateElement(var,desc+"."+var.getName()));
				}
			} else if (var.getType().getJavaName().equals("Type")) {
				//TODO: Untested
				CustomVariableType cvt = (CustomVariableType)var.getType();
				for (Variable v : cvt.getVariableStore().getVariables()) {
					handleVariable(sg,v,name+".",name,ls, debug);
				}
			} else {
				throw new AssertionError("ERROR: Unknown error trying to handle an integer");
			}
		} else {
			throw new AssertionError("ERROR: Unable to handle: " + var.getType().getName());
		}
		return;
	}

	/**
	 * Returns the C typedef name for the given variable. This typedef
	 * has been defined earlier to pad data to STATE_ELEMENT_SIZE.
	 * @param v The Variable of which the C typedef is wanted.
	 * @return The C typedef name for the given variable.
	 */
	static public TypeDesc getCTypeOfVar(Variable v) {
		TypeDesc td = new TypeDesc();
		VariableType type = v.getType();
		td.type = getCType(v.getRealName(), type);
		
		int size = v.getArraySize();
		if(size>1) {
			td.array = "[" + size + "]";
		}
		return td;
	}

	private static String getCType(String name, VariableType type)
			throws AssertionError {
		switch(type.getBits()) {
			case 1:
				return C_TYPE_INT1;
			case 8:
				return C_TYPE_UINT8;
			case 16:
				return C_TYPE_INT16;
			case 32:
				return C_TYPE_INT32;
			default:
				throw new AssertionError("ERROR: Unable to handle: " + name);
		}
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

	@Override
	public Iterator<LTSminStateElement> iterator() {
		return stateVector.iterator();
	}

	public int size() {
		return stateVector.size();
	}
	
	public Variable getPID(Proctype p) {
		return processIdentifier.get(p);
	}
	
	public Variable getPC(Proctype p) {
		return pcs.get(p);
	}

	public LTSminStateElement get(int i) {
		return stateVector.get(i);
	}
}

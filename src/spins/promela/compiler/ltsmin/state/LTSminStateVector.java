package spins.promela.compiler.ltsmin.state;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import spins.promela.compiler.ProcInstance;
import spins.promela.compiler.Proctype;
import spins.promela.compiler.Specification;
import spins.promela.compiler.expression.Identifier;
import spins.promela.compiler.ltsmin.LTSminPrinter.ExprPrinter;
import spins.promela.compiler.ltsmin.util.LTSminDebug;
import spins.promela.compiler.ltsmin.util.LTSminDebug.MessageKind;
import spins.promela.compiler.variable.ChannelType;
import spins.promela.compiler.variable.ChannelVariable;
import spins.promela.compiler.variable.CustomVariableType;
import spins.promela.compiler.variable.Variable;
import spins.promela.compiler.variable.VariableStore;
import spins.promela.compiler.variable.VariableType;

/**
 * This class is responsible for:
 * - Adding meta variables to the model (channel read/filled, process pic/pc)
 * - Creating a tree of type structures: state_t
 * - Flattening the state type into a fixed-length vector with slots repr. tree
 *   leafs (NativeTypes)
 * - Translation: c code names <-- vector slots <--> model variables 
 *
 * These functionalities are implemented using a Mixin pattern, this class
 * inherits from LTSminSubVector and LTSminTypeImpl 
 *
 * @author Freark van der Berg, Alfons Laarman
 */
public class LTSminStateVector extends LTSminSubVectorStruct
								implements LTSminTypeStructI<LTSminSlot> {

	public static String C_STATE;
	public static final String C_STATE_NAME = "state";
	public static final String C_STATE_GLOBALS = "globals";

	private List<LTSminSlot> 			stateVector;// the flattened vector
	LTSminTypeStruct 					state_t;	// tree of structs

	/**
	 * Creates a new StateVector
	 */
	public LTSminStateVector() {
		super();
		super.setRoot(this);
		state_t = new LTSminTypeStruct(C_STATE_NAME);
		C_STATE = state_t.getName();
		super.setType(state_t);
		stateVector = new ArrayList<LTSminSlot>();
	}

	/**
	 * Creates the state vector and required types
	 */
	public void createVectorStructs(Specification spec, LTSminDebug debug) {
	    debug.say("Creating state vector");
	    debug.say_indent++;

		addSpecification(state_t, spec, debug);	
		flattenStateVector(state_t, "");
		state_t.fix();

        debug.say_indent--;
	}
	
	List<LTSminTypeStruct> types = null;
	public List<LTSminTypeStruct> getTypes() {
		if (null == types) {
			types = new ArrayList<LTSminTypeStruct>();
			Set<LTSminTypeStruct> seen = new HashSet<LTSminTypeStruct>();
			extractStructs(types, state_t, seen);
		}
		return types;
	}

	private void extractStructs(List<LTSminTypeStruct> list,
								LTSminTypeStruct struct,
								Set<LTSminTypeStruct> seen) {
		if (!seen.add(struct)) return;
		for (LTSminVariable v : struct) {
			if (v.getType() instanceof LTSminTypeStruct) {
				extractStructs(list, (LTSminTypeStruct)v.getType(), seen);
			}
		}
		list.add(struct);
	}

	/**
	 * Flattens the state vector into a fixed-length array of slots.
	 * @param type the state vector
	 */
	private void flattenStateVector(LTSminTypeStruct type, String fullName) {
		for (LTSminVariable v : type) {
			// recursion
			for (int i = 0; i < Math.max(v.array(), 1); i++) {
				String fn = fullName +"."+ v.getName() + //TODO: use ExprPrinter
						(v.array() > -1 ? "["+i+"]" : "");
				if (v.getType() instanceof LTSminTypeStruct) {
					flattenStateVector ((LTSminTypeStruct)v.getType(), fn);
				} else {
					// Leafs (NativeTypes) in DFS order
					//System.out.println(stateVector.size() +"\t"+ fn +"");
					stateVector.add(new LTSminSlot(v, fn +"."+ LTSminTypeNative.ACCESS, stateVector.size()));
				}
			}
		}
	}

	/**
	 * Extract processes and globals from spec and add it to state_t
	 */
	private void addSpecification(LTSminTypeStruct state_t, Specification spec,
			LTSminDebug debug) {
		// Globals: initialise globals state struct and add to main state struct
		debug.say(MessageKind.DEBUG, "== Globals");
		LTSminTypeStruct global_t = new LTSminTypeStruct(C_STATE_GLOBALS);
		VariableStore globals = spec.getVariableStore();
		for (Variable var : globals.getVariables())
			addVariable(global_t, var, debug);
		// Add global state struct to main state struct
		//addMember(new LTSminVariable(global_t, C_STATE_GLOBALS, this));

		// Add Never process
		if (spec.getNever()!=null) {
			debug.say(MessageKind.DEBUG, "== Never");
			Proctype p = spec.getNever();
			addProcess (state_t, p, debug);
		}
		int i = 0;
		// Processes:
		debug.say(MessageKind.DEBUG, "== Processes");
		String prevName = "";
		boolean emitted_globals = false;
		for (ProcInstance p : spec) {
            if (!emitted_globals && i >= spec.instances() / 2 &&
                !prevName.equals(p.getProcName())) {
                addMember(new LTSminVariable(global_t, C_STATE_GLOBALS, this));
                emitted_globals = true;
            }
            prevName = p.getProcName();
            i++;
            addProcess (state_t, p, debug);
		}
		if (!emitted_globals) {
            addMember(new LTSminVariable(global_t, C_STATE_GLOBALS, this));
		}
	}

	/**
	 * Add a variable declarations of proctype p to struct
	 */
	private void addProcess(LTSminTypeStruct state_t, Proctype p, LTSminDebug debug) {
		String name = p.getName();
		
		// Initialise process state struct and add to main state struct
		debug.say(MessageKind.DEBUG, "[Proc] " + name);
		LTSminTypeStruct process_t = new LTSminTypeStruct(name);
	
		// Locals: add locals to the process state struct
		for (Variable var : p.getVariables()) {
			addVariable(process_t, var, debug);
		}

		// Add process state struct to main state struct
		addMember(new LTSminVariable(process_t, name, this));
	}
	
	/**
	 * Add a variable declaration to struct
	 */
	private void addVariable(LTSminTypeStruct struct, Variable var, LTSminDebug debug) {
		if (var.isHidden() || !var.getName().equals(var.getDisplayName())) return;
		String name = var.getName();
		LTSminVariable lvar = null;

		// Create LTSminType for the Variable
		if(var instanceof ChannelVariable) {
			ChannelVariable cv = (ChannelVariable)var;
			ChannelType ct = cv.getType();
			//skip channels references (ie proc arguments) and rendez-vous channels
			if (ct.isRendezVous() || ct.getBufferSize() == -1)
				return;
			debug.say(MessageKind.DEBUG, var.getName() + (var.getArraySize() == -1 ? "" : "["+ var.getArraySize() +"]") +
					" of {"+ ct.getTypes().size() +"} ["+ ct.getBufferSize() +"]");
			LTSminTypeI infoType = new LTSminTypeChanStruct(cv);
			lvar = new LTSminVariable(infoType, var, struct);
		} else if (var.getType() instanceof CustomVariableType) {
			CustomVariableType cvt = (CustomVariableType)var.getType();
			LTSminTypeStruct type = new LTSminTypeStruct(cvt.getName());
			for (Variable v : cvt.getVariableStore().getVariables())
				addVariable(type, v, debug);
			lvar = new LTSminVariable(type, var, struct);
		} else if (var.getType() instanceof VariableType) {
			debug.say(MessageKind.DEBUG, var.getType().getName() +" "+ name);
			lvar = new LTSminVariable(LTSminTypeNative.get(var), var, struct);
		} else {
			throw new AssertionError("ERROR: Unable to handle: " + var.getType().getName());
		}

		// Add it to the struct
		struct.addMember(lvar);
	}

	@Override
	public Iterator<LTSminSlot> iterator() {
		return stateVector.iterator();
	}

    public List<LTSminSlot> getSlots() {
        return stateVector;
    }

	public int size() {
		return stateVector.size();
	}

	public LTSminSlot get(int i) {
		return stateVector.get(i);
	}

	public LTSminSubVectorArray sub(Proctype proc) {
		String name = (null == proc ? C_STATE_GLOBALS : proc.getName());
		return getSubVector(name);
	}

	public LTSminSubVector sub(Variable v) {
		LTSminSubVectorArray ar = sub(v.getOwner());
		return ar.follow();
	}

	/*********************
	 * LTSminTypeStruct interface is implemented by delegation
	 * Multiple Inheritance Pattern
	 ***********************/
	public String getName() {
		return state_t.getName();
	}

	public void addMember(LTSminVariable var) {
		state_t.addMember(var);
	}

	public void fix() {
		state_t.fix();
	}

	public String printIdentifier(ExprPrinter p, Identifier id) {
		return state_t.printIdentifier(p, id);
	}

	public LTSminVariable getMember(String name) {
		return state_t.getMember(name);
	}

	// Additional sub type methods:

	public LTSminVariable getMember(Proctype proc) {
		String name = (null == proc ? C_STATE_GLOBALS : proc.getName());
		return getMember(name);
	}

}

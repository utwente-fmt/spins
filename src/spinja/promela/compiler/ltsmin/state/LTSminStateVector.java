package spinja.promela.compiler.ltsmin.state;

import static spinja.promela.compiler.ltsmin.model.LTSminUtil.constant;
import static spinja.promela.compiler.parser.Promela.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import spinja.promela.compiler.Proctype;
import spinja.promela.compiler.Specification;
import spinja.promela.compiler.expression.Identifier;
import spinja.promela.compiler.ltsmin.LTSminDebug;
import spinja.promela.compiler.ltsmin.LTSminDebug.MessageKind;
import spinja.promela.compiler.ltsmin.LTSminPrinter.ExprPrinter;
import spinja.promela.compiler.parser.ParseException;
import spinja.promela.compiler.variable.ChannelType;
import spinja.promela.compiler.variable.ChannelVariable;
import spinja.promela.compiler.variable.CustomVariableType;
import spinja.promela.compiler.variable.Variable;
import spinja.promela.compiler.variable.VariableStore;
import spinja.promela.compiler.variable.VariableType;

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

	private static final String C_NUM_PROCS_VAR = "_nr_pr";
	public static final Variable _NR_PR = new Variable(VariableType.INT, C_NUM_PROCS_VAR, -1);

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
		addSpecification(state_t, spec, debug);	
		flattenStateVector(state_t, "");
		state_t.fix();
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
		globals.addVariable(_NR_PR);
		for (Variable var : globals.getVariables())
			addVariable(global_t, var, debug);
		// Add global state struct to main state struct
		addMember(new LTSminVariable(global_t, C_STATE_GLOBALS, this));

		// Add Never process
		if (spec.getNever()!=null) {
			debug.say(MessageKind.DEBUG, "== Never");
			Proctype p = spec.getNever();
			addProcess (state_t, p, debug);
		}

		// Processes:
		debug.say(MessageKind.DEBUG, "== Processes");
		int nr_active = 0;
		for (Proctype p : spec) {
			addProcess (state_t, p, debug);
			nr_active += p.getNrActive();
		}
		// set number of processes to initial number of active processes.
		try { _NR_PR.setInitExpr(constant(nr_active));
		} catch (ParseException e) {assert (false);}
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
		List<Variable> proc_vars = p.getVariables();
		for (Variable var : proc_vars) {
			addVariable(process_t, var, debug);
		}

		// Add process state struct to main state struct
		addMember(new LTSminVariable(process_t, name, this));
	}
	
	/**
	 * Add a variable declaration to struct
	 */
	private void addVariable(LTSminTypeStruct struct, Variable var, LTSminDebug debug) {
		String name = var.getName();
		LTSminVariable lvar = null;
		debug.say_indent++;
		
		// Create LTSminType for the Variable
		if(var instanceof ChannelVariable) {
			ChannelVariable cv = (ChannelVariable)var;
			ChannelType ct = cv.getType();
			//skip channels references (ie proc arguments) and rendez-vous channels
			if (ct.getBufferSize() == -1 || ct.getBufferSize() == 0 ) return;
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
		} else if(var.getType() instanceof VariableType) {
			debug.say(MessageKind.DEBUG, var.getType().getName() +" "+ name);
			lvar = new LTSminVariable(new LTSminTypeNative(var), var, struct);
		} else {
			throw new AssertionError("ERROR: Unable to handle: " + var.getType().getName());
		}

		debug.say_indent--;
		// Add it to the struct
		struct.addMember(lvar);
	}

	@Override
	public Iterator<LTSminSlot> iterator() {
		return stateVector.iterator();
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

	public Variable getPC(Proctype process) {
		return process.getVariable(C_STATE_PROC_COUNTER);
	}

	public Variable getPID(Proctype p) {
		return p.getVariable(C_STATE_PID);
	}

	/*********************
	 * LTSminTypeStruct interface is implemented by delegation
	 * Multiple Inheritance Pattern
	 ***********************/
	@Override
	public String getName() {
		return state_t.getName();
	}

	@Override
	public void addMember(LTSminVariable var) {
		state_t.addMember(var);
	}

	@Override
	public void fix() {
		state_t.fix();
	}

	@Override
	public String printIdentifier(ExprPrinter p, Identifier id) {
		return state_t.printIdentifier(p, id);
	}

	@Override
	public LTSminVariable getMember(String name) {
		return state_t.getMember(name);
	}

	// Additional sub type methods:

	public LTSminVariable getMember(Proctype proc) {
		String name = (null == proc ? C_STATE_GLOBALS : proc.getName());
		return getMember(name);
	}

}

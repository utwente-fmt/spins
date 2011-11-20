package spinja.promela.compiler.ltsmin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import spinja.promela.compiler.Proctype;
import spinja.promela.compiler.Specification;
import spinja.promela.compiler.actions.Action;
import spinja.promela.compiler.actions.AssertAction;
import spinja.promela.compiler.actions.AssignAction;
import spinja.promela.compiler.actions.ChannelReadAction;
import spinja.promela.compiler.actions.ChannelSendAction;
import spinja.promela.compiler.actions.ExprAction;
import spinja.promela.compiler.actions.PrintAction;
import spinja.promela.compiler.automaton.Automaton;
import spinja.promela.compiler.automaton.ElseTransition;
import spinja.promela.compiler.automaton.State;
import spinja.promela.compiler.automaton.Transition;
import spinja.promela.compiler.expression.BooleanExpression;
import spinja.promela.compiler.expression.CompareExpression;
import spinja.promela.compiler.expression.ConstantExpression;
import spinja.promela.compiler.expression.Expression;
import spinja.promela.compiler.expression.Identifier;
import spinja.promela.compiler.ltsmin.instr.AtomicState;
import spinja.promela.compiler.ltsmin.instr.CStruct;
import spinja.promela.compiler.ltsmin.instr.ChannelSizeExpression;
import spinja.promela.compiler.ltsmin.instr.ChannelTopExpression;
import spinja.promela.compiler.ltsmin.instr.ElseTransitionItem;
import spinja.promela.compiler.ltsmin.instr.PCIdentifier;
import spinja.promela.compiler.ltsmin.instr.PriorityIdentifier;
import spinja.promela.compiler.ltsmin.instr.ReadAction;
import spinja.promela.compiler.ltsmin.instr.ReadersAndWriters;
import spinja.promela.compiler.ltsmin.instr.ResetProcessAction;
import spinja.promela.compiler.ltsmin.instr.SendAction;
import spinja.promela.compiler.ltsmin.instr.TimeoutTransition;
import spinja.promela.compiler.ltsmin.instr.TypeDesc;
import spinja.promela.compiler.ltsmin.instr.VarDescriptor;
import spinja.promela.compiler.ltsmin.instr.VarDescriptorArray;
import spinja.promela.compiler.ltsmin.instr.VarDescriptorChannel;
import spinja.promela.compiler.ltsmin.instr.VarDescriptorVar;
import spinja.promela.compiler.parser.ParseException;
import spinja.promela.compiler.parser.PromelaConstants;
import spinja.promela.compiler.parser.Token;
import spinja.promela.compiler.variable.ChannelType;
import spinja.promela.compiler.variable.ChannelVariable;
import spinja.promela.compiler.variable.CustomVariableType;
import spinja.promela.compiler.variable.Variable;
import spinja.promela.compiler.variable.VariableStore;
import spinja.promela.compiler.variable.VariableType;
import spinja.promela.compiler.expression.*;
import spinja.promela.compiler.parser.Token;
import spinja.promela.compiler.variable.VariableAccess;
import spinja.store.HashTable;
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
	public static HashMap<Variable, String> state_var_desc;
	private HashMap<Proctype,Integer> state_proc_offset;

	// For each channel, a list of read actions and send actions is kept
	// to later handle these separately
	private HashMap<ChannelVariable,ReadersAndWriters> channels;

	// The variables in the state struct
	private List<Variable> state_vector_var;

	// Textual description of the state vector, per integer
	private List<String> state_vector_desc;

	// Atomic states - of these loss of atomicity will be instrumentd
	private List<AtomicState> atomicStates;

	// The specification of which code is to be instrumentd,
	// initialised by constructor
	private final Specification spec;


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
	// These will be instrumentd after normal transitions
	List<ElseTransitionItem> else_transitions;

	private HashMap<Proctype,Identifier> PCIDs;

	private PriorityIdentifier priorityIdentifier;

	LTSminModel model;

	/**
	 * Creates a new LTSMinPrinter using the specified Specification.
	 * After this, the instrument() member will instrument and return C code.
	 * @param spec The Specification using which C code is instrumentd.
	 * @param name The name to give the model.
	 */
	public LTSMinPrinter(Specification spec, String name) {
		if(spec==null) {
			// error
		}
		this.spec = spec;

		state_var_offset = new HashMap<Variable,Integer>();
		state_var_desc = new HashMap<Variable,String>();
		state_proc_offset = new HashMap<Proctype,Integer>();
		state = null;
		state_vector_desc = new ArrayList<String>();
		state_vector_var = new ArrayList<Variable>();
		atomicStates = new ArrayList<AtomicState>();
		timeout_transitions = new ArrayList<TimeoutTransition>();
		else_transitions = new ArrayList<ElseTransitionItem>();

		model = new LTSminModel(name);

		channels = new HashMap<ChannelVariable,ReadersAndWriters>();
		PCIDs = new HashMap<Proctype,Identifier>();
		priorityIdentifier = new PriorityIdentifier();
	}

	/**
	 * instruments and returns C code according to the Specification provided
	 * when creating this LTSMinPrinter instance.
	 * @return The C code according to the Specification.
	 */
	public String generate() {
		//long start_t = System.currentTimeMillis();

		// Create structs describing channels and custom structs
		createCustomStructs();
		createStateStructs();
		instrumentTransitions();
		
		// Generate code for total time out expression
        if(spec.getNever()!=null) {
            instrumentTotalTimeout();
        }
        
		LTSminDMWalker.walkModel(model);
		LTSminGMWalker.walkModel(model);
		StringWriter w        = new StringWriter();
		LTSminPrinter2.generateModel(w, model);
		//long end_t = System.currentTimeMillis();
		return w.toString();
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

			// Create a new C struct generator
			CStruct struct = new CStruct(wrapNameForChannel(var.getName()));

			ChannelVariable cv = (ChannelVariable)var;
			ChannelType ct = cv.getType();
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

			// Remember this channel variable, to keep track of
			channels.put(cv,new ReadersAndWriters());

			model.addType(ls);
		}

	}

	/**
	 * Parse all globals and all local variables of processes to instrument
	 * custom struct typedefs where needed. Calls instrumentCustomStruct() for
	 * variable that need it.
	 * @param w
	 */
	private void createCustomStructs() {

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
			PCIDs.put(p,new PCIdentifier(p));
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
	public Variable never_var;
	public List<Variable> procs_var = new ArrayList<Variable>();
	static public HashMap<Proctype,Variable> processIdentifiers = new HashMap<Proctype, Variable>();
	private void createStateStructs() {

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
			model.addElement(new LTSminStateElement(PriorityIdentifier.priorVar));
		}

		// Globals: add globals to the global state struct
		VariableStore globals = spec.getVariableStore();
		globals.addVariable(_NR_PR);
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
		for(Proctype p : spec) {
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
				Variable var = new Variable(VariableType.INT, C_STATE_TMP + "." + wrapName(p.getName()), 1, p);
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
		model.addType(ls_t);
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

	// Helper functionality for debugging
	int say_indent = 0;
	private void say(String s) {
		for(int n=say_indent; n-->0;) {
			System.out.print("  ");
		}
		System.out.println(s);
	}

    /**
     * instruments the state transitions.
     * This calls instrumentTransitionsFromState() for every state in every process.
     * @param w The StringWriter to which the code is written.
     */
    private int instrumentTransitions() {
        int trans = instrumentTransitions_mid(0);
        trans = instrumentTransitions_post(trans);
        return trans;
    }
    
	/**
	 * instruments the state transitions.
	 * This calls instrumentTransitionsFromState() for every state in every process.
	 * @param w The StringWriter to which the code is written.
	 */
	private int instrumentTransitions_mid(int trans) {

		// instrument the normal transitions for all processes.
		// This does not include: rendezvous, else, timeout.
		// Loss of atomicity is handled separately as well.
		for(Proctype p: spec) {
			say("[Proc] " + p.getName());
			++say_indent;

			Automaton a = p.getAutomaton();

			// instrument transitions for all states in the process
			Iterator<State> i = a.iterator();
			while(i.hasNext()) {
				State st = i.next();

				Proctype never = spec.getNever();
				if(never!=null) {
					Automaton never_a = never.getAutomaton();
					Iterator<State> never_i = never_a.iterator();

					while(never_i.hasNext()) {
						trans = instrumentTransitionsFromState(p,trans,st,never_i.next());
					}
				} else {
						trans = instrumentTransitionsFromState(p,trans,st,null);
				}
			}

			--say_indent;
		}
		seenItAll = true;

		// instrument Else Transitions
		for(ElseTransitionItem eti: else_transitions) {
			Proctype never = spec.getNever();
			if(never!=null) {
				Automaton never_a = never.getAutomaton();
				Iterator<State> never_i = never_a.iterator();

				while(never_i.hasNext()) {
					State never_state = never_i.next();
					for(Transition never_t: never_state.output) {
						trans = instrumentStateTransition(eti.p, eti.t, trans,never_t);
					}
				}
			} else {
				trans = instrumentStateTransition(eti.p, eti.t, trans, null);
			}
		}

		// instrument the rendezvous transitions
		for(Map.Entry<ChannelVariable,ReadersAndWriters> e: channels.entrySet()) {
			//ChannelVariable cv = e.getKey(); //TODO: not used?
			ReadersAndWriters raw = e.getValue();
			for(SendAction sa: raw.sendActions) {
				for(ReadAction ra: raw.readActions) {
					//if(state_proc_offset.get(sa.p) != state_proc_offset.get(ra.p)) {

						// Add transition
						if(model.getTransitions().size() != trans) throw new AssertionError("Transition not set at correct location in the transition array");

						LTSminTransition lt = new LTSminTransition(sa.p);
						model.getTransitions().add(lt);

						Proctype never = spec.getNever();
						if(never!=null) {
							Automaton never_a = never.getAutomaton();
							Iterator<State> never_i = never_a.iterator();

							while(never_i.hasNext()) {
								State never_state = never_i.next();
								for(Transition never_t: never_state.output) {
									instrumentRendezVousAction(sa,ra,trans,never_t,lt);
								}
							}
						} else {
							instrumentRendezVousAction(sa,ra,trans,null,lt);
						}

						++trans;
					//}
				}

			}

		}
		return trans;
	}

	private int instrumentTransitions_post(int trans) {
		// Create loss of atomicity transition.
		// This is used when a process blocks inside an atomic transition.
		
		{ // Add transition
			if(model.getTransitions().size() != trans) throw new AssertionError("Transition not set at correct location in the transition array");
			LTSminTransitionCombo ltc = new LTSminTransitionCombo("loss of atomicity");
			model.getTransitions().add(ltc);

			for(AtomicState as: atomicStates) {
				LTSminTransition lt = new LTSminTransition(as.p);
				ltc.addTransition(lt);
				State s = as.s;
				Proctype process = as.p;
				assert (s.isInAtomic());

				lt.addGuard(new LTSminGuard(trans, makePCGuard(s, process)));
				lt.addGuard(new LTSminGuard(trans, makeExclusiveAtomicGuard(process)));

				for(Transition ot: s.output) {
					LTSminGuardNand gnand = new LTSminGuardNand();
					instrumentTransitionGuard(process,ot,trans,gnand);
					lt.addGuard(gnand);
				}

				lt.addAction(new AssignAction(
										new Token(PromelaConstants.ASSIGN,"="),
										priorityIdentifier,
										new ConstantExpression(new Token(PromelaConstants.NUMBER,"-1"), -1)));
			}
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

				LTSminGuardOr gor = new LTSminGuardOr();
				lt.addGuard(gor);

				Iterator<State> i = spec.getNever().getAutomaton().iterator();
				while(i.hasNext()) {
					State s = i.next();
					if(s.isAcceptState()) {
						gor.addGuard(new LTSminGuard(trans, makePCGuard(s, spec.getNever())));
					}
				}
				++trans;
			}

			// Add accepting cycle in the end state of never claim
			{
				// Add transition
				if(model.getTransitions().size() != trans) throw new AssertionError("Transition not set at correct location in the transition array");
				LTSminTransition lt = new LTSminTransition("cycle");
				model.getTransitions().add(lt);

				LTSminGuardOr gor = new LTSminGuardOr();
				lt.addGuard(gor);

				Iterator<State> i = spec.getNever().getAutomaton().iterator();
				while(i.hasNext()) {
					State s = i.next();
					if(s.isEndingState()) {
						gor.addGuard(new LTSminGuard(trans,makePCGuard(s, spec.getNever())));
					}
				}
				++trans;
			}

		}
		--say_indent;

		return trans;

	}

	/**
	 * instruments all transitions from the given state. This state should be
	 * in the specified process.
	 * @param w The StringWriter to which the code is written.
	 * @param process The state should be in this process.
	 * @param trans Starts generating transitions from this transition ID.
	 * @param state The state of which all outgoing transitions will be
	 * instrumentd.
	 * @return The next free transition ID
	 * ( = old.trans + "#transitions instrumentd" ).
	 */
	private int instrumentTransitionsFromState(Proctype process, int trans, State state, State never_state) {

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

			lt.addGuard(new LTSminGuard(trans, makePCGuard(state, process)));
			lt.addGuard(new LTSminGuard(trans, makeAtomicGuard(process)));
			lt.addGuard(new LTSminGuard(trans, makeAllowedToDie(process)));

			// In the case of an ending state, instrument a transition only
			// changing the process counter to -1.
			lt.addAction(new AssignAction(
							new Token(PromelaConstants.ASSIGN,"="),
							new PCIdentifier(process),
							new ConstantExpression(new Token(PromelaConstants.NUMBER,"-1"),-1)));

			// Keep track of the current transition ID
			++trans;
		} else {
			// In the normal case, instrument a transition changing the process
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

						// instrument transition
						trans = instrumentStateTransition(process,t,trans,never_t);

					}
				}
			} else {
				for(Transition t: state.output) {
					// instrument transition
					trans = instrumentStateTransition(process,t,trans,null);
				}
			}
			--say_indent;
		}

		// Return the next free transition ID
		return trans;

	}

	public int instrumentStateTransition(Proctype process, Transition t, int trans, Transition never_t) {
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

		// DO NOT try to instrument Else transitions immediately,
		// but buffer it until every state has been visited.
		// This is because during the normal generation, some transitions
		// are not instrumentd (e.g. rendezvous), so their enabledness is
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

		if(never_t!=null) {
			say("Handling trans: " + t.getClass().getName() + " || " + never_t.getClass().getName());
		} else {
			say("Handling trans: " + t.getClass().getName());
		}
		// Guard: process counter
		lt.addGuard(new LTSminGuard(trans, makePCGuard(t.getFrom(), process)));
		lt.addGuard(new LTSminGuard(trans, makeAtomicGuard(process)));

		if(never_t!=null) {
			lt.addGuard(new LTSminGuard(trans, makePCGuard(never_t.getFrom(),spec.getNever())));
		}
        // Check if the process is allowed to die if the target state is null
        if(t.getTo()==null) {
           // w.appendLine("&&( ALLOWED_DEATH_",wrapName(process.getName()),"() )");
//            guard_matrix.addGuard(trans,makeAllowedToDie(process));
//            lt.addGuard(new LTSminGuard(trans, makeAllowedToDie(process)));
        }
        
        instrumentTransitionGuard(process, t, trans, lt);
        
        if (never_t != null) {
        	instrumentTransitionGuard(process, never_t, trans, lt);
        }
        
        if(t instanceof ElseTransition) {
            ElseTransition et = (ElseTransition)t;
            for(Transition ot: t.getFrom().output) {
                if(ot!=et) {
                    instrumentTransitionGuard(process,ot,trans,lt);
                }
            }
        }
        if(never_t != null && never_t instanceof ElseTransition) {
            ElseTransition et = (ElseTransition)never_t;
            for(Transition ot: t.getFrom().output) {
                if(ot!=et) {
                    instrumentTransitionGuard(spec.getNever(),ot,trans,lt);
                }
            }
        }
        
		// If there is no never claim or the target state of the never
		// transition is not atomic, then instrument action code of the system
		// transition. Otherwise (if there is a claim and the target state is
		// atomic), do not instrument system transition code.
		// The dying transition (when never_t.getTo()==null) is not considered
		// atomic
		if(never_t == null || never_t.getTo()==null || !never_t.getTo().isInAtomic()) {
			// Change process counter to the next state.
			// For end transitions, the PC is changed to -1.
			lt.addAction(new AssignAction(
									new Token(PromelaConstants.ASSIGN,"="),
									new PCIdentifier(process),
									new ConstantExpression(new Token(PromelaConstants.NUMBER,""+(t.getTo()==null?-1:t.getTo().getStateId())),t.getTo()==null?-1:t.getTo().getStateId())));
			if(t.getTo()==null) lt.addAction(new ResetProcessAction(process));
	       
			for (Action a : t) {
	            lt.addAction(a);
	        }

			// If this transition is atomic
			if(t.getTo()!=null && t.getTo().isInAtomic()) {
				// Claim priority when taking this transition. It is
				// possible this process had already priority, so nothing
				// changes.
				lt.addAction(new AssignAction(
								new Token(PromelaConstants.ASSIGN,"="),
								priorityIdentifier,
								new ConstantExpression(new Token(PromelaConstants.NUMBER,""+state_proc_offset.get(process)), state_proc_offset.get(process))));
			// If this transition is not atomic
			} else {
				// Make sure no process has priority. This transition was
				// either executed while having priority and it is now given
				// up, or no process had priority and this remains the same.
				lt.addAction(new AssignAction(
								new Token(PromelaConstants.ASSIGN,"="),
								priorityIdentifier,
								new ConstantExpression(new Token(PromelaConstants.NUMBER,"-1"), -1)));
			}
		}

		// If there is a never claim, instrument the PC update code
		if(never_t != null) {
			lt.addAction(new AssignAction(
								new Token(PromelaConstants.ASSIGN,"="),
								new PCIdentifier(spec.getNever()),
								new ConstantExpression(new Token(PromelaConstants.NUMBER,""+(never_t.getTo()==null?-1:never_t.getTo().getStateId())),never_t.getTo()==null?-1:never_t.getTo().getStateId())));
		}

		return trans+1;
	}

	/**
	 * instruments the guard C code of a transition.
	 * @param w The StringWriter to which the code is written.
	 * @param process The state should be in this process.
	 * @param t The transition of which the guard will be instrumentd.
	 * @param trans The transition group ID to use for generation.
	 */
	void instrumentTransitionGuard(Proctype process, Transition t, int trans, LTSminGuardContainer lt) {
		try {
			Action a = null;
			if(t.getActionCount()>0) {
				a = t.getAction(0);
			}
			if(a!= null && a.getEnabledExpression()!=null) {
				instrumentEnabledExpression(process,a,t,trans,lt);
			}
			if(t.getTo()==null) {
				lt.addGuard(new LTSminGuard(trans,makeAllowedToDie(process)));
			}
		} catch(ParseException e) {
			e.printStackTrace();
		}
	}

	/**
	 * instruments the C code denoting when the specified Action is enabled.
	 * The enabledness of rendezvous channel actions can only be determined
	 * after all other transitions have been visited (when seenItAll is true).
	 * @param w The StringWriter to which the code is written.
	 * @param process The action should be in this process.
	 * @param a The action for which C code will be instrumentd.
	 * @param t The transition the action is in.
	 * @param trans The transition group ID to use for generation.
	 * @throws ParseException
	 */
	private void instrumentEnabledExpression(Proctype process, Action a, Transition t, int trans, LTSminGuardContainer lt) throws ParseException {
		// Handle assignment action
		if(a instanceof AssignAction) {

		// Handle assert action
		} else if(a instanceof AssertAction) {

		// Handle print action
		} else if(a instanceof PrintAction) {
			
		// Handle expression action
		} else if(a instanceof ExprAction) {
			ExprAction ea = (ExprAction)a;
			Expression expr = ea.getExpression();
			lt.addGuard(new LTSminGuard(trans, expr));
		// Handle a channel send action
		} else if(a instanceof ChannelSendAction) {
			ChannelSendAction csa = (ChannelSendAction)a;
			ChannelVariable var = (ChannelVariable)csa.getVariable();
			if(var.getType().getBufferSize()>0) {
				lt.addGuard(new LTSminGuard(trans,makeChannelUnfilledGuard(var)));
			} else if(seenItAll) {
				ReadersAndWriters raw = channels.get(var);
				LTSminGuardOr gor = new LTSminGuardOr();
				lt.addGuard(gor);
				for(ReadAction ra: raw.readActions) {
					List<Expression> csa_exprs = csa.getExprs();
					List<Expression> cra_exprs = ra.cra.getExprs();

					LTSminGuardAnd gand = new LTSminGuardAnd();
					gor.addGuard(gand);
					gand.addGuard(new LTSminGuard(trans, makePCGuard(ra.t.getFrom(), ra.p)));

					for (int i = 0; i < cra_exprs.size(); i++) {
						final Expression csa_expr = csa_exprs.get(i);
						final Expression cra_expr = cra_exprs.get(i);
						if (!(cra_expr instanceof Identifier)) {
							gand.addGuard(new LTSminGuard(trans,new CompareExpression(new Token(PromelaConstants.EQ,"=="),csa_expr,cra_expr)));
						}
					}
				}
			} else {
				throw new AssertionError("Trying to actionise rendezvous send before all others!");
			}

		// Handle a channel read action
		} else if(a instanceof ChannelReadAction) {
			ChannelReadAction cra = (ChannelReadAction)a;
			ChannelVariable var = (ChannelVariable)cra.getVariable();

			if(var.getType().getBufferSize()>0) {
				List<Expression> exprs = cra.getExprs();
				lt.addGuard(new LTSminGuard(trans,makeChannelHasContentsGuard(var)));

				for (int i = 0; i < exprs.size(); i++) {
					final Expression expr = exprs.get(i);
					if (!(expr instanceof Identifier)) {
						//throw new AssertionError("add guard addition here");
						lt.addGuard(new LTSminGuard(trans,new CompareExpression(
								new Token(PromelaConstants.EQ,"=="),
								new ChannelTopExpression(cra, i),expr))
						);
					}

				}
			} else if(seenItAll) {
				ReadersAndWriters raw = channels.get(var);
				LTSminGuardOr gor = new LTSminGuardOr();
				lt.addGuard(gor);
				for(SendAction sa: raw.sendActions) {
					List<Expression> csa_exprs = sa.csa.getExprs();
					List<Expression> cra_exprs = cra.getExprs();

					LTSminGuardAnd gand = new LTSminGuardAnd();
					gor.addGuard(gand);
					gand.addGuard(new LTSminGuard(trans, makePCGuard(sa.t.getFrom(), sa.p)));

					for (int i = 0; i < cra_exprs.size(); i++) {
						final Expression csa_expr = csa_exprs.get(i);
						final Expression cra_expr = cra_exprs.get(i);
						if (!(cra_expr instanceof Identifier)) {
							gand.addGuard(new LTSminGuard(trans,
									new CompareExpression(new Token(PromelaConstants.EQ,"=="),
											csa_expr,cra_expr)));

						}
					}
				}
			} else {
				throw new AssertionError("Trying to actionise rendezvous receive before all others!");
			}

			// Handle not yet implemented action
		} else {
			throw new ParseException("LTSMinPrinter: Not yet implemented: "+a.getClass().getName());
		}
	}

	/**
	 * instrument the timeout expression for the specified TimeoutTransition.
	 * This will instrument the expression that NO transition is enabled. The
	 * dependency matrix is fixed accordingly. If tt is null,
	 * @param w The StringWriter to which the code is written.
	 * @param tt The TimeoutTransition to instrument code for.
	 */
	public void instrumentTimeoutExpression(TimeoutTransition tt) {

		// Loop over all processes
		for(Proctype p: spec) {
			Automaton a = p.getAutomaton();
			for (State st : a) {
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
					tt.lt.addGuard(new LTSminGuard(tt.trans,makeAllowedToDie(p)));
					tt.lt.addGuard(new LTSminGuard(tt.trans,makeAtomicGuard(p)));
                    instrumentTransitionGuard(p,trans,tt.trans,tt.lt);
				}
			}
		}
	}

	/**
	 * instruments #define code for the total timeout expression.
	 * @param w The StringWriter to which the #define code will be written to.
	 */
	public void instrumentTotalTimeout() {
		instrumentTotalTimeoutExpression(total_timeout_id, lt_total_timeout);
	}

	/**
	 * instrument the total timeout expression. This expression is true iff
	 * no transition is enabled, including 'normal' time out transitions.
	 * The dependency matrix is fixed accordingly.
	 * @param w The StringWriter to which the code is written.
	 * @param tt The TimeoutTransition to instrument code for.
	 */
	public void instrumentTotalTimeoutExpression(int trans, LTSminTransition lt) {

		// Loop over all processes
		for(Proctype p: spec) {
			Automaton a = p.getAutomaton();
			// Loop over all states of the process
			for (State st : a) {
				// Cull other states of the current process
				//if(tt.p == p && tt.t.getFrom() != st) {
				//	continue;
				//}
				
				// Check if this state has an ElseTransition
				// If so, skip the transition, because this state
				// always has an active outgoing transition
				for(Transition t: st.output) {
					if (t instanceof ElseTransition) {
						continue;
					}
				}

				// Loop over all transitions of the state
				for(Transition t: st.output) {
					// Add the expression that the current transition from the
					// current state in the current process is not enabled.
					LTSminGuardNand gnand = new LTSminGuardNand();
					lt.addGuard(gnand);
					gnand.addGuard(new LTSminGuard(trans, makePCGuard(st, p)));
					gnand.addGuard(new LTSminGuard(trans, makeAtomicGuard(p)));
					instrumentTransitionGuard(p,t,trans,gnand);
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

	/**
	 * instrument Pre code for a rendezvous couple.
	 */
	private void instrumentPreRendezVousAction(SendAction sa, ReadAction ra, int trans, LTSminTransition lt) {
		ChannelSendAction csa = sa.csa;
		ChannelReadAction cra = ra.cra;


		if(csa.getVariable() != cra.getVariable()) {
			throw new AssertionError("instrumentRendezVousAction() called with inconsequent ChannelVariable");
		}
		ChannelVariable var = (ChannelVariable)csa.getVariable();
		if(var.getType().getBufferSize()>0) {
			throw new AssertionError("instrumentRendezVousAction() called with non-rendezvous channel");
		}

		List<Expression> csa_exprs = csa.getExprs();
		List<Expression> cra_exprs = cra.getExprs();

		if(csa_exprs.size() != cra_exprs.size()) {
			throw new AssertionError("instrumentRendezVousAction() called with incompatible actions: size mismatch");
		}

		lt.addGuard(new LTSminGuard(trans,makePCGuard(sa.t.getFrom(), sa.p)));
		lt.addGuard(new LTSminGuard(trans,makePCGuard(ra.t.getFrom(), ra.p)));

		/* Channel matches */
		for (int i = 0; i < cra_exprs.size(); i++) {
			final Expression csa_expr = csa_exprs.get(i);
			final Expression cra_expr = cra_exprs.get(i);
			if (!(cra_expr instanceof Identifier)) {
				lt.addGuard(new LTSminGuard(trans,new CompareExpression(new Token(PromelaConstants.EQ,"=="),csa_expr,cra_expr)));
			}
		}
	}
	
    /**
	 * instrument the transition for one rendezvous couple. The specified
	 * transition ID will be used to identify the instrumentd transition.
	 * @param w The StringWriter to which the code is written.
	 * @param sa The SendAction component.
	 * @param ra The ReadAction component.
	 * @param trans The transition ID to use for the instrumentd transition.
	 */
	private void instrumentRendezVousAction(SendAction sa, ReadAction ra, int trans, Transition never_t, LTSminTransition lt) {
		ChannelSendAction csa = sa.csa;
		ChannelReadAction cra = ra.cra;

		// Pre
		instrumentPreRendezVousAction(sa,ra,trans,lt);

		// Change process counter of sender
		lt.addAction(new AssignAction(
								new Token(PromelaConstants.ASSIGN,"="),
								new PCIdentifier(sa.p),
								new ConstantExpression(new Token(PromelaConstants.NUMBER,""+sa.t.getTo().getStateId()),sa.t.getTo().getStateId())));
		// Change process counter of receiver
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
			}
		}

		if(ra.t.getTo()!=null && ra.t.getTo().isInAtomic()) {
			lt.addAction(new AssignAction(
							new Token(PromelaConstants.ASSIGN,"="),
							priorityIdentifier,
							new ConstantExpression(new Token(PromelaConstants.NUMBER,""+state_proc_offset.get(ra.p)), state_proc_offset.get(ra.p))));
		} else {
			lt.addAction(new AssignAction(
							new Token(PromelaConstants.ASSIGN,"="),
							priorityIdentifier,
							new ConstantExpression(new Token(PromelaConstants.NUMBER,"-1"), -1)));
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

	/* TODO: die sequence of dynamically started processes ala http://spinroot.com/spin/Man/init.html */
	private Expression makeAllowedToDie(Proctype p) {
		Iterator<Proctype> it = spec.iterator();
		while (it.hasNext() && !it.next().equals(p)) {}
		if(it.hasNext()) {
			return makePCDeathGuard(it.next());
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


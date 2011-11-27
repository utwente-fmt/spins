package spinja.promela.compiler.ltsmin;

import static spinja.promela.compiler.parser.PromelaConstants.IDENTIFIER;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
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
import spinja.promela.compiler.automaton.ElseTransition;
import spinja.promela.compiler.automaton.State;
import spinja.promela.compiler.automaton.Transition;
import spinja.promela.compiler.expression.BooleanExpression;
import spinja.promela.compiler.expression.CompareExpression;
import spinja.promela.compiler.expression.ConstantExpression;
import spinja.promela.compiler.expression.Expression;
import spinja.promela.compiler.expression.Identifier;
import spinja.promela.compiler.expression.RunExpression;
import spinja.promela.compiler.expression.TimeoutExpression;
import spinja.promela.compiler.ltsmin.instr.AtomicState;
import spinja.promela.compiler.ltsmin.instr.ChannelSizeExpression;
import spinja.promela.compiler.ltsmin.instr.ChannelTopExpression;
import spinja.promela.compiler.ltsmin.instr.ElseTransitionItem;
import spinja.promela.compiler.ltsmin.instr.PriorityIdentifier;
import spinja.promela.compiler.ltsmin.instr.ReadAction;
import spinja.promela.compiler.ltsmin.instr.ReadersAndWriters;
import spinja.promela.compiler.ltsmin.instr.ResetProcessAction;
import spinja.promela.compiler.ltsmin.instr.SendAction;
import spinja.promela.compiler.ltsmin.instr.TimeoutTransition;
import spinja.promela.compiler.parser.ParseException;
import spinja.promela.compiler.parser.PromelaConstants;
import spinja.promela.compiler.parser.Token;
import spinja.promela.compiler.variable.ChannelType;
import spinja.promela.compiler.variable.ChannelVariable;
import spinja.promela.compiler.variable.Variable;
import spinja.promela.compiler.variable.VariableType;

/**
 * This class handles the generation of the LTSminModel.
 *
 * TODO: avoid atomic guards / priority updates when possible to improve DM
 * 
 * @author Freark van der Berg, Alfons Laarman
 */
public class LTSminTreeWalker {

	// The specification of which the model is created,
	// initialised by constructor
	private final Specification spec;

	private LTSminDebug debug = new LTSminDebug();

	private LTSminModel model;
	
	// For each channel, a list of read actions and send actions is kept for later processing
	private HashMap<ChannelVariable,ReadersAndWriters> channels;

	// Atomic states - for creation of loss of atomicity transitions
	private List<AtomicState> atomicStates;

	// List of Elsetransitions for delayed processing
	private List<ElseTransitionItem> else_transitions;

	// List of transition with a TimeoutExpression
    List<TimeoutTransition> timeout_transitions;

	private static final PriorityIdentifier priorityIdentifier  = new PriorityIdentifier();

	/**
	 * Creates a new LTSMinPrinter using the specified Specification.
	 * @param spec The specification.
	 * @param name The name to give the model.
	 */
	public LTSminTreeWalker(Specification spec, String name) {
		this.spec = spec;
		atomicStates = new ArrayList<AtomicState>();
		else_transitions = new ArrayList<ElseTransitionItem>();
        timeout_transitions = new ArrayList<TimeoutTransition>();
		channels = new HashMap<ChannelVariable,ReadersAndWriters>();
		model = new LTSminModel(name);
	}
		
	/**
	 * generates and returns an LTSminModel to the Specification provided
	 * when creating this LTSMinPrinter instance.
	 * @return The LTSminModel according to the Specification.
	 */
	public LTSminModel createLTSminModel() {
		//long start_t = System.currentTimeMillis();
		model.createVectorStructs(spec, debug);
		bindByReferenceCalls();
		createTransitions();
		LTSminDMWalker.walkModel(model);
		LTSminGMWalker.walkModel(model);
		//long end_t = System.currentTimeMillis();
		return model;
	}

	/**
	 * Binds any channel type arguments of all RunExpressions by reference.
	 */
	private void bindByReferenceCalls() {
		debug.say("");
		for (RunExpression re : spec.getRuns()){
			bindArguments(re);
		}
	}

	private void bindArguments(RunExpression re) {
		Proctype target = spec.getProcess(re.getId());
		List<Variable> args = target.getArguments();
		Iterator<Expression> eit = re.getExpressions().iterator();
		if (args.size() != re.getExpressions().size())
			throw error("Run expression's parameters do not match the proc's arguments.", re.getToken());
		//write to the arguments of the target process
		int 			count = 0;
		for (Variable v : args) {
			count++;
			Expression param = eit.next();
			if (v.getType() instanceof ChannelType) {
				if (!(param instanceof Identifier))
					throw error("Run expression's parameters do not match the proc's arguments.", re.getToken());
				Identifier id = (Identifier)param;
				Variable varParameter = id.getVariable();
				VariableType t = varParameter.getType();
				if (!(t instanceof ChannelType))
					throw error("Parameter "+ count +" of "+ re.getId() +" should be a channeltype.", re.getToken());
				ChannelType ct = (ChannelType)t;
				if (ct.getBufferSize() == -1) //TODO: implement more analysis on AST
					throw error("Could not deduce channel declaration for parameter "+ count +" of "+ re.getId() +".", re.getToken());
				String name = v.getName();
				debug.say("Binding "+ target +"."+ name +" to "+ varParameter.getOwner() +"."+ varParameter.getName());
				v.setRealName(v.getName());
				v.setType(varParameter.getType());
				v.setOwner(varParameter.getOwner());
				v.setName(varParameter.getName());
			}
		}
	}

	private Iterable<State> getNeverAutomatonOrNullSet(boolean forceNullSet) {
		if (forceNullSet || spec.getNever()==null)
			return new HashSet<State>(Arrays.asList((State)null));
		return spec.getNever().getAutomaton();
	}
	
	private Iterable<Transition> getOutTransitionsOrNullSet(State s) {
		if (s==null)
			return new HashSet<Transition>(Arrays.asList((Transition)null));
		return s.output;
	}
	
	/**
	 * Creates the state transitions.
	 */
	private int createTransitions() {
		int trans = 0;
		debug.say("");
		// Create the normal transitions for all processes.
		// This excludes: rendezvous, else, timeout and loss of atomicity
		// Calculate cross product with the never claim when not in atomic state 
		for (Proctype p : spec) {
			debug.say("[Proc] " + p.getName());
			for (State st : p.getAutomaton()) {
				for (State ns : getNeverAutomatonOrNullSet(st.isInAtomic())) {
					trans = createTransitionsFromState(p,trans,st, ns);
				}
			}
		}

		// Create else transitions, except for atomic states
		for (ElseTransitionItem eti : else_transitions) {
			for (State ns : getNeverAutomatonOrNullSet(eti.t.getFrom().isInAtomic())) {
				for (Transition nt : getOutTransitionsOrNullSet(ns)) {
					trans = createStateTransition(eti.p, eti.t, trans, nt);
				}
			}			
		}

		// create the rendezvous transitions
		for (Map.Entry<ChannelVariable,ReadersAndWriters> e : channels.entrySet()) {
			for (SendAction sa : e.getValue().sendActions) {
				for (ReadAction ra : e.getValue().readActions) {
					//if(state_proc_offset.get(sa.p) == state_proc_offset.get(ra.p)) continue;
					if (model.getTransitions().size() != trans)
						throw new AssertionError("Transition not set at correct location in the transition array");
					LTSminTransition lt = new LTSminTransition(sa.p);
					model.getTransitions().add(lt);
					for (State ns : getNeverAutomatonOrNullSet(false)) {
						for (Transition nt : getOutTransitionsOrNullSet(ns)) {
							createRendezVousAction(sa,ra,trans,nt,lt);
						}
					}
					++trans;
				}
			}
		}
		
		// Create loss of atomicity transition.
		// This is used when a process blocks inside an atomic transition.
		LTSminTransitionCombo ltc = new LTSminTransitionCombo("loss of atomicity");
		model.getTransitions().add(ltc);
		for (AtomicState as : atomicStates) {
			LTSminTransition lt = new LTSminTransition(as.p);
			ltc.addTransition(lt);
			State s = as.s;
			Proctype process = as.p;
			assert (s.isInAtomic());

			lt.addGuard(new LTSminGuard(trans, makePCGuard(s, process)));
			lt.addGuard(new LTSminGuard(trans, makeExclusiveAtomicGuard(process)));
			for (Transition ot : s.output) {
				LTSminGuardNand gnand = new LTSminGuardNand();
				createEnabledAndDieGuard(process,ot,trans,gnand);
				lt.addGuard(gnand);
			}
			lt.addAction(assign(priorityIdentifier, -1));
		}
		++trans;

		// Add total timeout transition in case of a never claim.
		// This is because otherwise accepting cycles might not be found,
		// although the never claim is violated.
		if (spec.getNever()!=null) {
            trans = createTotalTimeout(trans);
			LTSminTransition lt_cycle = new LTSminTransition("cycle");
			LTSminGuardOr gor = new LTSminGuardOr();
			lt_cycle.addGuard(gor);
			model.getTransitions().add(lt_cycle);
			for (State s : spec.getNever().getAutomaton()) {
				if (s.isEndingState()) {
					gor.addGuard(new LTSminGuard(trans,makePCGuard(s, spec.getNever())));
				}
			}
			++trans;
		}
		if (model.getTransitions().size() != trans)
			throw new AssertionError("Transition not set at correct location in the transition array");
		return trans;
	}

	/**
	 * Creates all transitions from the given state. This state should be
	 * in the specified process.
	 * @param process The state should be in this process.
	 * @param trans Starts generating transitions from this transition ID.
	 * @param state The state of which all outgoing transitions will be created.
	 * @return The next free transition ID (= old.trans + #new_transitions).
	 */
	private int createTransitionsFromState(Proctype process, int trans, State state, State never_state) {
		++debug.say_indent;
		debug.say(state.toString());

		// Check if it is an ending state
		if (state.sizeOut()==0) { // FIXME: Is this the correct prerequisite for THE end state of a process?
			LTSminTransition lt = new LTSminTransition(process);
			model.getTransitions().add(lt);

			lt.addGuard(new LTSminGuard(trans, makePCGuard(state, process)));
			lt.addGuard(new LTSminGuard(trans, makeAtomicGuard(process)));
			lt.addGuard(new LTSminGuard(trans, makeAllowedToDie(process)));

			// In the case of an ending state, create a transition only
			// changing the process counter to -1.
			lt.addAction(assign(model.sv.procId(process), -1));

			// Keep track of the current transition ID
			++trans;
		} else {
			// In the normal case, create a transition changing the process
			// counter to the next state and any actions the transition does.
			if (state.isInAtomic()) // If this is an atomic state, add it to the list
				atomicStates.add(new AtomicState(state,process));
			for (Transition t : state.output) {
				for (Transition never_t : getOutTransitionsOrNullSet(never_state)) {
					if (collectElseAndRendezVous(process, t, trans))
						continue;
					trans = createStateTransition(process,t,trans,never_t);
				}
			}
		}
		// Return the next free transition ID
		--debug.say_indent;
		return trans;
	}

	/**
 	 * Collects else transition or rendezvous enabled action for later processing 
	 * Check only for the normal process, not for the never claim
	 * 
	 * For rendezvous actions we first need to calculate a cross product to
	 * determine enabledness, therefore else transitions have to be processed even later.   
	 * 
	 * The never claim process is not allowed to contain message passing
	 * statements.
	 * "This means that a never claim may not contain assignment or message
	 * passing statements." @ http://spinroot.com/spin/Man/never.html)
	 * @param process
	 * @param t
	 * @param trans
	 * @return true = found either else transition or rendezvous enabled action 
	 */
	private boolean collectElseAndRendezVous(Proctype process, Transition t, int trans) {
		if (t.iterator().hasNext()) {
			Action a = t.iterator().next();
			if (a instanceof ChannelSendAction) {
				ChannelSendAction csa = (ChannelSendAction)a;
				ChannelVariable var = (ChannelVariable)csa.getVariable();
				if(var.getType().getBufferSize()==0) {
					ReadersAndWriters raw = channels.get(var);
					if(raw==null) {
						raw = new ReadersAndWriters();
						channels.put(var, raw);
					}
					raw.sendActions.add(new SendAction(csa,t,process));
					return true;
				}
			} else if (a instanceof ChannelReadAction) {
				ChannelReadAction cra = (ChannelReadAction)a;
				ChannelVariable var = (ChannelVariable)cra.getVariable();
				if (var.getType().getBufferSize()==0) {
					ReadersAndWriters raw = channels.get(var);
					if (raw==null) {
						raw = new ReadersAndWriters();
						channels.put(var, raw);
					}
					raw.readActions.add(new ReadAction(cra,t,process));
					return true;
				}
			}
		}

		if (t instanceof ElseTransition) {
			else_transitions.add(new ElseTransitionItem(-1,(ElseTransition)t,process));
			return true;
		}
		return false;
	}
	
	private int createStateTransition(Proctype process, Transition t, int trans,
									 Transition never_t) {
		++debug.say_indent;
		if(never_t!=null) {
			debug.say("Handling trans: " + t.getClass().getName() + " || " + never_t.getClass().getName());
		} else {
			debug.say("Handling trans: " + t.getClass().getName());
		}
		--debug.say_indent;

		// Add transition
		if(model.getTransitions().size() != trans)
			throw new AssertionError("Transition not set at correct location in the transition array");
		LTSminTransition lt = new LTSminTransition(process);
		model.getTransitions().add(lt);

		// Guard: process counter
		lt.addGuard(new LTSminGuard(trans, makePCGuard(t.getFrom(), process)));
		lt.addGuard(new LTSminGuard(trans, makeAtomicGuard(process)));
		if(never_t!=null)
			lt.addGuard(new LTSminGuard(trans, makePCGuard(never_t.getFrom(),spec.getNever())));

		// Guard: action enabled & die
        createEnabledAndDieGuard(process, t, trans, lt);
        if (never_t != null)
        	createEnabledAndDieGuard(process, never_t, trans, lt);
        
        // Guard: else transition
        if (t instanceof ElseTransition) {
            ElseTransition et = (ElseTransition)t;
            for (Transition ot : t.getFrom().output) {
                if (ot!=et) {
                	LTSminGuardNand nand = new LTSminGuardNand();
                    createEnabledAndDieGuard(process,ot,trans,nand);
                    lt.addGuard(nand);
                }
            }
        }
        if (never_t != null && never_t instanceof ElseTransition) {
            ElseTransition et = (ElseTransition)never_t;
            for (Transition ot : t.getFrom().output) {
                if (ot!=et) {
                	LTSminGuardNand nand = new LTSminGuardNand();
                    createEnabledAndDieGuard(spec.getNever(),ot,trans,nand);
                    lt.addGuard(nand);
                }
            }
        }
        
		// Create actions of the transition, iff never is absent, dying or not atomic
		if  (never_t == null || never_t.getTo()==null || !never_t.getTo().isInAtomic()) {
			// Action: PC counter update
			lt.addAction(assign(model.sv.procId(process), t.getTo()==null?-1:t.getTo().getStateId()));
			if (t.getTo()==null)
				lt.addAction(new ResetProcessAction(process,model.sv.getProcId(process)));
	       
			// Actions: transition
			for (Action action : t) {
	            lt.addAction(action);
	        }

			// Action: priority (take if to.isInAtomic)
			if (t.getTo()!=null && t.getTo().isInAtomic()) {
				lt.addAction(assign(priorityIdentifier, model.sv.procOffset(process)));
			} else {
				lt.addAction(assign(priorityIdentifier, -1));
			}
		}

		// Action: Never PC update
		if (never_t != null) {
			lt.addAction(assign(model.sv.procId(spec.getNever()),
								never_t.getTo()==null?-1:never_t.getTo().getStateId()));
		}
		return trans+1;
	}
	
	/**
	 * Creates the guard of a transition for its action and for the end states.
	 * @param process The state should be in this process.
	 * @param t The transition of which the guard will be created.
	 * @param trans The transition group ID to use for generation.
	 */
	private void createEnabledAndDieGuard(Proctype process, Transition t, 
								  int trans, LTSminGuardContainer lt) {
		try {
			if (t.iterator().hasNext()) {
				Action a = t.iterator().next();
				createEnabledGuard(process,a,t,trans,lt);
			}
	        // Check if the process is allowed to die if the target state is null
			if (t.getTo()==null) {
				lt.addGuard(new LTSminGuard(trans,makeAllowedToDie(process)));
			}
		} catch(ParseException e) { // removed if (a.getEnabledExpression()!=null)
			e.printStackTrace();
		}
	}

	/**
	 * Creates the guards denoting when the specified Action is enabled.
	 * The enabledness of rendezvous channel actions can only be determined
	 * after all other transitions have been visited (when seenItAll is true).
	 * @param process The action should be in this process.
	 * @param a The action for which the guard is created.
	 * @param t The transition the action is in.
	 * @param trans The transition group ID to use for generation.
	 * @throws ParseException
	 */
	private void createEnabledGuard(Proctype process, Action a, Transition t,
			int trans, LTSminGuardContainer lt) throws ParseException {
		if (a instanceof AssignAction) {
		} else if(a instanceof AssertAction) {
		} else if(a instanceof PrintAction) {
		} else if(a instanceof ExprAction) {
			ExprAction ea = (ExprAction)a;
			Expression expr = ea.getExpression();
			lt.addGuard(new LTSminGuard(trans, expr));
		} else if(a instanceof ChannelSendAction) {
			ChannelSendAction csa = (ChannelSendAction)a;
			ChannelVariable var = (ChannelVariable)csa.getVariable();
			if(var.getType().getBufferSize()>0) {
				lt.addGuard(new LTSminGuard(trans,makeChannelUnfilledGuard(var)));
			} else {
				throw new AssertionError("Trying to actionise rendezvous send before all others! "+ var);
			}
		} else if(a instanceof ChannelReadAction) {
			ChannelReadAction cra = (ChannelReadAction)a;
			ChannelVariable var = (ChannelVariable)cra.getVariable();

			if(var.getType().getBufferSize()>0) {
				List<Expression> exprs = cra.getExprs();
				lt.addGuard(new LTSminGuard(trans,makeChannelHasContentsGuard(var)));
				// Compare constant arguments with channel content
				for (int i = 0; i < exprs.size(); i++) {
					final Expression expr = exprs.get(i);
					if (!(expr instanceof Identifier)) {
						String name = wrapNameForChannelDesc(model.sv.getDescr(cra.getVariable()));
						ChannelTopExpression cte = new ChannelTopExpression(cra, name, i);
						lt.addGuard(new LTSminGuard(trans,compare(cte,expr)));
					}
				}
			} else {
				throw new AssertionError("Trying to actionise rendezvous receive before all others!");
			}
		} else { //unsupported action
			throw new ParseException("LTSMinPrinter: Not yet implemented: "+a.getClass().getName());
		}
	}

	/**
	 * Creates the timeout expression for the specified TimeoutTransition.
	 * This will create the expression that NO transition is enabled. The
	 * dependency matrix is fixed accordingly. If tt is null,
	 * @param tt The timeoutTransition.
	 */
	private void createTimeoutExpression(TimeoutTransition tt) {
		for (Proctype p : spec) {
state_loop:	for (State st : p.getAutomaton()) {
				// Check if this state has an ElseTransition
				// If so, skip the transition, because this state
				// always has an active outgoing transition
				for (Transition trans : st.output) {
					if (trans instanceof ElseTransition) continue state_loop;
				}
				// Loop over all transitions of the state
				for (Transition trans : st.output) {
					tt.lt.addGuard(new LTSminGuard(tt.trans,makeAllowedToDie(p)));
					tt.lt.addGuard(new LTSminGuard(tt.trans,makeAtomicGuard(p)));
                    createEnabledAndDieGuard(p,trans,tt.trans,tt.lt);
				}
			}
		}
	}

	/**
	 * Creates the total timeout expression. This expression is true iff
	 * no transition is enabled, including 'normal' time out transitions.
	 * The dependency matrix is fixed accordingly.
	 * @param tt The TimeoutTransition
	 */
	private int createTotalTimeout(int trans) {
		LTSminTransition lt = new LTSminTransition("total timeout");
		LTSminGuardOr gor = new LTSminGuardOr();
		lt.addGuard(gor);
		model.getTransitions().add(lt);
		for (State s : spec.getNever().getAutomaton()) {
			if (s.isAcceptState()) {
				gor.addGuard(new LTSminGuard(trans, makePCGuard(s, spec.getNever())));
			}
		}
		for (Proctype p: spec) {
state_loop:	for (State st : p.getAutomaton()) {				
				// Check if this state has an ElseTransition
				// If so, skip the transition, because this state
				// always has an active outgoing transition
				for(Transition t : st.output) {
					if (t instanceof ElseTransition) continue state_loop;
				}
				for (Transition t : st.output) {
					// Add the expression that the current transition from the
					// current state in the current process is not enabled.
					LTSminGuardNand gnand = new LTSminGuardNand();
					lt.addGuard(gnand);
					gnand.addGuard(new LTSminGuard(trans, makePCGuard(st, p)));
					gnand.addGuard(new LTSminGuard(trans, makeAtomicGuard(p)));
					createEnabledAndDieGuard(p,t,trans,gnand);
				}
			}
		}
		return trans + 1;
	}

    /**
	 * Creates the transition for one rendezvous couple. The specified
	 * transition ID will be used to identify the created transition.
	 * @param sa The SendAction component.
	 * @param ra The ReadAction component.
	 * @param trans The transition ID to use for the created transition.
	 */
	private void createRendezVousAction(SendAction sa, ReadAction ra, int trans,
										Transition never_t, LTSminTransition lt) {
		ChannelSendAction csa = sa.csa;
		ChannelReadAction cra = ra.cra;
		List<Expression> csa_exprs = csa.getExprs();
		List<Expression> cra_exprs = cra.getExprs();
		
		// Checks
		if(csa.getVariable() != cra.getVariable())
			throw new AssertionError("createRendezVousAction() called with inconsequent ChannelVariable");
		ChannelVariable var = (ChannelVariable)csa.getVariable();
		if(var.getType().getBufferSize()>0)
			throw new AssertionError("createRendezVousAction() called with non-rendezvous channel");

		if(csa_exprs.size() != cra_exprs.size())
			throw new AssertionError("createRendezVousAction() called with incompatible actions: size mismatch");

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
		// Change process counter of sender
		lt.addAction(assign(model.sv.procId(sa.p),sa.t.getTo().getStateId()));
		// Change process counter of receiver
		lt.addAction(assign(model.sv.procId(ra.p),ra.t.getTo().getStateId()));
		
		for (int i = 0; i < cra_exprs.size(); i++) {
			final Expression csa_expr = csa_exprs.get(i);
			final Expression cra_expr = cra_exprs.get(i);
			if ((cra_expr instanceof Identifier)) {
				lt.addAction(assign((Identifier)cra_expr,csa_expr));
			}
		}

		int priority = -1;
		if(ra.t.getTo()!=null && ra.t.getTo().isInAtomic())
			priority = model.sv.procOffset(ra.p);
		lt.addAction(assign(priorityIdentifier, priority));
	}

	/**
	 * Returns the name
	 * @param name The name to clean.
	 * @return The cleaned name.
	 */
	static public String wrapName(String name) {
		return name;
	}

	/**
	 * Returns a channel name given the specified name.
	 * @param name The name to use to instrument a channel name.
	 * @return The clean channel name.
	 */
	static public String wrapNameForChannel(String name) {
		return "ch_"+wrapName(name)+"_t";
	}

	/**
	 * Returns a channel descriptor name given the specified name. 
	 * @param name The name to use to instrument a channel descriptor name.
	 * @return The clean channel descriptor name.
	 */
	static public String wrapNameForChannelDesc(String name) {
		return wrapName(name);
	}

	/**
	 * Returns a channel buffer name given the specified name.
	 * @param name The name to use to instrument a channel buffer name.
	 * @return The instrumentd, clean channel buffer name.
	 */
	static public String wrapNameForChannelBuffer(String name) {
		return wrapName(name)+"_buffer";
	}

	public static AssignAction assign(Variable v, Expression expr) {
		return assign (new Identifier(new Token(IDENTIFIER,v.getName()), v), expr);
	}
	
	public static AssignAction assign(Identifier id, Expression expr) {
		return new AssignAction(new Token(PromelaConstants.ASSIGN,"="), id, expr);
	}

	public static AssignAction assign(Identifier id, int nr) {
		return assign(id, new ConstantExpression(new Token(PromelaConstants.NUMBER, ""+nr), nr));
	}

	private static CompareExpression compare(Expression e1, Expression e2) {
		return new CompareExpression(new Token(PromelaConstants.EQ,"=="), e1, e2);
	}

	private static Expression compare(Expression e1, int nr) {
		return compare(e1, new ConstantExpression(new Token(PromelaConstants.NUMBER, ""+nr), nr));
	}
	
    private Expression makePCGuard(State s, Proctype p) {
		Expression left = model.sv.procId(p);
		Expression right = new ConstantExpression(new Token(PromelaConstants.NUMBER,""+s.getStateId()), s.getStateId());
		Expression e = new CompareExpression(new Token(PromelaConstants.EQ,"=="), left, right);
		return e;
	}

	private Expression makePCDeathGuard(Proctype p) {
		Expression left = model.sv.procId(p);
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
		Expression left = priorityIdentifier;
		Expression right = new ConstantExpression(new Token(PromelaConstants.NUMBER,"0"), 0);
		Expression e = new CompareExpression(new Token(PromelaConstants.LT,"<"), left, right);

		Expression e2 = compare(priorityIdentifier, model.sv.procOffset(p));

		return new BooleanExpression(new Token(PromelaConstants.LOR,"||"), e, e2);
	}

	private Expression makeExclusiveAtomicGuard(Proctype p) {
		return compare(priorityIdentifier, model.sv.procOffset(p));
	}

	private Expression makeChannelUnfilledGuard(ChannelVariable var) {
		String name = wrapNameForChannelDesc(model.sv.getDescr(var));
		Expression left = new ChannelSizeExpression(var, name);
		Expression right = new ConstantExpression(new Token(PromelaConstants.NUMBER,""+var.getType().getBufferSize()), var.getType().getBufferSize());
		Expression e = new CompareExpression(new Token(PromelaConstants.LT,"<"), left, right);
		return e;
	}

	private Expression makeChannelHasContentsGuard(ChannelVariable var) {
		String name = wrapNameForChannelDesc(model.sv.getDescr(var));
		Expression left = new ChannelSizeExpression(var, name);
		Expression right = new ConstantExpression(new Token(PromelaConstants.NUMBER,"0"), 0);
		Expression e = new CompareExpression(new Token(PromelaConstants.GT,">"), left, right);
		return e;
	}

	public static AssertionError error(String string, Token token) {
		return new AssertionError(string + " At line "+token.beginLine +"column "+ token.beginColumn +".");
	}
}

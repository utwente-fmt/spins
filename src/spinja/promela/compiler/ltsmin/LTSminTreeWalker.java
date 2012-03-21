package spinja.promela.compiler.ltsmin;

import static spinja.promela.compiler.ltsmin.model.LTSminUtil.assign;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.bool;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.calc;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.compare;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.constant;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.error;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.id;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import spinja.promela.compiler.Proctype;
import spinja.promela.compiler.Specification;
import spinja.promela.compiler.actions.Action;
import spinja.promela.compiler.actions.AssertAction;
import spinja.promela.compiler.actions.AssignAction;
import spinja.promela.compiler.actions.ChannelReadAction;
import spinja.promela.compiler.actions.ChannelSendAction;
import spinja.promela.compiler.actions.ElseAction;
import spinja.promela.compiler.actions.ExprAction;
import spinja.promela.compiler.actions.OptionAction;
import spinja.promela.compiler.actions.PrintAction;
import spinja.promela.compiler.actions.Sequence;
import spinja.promela.compiler.automaton.ElseTransition;
import spinja.promela.compiler.automaton.State;
import spinja.promela.compiler.automaton.Transition;
import spinja.promela.compiler.expression.BooleanExpression;
import spinja.promela.compiler.expression.Expression;
import spinja.promela.compiler.expression.Identifier;
import spinja.promela.compiler.expression.RunExpression;
import spinja.promela.compiler.ltsmin.matrix.LTSminGuardContainer;
import spinja.promela.compiler.ltsmin.matrix.LTSminGuardNand;
import spinja.promela.compiler.ltsmin.matrix.LTSminGuardOr;
import spinja.promela.compiler.ltsmin.matrix.LTSminLocalGuard;
import spinja.promela.compiler.ltsmin.model.ChannelSizeExpression;
import spinja.promela.compiler.ltsmin.model.ChannelTopExpression;
import spinja.promela.compiler.ltsmin.model.LTSminIdentifier;
import spinja.promela.compiler.ltsmin.model.LTSminModel;
import spinja.promela.compiler.ltsmin.model.LTSminTransition;
import spinja.promela.compiler.ltsmin.model.LTSminTransitionCombo;
import spinja.promela.compiler.ltsmin.model.ReadAction;
import spinja.promela.compiler.ltsmin.model.ReadersAndWriters;
import spinja.promela.compiler.ltsmin.model.ResetProcessAction;
import spinja.promela.compiler.ltsmin.model.SendAction;
import spinja.promela.compiler.ltsmin.model.TimeoutTransition;
import spinja.promela.compiler.ltsmin.state.LTSminStateVector;
import spinja.promela.compiler.parser.ParseException;
import spinja.promela.compiler.parser.PromelaConstants;
import spinja.promela.compiler.variable.ChannelType;
import spinja.promela.compiler.variable.ChannelVariable;
import spinja.promela.compiler.variable.Variable;
import spinja.promela.compiler.variable.VariableType;

/**
 * Constructs the LTSminModel by walking over the SpinJa {@link Specification}.
 *
 * TODO: avoid atomic guards.
 * 
 * @author Freark van der Berg, Alfons Laarman
 */
public class LTSminTreeWalker {

	// The specification of which the model is created,
	// initialized by constructor
	private final Specification spec;

	private LTSminDebug debug = new LTSminDebug();

	private LTSminModel model = null;
	
	// For each channel, a list of read actions and send actions is kept for later processing
	private HashMap<ChannelVariable,ReadersAndWriters> channels;

	// Maintain transition
	private HashMap<Transition, Set<LTSminTransition>> t2t;

	// List of transition with a TimeoutExpression
    List<TimeoutTransition> timeout_transitions;

	/**
	 * Creates a new LTSMinPrinter using the specified Specification.
	 * @param spec The specification.
	 * @param name The name to give the model.
	 */
	public LTSminTreeWalker(Specification spec) {
		this.spec = spec;
        timeout_transitions = new ArrayList<TimeoutTransition>();
		channels = new HashMap<ChannelVariable,ReadersAndWriters>();
		t2t = new HashMap<Transition, Set<LTSminTransition>>();
	}
		
	/**
	 * generates and returns an LTSminModel to the Specification provided
	 * when creating this LTSMinPrinter instance.
	 * @return The LTSminModel according to the Specification.
	 */
	public LTSminModel createLTSminModel(String name) {
		//long start_t = System.currentTimeMillis();
		LTSminStateVector sv = new LTSminStateVector();
		sv.createVectorStructs(spec, debug);
		model = new LTSminModel(name, sv);
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
		for (RunExpression re : spec.getRuns()) {
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
		
		createProcessConstantVars();
		
		// create the rendezvous transitions
		for (Map.Entry<ChannelVariable,ReadersAndWriters> e : channels.entrySet()) {
			for (SendAction sa : e.getValue().sendActions) {
				for (ReadAction ra : e.getValue().readActions) {
					for (State ns : getNeverAutomatonOrNullSet(false)) {
						for (Transition nt : getOutTransitionsOrNullSet(ns)) {
							trans = createRendezVousTransition(sa,ra,trans,nt);
						}
					}
				}
			}
		}
		
		for (LTSminTransition t : model.getTransitions()) {
			if (!(t instanceof LTSminTransitionCombo))
				continue;
			LTSminTransitionCombo tc = (LTSminTransitionCombo)t;
			HashSet<State> seen = new HashSet<State>();
			reachability(tc.getTransition().getTo(), seen, tc);
			tc.addTransition(tc);
			//System.out.println(tc +" --> "+ tc.transitions);
		}

		/*
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

			lt.addGuard(trans, makePCGuard(s, process));
			//lt.addGuard(trans, makeExclusiveAtomicGuard(process));
			for (Transition ot : s.output) {
				LTSminGuardNand gnand = new LTSminGuardNand();
				createEnabledAndDieGuard(process,ot,trans,gnand);
				lt.addGuard(gnand);
			}
			//lt.addAction(assign(priorityIdentifier, -1));
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
					gor.addGuard(trans,makePCGuard(s, spec.getNever()));
				}
			}
			++trans;
		}
		*/
		if (model.getTransitions().size() != trans)
			throw new AssertionError("Transition not set at correct location in the transition array");

		return trans;
	}

	/**
	 * Add all reachable atomic transitions to tc
	 */
	private void reachability(State state, HashSet<State> seen,
			LTSminTransitionCombo tc) {
		if (state == null || !state.isInAtomic()) return;
		if (!seen.add(state)) return;
		for (Transition t : state.output) {
			Set<LTSminTransition> set = t2t.get(t);
			for (LTSminTransition lt : set) {
				assert (lt.isAtomic());
				tc.addTransition(lt);
			}
			reachability(t.getTo(), seen, tc);
		}
	}

	/**
	 * Run expressions usually pass constants to channel variables. If these
	 * variables are never assigned to elsewhere, we can safely mark them
	 * constant.
	 */
	private void createProcessConstantVars() {
		for (RunExpression re : spec.getRuns()){
			Iterator<Expression> rei = re.getExpressions().iterator();
			Proctype p = re.getSpecification().getProcess(re.getId());
			for (Variable v : p.getArguments()) {
				Expression next = rei.next();
				if (v.getType() instanceof ChannelType) continue; //passed by reference
				if (v.isNotAssignedTo()) {
					try {
						v.setConstantValue(next.getConstantValue());
					} catch (ParseException e) {} // expected
				}
			}
		}
	}

	/**
	 * Creates all transitions from the given state. This state should be
	 * in the specified process.
	 * @param process The state should be in this process.
	 * @param trans Starts generating transitions from this transition ID.
	 * @param state The state of which all outgoing transitions will be created.
	 * @return The next free transition ID (= old.trans + #new_transitions).
	 */
	private int createTransitionsFromState (Proctype process, int trans, State state, State never_state) {
		++debug.say_indent;
		debug.say(state.toString());

		// Check if it is an ending state
		if (state.sizeOut()==0) { // FIXME: Is this the correct prerequisite for THE end state of a process?
			LTSminTransition lt = makeTransition(process, trans, state, process.getName() +"_end");
			model.getTransitions().add(lt);

			lt.addGuard(makePCGuard(state, process));
			lt.addGuard(makeAllowedToDie(process));
			lt.addGuard(makeInAtomicGuard(process));

			lt.addAction(new ResetProcessAction(process));

			// Keep track of the current transition ID
			++trans;
		} else {
			// In the normal case, create a transition changing the process
			// counter to the next state and any actions the transition does.
			for (Transition t : state.output) {
				if (collectRendezVous(process, t, trans))
					continue;
				for (Transition never_t : getOutTransitionsOrNullSet(never_state)) {
					LTSminTransition lt = createStateTransition(process,trans,t,never_t);
					model.getTransitions().add(lt);
					trans++;
				}
			}
		}
		// Return the next free transition ID
		--debug.say_indent;
		return trans;
	}

	private LTSminLocalGuard makeInAtomicGuard(Proctype process) {
		Identifier id = new LTSminIdentifier(new Variable(VariableType.BOOL, "atomic", 0));
		Variable pid = model.sv.getPID(process);
		BooleanExpression boolExpr = bool(PromelaConstants.LOR,
				compare(PromelaConstants.EQ, id, constant(-1)),
				compare(PromelaConstants.EQ, id, id(pid)));
		LTSminLocalGuard guard = new LTSminLocalGuard(boolExpr);
		return guard;
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
	private boolean collectRendezVous(Proctype process, Transition t, int trans) {
		if (t.iterator().hasNext()) {
			Action a = t.iterator().next();
			if (a instanceof ChannelSendAction) {
				ChannelSendAction csa = (ChannelSendAction)a;
				ChannelVariable var = (ChannelVariable)csa.getIdentifier().getVariable();
				if(var.getType().getBufferSize()==0) {
					ReadersAndWriters raw = channels.get(var);
					if (raw == null) {
						raw = new ReadersAndWriters();
						channels.put(var, raw);
					}
					raw.sendActions.add(new SendAction(csa,t,process));
					return true;
				}
			} else if (a instanceof ChannelReadAction) {
				ChannelReadAction cra = (ChannelReadAction)a;
				ChannelVariable var = (ChannelVariable)cra.getIdentifier().getVariable();
				if (var.getType().getBufferSize()==0) {
					ReadersAndWriters raw = channels.get(var);
					if (raw == null) {
						raw = new ReadersAndWriters();
						channels.put(var, raw);
					}
					raw.readActions.add(new ReadAction(cra,t,process));
					return true;
				}
			}
		}
		return false;
	}

	private LTSminTransition createStateTransition(Proctype process, int trans,
											Transition t, Transition never_t) {
		++debug.say_indent;
		if(never_t!=null) {
			debug.say("Handling trans: " + t.getClass().getName() + " || " + never_t.getClass().getName());
		} else {
			debug.say("Handling trans: " + t.getClass().getName());
		}
		--debug.say_indent;

		String t_name = makeTranstionName(t, null, never_t);
		LTSminTransition lt = makeTransition(process, trans, t, t_name);

		// Guard: process counter
		lt.addGuard(makePCGuard(t.getFrom(), process));
		if(never_t!=null)
			lt.addGuard(makePCGuard(never_t.getFrom(),spec.getNever()));

        // Guard: enabled action or else transition
        if (t instanceof ElseTransition) {
            ElseTransition et = (ElseTransition)t;
            for (Transition ot : t.getFrom().output) {
                if (ot!=et) {
                	LTSminGuardNand nand = new LTSminGuardNand();
                    createEnabledGuard(process,ot,nand);
                    lt.addGuard(nand);
                }
            }
        } else {
            createEnabledGuard(process, t, lt);
        }

        // Guard: allowed to die
		if (t.getTo()==null) {
			lt.addGuard(makeAllowedToDie(process));
		}

		// Guard: never enabled action or else transition
        if (never_t != null) {
	        if (never_t instanceof ElseTransition) {
	            ElseTransition et = (ElseTransition)never_t;
	            for (Transition ot : t.getFrom().output) {
	                if (ot!=et) {
	                	LTSminGuardNand nand = new LTSminGuardNand();
	                    createEnabledGuard(spec.getNever(),ot,nand);
	                    lt.addGuard(nand);
	                }
	            }
	        } else {
	        	createEnabledGuard(process, never_t, lt);
	        }
        }
        
		lt.addGuard(makeInAtomicGuard(process));

		// Create actions of the transition, iff never is absent, dying or not atomic
		if  (never_t == null || never_t.getTo()==null || !never_t.getTo().isInAtomic()) {
			if (t.getTo()==null)
				lt.addAction(new ResetProcessAction(process));
			else // Action: PC counter update
				lt.addAction(assign(model.sv.getPC(process), t.getTo().getStateId()));
	       
			// Actions: transition
			for (Action action : t) {
	            lt.addAction(action);
	        }

			/*
			// Action: priority (take if to.isInAtomic)
			if (t.getTo()!=null && t.getTo().isInAtomic()) {
				lt.addAction(assign(priorityIdentifier, model.sv.procOffset(process)));
			} else {
				lt.addAction(assign(priorityIdentifier, -1));
			}
			*/
		}

		// Action: Never PC update
		if (never_t != null) {
			lt.addAction(assign(model.sv.getPC(process),
								never_t.getTo()==null?-1:never_t.getTo().getStateId()));
		}
		return lt;
	}

    private LTSminTransition makeTransition(Proctype process, int trans,
			State from, String t_name) {
		Transition t = from.newTransition(null);
		return makeTransition(process, trans, t, t_name);
	}

	private LTSminTransition makeTransition(Proctype process, int trans,
											Transition t, String t_name) {
		LTSminTransition lt;
		if (t == null || !t.isAtomic()) {
			lt = new LTSminTransition(t, trans, t_name, process);
		} else {
			lt = new LTSminTransitionCombo(t, trans, t_name, process);
		}
		Set <LTSminTransition> set = t2t.get(t);
		if (null == set) {
			set = new HashSet<LTSminTransition>();
			t2t.put(t, set);
		}
		set.add(lt);
		return lt;
	}
	
	/**
	 * Creates the guard of a transition for its action and for the end states.
	 * @param process The state should be in this process.
	 * @param t The transition of which the guard will be created.
	 * @param trans The transition group ID to use for generation.
	 */
	private void createEnabledGuard(Proctype process, Transition t, 
								   LTSminGuardContainer lt) {
		try {
			if (t.iterator().hasNext()) {
				Action a = t.iterator().next();
				createEnabledGuard(a, lt);
			}
		} catch (ParseException e) { // removed if (a.getEnabledExpression()!=null)
			e.printStackTrace();
		}
	}

	/**
	 * Creates the guards denoting when the specified Action is enabled.
	 * The enabledness of rendezvous channel actions can only be determined
	 * after all other transitions have been visited (when seenItAll is true).
	 * 
	 * Also records the assignTo property of identifier, to detect constants later. 
	 * 
	 * @param process The action should be in this process.
	 * @param a The action for which the guard is created.
	 * @param t The transition the action is in.
	 * @param trans The transition group ID to use for generation.
	 * @throws ParseException
	 */
	public void createEnabledGuard(Action a, LTSminGuardContainer lt) throws ParseException {
		if (a instanceof AssignAction) {
			AssignAction ae = (AssignAction)a;
			ae.getIdentifier().getVariable().setAssignedTo();
		} else if(a instanceof AssertAction) {
		} else if(a instanceof PrintAction) {
		} else if(a instanceof ExprAction) {
			ExprAction ea = (ExprAction)a;
			lt.addGuard(ea.getExpression());
		} else if(a instanceof ChannelSendAction) {
			ChannelSendAction csa = (ChannelSendAction)a;
			ChannelVariable var = (ChannelVariable)csa.getIdentifier().getVariable();
			if(var.getType().getBufferSize()>0) {
				lt.addGuard(makeChannelUnfilledGuard(csa.getIdentifier()));
			} else {
				throw new AssertionError("Trying to actionise rendezvous send before all others! "+ var);
			}
		} else if(a instanceof OptionAction) { // options in a d_step sequence
			OptionAction oa = (OptionAction)a;
			LTSminGuardOr orc = new LTSminGuardOr();
			for (Sequence seq : oa) {
				Action act = seq.iterator().next(); // guaranteed by parser
				if (act instanceof ElseAction)
					return; // options with else have a vacuously true guard
				createEnabledGuard(act, orc);
			}
			lt.addGuard(orc);
		} else if(a instanceof ElseAction) {
			throw new AssertionError("ElseAction outside OptionAction!");
		} else if(a instanceof ChannelReadAction) {
			ChannelReadAction cra = (ChannelReadAction)a;
			ChannelVariable var = (ChannelVariable)cra.getIdentifier().getVariable();
			if(var.getType().getBufferSize()>0) {
				List<Expression> exprs = cra.getExprs();
				lt.addGuard(makeChannelHasContentsGuard(cra.getIdentifier()));
				// Compare constant arguments with channel content
				for (int i = 0; i < exprs.size(); i++) {
					final Expression expr = exprs.get(i);
					if (!(expr instanceof Identifier)) {
						ChannelTopExpression cte = new ChannelTopExpression(cra, i);
						lt.addGuard(compare(PromelaConstants.EQ,cte,expr));
					} else {
						((Identifier)expr).getVariable().setAssignedTo();
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
					tt.lt.addGuard(makeAllowedToDie(p));
					//tt.lt.addGuard(tt.trans,makeAtomicGuard(p));
                    createEnabledGuard(p,trans,tt.lt);
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
	private int createTotalTimeout(State from, int trans, Proctype process) {
		LTSminTransition lt = makeTransition(process, trans, from, "total timeout");
		LTSminGuardOr gor = new LTSminGuardOr();
		lt.addGuard(gor);
		model.getTransitions().add(lt);
		for (State s : spec.getNever().getAutomaton()) {
			if (s.isAcceptState()) {
				gor.addGuard(makePCGuard(s, spec.getNever()));
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
					gnand.addGuard(makePCGuard(st, p));
					//gnand.addGuard(trans, makeAtomicGuard(p));
					createEnabledGuard(p,t,gnand);
				}
			}
		}
		return trans + 1;
	}

	/**
	 * Creates the transition for one rendezvous couple. The specified
	 * transition ID will be used to identify the created transition.
	 * 
	 * "If an atomic sequence contains a rendezvous send statement, control
	 * passes from sender to receiver when the rendezvous handshake completes."
	 * 
	 * @param sa The SendAction component.
	 * @param ra The ReadAction component.
	 * @param trans The transition ID to use for the created transition.
	 */
	private int createRendezVousTransition(SendAction sa, ReadAction ra,
										   int trans, Transition never_t) {
		String name = makeTranstionName(sa.t, ra.t, never_t);
		LTSminTransition lt = makeTransition(ra.p, trans, ra.t, name);
		//new LTSminTransitionCombo(trans, name,  ra.p, sa.t)

		ChannelSendAction csa = sa.csa;
		ChannelReadAction cra = ra.cra;
		List<Expression> csa_exprs = csa.getExprs();
		List<Expression> cra_exprs = cra.getExprs();

		lt.addGuard(makePCGuard(sa.t.getFrom(), sa.p));
		lt.addGuard(makePCGuard(ra.t.getFrom(), ra.p));
		Identifier sendId = sa.csa.getIdentifier();
		if (sendId.getVariable().getArraySize() > 1) { // array of channels
			Expression e1 = ra.cra.getIdentifier().getArrayExpr();
			Expression e2 = sa.csa.getIdentifier().getArrayExpr();
			if (e1 == null) e1 = constant(0);
			if (e2 == null) e2 = constant(0);
			lt.addGuard(compare(PromelaConstants.EQ, e2, e2));
		}
		/* Channel matches */
		for (int i = 0; i < cra_exprs.size(); i++) {
			final Expression csa_expr = csa_exprs.get(i);
			final Expression cra_expr = cra_exprs.get(i);
			try { // we skip creating transitions for impotent matches:
				if (csa_expr.getConstantValue() != cra_expr.getConstantValue())
					return trans;
			} catch (ParseException pe) {}
			if (!(cra_expr instanceof Identifier)) {
				lt.addGuard(compare(PromelaConstants.EQ,csa_expr,cra_expr));
			}
		}

		lt.addGuard(makeInAtomicGuard(sa.p));
		if (sa.t.isAtomic() && ra.t.isAtomic()) // control passes from sender to receiver
			lt.passesControlAtomically(ra.p);
		
		// Change process counter of sender
		lt.addAction(assign(model.sv.getPC(sa.p),sa.t.getTo().getStateId()));
		// Change process counter of receiver
		lt.addAction(assign(model.sv.getPC(ra.p),ra.t.getTo().getStateId()));
		
		for (int i = 0; i < cra_exprs.size(); i++) {
			final Expression csa_expr = csa_exprs.get(i);
			final Expression cra_expr = cra_exprs.get(i);
			if ((cra_expr instanceof Identifier)) {
				lt.addAction(assign((Identifier)cra_expr,csa_expr));
			}
		}

		model.getTransitions().add(lt);
		return trans + 1;
	}

	private String makeTranstionName(Transition t) {
		String t_name = t.getFrom().getAutomaton().getProctype().getName();
		t_name += "("+ t.getFrom().getStateId() +"-->";
		return t_name + (t.getTo()== null ? "end" : t.getTo().getStateId()) +")";
	}

	private String makeTranstionName(Transition t, Transition sync_t,
									 Transition never_t) {
		String name = makeTranstionName(t);
		if (sync_t != null)
			name += " X "+ makeTranstionName(sync_t);
		if (never_t != null)
			name += " X "+ makeTranstionName(never_t);
		return name;
	}
	
    private Expression makePCGuard(State s, Proctype p) {
		Variable pc = model.sv.getPC(p);
		Expression left = id(pc);
		Expression right = constant(s.getStateId());
		Expression e = compare(PromelaConstants.EQ, left, right);
		return e;
	}

	/* TODO: die sequence of dynamically started processes ala http://spinroot.com/spin/Man/init.html */
	private Expression makeAllowedToDie(Proctype p) {
		Variable pid = model.sv.getPID(p);
		Expression left = calc(PromelaConstants.PLUS, id(pid), constant(1)); 
		return compare (PromelaConstants.EQ, left, id(LTSminStateVector._NR_PR));
	}

	private Expression makeChannelUnfilledGuard(Identifier id) {
		Expression left = new ChannelSizeExpression(id);
		Expression right = constant(((ChannelType)id.getVariable().getType()).getBufferSize());
		Expression e = compare(PromelaConstants.LT, left, right);
		return e;
	}

	private Expression makeChannelHasContentsGuard(Identifier id) {
		Expression left = new ChannelSizeExpression(id);
		Expression e = compare(PromelaConstants.GT, left, constant(0));
		return e;
	}
}

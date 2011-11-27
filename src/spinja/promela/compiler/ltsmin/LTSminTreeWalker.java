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
public class LTSminTreeWalker {

	private LTSminDebug debug = new LTSminDebug();
	
	// For each channel, a list of read actions and send actions is kept
	// to later handle these separately
	private HashMap<ChannelVariable,ReadersAndWriters> channels;

	// Atomic states - of these loss of atomicity will be instrumentd
	private List<AtomicState> atomicStates;

	// The specification of which code is to be instrumentd,
	// initialised by constructor
	private final Specification spec;

	// The transition ID of the transition that handles total timeout
	private int total_timeout_id;
	private LTSminTransition lt_total_timeout;

	// Set to true when all transitions have been parsed.
	// After this, channels, else, timeout, and loss of atomicity is handled.
	boolean seenItAll = false;

	// List of Elsetransitions
	// These will be instrumentd after normal transitions
	private List<ElseTransitionItem> else_transitions;

	private PriorityIdentifier priorityIdentifier;

	private LTSminModel model;

	/**
	 * Creates a new LTSMinPrinter using the specified Specification.
	 * After this, the instrument() member will instrument and return C code.
	 * @param spec The Specification using which C code is instrumentd.
	 * @param name The name to give the model.
	 */
	public LTSminTreeWalker(Specification spec, String name) {
		this.spec = spec;
		atomicStates = new ArrayList<AtomicState>();
		else_transitions = new ArrayList<ElseTransitionItem>();
		channels = new HashMap<ChannelVariable,ReadersAndWriters>();
		priorityIdentifier = new PriorityIdentifier();
		model = new LTSminModel(name);
	}
		
	/**
	 * genrates and returns C code according to the Specification provided
	 * when creating this LTSMinPrinter instance.
	 * @return The C code according to the Specification.
	 */
	public LTSminModel createLTSminModel() {
		//long start_t = System.currentTimeMillis();
		model.createVectorStructs(spec, debug);
		bindByReferenceCalls();
		instrumentTransitions();
        if(spec.getNever()!=null) 
            instrumentTotalTimeout();
		LTSminDMWalker.walkModel(model);
		LTSminGMWalker.walkModel(model);
		//long end_t = System.currentTimeMillis();
		return model;
	}

	/**
	 * Binds any channeltype arguments of all RunExpressions by reference.
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

	private Iterable<State> getNeverAutomatonOrNullSet() {
		if (spec.getNever()==null)
			return new HashSet<State>(Arrays.asList((State)null));
		return spec.getNever().getAutomaton();
	}
	
	private Iterable<Transition> getOutTransitionsOrNullSet(State s) {
		if (s==null)
			return new HashSet<Transition>(Arrays.asList((Transition)null));
		return s.output;
	}
	
	/**
	 * instruments the state transitions.
	 * This calls instrumentTransitionsFromState() for every state in every process.
	 * @param w The StringWriter to which the code is written.
	 */
	private int instrumentTransitions() {
		int trans = 0;
		debug.say("");
		// instrument the normal transitions for all processes.
		// This does not include: rendezvous, else, timeout.
		// Loss of atomicity is handled separately as well.
		for(Proctype p : spec) {
			debug.say("[Proc] " + p.getName());
			for (State st : p.getAutomaton()) {
				for (State n : getNeverAutomatonOrNullSet()) {
					trans = instrumentTransitionsFromState(p,trans,st, n);
				}
			}
		}
		seenItAll = true;

		// instrument Else Transitions
		for(ElseTransitionItem eti : else_transitions) {
			for (State ns : getNeverAutomatonOrNullSet()) {
				for(Transition nt : getOutTransitionsOrNullSet(ns)) {
					trans = instrumentStateTransition(eti.p, eti.t, trans,nt);
				}
			}			
		}

		// instrument the rendezvous transitions
		for(Map.Entry<ChannelVariable,ReadersAndWriters> e: channels.entrySet()) {
			for(SendAction sa: e.getValue().sendActions) {
				for(ReadAction ra: e.getValue().readActions) {
					//if(state_proc_offset.get(sa.p) == state_proc_offset.get(ra.p)) continue;
					if(model.getTransitions().size() != trans)
						throw new AssertionError("Transition not set at correct location in the transition array");
					LTSminTransition lt = new LTSminTransition(sa.p);
					model.getTransitions().add(lt);
					for (State ns : getNeverAutomatonOrNullSet()) {
						for(Transition nt : getOutTransitionsOrNullSet(ns)) {
							instrumentRendezVousAction(sa,ra,trans,nt,lt);
						}
					}
					++trans;
				}
			}
		}
		
		// Create loss of atomicity transition.
		// This is used when a process blocks inside an atomic transition.
		if(model.getTransitions().size() != trans)
			throw new AssertionError("Transition not set at correct location in the transition array");
		LTSminTransitionCombo ltc = new LTSminTransitionCombo("loss of atomicity");
		model.getTransitions().add(ltc);
		for(AtomicState as : atomicStates) {
			LTSminTransition lt = new LTSminTransition(as.p);
			ltc.addTransition(lt);
			State s = as.s;
			Proctype process = as.p;
			assert (s.isInAtomic());

			lt.addGuard(new LTSminGuard(trans, makePCGuard(s, process)));
			lt.addGuard(new LTSminGuard(trans, makeExclusiveAtomicGuard(process)));
			for(Transition ot : s.output) {
				LTSminGuardNand gnand = new LTSminGuardNand();
				instrumentTransitionGuard(process,ot,trans,gnand);
				lt.addGuard(gnand);
			}
			lt.addAction(assign(priorityIdentifier, -1));
		}
		++trans;

		// Add total timeout transition in case of a never claim.
		// This is because otherwise accepting cycles might not be found,
		// although the never claim is violated.
		if(spec.getNever()!=null) {
			if(model.getTransitions().size() != trans)
				throw new AssertionError("Transition not set at correct location in the transition array");
			lt_total_timeout = new LTSminTransition("total timeout");
			LTSminGuardOr gorAcc = new LTSminGuardOr();
			lt_total_timeout.addGuard(gorAcc);
			LTSminTransition lt_cycle = new LTSminTransition("cycle");
			LTSminGuardOr gorEnd = new LTSminGuardOr();
			lt_cycle.addGuard(gorEnd);
			model.getTransitions().add(lt_total_timeout);
			model.getTransitions().add(lt_cycle);
			for (State s : spec.getNever().getAutomaton()) {
				if(s.isAcceptState()) {
					gorAcc.addGuard(new LTSminGuard(trans, makePCGuard(s, spec.getNever())));
				}
				if(s.isEndingState()) {
					gorEnd.addGuard(new LTSminGuard(trans,makePCGuard(s, spec.getNever())));
				}
			}
			trans += 2;
		}
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
	 * ( = old.trans + "#transitions instrumented" ).
	 * 
	 * Side effects:
	 * 	collects else transition in elsetransitions
	 */
	private int instrumentTransitionsFromState(Proctype process, int trans, State state, State never_state) {
		++debug.say_indent;
		debug.say(state.toString());

		// Check if it is an ending state
		if (state.sizeOut()==0) { // FIXME: Is this the correct prerequisite for THE end state of a process?
			if(model.getTransitions().size() != trans)
				throw new AssertionError("Transition now set at correct location in the transition array");
			LTSminTransition lt = new LTSminTransition(process);
			model.getTransitions().add(lt);

			lt.addGuard(new LTSminGuard(trans, makePCGuard(state, process)));
			lt.addGuard(new LTSminGuard(trans, makeAtomicGuard(process)));
			lt.addGuard(new LTSminGuard(trans, makeAllowedToDie(process)));

			// In the case of an ending state, instrument a transition only
			// changing the process counter to -1.
			lt.addAction(assign(model.sv.procId(process), -1));

			// Keep track of the current transition ID
			++trans;
		} else {
			// In the normal case, instrument a transition changing the process
			// counter to the next state and any actions the transition does.
			if(state.isInAtomic()) {// If this is an atomic state, add it to the list
				atomicStates.add(new AtomicState(state,process));
			}
			for(Transition t : state.output) {
				for(Transition never_t : getOutTransitionsOrNullSet(never_state)) {
					trans = instrumentStateTransition(process,t,trans,never_t);
				}
			}
		}
		// Return the next free transition ID
		--debug.say_indent;
		return trans;
	}

	public int instrumentStateTransition(Proctype process, Transition t, int trans, Transition never_t) {
		// If the from state is atomic, ignore the never transition
		if(t.getFrom().isInAtomic()) never_t = null;

		// DO NOT actionise RENDEZVOUS channel send/read
		// These will be remembered and handled later separately
		// Check only for the normal process, not for the never claim
		// The never claim process is not allowed to contain message passing
		// statements.
		// "This means that a never claim may not contain assignment or message
		// passing statements." @ http://spinroot.com/spin/Man/never.html)
		if (t.iterator().hasNext()) {
			Action a = t.iterator().next();
			if(a instanceof ChannelSendAction) {
				ChannelSendAction csa = (ChannelSendAction)a;
				ChannelVariable var = (ChannelVariable)csa.getVariable();
				if(var.getType().getBufferSize()==0) {
					ReadersAndWriters raw = channels.get(var);
					if(raw==null) {
						raw = new ReadersAndWriters();
						channels.put(var, raw);
					}
					raw.sendActions.add(new SendAction(csa,t,process));
					return trans;
				}
			} else if(a!= null && a instanceof ChannelReadAction) {
				ChannelReadAction cra = (ChannelReadAction)a;
				ChannelVariable var = (ChannelVariable)cra.getVariable();
				if(var.getType().getBufferSize()==0) {
					ReadersAndWriters raw = channels.get(var);
					if(raw==null) {
						raw = new ReadersAndWriters();
						channels.put(var, raw);
					}
					raw.readActions.add(new ReadAction(cra,t,process));
					return trans;
				}
			}
		}

		// DO NOT try to instrument Else transitions immediately,
		// but buffer it until every state has been visited.
		// This is because during the normal generation, some transitions
		// are not instrumentd (e.g. rendezvous), so their enabledness is
		// unknown.
		if (!seenItAll && t instanceof ElseTransition) {
			else_transitions.add(new ElseTransitionItem(-1,(ElseTransition)t,process));
			return trans;
		}

		// Add transition
		if(model.getTransitions().size() != trans) throw new AssertionError("Transition not set at correct location in the transition array");
		LTSminTransition lt = new LTSminTransition(process);
		model.getTransitions().add(lt);

		++debug.say_indent;
		if(never_t!=null) {
			debug.say("Handling trans: " + t.getClass().getName() + " || " + never_t.getClass().getName());
		} else {
			debug.say("Handling trans: " + t.getClass().getName());
		}
		--debug.say_indent;
		
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
            for(Transition ot : t.getFrom().output) {
                if(ot!=et) {
                    instrumentTransitionGuard(process,ot,trans,lt);
                }
            }
        }
        if(never_t != null && never_t instanceof ElseTransition) {
            ElseTransition et = (ElseTransition)never_t;
            for(Transition ot : t.getFrom().output) {
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
			lt.addAction(assign(model.sv.procId(process), t.getTo()==null?-1:t.getTo().getStateId()));
			if(t.getTo()==null) lt.addAction(new ResetProcessAction(process,model.sv.getProcId(process)));
	       
			for (Action action : t) {
	            lt.addAction(action);
	        }

			// If this transition is atomic
			if(t.getTo()!=null && t.getTo().isInAtomic()) {
				// Claim priority when taking this transition. It is
				// possible this process had already priority, so nothing
				// changes.
				lt.addAction(assign(priorityIdentifier, model.sv.procOffset(process)));
			// If this transition is not atomic
			} else {
				// Make sure no process has priority. This transition was
				// either executed while having priority and it is now given
				// up, or no process had priority and this remains the same.
				lt.addAction(assign(priorityIdentifier, -1));
			}
		}

		// If there is a never claim, instrument the PC update code
		if(never_t != null) {
			lt.addAction(assign(model.sv.procId(spec.getNever()),
								never_t.getTo()==null?-1:never_t.getTo().getStateId()));
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
							gand.addGuard(new LTSminGuard(trans,compare(csa_expr,cra_expr)));
						}
					}
				}
			} else {
				throw new AssertionError("Trying to actionise rendezvous send before all others! "+ var);
			}
		} else if(a instanceof ChannelReadAction) {
			ChannelReadAction cra = (ChannelReadAction)a;
			ChannelVariable var = (ChannelVariable)cra.getVariable();

			if(var.getType().getBufferSize()>0) {
				List<Expression> exprs = cra.getExprs();
				lt.addGuard(new LTSminGuard(trans,makeChannelHasContentsGuard(var)));
				for (int i = 0; i < exprs.size(); i++) {
					final Expression expr = exprs.get(i);
					if (!(expr instanceof Identifier)) {
						String name = wrapNameForChannelDesc(model.sv.getDescr(cra.getVariable()));
						ChannelTopExpression cte = new ChannelTopExpression(cra, name, i);
						lt.addGuard(new LTSminGuard(trans,compare(cte,expr)));
					}
				}
			} else if(seenItAll) {
				ReadersAndWriters raw = channels.get(var);
				LTSminGuardOr gor = new LTSminGuardOr();
				lt.addGuard(gor);
				List<Expression> cra_exprs = cra.getExprs();
				for (SendAction sa: raw.sendActions) {
					List<Expression> csa_exprs = sa.csa.getExprs();
					LTSminGuardAnd gand = new LTSminGuardAnd();
					gor.addGuard(gand);
					gand.addGuard(new LTSminGuard(trans, makePCGuard(sa.t.getFrom(), sa.p)));
					for (int i = 0; i < cra_exprs.size(); i++) {
						final Expression csa_expr = csa_exprs.get(i);
						final Expression cra_expr = cra_exprs.get(i);
						if (!(cra_expr instanceof Identifier)) {
							gand.addGuard(new LTSminGuard(trans, compare(csa_expr,cra_expr)));
						}
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
	 * instrument the timeout expression for the specified TimeoutTransition.
	 * This will instrument the expression that NO transition is enabled. The
	 * dependency matrix is fixed accordingly. If tt is null,
	 * @param w The StringWriter to which the code is written.
	 * @param tt The TimeoutTransition to instrument code for.
	 */
	public void instrumentTimeoutExpression(TimeoutTransition tt) {
		for(Proctype p : spec) {
state_loop:	for (State st : p.getAutomaton()) {
				// Check if this state has an ElseTransition
				// If so, skip the transition, because this state
				// always has an active outgoing transition
				for(Transition trans: st.output) {
					if(trans instanceof ElseTransition) continue state_loop;
				}
				// Loop over all transitions of the state
				for(Transition trans : st.output) {
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
		for (Proctype p: spec) {
state_loop:	for (State st : p.getAutomaton()) {				
				// Check if this state has an ElseTransition
				// If so, skip the transition, because this state
				// always has an active outgoing transition
				for(Transition t : st.output) {
					if (t instanceof ElseTransition) continue state_loop;
				}
				for (Transition t: st.output) {
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
	
	/**
	 * instrument Pre code for a rendezvous couple.
	 */
	private void instrumentPreRendezVousAction(SendAction sa, ReadAction ra, int trans, LTSminTransition lt) {
		ChannelSendAction csa = sa.csa;
		ChannelReadAction cra = ra.cra;
		if(csa.getVariable() != cra.getVariable())
			throw new AssertionError("instrumentRendezVousAction() called with inconsequent ChannelVariable");
		ChannelVariable var = (ChannelVariable)csa.getVariable();
		if(var.getType().getBufferSize()>0)
			throw new AssertionError("instrumentRendezVousAction() called with non-rendezvous channel");
		List<Expression> csa_exprs = csa.getExprs();
		List<Expression> cra_exprs = cra.getExprs();
		if(csa_exprs.size() != cra_exprs.size())
			throw new AssertionError("instrumentRendezVousAction() called with incompatible actions: size mismatch");

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
		lt.addAction(assign(model.sv.procId(sa.p),sa.t.getTo().getStateId()));
		// Change process counter of receiver
		lt.addAction(assign(model.sv.procId(ra.p),ra.t.getTo().getStateId()));
		
		List<Expression> csa_exprs = csa.getExprs();
		List<Expression> cra_exprs = cra.getExprs();
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

	private static Expression compare(PriorityIdentifier e1, int nr) {
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
		Expression left = new PriorityIdentifier();
		Expression right = new ConstantExpression(new Token(PromelaConstants.NUMBER,"0"), 0);
		Expression e = new CompareExpression(new Token(PromelaConstants.LT,"<"), left, right);

		Expression e2 = compare(new PriorityIdentifier(), model.sv.procOffset(p));

		return new BooleanExpression(new Token(PromelaConstants.LOR,"||"), e, e2);
	}

	private Expression makeExclusiveAtomicGuard(Proctype p) {
		return compare(new PriorityIdentifier(), model.sv.procOffset(p));
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

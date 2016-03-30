package spins.promela.compiler.ltsmin;

import static spins.promela.compiler.Specification._NR_PR;
import static spins.promela.compiler.ltsmin.LTSminPrinter.ACCEPTING_STATE_LABEL_NAME;
import static spins.promela.compiler.ltsmin.LTSminPrinter.ACTION_EDGE_LABEL_NAME;
import static spins.promela.compiler.ltsmin.LTSminPrinter.ACTION_TYPE_NAME;
import static spins.promela.compiler.ltsmin.LTSminPrinter.ASSERT_ACTION_NAME;
import static spins.promela.compiler.ltsmin.LTSminPrinter.BOOLEAN_TYPE_NAME;
import static spins.promela.compiler.ltsmin.LTSminPrinter.GUARD_TYPE_NAME;
import static spins.promela.compiler.ltsmin.LTSminPrinter.NON_PROGRESS_STATE_LABEL_NAME;
import static spins.promela.compiler.ltsmin.LTSminPrinter.NO_ACTION_NAME;
import static spins.promela.compiler.ltsmin.LTSminPrinter.PROGRESS_ACTION_NAME;
import static spins.promela.compiler.ltsmin.LTSminPrinter.PROGRESS_STATE_LABEL_NAME;
import static spins.promela.compiler.ltsmin.LTSminPrinter.STATEMENT_EDGE_LABEL_NAME;
import static spins.promela.compiler.ltsmin.LTSminPrinter.STATEMENT_TYPE_NAME;
import static spins.promela.compiler.ltsmin.LTSminPrinter.VALID_END_STATE_LABEL_NAME;
import static spins.promela.compiler.ltsmin.state.LTSminTypeChanStruct.bufferVar;
import static spins.promela.compiler.ltsmin.state.LTSminTypeChanStruct.elemVar;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.and;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.assign;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.calc;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.chanContentsGuard;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.chanFreeGuard;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.compare;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.constant;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.dieGuard;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.eq;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.error;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.getOutTransitionsOrNullSet;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.id;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.isRendezVousReadAction;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.isRendezVousSendAction;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.negate;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.or;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.pcGuard;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import spins.promela.compiler.Preprocessor;
import spins.promela.compiler.Preprocessor.DefineMapping;
import spins.promela.compiler.ProcInstance;
import spins.promela.compiler.Proctype;
import spins.promela.compiler.Specification;
import spins.promela.compiler.actions.Action;
import spins.promela.compiler.actions.AssertAction;
import spins.promela.compiler.actions.AssignAction;
import spins.promela.compiler.actions.BreakAction;
import spins.promela.compiler.actions.ChannelReadAction;
import spins.promela.compiler.actions.ChannelSendAction;
import spins.promela.compiler.actions.ElseAction;
import spins.promela.compiler.actions.ExprAction;
import spins.promela.compiler.actions.GotoAction;
import spins.promela.compiler.actions.OptionAction;
import spins.promela.compiler.actions.PrintAction;
import spins.promela.compiler.actions.Sequence;
import spins.promela.compiler.automaton.ActionTransition;
import spins.promela.compiler.automaton.Automaton;
import spins.promela.compiler.automaton.ElseTransition;
import spins.promela.compiler.automaton.EndTransition;
import spins.promela.compiler.automaton.GotoTransition;
import spins.promela.compiler.automaton.NeverEndTransition;
import spins.promela.compiler.automaton.State;
import spins.promela.compiler.automaton.Transition;
import spins.promela.compiler.automaton.UselessTransition;
import spins.promela.compiler.expression.AritmicExpression;
import spins.promela.compiler.expression.BooleanExpression;
import spins.promela.compiler.expression.ChannelLengthExpression;
import spins.promela.compiler.expression.ChannelOperation;
import spins.promela.compiler.expression.ChannelReadExpression;
import spins.promela.compiler.expression.CompareExpression;
import spins.promela.compiler.expression.ConstantExpression;
import spins.promela.compiler.expression.EvalExpression;
import spins.promela.compiler.expression.Expression;
import spins.promela.compiler.expression.Identifier;
import spins.promela.compiler.expression.RemoteRef;
import spins.promela.compiler.expression.RunExpression;
import spins.promela.compiler.expression.TimeoutExpression;
import spins.promela.compiler.ltsmin.matrix.LTSminGuard;
import spins.promela.compiler.ltsmin.matrix.LTSminGuardAnd;
import spins.promela.compiler.ltsmin.matrix.LTSminGuardBase;
import spins.promela.compiler.ltsmin.matrix.LTSminGuardContainer;
import spins.promela.compiler.ltsmin.matrix.LTSminGuardNand;
import spins.promela.compiler.ltsmin.matrix.LTSminGuardOr;
import spins.promela.compiler.ltsmin.model.LTSminModel;
import spins.promela.compiler.ltsmin.model.LTSminState;
import spins.promela.compiler.ltsmin.model.LTSminTransition;
import spins.promela.compiler.ltsmin.model.ReadAction;
import spins.promela.compiler.ltsmin.model.ResetProcessAction;
import spins.promela.compiler.ltsmin.model.SendAction;
import spins.promela.compiler.ltsmin.state.LTSminSlot;
import spins.promela.compiler.ltsmin.state.LTSminVariable;
import spins.promela.compiler.ltsmin.util.LTSminDebug;
import spins.promela.compiler.ltsmin.util.LTSminDebug.MessageKind;
import spins.promela.compiler.ltsmin.util.LTSminProgress;
import spins.promela.compiler.ltsmin.util.LTSminRendezVousException;
import spins.promela.compiler.ltsmin.util.LTSminUtil.Pair;
import spins.promela.compiler.optimizer.RenumberAll;
import spins.promela.compiler.parser.ParseException;
import spins.promela.compiler.parser.Promela;
import spins.promela.compiler.parser.PromelaConstants;
import spins.promela.compiler.parser.Token;
import spins.promela.compiler.variable.ChannelType;
import spins.promela.compiler.variable.ChannelVariable;
import spins.promela.compiler.variable.Variable;
import spins.promela.compiler.variable.VariableType;

/**
 * Constructs the LTSminModel by walking over the SpinJa {@link Specification}.
 * First processes are instantiated by copying their CST.
 * 
 * @author Freark van der Berg, Alfons Laarman
 */
public class LTSminTreeWalker {

    private static final boolean SPLIT = false;
    
    public List<ReadAction> reads = new ArrayList<ReadAction>();
    public List<SendAction> writes = new ArrayList<SendAction>();
    
	private List<Identifier> ids = new ArrayList<Identifier>();

	private Identifier newid(Variable elemVar) {
		Identifier identifier = new Identifier(elemVar);
		ids.add(identifier);
		return identifier;
	}

	private Expression newid(Token token, Variable newVar, Expression arrayExpr, Identifier sub) {
		Identifier newId = new Identifier(token, newVar, arrayExpr, sub);
		ids.add(newId);
		return newId;
	}

	private void replaceVars(Variable v, Identifier id) {
		for (Identifier i : ids) {
			if (i.getVariable() == v) {
				i.setVariable(id.getVariable());
				if (i.getArrayExpr() != null) 
					throw new AssertionError("Not a lhs var place holder: "+ i +" = "+ id);
				i.setArrayIndex(id.getArrayExpr());
			}
		}
	}

	private final Specification spec;
	static boolean NEVER;
	static boolean LTSMIN_LTL = false;

	private LTSminDebug debug;

	LTSminGuardAnd deadlock = new LTSminGuardAnd();

	public LTSminTreeWalker(Specification spec, boolean ltsmin_ltl) {
		this.spec = spec;
		LTSMIN_LTL = ltsmin_ltl;
		NEVER = null != spec.getNever();
	}

	public static class Options {
	    public Options(boolean verbose, boolean no_gm, boolean must_write,
	                   boolean cnf, boolean nonever) {
	        this.verbose = verbose;
	        this.no_gm = no_gm;
	        this.must_write = must_write;
	        this.cnf = cnf;
	        this.nonever = nonever;
        }
	    public boolean nonever = false;
        public boolean verbose = false;
	    public boolean no_gm = false;
	    public boolean must_write = false;
        public boolean cnf = false;
	}

	TimeoutExpression timeout = null;
	
	/**
	 * generates and returns an LTSminModel from the provided Specification
	 * @param no_gm skip guard matrices
	 */
	public LTSminModel createLTSminModel(String name, Options opts,
	                                     Map<String, Expression> exports,
                                         Expression progress) {
		this.debug = new LTSminDebug(opts.verbose);
        debug.say("Generating next-state function ...");
        debug.say_indent++;
        LTSminProgress report = new LTSminProgress(debug).startTimer();

		instantiate();
		bindByReferenceCalls();

        LTSminModel model = new LTSminModel(name, spec);
		createModelTransitions(model);
		createModelAssertions(model);

        debug.say_indent--;
        debug.say("Generating next-state function done (%s sec)",
                  report.stopTimer().sec());
        debug.say("");

        model.createStateVector(spec, debug);
		createModelLabels(model, exports, progress);
		LTSminDMWalker.walkModel(model, debug, opts);
		LTSminGMWalker.generateGuardInfo(model, opts, debug);
		return model;
	}

	private void createModelAssertions(LTSminModel model) {
        for (RemoteRef ref : spec.remoteRefs) {
            ProcInstance instance = ref.getInstance();
            Expression pid = newid(instance.getPID());
            Expression left = eq(pid, constant(-1));
            Expression right = eq(pid, constant(instance.getID()));
            Expression condition = or(left, right);
            model.assertions.add(new Pair<Expression,String>(condition,
                    "Statically computed PID ("+ instance.getID()+ ") of process "+
                    instance.getName() +" differs from actual PID"));
        }
    }

    /**
	 * Set accepting state, valid end-state and progress-state conditions for this model. 
	 * Accepting condition semantics are overloaded with valid end state semantics.
     * @param model TODO
	 */
    private void createModelLabels(LTSminModel model,
                                   Map<String, Expression> exports,
                                   Expression progress) {

        debug.say("Creating state labels");
        
        if (!BOOLEAN_TYPE_NAME.equals(VariableType.BOOL.getName()))
             debug.say(MessageKind.FATAL, "Not exporting boolean type as \"bool\" (LTSmin standard)");
        /* always add the bool type */
        model.addType(VariableType.BOOL.getName());
        model.addTypeValue(VariableType.BOOL.getName(), "false", 0); // index 0
        model.addTypeValue(VariableType.BOOL.getName(), "true", 1);  // index 1

        model.addType(GUARD_TYPE_NAME);
        model.addTypeValue(GUARD_TYPE_NAME, "false", 0); // index 0
        model.addTypeValue(GUARD_TYPE_NAME, "true",  1); // index 1
        model.addTypeValue(GUARD_TYPE_NAME, "maybe", 2); // index 2
        
        /* Generate static list of types. mtypes have values */
        for (LTSminSlot slot : model.sv) {
            LTSminVariable var = slot.getVariable();
            String cType = var.getVariable().getType().getName();
            if (model.getType(cType) != null) continue; 
            model.addType(cType);
            if (cType.equals("mtype")) {
                List<String> mtypes = model.getMTypes();
                model.addTypeValue("mtype", "uninitialized");
                ListIterator<String> it = mtypes.listIterator(mtypes.size());
                while (it.hasPrevious()) { // indices are reversed!
                    model.addTypeValue("mtype", it.previous());
                }
            }
        }

        /* Statements are exported in SPIN format with line numbers for traces */
        model.addType(STATEMENT_TYPE_NAME);
        model.addEdgeLabel(STATEMENT_EDGE_LABEL_NAME, STATEMENT_TYPE_NAME);
        for(LTSminTransition t : model.getTransitions()) {
            Action act =  (t.getTransition().getActionCount() > 0 ? t.getTransition().getAction(0) : null);
            String name = t.getName().split("\t")[1];
            int line = null == act ? -1 : act.getToken().beginLine;
            State to = t.getTransition().getTo();
            int id = null == to ? -1 : to.getStateId();
            String valid = to == null || to.isEndingState() ? "valid" : "invalid";
            String progr = t.isProgress() ? " <progress>" : "";
            name = "group "+ t.getGroup() +" ("+ t.getProcess().getName() +") "+ 
                   Preprocessor.getFileName() +":"+ line + progr +
                   " (state "+ id +") <"+ valid +" end state> "+ name;
            // group G (PROCESS) FILE:LINE <progress> (state TO) <(in)valid end state> NAME 
            model.addTypeValue(STATEMENT_TYPE_NAME, name);
        }

        /* Action edge labels are used for assertion violation detection */
        model.addType(ACTION_TYPE_NAME);
        model.addTypeValue(ACTION_TYPE_NAME, NO_ACTION_NAME, 0); // no action
        model.addTypeValue(ACTION_TYPE_NAME, ASSERT_ACTION_NAME, 1);
        model.addTypeValue(ACTION_TYPE_NAME, PROGRESS_ACTION_NAME, 2);
        model.addEdgeLabel(ACTION_EDGE_LABEL_NAME, ACTION_TYPE_NAME);

        /* Add accepting state labels for never claim */
		if (NEVER) {
			Proctype never = spec.getNever();
			Expression or = null;
			if (never.getStartState().isAcceptState()) {
	            Variable pc = never.getPC();
			    Expression g = compare(PromelaConstants.EQ, newid(pc), constant(-1));
			    or = or == null ? g : or(or, g) ; // Or
			}
			for (State s : never.getAutomaton()) {
				if (s.isAcceptState()) {
				    Expression g = pcGuard(s, never).getExpr();
				    or = or == null ? g : or(or, g) ; // Or
				}
			}
			if (or != null) { // maybe a never claim with an invariant (assertion)
			    model.addStateLabel(ACCEPTING_STATE_LABEL_NAME, new LTSminGuard(or));
			}
		}

		/* Add nonprogress state label and progress edge label */
		if (progress == null) {
            Expression or = null;
            Expression and = null;
    		for (ProcInstance pi : spec) {
    		    for (State s : pi.getAutomaton()) {
    		        if (s.isProgressState()) {
    		            Variable pc = pi.getPC();
    		            Expression counter = constant(s.getStateId());
    		            Expression e = compare(PromelaConstants.EQ, newid(pc), counter);
    	                or = or == null ? e : or(or, e);
                        Expression ne = compare(PromelaConstants.NEQ, newid(pc), counter);
                        and = and == null ? ne : and(and, ne) ;
    		        }
    		    }
    		}
    		if (or != null) {
    		    model.addStateLabel(PROGRESS_STATE_LABEL_NAME, new LTSminGuard(or));
                model.addStateLabel(NON_PROGRESS_STATE_LABEL_NAME, new LTSminGuard(and));
    		}
		} else {
            model.addStateLabel(PROGRESS_STATE_LABEL_NAME, new LTSminGuard(progress));
            Expression np = negate(progress);
            model.addStateLabel(NON_PROGRESS_STATE_LABEL_NAME, new LTSminGuard(np));
		}

		/* Export label for valid end states */
		Expression end = compare(PromelaConstants.EQ, newid(_NR_PR), constant(0)); // or
        Expression and = null;
    	for (ProcInstance instance : spec) {
			Variable pc = instance.getPC();
            Expression labeled = compare(PromelaConstants.EQ, newid(pc), constant(-1));
	    	for (State s : instance.getAutomaton()) {
		    	if (s.hasLabelPrefix("end")) {
		    		labeled = or(labeled, pcGuard(s, instance).getExpr()); // Or
		    	}
	    	}
	    	and = and == null ? labeled : and(and, labeled) ; // And
    	}
    	if (and != null)
    	    end = or(end, and); // Or
		model.addStateLabel(VALID_END_STATE_LABEL_NAME, new LTSminGuard(end));

		// Add export labels
		for (Map.Entry<String,Expression> export : exports.entrySet()) {
		    Expression ex = instantiate(export.getValue(), null, false);
            LTSminGuard guard = new LTSminGuard(ex);
            model.addStateLabel(export.getKey(), guard);
		}
		
		// Export all labels:

        for (ProcInstance pi : spec) {
            for (State s : pi.getAutomaton()) {
                //if (s.isProgressState())  continue;
                for (String label : s.getLabels()) {
                    LTSminGuard g = model.getStateLabel(label);
                    Expression x = g == null ? null : g.getExpr();
                    
                    Variable pc = pi.getPC();
                    Expression counter = constant(s.getStateId());
                    Expression e = compare(PromelaConstants.EQ, newid(pc), counter);
                    e = x == null ? e : or(e, x);
                    model.addStateLabel(label, new LTSminGuard(e));
                    //System.out.println("Adding label "+ label +" --> "+ e);
                }
            }
        }

        for (Map.Entry<String, LTSminGuard> label : model.getLabels()) {
            Expression e = label.getValue().getExpr();
            if (e instanceof RemoteRef) {
                label.setValue(new LTSminGuard(instantiate(e, null, false)));
            }
        }
	}

    private List<String> iCount = new ArrayList<String>();

    private int getInstanceCount(Proctype p) {
    	DefineMapping nrInstances, original;
    	nrInstances = original = Preprocessor.defines("__instances_"+ p.getName());
		if (null != nrInstances) {
			int count = -1;
			while (-1 == count) try {
				count = Integer.parseInt(nrInstances.defineText.trim());
			} catch (NumberFormatException nf) {
				nrInstances = Preprocessor.defines(nrInstances.defineText.trim());
				if (null == nrInstances) break; 
			}
			if (-1 == count) throw new AssertionError("Cannot parse "+ original);
			return count;
		}
		// query instantiation count from user
		System.out.print("Provide instantiation number for proctype "+ p.getName() +": ");
		InputStreamReader converter = new InputStreamReader(System.in);
		BufferedReader in = new BufferedReader(converter);
		String number;
		try {
			number = in.readLine();
		} catch (IOException e) {throw new AssertionError(e);}
		int num = Integer.parseInt(number);
		iCount.add(p.getName() +" "+ num);
		return num;
    }
    
	/** Active processes can be differentiated from each other by the value of
	 * their process instantiation number, which is available in the predefined
	 * local variable _pid . Active processes are always instantiated in the
	 * order in which they appear in the model, so that the first such process
	 * (whether it is declared as an active process or as an init process) will
	 * receive the lowest instantiation number, which is zero. 
	 * @param debug */
	private void instantiate() {

        debug.say("Instantiating processes");
        debug.say_indent++;
	    
		List<ProcInstance> instances = new ArrayList<ProcInstance>();
		List<ProcInstance> active = new ArrayList<ProcInstance>();

		timeout = new TimeoutExpression(new Token(PromelaConstants.TIMEOUT, "timeout"));
		int id = 0;
		for (Proctype p : spec.getProcs()) { // add active processes (including init)
			for (int i = 0; i < p.getNrActive(); i++) {
				ProcInstance instance = instantiate(p, id, i);
				p.addInstance(instance);
				active.add(instance);
				id++;
			}
		}

		// set number of processes to initial number of active processes.
		try {
			_NR_PR.setInitExpr(constant(id));
		} catch (ParseException e) { assert (false); }

		for (Proctype p : spec.getProcs()) {
			if (0 != p.getNrActive())
				continue;
			int instanceCount = getInstanceCount(p);
			for (int i = 0; i < instanceCount; i++) {
				ProcInstance instance = instantiate(p, id, i);
				p.addInstance(instance);
				instances.add(instance);
				id++;
			}
		}
		if (null != spec.getNever()) {
			Proctype never = spec.getNever();
			ProcInstance n = instantiate(never, -1, -1);
			spec.setNever(n);
		}
		for (String binding : iCount) {
			debug.say(MessageKind.NORMAL, "#define __instances_"+ binding);
		}
		for (ProcInstance instance : active)
			instances.add(instance);
		spec.setInstances(instances);

        debug.say_indent--;
	}

	/**
	 * Copies proctype to an instance.
	 */
	private ProcInstance instantiate(Proctype p, int id, int index) {
		ProcInstance instance = new ProcInstance(p, index, id);
		Expression e = instantiate(p.getEnabler(), instance, false);
		instance.setEnabler(e);
		for (Variable var : p.getVariables()) {
			Variable newvar = instantiate(var, instance);
			if (newvar.getName().equals(Promela.C_STATE_PROC_COUNTER))
				newvar.setAssignedTo(); // Process counter is always assigned to
			instance.addVariable(newvar, p.getArguments().contains(var));
		}
		instance.lastArgument();
		HashMap<State, State> seen = new HashMap<State, State>();
		instantiate(p.getStartState(), instance.getStartState(), seen, instance);
		new RenumberAll().optimize(instance.getAutomaton());
		return instance;
	}
	
	/**
	* Copy the automaton
	*/
	private void instantiate(State state, State newState,
							 HashMap<State, State> seen, ProcInstance p) {
		if (null == state || null != seen.put(state, newState))
			return;
		newState.setLabels(state.getLabels());
		for (Transition trans : state.output) {
			State next = trans.getTo();
			if (next == null)
    			_NR_PR.setAssignedTo();
			State newNextState = null;
			if (null != next) if (seen.containsKey(next))
				newNextState = seen.get(next);
			else
				newNextState = new State(p.getAutomaton(), next.isInAtomic());
			Transition newTrans =
				(trans instanceof ActionTransition ? new ActionTransition(newState, newNextState) :
				(trans instanceof ElseTransition ? new ElseTransition(newState, newNextState) :
				(trans instanceof EndTransition ? new EndTransition(newState) :
				(trans instanceof NeverEndTransition ? new NeverEndTransition(newState) :
				(trans instanceof GotoTransition ? new GotoTransition(newState, newNextState, trans.getText().substring(5)) :
				(trans instanceof UselessTransition ? new UselessTransition(newState, newNextState, trans.getText()) :
				 null))))));
			newTrans.setLabels(trans.getLabels());
			for (Action a : trans)
				newTrans.addAction(instantiate(a, newTrans, p, null));
			instantiate(next, newNextState, seen, p);
		}
	}

	private Variable instantiate(Variable var, ProcInstance p) {
		if (null == var.getOwner()) // global var, no copy required
			return var;
		if (!p.getTypeName().equals(var.getOwner().getName()))
			throw new AssertionError("Expected instance of type "+ var.getOwner().getName() +" not of "+ p.getTypeName());
        Variable newvar = var instanceof ChannelVariable ?
                new ChannelVariable(var) : new Variable(var);
        newvar.setOwner(p);
		try {
			if (null != var.getInitExpr())
				newvar.setInitExpr(instantiate(var.getInitExpr(), p, false));
		} catch (ParseException e1) { throw new AssertionError("Identifier"); }
		if (newvar.getName().equals(Promela.C_STATE_PID)) {
			int initial_pid = (p.getNrActive() == 0 ? -1 : p.getID());
			try { newvar.setInitExpr(constant(initial_pid));
			} catch (ParseException e) { assert (false); }
		}
		return newvar;
	}

	/**
	 * Copy actions
	 */
	private Action instantiate(Action a, Transition t, ProcInstance p, OptionAction loop) {
		if(a instanceof AssignAction) {
			AssignAction as = (AssignAction)a;
			Identifier id = (Identifier)instantiate(as.getIdentifier(), p, true);
			Expression e = instantiate(as.getExpr(), p, false);
			return new AssignAction(as.getToken(), id, e);
		} else if(a instanceof ResetProcessAction) {
			throw new AssertionError("Unexpected ResetProcessAction");
		} else if(a instanceof AssertAction) {
			AssertAction as = (AssertAction)a;
			Expression e = instantiate(as.getExpr(), p, false);
			return new AssertAction(as.getToken(), e);
		} else if(a instanceof PrintAction) {
			PrintAction pa = (PrintAction)a;
			PrintAction newpa = new PrintAction(pa.getToken(), pa.getString());
			for (final Expression expr : pa.getExprs())
				newpa.addExpression(instantiate(expr, p, false));
			return newpa;
		} else if(a instanceof ExprAction) {
			ExprAction ea = (ExprAction)a;
			Expression expr = ea.getExpression();
			Expression e;
			try {
			    e = instantiate(expr, p, false);
			} catch (AssertionError ae) {
			    if (!(expr instanceof TimeoutExpression)) {
			        throw new AssertionError("Complex expression with timeout: "+ expr);
			    }
			    e = timeout;//bool(true);
			}
			return new ExprAction(e);
		} else if(a instanceof OptionAction) { // options in a d_step sequence
			OptionAction oa = (OptionAction)a;
			OptionAction newoa = new OptionAction(oa.getToken(), oa.loops());
			newoa.hasSuccessor(oa.hasSuccessor());
			loop = newoa.loops() ? newoa : loop;
			for (Sequence seq : oa)
				newoa.startNewOption((Sequence)instantiate(seq, t, p, loop)); 
			return newoa;
		} else if(a instanceof Sequence) {
			Sequence seq = (Sequence)a;
			Sequence newseq = new Sequence(seq.getToken());
			for (Action aa : seq) {
				Action sub = instantiate(aa, t, p, loop);
				newseq.addAction(sub);
			}
			return newseq;
		} else if(a instanceof BreakAction) {
			BreakAction ba = (BreakAction)a;
			BreakAction newba = new BreakAction(ba.getToken(), loop);
			return newba;
		} else if(a instanceof ElseAction) {
			return a; // readonly, hence can be shared
		} else if(a instanceof GotoAction) {
			return a; // readonly, hence can be shared
		} else if(a instanceof ChannelSendAction) {
			ChannelSendAction csa = (ChannelSendAction)a;
			Identifier id = (Identifier)instantiate(csa.getIdentifier(), p, false);
			ChannelSendAction newcsa = new ChannelSendAction(csa.getToken(), id, csa.isSorted());
			for (Expression e : csa.getExprs())
				newcsa.addExpression(instantiate(e, p, false));
            writes.add(new SendAction(newcsa, t, t.getProc()));
			return newcsa;
		} else if(a instanceof ChannelReadAction) {
			ChannelReadAction cra = (ChannelReadAction)a;
			Identifier id = (Identifier)instantiate(cra.getIdentifier(), p, false);
			ChannelReadAction newcra = new ChannelReadAction(cra.getToken(), id, cra.isPoll(), cra.isRandom());
			for (Expression e : cra.getExprs()) {
				Expression newe = instantiate(e, p, true);
				newcra.addExpression(newe);
			}
			reads.add(new ReadAction(newcra, t, t.getProc()));
			return newcra;
		} else { // Handle not yet implemented action
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+a.getClass().getName());
		}
	}	
	
	/**
	 * Copy expressions with instantiated processes.
	 */
	private Expression instantiate(Expression e, ProcInstance p, boolean write) {
		if (null == e) return null;

		if (e instanceof Identifier) { // also: LTSminIdentifier
			Identifier id = (Identifier)e;
			Variable var = id.getVariable();
			Variable newVar = var;
			if (null != var.getOwner()) {
			    if (id.getInstanceIndex() != -1) {
			        for (ProcInstance i : var.getOwner().getInstances() ) {
			            if (i.getID() == id.getInstanceIndex()) p = i;
			        }
			        if (p == null) throw new AssertionError("ProcInstance "+ id.getInstanceIndex() +" with PID "+ id.getInstanceIndex() +" not found for remote ref "+ id.toString() +" (wrong PID?)");
			    } else if (p == null) {
                    throw new AssertionError("Instantiating global expression (a label?) failed: "+ e);
                } else if (!p.getTypeName().equals(var.getOwner().getName())) {
					throw new AssertionError("Expected instance of type "+ var.getOwner().getName() +" not of "+ p.getTypeName());
				}
				newVar = p.getVariable(var.getName()); // load copied variable
				if (newVar == null) {
				    throw new AssertionError("Could not locate instantiated variable: "+ var);
				}
			}
			if (write) newVar.setAssignedTo();
			Expression arrayExpr = instantiate(id.getArrayExpr(), p, write);
			Identifier sub = (Identifier)instantiate(id.getSub(), p, write);
			return newid(id.getToken(), newVar, arrayExpr, sub);
		} else if (e instanceof AritmicExpression) {
			AritmicExpression ae = (AritmicExpression)e;
			Expression ex1 = instantiate(ae.getExpr1(), p, write);
			Expression ex2 = instantiate(ae.getExpr2(), p, write);
			Expression ex3 = instantiate(ae.getExpr3(), p, write);
			return new AritmicExpression(ae.getToken(), ex1, ex2, ex3);
		} else if (e instanceof BooleanExpression) {
			BooleanExpression be = (BooleanExpression)e;
			Expression ex1 = instantiate(be.getExpr1(), p, write);
			Expression ex2 = instantiate(be.getExpr2(), p, write);
			return new BooleanExpression(be.getToken(), ex1, ex2);
		} else if (e instanceof CompareExpression) {
			CompareExpression ce = (CompareExpression)e;
			Expression ex1 = instantiate(ce.getExpr1(), p, write);
			Expression ex2 = instantiate(ce.getExpr2(), p, write);
			return new CompareExpression(ce.getToken(), ex1, ex2);
		} else if (e instanceof ChannelLengthExpression) {
			ChannelLengthExpression cle = (ChannelLengthExpression)e;
			Identifier id = (Identifier)cle.getExpression();
			Identifier newid = (Identifier)instantiate(id, p, write);
			try {
				return new ChannelLengthExpression(cle.getToken(), newid);
			} catch (ParseException e1) {
				throw new AssertionError(e1);
			}
		} else if (e instanceof ChannelReadExpression) {
			ChannelReadExpression cre = (ChannelReadExpression)e;
			Identifier id = (Identifier)instantiate(cre.getIdentifier(), p, write);
			ChannelReadExpression res = new ChannelReadExpression(cre.getToken(), id, cre.isRandom());
			for (Expression expr : cre.getExprs())
				res.addExpression(instantiate(expr, p, write));
			return res;
		} else if (e instanceof ChannelOperation) {
			ChannelOperation co = (ChannelOperation)e;
			Identifier id = (Identifier)instantiate(co.getExpression(), p, write);
			try {
				return new ChannelOperation(co.getToken(), id);
			} catch (ParseException e1) {
				throw new AssertionError("ChanOp");
			}
		} else if (e instanceof RunExpression) {
			RunExpression re = (RunExpression)e;
			RunExpression newre = new RunExpression(e.getToken(), spec.getProcess(re.getId())); 
			try {
				for (Expression expr : re.getExpressions())
					newre.addExpression(instantiate(expr, p, write));
			} catch (ParseException e1) {
				throw new AssertionError("RunExpression");
			}
			spec.runs.add(newre); // add runexpression to a list
			return newre;
		} else if (e instanceof EvalExpression) {
			EvalExpression eval = (EvalExpression)e;
			Expression ex = instantiate(eval.getExpression(), p, write);
			return new EvalExpression(e.getToken(), ex);
	    } else if (e instanceof TimeoutExpression) {
	        throw new AssertionError("Timeout outside ExprAction.");
		} else if (e instanceof ConstantExpression) {
		    return e; // readonly, hence can be shared
		} else if (e instanceof RemoteRef) {
			RemoteRef rr = (RemoteRef)e;
			Expression ex = instantiate(rr.getExpr(), p, write);
			Proctype proc = spec.getProcess(rr.getProcessName());
			if (null == proc) throw new AssertionError("Wrong process: "+ rr);
			RemoteRef ref = new RemoteRef(rr.getToken(), proc, rr.getLabel(), ex);
			spec.remoteRefs.add(ref);
	        return ref;
		} else {
			throw new AssertionError("Not yet implemented: "+e.getClass().getName());
		}
	}

	/**
	 * Binds any channel type arguments of all RunExpressions by reference.
	 */
	private void bindByReferenceCalls() {
	    debug.say("Statically binding references");
        debug.say_indent++;

		if (spec.runs.size() > 0)
			_NR_PR.setAssignedTo();
		for (Proctype p : spec.getProcs()) {
			if (p.getNrActive() > 0) continue;
			List<RunExpression> rr = new ArrayList<RunExpression>();
			for (RunExpression re : spec.runs)
				if (re.getProctype().equals(p)) rr.add(re);
			if (rr.size() == 0) {
				debug.say(MessageKind.WARNING, "Process "+ p.getName() +" is inactive.");
				continue;
			}
			if (rr.size() == 1 && p.getInstances().size() > 1) {
				for (ProcInstance target : p.getInstances()) {
					target.getPID().setAssignedTo(); // PID is changed
					bindArguments(rr.get(0), target, true);
				}
			} else if (rr.size() == p.getInstances().size()) {
				Iterator<ProcInstance> it = p.getInstances().iterator();
				for (RunExpression re : rr) {
					ProcInstance target = it.next();
					target.getPID().setAssignedTo(); // PID is changed
					re.setInstance(target);
					debug.say(MessageKind.NORMAL, "Statically binding chans of procinstance "+ target +" to run expression at l."+ re.getToken().beginLine);
					bindArguments(re, target, false);
				}
			} else {
				for (ProcInstance target : p.getInstances()) {
					bindArguments(rr.get(0), target, true);
					target.getPID().setAssignedTo(); // PID is changed
				}
			}
		}

		List<Variable> remove = new LinkedList<Variable>();
		for (Variable v : spec.getVariableStore().getVariables()) {
			Expression init = v.getInitExpr();
			if (!(v instanceof ChannelVariable) || null == init) continue;

			if (!(init instanceof Identifier)) throw new AssertionError("No ID:"+ init +" Init of: "+ v);
			Identifier id = (Identifier) init;
			System.out.println(v.getName() +" --> "+ id.toString());
			replaceVars (v, id);
			remove.add(v);
		}
		for (Variable v : remove) {
			spec.getVariableStore().delVariable(v);
		}

		// Update the spec with the results
        spec.clearReadActions();
        spec.clearWriteActions();
        for (ReadAction ra : reads) spec.addReadAction(ra);
        for (SendAction rs : writes) spec.addWriteAction(rs);

        debug.say_indent--;
	}

	private void bindArguments(RunExpression re, ProcInstance target,
							   boolean dynamic) {
		if (null == target) throw new AssertionError("Target of run expression is not found: "+ re.getId());
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
					throw error("Run expression's parameter for "+ v +" does not match the proc's argument type.", re.getToken());
				Identifier id = (Identifier)param;
				Variable varParameter = id.getVariable();
				VariableType t = varParameter.getType();
				if (!(t instanceof ChannelType))
					throw error("Parameter "+ count +" of "+ re.getId() +" should be a channeltype.", re.getToken());
				ChannelType ct = (ChannelType)t;
				if (ct.getBufferSize() == -1)
					throw error("Could not deduce channel declaration for parameter "+ count +" of "+ re.getId() +".", re.getToken());
				if (dynamic/* || varParameter.getArraySize() > -1*/)
					throw new AssertionError("Cannot dynamically bind "+ target.getTypeName() +" to the run expressions in presence of arguments of type channel.\n" +
							"Change the proctype's arguments or unroll the loop with run expressions in the model.");
				String name = v.getName();
				debug.say(MessageKind.DEBUG, "Binding "+ target +"."+ name +" to "+ varParameter.toString());
				
				if (varParameter.getArraySize() > -1) {
				    try {
                        id.getArrayExpr().getConstantValue();
                    } catch (ParseException e) {
                        throw new AssertionError("Cannot () statically bind "+ target.getTypeName() +" to the run expressions without constant channel array index.");
                    }
				}

				replaceVars (v, id);
				
			} else if (!dynamic) {
				try {
					v.setInitExpr(param);
				} catch (ParseException e) {
					throw new AssertionError("Wrong type in run parameter." + e);
				}
			} else if (dynamic) {
				v.setAssignedTo();
			}
		}
		for (Variable v : target.getVariables()) {
			if (null == v.getInitExpr()) continue;
			try {
				v.getInitExpr().getConstantValue();
			} catch (ParseException e) {
				if (dynamic)
					throw new AssertionError("Cannot dynamically bind "+ target.getTypeName() +" to the run expressions in presence of arguments of init expressions that use the arguments.\n" +
							"Change the proctype's arguments or unroll the loop with run expressions in the model.");
				Expression init = v.getInitExpr();
				v.unsetInitExpr();
				v.setAssignedTo();
				re.addInitAction(new AssignAction(new Token(PromelaConstants.ASSIGN), newid(v), init));
			}
		}
	}

	/**
	 * Creates the state transitions.
	 * @param model TODO
	 */
	private void createModelTransitions(LTSminModel model) {
        debug.say("Creating transitions");
        debug.say_indent++;

		for (ProcInstance p : spec) {
			debug.say(MessageKind.DEBUG, "[Proc] " + p.getName());
			State state = p.getAutomaton().getStartState();
			State ns = null;
			if (NEVER) ns = spec.getNever().getAutomaton().getStartState();
			createCrossProduct(model, state, ns, -1);
		}

		Set<Expression> gexpr = new HashSet<Expression>();

		boolean has_timeout = false;
		for (LTSminTransition lt : model) {
			LTSminGuardNand tg = new LTSminGuardNand();
            for (LTSminGuardBase g : lt) {
                if (g instanceof LTSminGuard) {
                    Expression x = ((LTSminGuard)g).getExpr();
                    if (x instanceof TimeoutExpression) {
                        lt.setTimeout();
                        has_timeout = true;
                        break;
                    }
                }
            }
            if (lt.isTimeout()) continue;

			for (LTSminGuardBase g : lt) {
			    Expression x = g.getExpression();
			    if (!gexpr.add(x)) {
			        tg.guards.add(g);
			    }
			}
			if (tg.guards.size() > 0) {
			    deadlock.guards.add(tg);
			}
		}
		deadlock.setDeadlock();
		timeout.setDeadlock(deadlock.getExpression());

		// let never automata continue on deadlock
		if (NEVER && !LTSMIN_LTL) {
		    if (has_timeout) throw new AssertionError("Timeouts and never not implemented");
			Automaton never = spec.getNever().getAutomaton();
			NEVER = false;
			State start = never.getStartState();
            createCrossProduct(model, start, null, 1);
			NEVER = true;
			for (State s : never) { // add guards:
				LTSminState ns = model.getOrAddState(new LTSminState(s, null));
				for (LTSminTransition lt : ns.getOut()) {
					lt.buchi = true;
					lt.addGuard(deadlock);
				}
			}
		}

		debug.say_indent--;
	}

    /**
	 * Walks over process automaton (and, if present, over the never automaton
	 * synchronizing its execution).
	 * For rendez-vous send actions, all matching read actions are found and 
	 * synchronized. The control passes to the read action process, which matters
	 * if this action is in a synchronized block.
     * @param model TODO
	 */
	private LTSminState createCrossProduct(LTSminModel model, State state, State never, int alevel) {
		LTSminState state2 = new LTSminState(state, never);
		LTSminState begin = model.getOrAddState(state2);
		if (null == state || (NEVER && null == never)) { // no outgoing transitions
		    return begin;
		}

	    // update stack numbering for atomic cycle search
        alevel = atomicLevel(state, alevel);
		if (begin != state2) { // seen state
		    atomicCycleCheck (model, begin, alevel); // check for atomic cycle on stack
		    return begin;
		}
		// update stack table
        addSearchStack (begin, alevel);

		addEndTransitions(state, never);
		for (Transition out : state.output) { // trans X (ntrans | {null}):
		for (Transition nout : getOutTransitionsOrNullSet(never)) {
			Action a = out.getActionCount() > 0 ? out.iterator().next() : null;
			State nto = null != nout ? nout.getTo() : null;
			if (isRendezVousSendAction(a)) {
				List<LTSminTransition> set = // matches all possible partners
					createRendezVousTransitions(out, nout, (ChannelSendAction)a);
				for (LTSminTransition lt : set) {
					State to = lt.getSync().getTo(); // pass control to read
					model.addTransition(lt);
					LTSminState end = createCrossProduct(model, to, nto, alevel);
					annotate(lt, begin, end);
				}
                createCrossProduct(model, out.getTo(), nto, alevel);
			} else if (isRendezVousReadAction(a)) {
				// skip, a transition is created for the comm. partner
			} else {
				for (LTSminTransition lt : createStateTransition(out, nout)) {
        			model.addTransition(lt);
        			State to = out.getTo();
                    LTSminState end = createCrossProduct(model, to, nto, alevel);
        			annotate(lt, begin, end);
				}
			}
		}}

		// update stack table
		removeSearchStack (begin);
		return begin;
	}

	/**
	 * We use the DFS above to detect cycles containing only atomic states.
	 * The states on the stack are annotated with a number which only grows
	 * if atomicity changes from one stack state to the next (a transition
	 * entering or leaving an atomic block is traversed). If a stack state is
	 * encountered (the DFS found a cycle) with the same counter as the current
	 * stack counter, an atomic cycle is found.
	 * We dually use the stack number to indicate atomicity of states to
	 * simplify the code:
	 * a negative number is given to non atomic states and a positive number is
	 * given to atomic states. The absolute value of the integer is incremented. 
	 */
    private Map<LTSminState, Integer> stackMap = new HashMap<LTSminState, Integer>();

	private void removeSearchStack(LTSminState begin) {
        stackMap.remove(begin);
    }

    private void addSearchStack(LTSminState begin, int alevel) {
        Integer x = stackMap.put(begin, alevel);
        if (x != null) throw new RuntimeException("seen: " + x);
    }

    private void atomicCycleCheck(LTSminModel model, LTSminState begin, int alevel) {
        if (alevel < 0) // state is not atomic, cycle cannot be atomic
            return;
        Integer x = stackMap.get(begin);
        if (x == null ||  x.intValue() == alevel) { // self loop or cycle
            model.hasAtomicCycles = true;
            begin.setOnCycle();
        }
    }

    /**
     * @param newState
     * @param previous
     * @return predecessor.atomic == newState.atomic /\ return == previous \/
     *         predecessor.atomic != newState.atomic /\ abs(return) > abs(previous) /\ return>0 != prevous>0
     */
    private int atomicLevel(State newState, int previous) {
        boolean patomic = previous > 0;
        if (newState.isInAtomic() == patomic) {
            return previous;
        } else {
            int res = -previous + (previous > 0 ? -1 : 1);
            if (res <= 0 && newState.isInAtomic()) {
                throw new RuntimeException("Invariant violated");
            }
            return res;
        }
    }

    private void addEndTransitions(State state, State never) {
		if (0 == state.sizeOut())
			state.newTransition(null);
		if (null != never && 0 == never.sizeOut()) {
            never.newTransition(null);
		}
	}

	/**
	 * Executed in the edge backtrack of the DFS to collect reachable atomic
	 * transitions .
	 */
	private void annotate(LTSminTransition lt, LTSminState begin,
							LTSminState end) {
		lt.setBegin(begin);
		lt.setEnd(end);
		if (begin.isAtomic())
			begin.addTransition(lt);
		if (begin.isAtomic() && end.isAtomic()) {
			begin.addTransitions(end.getTransitions());
		}
	}

	@SuppressWarnings("unused")
    private List<LTSminTransition> createStateTransition(Transition t,
	                                                     Transition n) {
		++debug.say_indent;
		if(n!=null) {
			debug.say(MessageKind.DEBUG, "Handling trans: " + t.getClass().getName() + " || " + n.getClass().getName());
		} else {
			debug.say(MessageKind.DEBUG, "Handling trans: " + t.getClass().getName());
		}
		--debug.say_indent;

        LinkedList<LTSminTransition> list = new LinkedList<LTSminTransition>();      
		ProcInstance p = (ProcInstance)t.getProc();

		// Find a channel for which we can create multiple transitions (buffer > 0)
		// (also is buffer == 1, to simplify expressions)
		Identifier multiple = null;
		int buffer = 1;
		int array = -1;
		boolean read = false;
		if (SPLIT) {
            for (Action action : t) {
                Identifier id = getChannel(action);
                if (id == null) continue;
    
                ChannelVariable cv = (ChannelVariable)id.getVariable();
                if (cv.getType().getBufferSize() > -1) {
                    multiple = id;
                    buffer = cv.getType().getBufferSize();
                    array = cv.getArraySize();
                    read = action instanceof ChannelReadAction;
                    break;
                }
            }
		}

        for (int x = 0; x < (array == -1 || !SPLIT ? 1 : array) ; x++ ) {
        for (int y = 0; y < buffer; y++ ) {
    		LTSminTransition lt = new LTSminTransition(t, n);
    
    		lt.addGuard(pcGuard(t.getFrom(), p)); // process counter

    		if (multiple != null) {
                int next = read ? y+1 : y;
                lt.addGuard(chanContentsGuard(multiple, PromelaConstants.EQ, next));
                if (array != -1) {
                    lt.addGuard(compare(PromelaConstants.EQ,
                                        multiple.getArrayExpr(), constant(x)));
                }
            }
    		addSpecialGuards(lt, t, p); // proc die order && provided condition 
    		createEnabledGuard(t, lt); // enabled action or else transition 
    
    		if (lt.isNeverExecutable()) {
    		    debug.say(MessageKind.NORMAL, "Removing dead transition "+ t +" of process "+ t.getProc());
    		    continue; // skip dead transitions
    		}

            if (addNever(lt, n)) {
                list.add(lt);
                continue;
            } // never deadlocks
    
            // Create actions
            if (t.getTo()==null) {
                lt.addAction(new ResetProcessAction(p));
            } else { // Action: PC counter update
                lt.addAction(assign(p.getPC(), t.getTo().getStateId()));
            }
    
            // Actions: transition
            for (Action action : t) {
                if (action instanceof AssignAction) {
                    AssignAction aa = (AssignAction)action;
                    if (aa.getExpr() instanceof RunExpression) {
                        lt.addAction(new ExprAction(aa.getExpr()));
                        aa.setExpr(calc(PromelaConstants.MINUS, newid(_NR_PR), constant(1)));
                    }
                }
                lt.addAction(action);
            }
            
    		list.add(lt);
		}}
		
		return list;
	}

    private Identifier getChannel(Action action) {
        if (action instanceof ChannelSendAction) {
            return ((ChannelSendAction)action).getIdentifier();
        } else if (action instanceof ChannelReadAction) {
            return ((ChannelReadAction)action).getIdentifier();
        }
        return null;
    }

	/**
	 * Process enabler and allowed-to-die (instances die in stack order)
	 */
    private void addSpecialGuards(LTSminTransition lt, Transition t, Proctype p) {
        if (null != p.getEnabler())
			lt.addGuard(p.getEnabler()); // process enabler (provided keyword)
        if (t.getTo() == null && t.getProc() != spec.getNever()) // never process may deadlock (accepting loop!)
            lt.addGuard(dieGuard(p)); // allowed to die (stack order)
    }

	/**
	 * returns: true if never deadlocks
	 */
	private boolean addNever(LTSminTransition lt, Transition never_t) {
        if (never_t == null) return false;
        
        // create (accepting) self loop (no actions) if never is dying
        if  (never_t.getTo() == null) {
            if (!never_t.getFrom().isAcceptState())
                never_t.getFrom().addLabel("accept_never_deadlock");
            if (lt.getActions().size() != 0) throw new AssertionError("Supposed to have no actions!");
            return true;
        }
        
        if (never_t.getTo().isInAtomic() || never_t.getFrom().isInAtomic())
    		throw new AssertionError("Atomic in never claim not implemented");
		lt.addGuard(pcGuard(never_t.getFrom(), spec.getNever()));
        createEnabledGuard(never_t, lt);
        lt.addAction(assign(spec.getNever().getPC(),
					never_t.getTo()==null?-1:never_t.getTo().getStateId()));
        return false;
	}

	/**
	 * Creates the guard of a transition for its action and for the end states.
	 */
	private void createEnabledGuard(Transition t, LTSminGuardContainer lt) {
        if (t instanceof ElseTransition) {
            ElseTransition et = (ElseTransition)t;
            for (Transition ot : t.getFrom().output) {
                if (ot == et) continue;
                if (ot.getActionCount() == 0) continue;
                try {
                    LTSminGuardNand nand = new LTSminGuardNand();
                    createEnabledGuard(ot.getAction(0), nand);
                    lt.addGuard(nand);
                } catch (LTSminRendezVousException e) {
                    List<LTSminTransition> lts =
                            createRendezVousGuardTransitions (ot, ot.getAction(0));
                    for (LTSminTransition lto : lts) {
                        LTSminGuardNand nand = new LTSminGuardNand();
                        nand.addGuards(lto.getGuards());
                        lt.addGuard(nand);
                    }
                }
            }
        } else if (t.getActionCount() > 0 ) {
            try {
                createEnabledGuard(t.getAction(0), lt);
            } catch (LTSminRendezVousException e) {
                throw new AssertionError(e);
            }
        }
	}

	/**
	 * Creates the guards denoting when the specified Action is enabled.
	 * The enabledness of rendezvous channel actions can only be determined
	 * after all other transitions have been visited (when seenItAll is true).
	 * 
	 * Also records the assignTo property of identifier, to detect constants later.
	 * @throws LTSminRendezVousException 
	 */
	public static void createEnabledGuard(Action a, LTSminGuardContainer lt)
	                                         throws LTSminRendezVousException {
		if (a instanceof AssignAction) {
			AssignAction aa = (AssignAction)a;
			if (aa.getExpr() instanceof RunExpression) 
				createEnabledGuard(new ExprAction(aa.getExpr()), lt);
		} else if(a instanceof AssertAction) {
		} else if(a instanceof PrintAction) {
		} else if(a instanceof ExprAction) {
			ExprAction ea = (ExprAction)a;
            lt.addGuard(ea.getExpression());
		} else if(a instanceof ChannelSendAction) {
			ChannelSendAction csa = (ChannelSendAction)a;
			ChannelVariable var = (ChannelVariable)csa.getIdentifier().getVariable();
			if (var.getType().isRendezVous())
                throw new LTSminRendezVousException("Trying to actionise rendezvous send before all others! "+ var);
			lt.addGuard(chanFreeGuard(csa.getIdentifier()));
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
		} else if(a instanceof GotoAction) { //only in d_step
		} else if(a instanceof ChannelReadAction) {
			ChannelReadAction cra = (ChannelReadAction)a;
			Identifier id = cra.getIdentifier();
			ChannelVariable cv = (ChannelVariable)id.getVariable();
			if (cv.getType().isRendezVous())
                throw new LTSminRendezVousException("Trying to actionise rendezvous receive before all others!");
			List<Expression> exprs = cra.getExprs();
			if (!cra.isRandom()) {
				// Compare constant arguments with channel content
				lt.addGuard(chanContentsGuard(id));
				for (int i = 0; i < exprs.size(); i++) {
					final Expression expr = exprs.get(i);
					if (!(expr instanceof Identifier)) {
						Identifier elem = id(elemVar(i));
						Identifier buf = id(bufferVar(cv), constant(0), elem);
						Identifier next = new Identifier(id, buf);
						lt.addGuard(eq(next,expr));
					}
				}
			} else {
				LTSminGuardOr or = new LTSminGuardOr();
				// Compare constant arguments with channel content
				for (int b = 1 ; b <= cv.getType().getBufferSize(); b++) {
					LTSminGuardAnd and = new LTSminGuardAnd();
					and.addGuard(chanContentsGuard(id, PromelaConstants.EQ, b));
					for (int i = 0; i < exprs.size(); i++) {
						final Expression expr = exprs.get(i);
						if (!(expr instanceof Identifier)) {
							Identifier elem = id(elemVar(i));
							Identifier buf = id(bufferVar(cv), constant(b - 1), elem);
							Identifier next = new Identifier(id, buf);
							and.addGuard(eq(next, expr));
						}
					}
					or.addGuard(and);
				}
				lt.addGuard(or);
			}
		} else { //unsupported action
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+a.getClass().getName());
		}
	}

	/**
	 * Collects all rendezvous combinations for use in else transitions
	 */
    private List<LTSminTransition> createRendezVousGuardTransitions(
                                                   Transition t, Action a) {
        if (a instanceof ChannelSendAction)
            return createRendezVousTransitions(t, null, (ChannelSendAction)a);

        ChannelReadAction csa = (ChannelReadAction)a;
        Identifier id = csa.getIdentifier();
        ChannelVariable cv = (ChannelVariable)id.getVariable();
        List<LTSminTransition> set = new ArrayList<LTSminTransition>();
        List<SendAction> writeActions = spec.getWriteActions(cv);
        if (null == writeActions) {
            debug.say("No rendez-vous writes found for "+ csa);
            return set;
        }
        ReadAction ra = new ReadAction(csa, t, (ProcInstance)t.getProc());
        for (SendAction sa : writeActions) {
            createRendezVousTransition(null, sa, ra, set);
        }
        return set;
    }
	
	private List<LTSminTransition> createRendezVousTransitions(
				Transition t, Transition n, ChannelSendAction csa) {
		Identifier id = csa.getIdentifier();
		ChannelVariable cv = (ChannelVariable)id.getVariable();
		List<LTSminTransition> set = new ArrayList<LTSminTransition>();
		List<ReadAction> readActions = spec.getReadActions(cv);
		if (null == readActions) {
			debug.say("No rendez-vous reads found for "+ csa);
			return set;
		}
		SendAction sa = new SendAction(csa, t, (ProcInstance)t.getProc());
		for (ReadAction ra : readActions) {
			createRendezVousTransition(n, sa, ra, set);
		}
		return set;
	}

	/**
	 * Creates the transition for one rendezvous couple. The specified
	 * transition ID will be used to identify the created transition.
	 * 
	 * "If an atomic sequence contains a rendezvous send statement, control
	 * passes from sender to receiver when the rendezvous handshake completes."
	 */
    @SuppressWarnings("unused")
    private void createRendezVousTransition(Transition n,
	                                        SendAction sa, ReadAction ra,
	                                        List<LTSminTransition> set) {
		if (sa.p == ra.p) return; // skip impotent matches
		ChannelSendAction csa = sa.csa;
		ChannelReadAction cra = ra.cra;
		List<Expression> csa_exprs = csa.getExprs();
		List<Expression> cra_exprs = cra.getExprs();
		Identifier sendId = csa.getIdentifier();
		Identifier recvId = ra.cra.getIdentifier();
		Expression array1 = null, array2 = null;
		int arraySize = sendId.getVariable().getArraySize();
        if (arraySize > -1) { // array of channels
			assert (recvId.getVariable().getArraySize() > -1);
			array1 = recvId.getArrayExpr();
			array2 = sendId.getArrayExpr();
			if (array1 == null) {
			    if (recvId.getVariable().getArrayIndex() > -1) {
			        array1 = constant(recvId.getVariable().getArrayIndex());
			    } else {
			        throw new AssertionError("No channel array index for "+ cra);
			    }
			} else if (recvId.getVariable().getArrayIndex() > -1) {
			    throw new AssertionError("Superfluous Array index on "+ cra);
			}
			if (array2 == null) {
                if (sendId.getVariable().getArrayIndex() > -1) {
                    array1 = constant(sendId.getVariable().getArrayIndex());
                } else {
                    throw new AssertionError("No channel array index for "+ csa);
                }
            } else if (sendId.getVariable().getArrayIndex() > -1) {
                throw new AssertionError("Superfluous Array index on "+ csa);
            }
			if (array2 == null) throw new AssertionError("To channel array index for "+ ra.cra);
			try { array1 = constant(array1.getConstantValue());
			} catch (ParseException e) {}
			try { array2 = constant(array2.getConstantValue());
			} catch (ParseException e) {}
			try { // we skip creating transitions for impotent matches:
				if (array1.getConstantValue() != array2.getConstantValue())
					return;
			} catch (ParseException e) {}
		}
		for (int i = 0; i < cra_exprs.size(); i++) {
			final Expression csa_expr = csa_exprs.get(i);
			final Expression cra_expr = cra_exprs.get(i);
			try { // we skip creating transitions for impotent matches:
				if (csa_expr.getConstantValue() != cra_expr.getConstantValue())
					return;
			} catch (ParseException pe) {}
		}

		// create transitions for all items in a channel array
		for (int x = 0; x < (arraySize == -1 || !SPLIT ? 1 : arraySize); x++) {
		    LTSminTransition lt = new LTSminTransition(sa.t, n);
    		lt.setSync(ra.t);
    
    		lt.addGuard(pcGuard(sa.t.getFrom(), sa.p));
    		lt.addGuard(pcGuard(ra.t.getFrom(), ra.p));
            addSpecialGuards(lt, sa.t, sa.p); // proc die order && provided condition
            addSpecialGuards(lt, ra.t, ra.p); // proc die order && provided condition
    		if (arraySize > -1) { // array of channels
    		    if (SPLIT)
    		        lt.addGuard(compare(PromelaConstants.EQ, array1, constant(x)));
    			lt.addGuard(compare(PromelaConstants.EQ, array1, array2));
    		}
    
    		/* Channel matches */
    		for (int i = 0; i < cra_exprs.size(); i++) {
    			final Expression csa_expr = csa_exprs.get(i);
    			final Expression cra_expr = cra_exprs.get(i);
    			if (!(cra_expr instanceof Identifier)) {
    				lt.addGuard(compare(PromelaConstants.EQ,csa_expr,cra_expr));
    			}
    		}

    		if (addNever(lt, n)) {
    		    set.add(lt);
    		    continue;
    		}
    
            /* Channel reads */
            for (int i = 0; i < cra_exprs.size(); i++) {
                final Expression csa_expr = csa_exprs.get(i);
                final Expression cra_expr = cra_exprs.get(i);
                if (cra_expr instanceof Identifier) {
                    lt.addAction(assign((Identifier)cra_expr,csa_expr));
                }
            }
    
    		// Change process counter of sender
    		lt.addAction(assign(sa.p.getPC(), sa.t.getTo().getStateId()));
    		// Change process counter of receiver
    		lt.addAction(assign(ra.p.getPC(), ra.t.getTo().getStateId()));
    
    		for (int i = 1; i < ra.t.getActionCount(); i++)
    			lt.addAction(ra.t.getAction(i));
    		if (sa.t.getActionCount() > 1) throw new AssertionError("Rendez-vous send action in d_step.");
    
            set.add(lt);
		}
	}
}

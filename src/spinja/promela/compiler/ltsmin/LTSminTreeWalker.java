package spinja.promela.compiler.ltsmin;

import static spinja.promela.compiler.ltsmin.model.LTSminUtil.and;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.assign;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.calc;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.chanContentsGuard;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.chanEmptyGuard;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.compare;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.constant;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.dieGuard;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.eq;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.error;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.getOutTransitionsOrNullSet;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.id;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.isRendezVousReadAction;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.isRendezVousSendAction;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.or;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.pcGuard;
import static spinja.promela.compiler.ltsmin.state.LTSminStateVector._NR_PR;
import static spinja.promela.compiler.ltsmin.state.LTSminTypeChanStruct.bufferVar;
import static spinja.promela.compiler.ltsmin.state.LTSminTypeChanStruct.elemVar;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.Pair;
import static spinja.promela.compiler.ltsmin.LTSminPrinter.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import spinja.promela.compiler.ProcInstance;
import spinja.promela.compiler.Proctype;
import spinja.promela.compiler.Specification;
import spinja.promela.compiler.actions.Action;
import spinja.promela.compiler.actions.AssertAction;
import spinja.promela.compiler.actions.AssignAction;
import spinja.promela.compiler.actions.BreakAction;
import spinja.promela.compiler.actions.ChannelReadAction;
import spinja.promela.compiler.actions.ChannelSendAction;
import spinja.promela.compiler.actions.ElseAction;
import spinja.promela.compiler.actions.ExprAction;
import spinja.promela.compiler.actions.GotoAction;
import spinja.promela.compiler.actions.OptionAction;
import spinja.promela.compiler.actions.PrintAction;
import spinja.promela.compiler.actions.Sequence;
import spinja.promela.compiler.automaton.ActionTransition;
import spinja.promela.compiler.automaton.Automaton;
import spinja.promela.compiler.automaton.ElseTransition;
import spinja.promela.compiler.automaton.EndTransition;
import spinja.promela.compiler.automaton.GotoTransition;
import spinja.promela.compiler.automaton.NeverEndTransition;
import spinja.promela.compiler.automaton.State;
import spinja.promela.compiler.automaton.Transition;
import spinja.promela.compiler.automaton.UselessTransition;
import spinja.promela.compiler.expression.AritmicExpression;
import spinja.promela.compiler.expression.BooleanExpression;
import spinja.promela.compiler.expression.ChannelLengthExpression;
import spinja.promela.compiler.expression.ChannelOperation;
import spinja.promela.compiler.expression.ChannelReadExpression;
import spinja.promela.compiler.expression.CompareExpression;
import spinja.promela.compiler.expression.ConstantExpression;
import spinja.promela.compiler.expression.EvalExpression;
import spinja.promela.compiler.expression.Expression;
import spinja.promela.compiler.expression.Identifier;
import spinja.promela.compiler.expression.RemoteRef;
import spinja.promela.compiler.expression.RunExpression;
import spinja.promela.compiler.expression.TimeoutExpression;
import spinja.promela.compiler.ltsmin.LTSminDebug.MessageKind;
import spinja.promela.compiler.ltsmin.matrix.LTSminGuard;
import spinja.promela.compiler.ltsmin.matrix.LTSminGuardAnd;
import spinja.promela.compiler.ltsmin.matrix.LTSminGuardBase;
import spinja.promela.compiler.ltsmin.matrix.LTSminGuardContainer;
import spinja.promela.compiler.ltsmin.matrix.LTSminGuardNand;
import spinja.promela.compiler.ltsmin.matrix.LTSminGuardNor;
import spinja.promela.compiler.ltsmin.matrix.LTSminGuardOr;
import spinja.promela.compiler.ltsmin.model.LTSminModel;
import spinja.promela.compiler.ltsmin.model.LTSminState;
import spinja.promela.compiler.ltsmin.model.LTSminTransition;
import spinja.promela.compiler.ltsmin.model.ReadAction;
import spinja.promela.compiler.ltsmin.model.ResetProcessAction;
import spinja.promela.compiler.ltsmin.model.SendAction;
import spinja.promela.compiler.ltsmin.state.LTSminSlot;
import spinja.promela.compiler.ltsmin.state.LTSminStateVector;
import spinja.promela.compiler.ltsmin.state.LTSminVariable;
import spinja.promela.compiler.optimizer.RenumberAll;
import spinja.promela.compiler.parser.ParseException;
import spinja.promela.compiler.parser.Preprocessor;
import spinja.promela.compiler.parser.Preprocessor.DefineMapping;
import spinja.promela.compiler.parser.Promela;
import spinja.promela.compiler.parser.PromelaConstants;
import spinja.promela.compiler.parser.Token;
import spinja.promela.compiler.variable.ChannelType;
import spinja.promela.compiler.variable.ChannelVariable;
import spinja.promela.compiler.variable.Variable;
import spinja.promela.compiler.variable.VariableType;

/**
 * Constructs the LTSminModel by walking over the SpinJa {@link Specification}.
 * First processes are instantiated by copying their CST.
 * 
 * @author Freark van der Berg, Alfons Laarman
 */
public class LTSminTreeWalker {

    public List<Pair<ChannelReadAction,Transition>> pairs =
	        new ArrayList<Pair<ChannelReadAction,Transition>>();
	
	private final Specification spec;
	static boolean NEVER;
	static boolean LTSMIN_LTL = false;

	private LTSminDebug debug;

	private LTSminModel model = null;

	LTSminGuardAnd deadlock = new LTSminGuardAnd();

	public LTSminTreeWalker(Specification spec, boolean ltsmin_ltl) {
		this.spec = spec;
		LTSMIN_LTL = ltsmin_ltl;
		NEVER = null != spec.getNever();
	}

	/**
	 * generates and returns an LTSminModel from the provided Specification
	 */
	public LTSminModel createLTSminModel(String name, boolean verbose) {
		this.debug = new LTSminDebug(verbose);
		instantiate();
        LTSminStateVector sv = new LTSminStateVector();
		sv.createVectorStructs(spec, debug);
		model = new LTSminModel(name, sv, spec);
		bindByReferenceCalls();
		for (Pair<ChannelReadAction,Transition> p : pairs)
			spec.addReadAction(p.left, p.right);
		createModelTransitions();
		createModelAssertions();
		createModelLabels();
		LTSminDMWalker.walkModel(model, debug);
		LTSminGMWalker.generateGuardInfo(model, debug);
		return model;
	}

	private void createModelAssertions() {
        for (RemoteRef ref : spec.remoteRefs) {
            ProcInstance instance = ref.getInstance();
            Expression pid = id(model.sv.getPID(instance));
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
	 */
    private void createModelLabels() {
        
        /* always add the bool type */
        model.addType(VariableType.BOOL.getName());
        model.addTypeValue(VariableType.BOOL.getName(), "false", 0); // index 0
        model.addTypeValue(VariableType.BOOL.getName(), "true", 1);  // index 1
        
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
            name = "group "+ t.getGroup() +" ("+ t.getProcess().getName() +") "+ 
                   Preprocessor.getFileName() +":"+ line +
                   " (state "+ id +") <"+ valid +" end state> "+ name;
            model.addTypeValue(STATEMENT_TYPE_NAME, name);
        }

        /* Action edge labels are used for assertion violation detection */
        model.addType(ACTION_TYPE_NAME);
        model.addTypeValue(ACTION_TYPE_NAME, NO_ACTION_NAME, 0); // no action
        model.addTypeValue(ACTION_TYPE_NAME, ASSERT_ACTION_NAME, 1);
        model.addEdgeLabel(ACTION_EDGE_LABEL_NAME, ACTION_TYPE_NAME);

        /* Add accepting state labels for never claim */
		if (NEVER) {
			Proctype never = spec.getNever();
			Expression or = null;
			if (never.getStartState().isAcceptState()) {
	            Variable pc = model.sv.getPC(never);
			    Expression g = compare(PromelaConstants.EQ, id(pc), constant(-1));
			    or = or == null ? g : or(or, g) ; // Or
			}
			for (State s : never.getAutomaton()) {
				if (s.isAcceptState()) {
				    Expression g = pcGuard(model, s, never).getExpr();
				    or = or == null ? g : or(or, g) ; // Or
				}
			}
			if (or != null) { // maybe a never claim with an invariant (assertion)
			    model.addStateLabel(ACCEPTING_STATE_LABEL_NAME, new LTSminGuard(or));
			}
		}

		/* Add nonprogress state label and progress edge label */
        Expression or = null;
		for (ProcInstance pi : spec) {
		    for (State s : pi.getAutomaton()) {
		        if (s.isProgressState()) {
		            Expression g = pcGuard(model, s, pi).getExpr();
	                or = or == null ? g : or(or, g) ; // Or
		        }
		    }
		}
		if (or != null) {
		    model.addStateLabel(NON_PROGRESS_STATE_LABEL_NAME, new LTSminGuard(or));
		    // also as progress trans label:
		    model.addEdgeLabel(PROGRESS_EDGE_LABEL_NAME, VariableType.BOOL.getName());
		}

		/* Export label for valid end states */
		Expression end = compare(PromelaConstants.EQ, id(_NR_PR), constant(0)); // or
		Expression and = null;
    	for (ProcInstance instance : spec) {
			Variable pc = model.sv.getPC(instance);
            Expression labeled = compare(PromelaConstants.EQ, id(pc), constant(-1));
	    	for (State s : instance.getAutomaton()) {
		    	if (s.hasLabelPrefix("end")) {
		    		labeled = or(labeled, pcGuard(model, s, instance).getExpr()); // Or
		    	}
	    	}
	    	and = and == null ? labeled : and(and, labeled) ; // And
    	}
    	if (and != null)
    	    end = or(end, and); // Or
		model.addStateLabel(VALID_END_STATE_LABEL_NAME, new LTSminGuard(end));
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
	 * receive the lowest instantiation number, which is zero. */
	private void instantiate() {
		List<ProcInstance> instances = new ArrayList<ProcInstance>();
		List<ProcInstance> active = new ArrayList<ProcInstance>();
		spec.clearReadActions();

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
			LTSminStateVector._NR_PR.setInitExpr(constant(id));
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
			try {
				spec.setNever(n);
			} catch (ParseException e) {
				e.printStackTrace();
			}
		}
		for (String binding : iCount)
			debug.say(MessageKind.NORMAL, "#define __instances_"+ binding);
		for (ProcInstance instance : active)
			instances.add(instance);
		spec.setInstances(instances);
	}

	/**
	 * Copies proctype to an instance.
	 */
	private ProcInstance instantiate(Proctype p, int id, int index) {
		ProcInstance instance = new ProcInstance(p, index, id);
		Expression e = instantiate(p.getEnabler(), instance);
		instance.setEnabler(e);
		for (Variable var : p.getVariables()) {
			Variable newvar = instantiate(var, instance);
			if (newvar.getName().equals(Promela.C_STATE_PROC_COUNTER))
				newvar.setAssignedTo(); // Process counter is always assigned to
			instance.addVariable(newvar, p.getArguments().contains(var));
		}
		instance.lastArgument();
		for (String mapped : p.getVariableMappings().keySet()) {
			String to = p.getVariableMapping(mapped);
			instance.addVariableMapping(mapped, to);
		}
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
				new ChannelVariable(var.getName(), var.getArraySize()) :
				new Variable(var.getType(), var.getName(), var.getArraySize());
		newvar.setOwner(p);
		newvar.setType(var.getType());
		newvar.setRealName(var.getRealName());
		try {
			if (null != var.getInitExpr())
				newvar.setInitExpr(instantiate(var.getInitExpr(), p));
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
			Identifier id = (Identifier)instantiate(as.getIdentifier(), p);
			Expression e = instantiate(as.getExpr(), p);
			id.getVariable().setAssignedTo();
			return new AssignAction(as.getToken(), id, e);
		} else if(a instanceof ResetProcessAction) {
			throw new AssertionError("Unexpected ResetProcessAction");
		} else if(a instanceof AssertAction) {
			AssertAction as = (AssertAction)a;
			Expression e = instantiate(as.getExpr(), p);
			return new AssertAction(as.getToken(), e);
		} else if(a instanceof PrintAction) {
			PrintAction pa = (PrintAction)a;
			PrintAction newpa = new PrintAction(pa.getToken(), pa.getString());
			for (final Expression expr : pa.getExprs())
				newpa.addExpression(instantiate(expr, p));
			return newpa;
		} else if(a instanceof ExprAction) {
			ExprAction ea = (ExprAction)a;
			Expression e = instantiate(ea.getExpression(), p);
			return new ExprAction(e);
		} else if(a instanceof OptionAction) { // options in a d_step sequence
			OptionAction oa = (OptionAction)a;
			OptionAction newoa = new OptionAction(oa.getToken(), oa.loops());
			newoa.hasSuccessor(oa.hasSuccessor());
			loop = newoa.loops() ? newoa : null;
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
			Identifier id = (Identifier)instantiate(csa.getIdentifier(), p);
			ChannelSendAction newcsa = new ChannelSendAction(csa.getToken(), id);
			for (Expression e : csa.getExprs())
				newcsa.addExpression(instantiate(e, p));
			return newcsa;
		} else if(a instanceof ChannelReadAction) {
			ChannelReadAction cra = (ChannelReadAction)a;
			Identifier id = (Identifier)instantiate(cra.getIdentifier(), p);
			ChannelReadAction newcra = new ChannelReadAction(cra.getToken(), id, cra.isPoll(), cra.isRandom());
			for (Expression e : cra.getExprs()) {
				newcra.addExpression(instantiate(e, p));
				if (e instanceof Identifier) {
					((Identifier)e).getVariable().setAssignedTo();
				}
			}
			pairs.add(new Pair<ChannelReadAction, Transition>(newcra, t));
			return newcra;
		} else { // Handle not yet implemented action
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+a.getClass().getName());
		}
	}	
	
	/**
	 * Copy expressions with instantiated processes.
	 */
	private Expression instantiate(Expression e, ProcInstance p) {
		if (null == e) return null;

		if (e instanceof Identifier) { // also: LTSminIdentifier
			Identifier id = (Identifier)e;
			Variable var = id.getVariable();
			if (null != var.getOwner()) {
			    if (id.getInstanceIndex() != -1) {
			        for (ProcInstance i : var.getOwner().getInstances() ) {
			            if (i.getID() == id.getInstanceIndex()) p = i;
			        }
			        if (p == null) throw new AssertionError("ProcInstance "+ id.getInstanceIndex() +" not found for remote ref "+ id.toString());
			    } else if (!p.getTypeName().equals(var.getOwner().getName())) {
					throw new AssertionError("Expected instance of type "+ var.getOwner().getName() +" not of "+ p.getTypeName());
				}
				var = p.getVariable(var.getName()); // load copied variable
			}
			Expression arrayExpr = instantiate(id.getArrayExpr(), p);
			Identifier sub = (Identifier)instantiate(id.getSub(), p);
			return new Identifier(id.getToken(), var, arrayExpr, sub);
		} else if (e instanceof AritmicExpression) {
			AritmicExpression ae = (AritmicExpression)e;
			Expression ex1 = instantiate(ae.getExpr1(), p);
			Expression ex2 = instantiate(ae.getExpr2(), p);
			Expression ex3 = instantiate(ae.getExpr3(), p);
			return new AritmicExpression(ae.getToken(), ex1, ex2, ex3);
		} else if (e instanceof BooleanExpression) {
			BooleanExpression be = (BooleanExpression)e;
			Expression ex1 = instantiate(be.getExpr1(), p);
			Expression ex2 = instantiate(be.getExpr2(), p);
			return new BooleanExpression(be.getToken(), ex1, ex2);
		} else if (e instanceof CompareExpression) {
			CompareExpression ce = (CompareExpression)e;
			Expression ex1 = instantiate(ce.getExpr1(), p);
			Expression ex2 = instantiate(ce.getExpr2(), p);
			return new CompareExpression(ce.getToken(), ex1, ex2);
		} else if (e instanceof ChannelLengthExpression) {
			ChannelLengthExpression cle = (ChannelLengthExpression)e;
			Identifier id = (Identifier)cle.getExpression();
			Identifier newid = (Identifier)instantiate(id, p);
			try {
				return new ChannelLengthExpression(cle.getToken(), newid);
			} catch (ParseException e1) {
				throw new AssertionError(e1);
			}
		} else if (e instanceof ChannelReadExpression) {
			ChannelReadExpression cre = (ChannelReadExpression)e;
			Identifier id = (Identifier)instantiate(cre.getIdentifier(), p);
			ChannelReadExpression res = new ChannelReadExpression(cre.getToken(), id, cre.isRandom());
			for (Expression expr : cre.getExprs())
				res.addExpression(instantiate(expr, p));
			return res;
		} else if (e instanceof ChannelOperation) {
			ChannelOperation co = (ChannelOperation)e;
			Identifier id = (Identifier)instantiate(co.getExpression(), p);
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
					newre.addExpression(instantiate(expr, p));
			} catch (ParseException e1) {
				throw new AssertionError("RunExpression");
			}
			spec.runs.add(newre); // add runexpression to a list
			return newre;
		} else if (e instanceof EvalExpression) {
			EvalExpression eval = (EvalExpression)e;
			Expression ex = instantiate(eval.getExpression(), p);
			return new EvalExpression(e.getToken(), ex);
	    } else if (e instanceof TimeoutExpression) {
	        throw new AssertionError("Not yet implemented: "+e.getClass().getName());
		} else if (e instanceof ConstantExpression) {
		    return e; // readonly, hence can be shared
		} else if (e instanceof RemoteRef) {
			RemoteRef rr = (RemoteRef)e;
			Expression ex = instantiate(rr.getExpr(), p);
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
		debug.say(MessageKind.DEBUG, "");
		if (spec.runs.size() > 0)
			LTSminStateVector._NR_PR.setAssignedTo();
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
					model.sv.getPID(target).setAssignedTo(); // PID is changed
					bindArguments(rr.get(0), target, true);
				}
			} else if (rr.size() == p.getInstances().size()) {
				Iterator<ProcInstance> it = p.getInstances().iterator();
				for (RunExpression re : rr) {
					ProcInstance target = it.next();
					model.sv.getPID(target).setAssignedTo(); // PID is changed
					re.setInstance(target);
					debug.say(MessageKind.NORMAL, "Statically binding chans of procinstance "+ target +" to run expression at l."+ re.getToken().beginLine);
					bindArguments(re, target, false);
				}
			} else {
				for (ProcInstance target : p.getInstances()) {
					bindArguments(rr.get(0), target, true);
					model.sv.getPID(target).setAssignedTo(); // PID is changed
				}
			}
		}
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
				debug.say(MessageKind.DEBUG, "Binding "+ target +"."+ name +" to "+ varParameter.getOwner() +"."+ varParameter.getName());
				//List<ReadAction> ras = spec.getReadActions(v);
				v.setRealName(v.getName()); //TODO: this is also a variable mapping
				v.setType(varParameter.getType());
				v.setOwner(varParameter.getOwner());
				v.setName(varParameter.getName());
				if (varParameter.getArraySize() > -1) {
				    try {
                        int c = id.getArrayExpr().getConstantValue();
                        v.setArrayIndex(c);
                    } catch (ParseException e) {
                        throw new AssertionError("Cannot () statically bind "+ target.getTypeName() +" to the run expressions without constant channel array index.");
                    }
				}
				//if (null != ras) spec.addReadActions(v.ras);
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
				re.addAction(new AssignAction(new Token(PromelaConstants.ASSIGN), id(v), init));
			}
		}
	}

	/**
	 * Creates the state transitions.
	 */
	private void createModelTransitions() {
		debug.say(MessageKind.DEBUG, "");

		for (ProcInstance p : spec) {
			debug.say(MessageKind.DEBUG, "[Proc] " + p.getName());
			State state = p.getAutomaton().getStartState();
			State ns = null;
			if (NEVER) ns = spec.getNever().getAutomaton().getStartState();
			createCrossProduct(state, ns);
		}

		for (LTSminTransition lt : model) {
			LTSminGuardNand tg = new LTSminGuardNand();
			for (LTSminGuardBase g : lt) {
				tg.guards.add(g);
			}
			deadlock.guards.add(tg);
		}
		deadlock.setDeadlock();
		
		// let never automata continue on deadlock
		if (NEVER && !LTSMIN_LTL) {
			Automaton never = spec.getNever().getAutomaton();
			NEVER = false;
			createCrossProduct(never.getStartState(), null);
			NEVER = true;
			for (State s : never) { // add guards:
				LTSminState ns = model.getOrAddState(new LTSminState(s, null));
				for (LTSminTransition lt : ns.getOut()) {
					lt.buchi = true;
					lt.addGuard(deadlock);
				}
			}
		}
	}

	/**
	 * Walks over process automaton (and, if present, over the never automaton
	 * synchronizing its execution).
	 * For rendez-vous send actions, all matching read actions are found and 
	 * synchronized. The control passes to the read action process, which matters
	 * if this action is in a synchronized block.
	 */
	private LTSminState createCrossProduct(State state, State never) {
		LTSminState state2 = new LTSminState(state, never);
		LTSminState begin = model.getOrAddState(state2);
		if (begin != state2 || null == state || (NEVER && null == never)) // no outgoing transitions
			return begin;
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
					LTSminState end = createCrossProduct(to, nto);
					annotate(lt, begin, end);
					createCrossProduct(out.getTo(), nto);
				}
			} else if (isRendezVousReadAction(a)) {
				// skip, a transition is created for the comm. partner
			} else {
				LTSminTransition lt = createStateTransition(out, nout);
				model.addTransition(lt);
				LTSminState end = createCrossProduct(out.getTo(), nto);
				annotate(lt, begin, end);
			}
		}}
		return begin;
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

	private LTSminTransition createStateTransition(Transition t, Transition n) {
		++debug.say_indent;
		if(n!=null) {
			debug.say(MessageKind.DEBUG, "Handling trans: " + t.getClass().getName() + " || " + n.getClass().getName());
		} else {
			debug.say(MessageKind.DEBUG, "Handling trans: " + t.getClass().getName());
		}
		--debug.say_indent;

		ProcInstance p = (ProcInstance)t.getProc();
		LTSminTransition lt = new LTSminTransition(t, n);

		lt.addGuard(pcGuard(model, t.getFrom(), p)); // process counter
        createEnabledGuard(t, lt); // enabled action or else transition 
		if (null != p.getEnabler())
			lt.addGuard(p.getEnabler()); // process enabler (provided keyword)
		if (t.getTo() == null && t.getProc() != spec.getNever()) // never process may deadlock (accepting loop!)
			lt.addGuard(dieGuard(model, p)); // allowed to die (stack order)

		// create (accepting) self loop (no actions) if never is dying
		if  (n != null && n.getTo() == null) {
		    if (!n.getFrom().isAcceptState())
		        n.getFrom().addLabel("accept_never_deadlock");
		    if (lt.getActions().size() != 0) throw new AssertionError("Supposed to have no actions!");
		    return lt;
		}

		// sync with never transition
        addNever(lt, n); 

        // Create actions
        if (t.getTo()==null) {
            lt.addAction(new ResetProcessAction(p));
        } else { // Action: PC counter update
            lt.addAction(assign(model.sv.getPC(p), t.getTo().getStateId()));
        }

        // Actions: transition
        for (Action action : t) {
            if (action instanceof AssignAction) {
                AssignAction aa = (AssignAction)action;
                if (aa.getExpr() instanceof RunExpression) {
                    lt.addAction(new ExprAction(aa.getExpr()));
                    aa.setExpr(calc(PromelaConstants.MINUS, id(_NR_PR), constant(1)));
                }
            }
            lt.addAction(action);
        }
        
		return lt;
	}

	private void addNever(LTSminTransition lt, Transition never_t)
			throws AssertionError {
        if (never_t != null) {
        	if (never_t.getTo().isInAtomic() || never_t.getFrom().isInAtomic())
        		throw new AssertionError("Atomic in never claim not implemented");
			lt.addGuard(pcGuard(model, never_t.getFrom(), spec.getNever()));
	        createEnabledGuard(never_t, lt);
	        lt.addAction(assign(model.sv.getPC(spec.getNever()),
						never_t.getTo()==null?-1:never_t.getTo().getStateId()));
		}
	}

	/**
	 * Creates the guard of a transition for its action and for the end states.
	 */
	private void createEnabledGuard(Transition t, LTSminGuardContainer lt) {
        if (t instanceof ElseTransition) {
            ElseTransition et = (ElseTransition)t;
        	LTSminGuardNor nor = new LTSminGuardNor();
            for (Transition ot : t.getFrom().output) {
                if (ot != et) {
                    createEnabledGuard(ot, nor);
                }
            }
            lt.addGuard(nor);
        } else if (t.getActionCount() > 0 ) {
			createEnabledGuard(t.getAction(0), lt);
        }
	}

	/**
	 * Creates the guards denoting when the specified Action is enabled.
	 * The enabledness of rendezvous channel actions can only be determined
	 * after all other transitions have been visited (when seenItAll is true).
	 * 
	 * Also records the assignTo property of identifier, to detect constants later.
	 */
	public static void createEnabledGuard(Action a, LTSminGuardContainer lt) {
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
			if (!var.getType().isRendezVous()) {
				lt.addGuard(chanEmptyGuard(csa.getIdentifier()));
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
		} else if(a instanceof GotoAction) { //only in d_step
		} else if(a instanceof ChannelReadAction) {
			ChannelReadAction cra = (ChannelReadAction)a;
			Identifier id = cra.getIdentifier();
			ChannelVariable cv = (ChannelVariable)id.getVariable();
			if (cv.getType().getBufferSize()>0) {
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
							lt.addGuard(compare(PromelaConstants.EQ,next,expr));
						}
					}
				} else {
					LTSminGuardOr or = new LTSminGuardOr();
					// Compare constant arguments with channel content
					Expression g = null;
					for (int b = 0 ; b < cv.getType().getBufferSize(); b++) {
						g = chanContentsGuard(id, b);
						for (int i = 0; i < exprs.size(); i++) {
							final Expression expr = exprs.get(i);
							if (!(expr instanceof Identifier)) {
								Identifier elem = id(elemVar(i));
								Identifier buf = id(bufferVar(cv), constant(0), elem);
								Identifier next = new Identifier(id, buf);
								g = and(g, eq(next, expr));
							}
						}
						or.addGuard(g);
					}
					lt.addGuard(or);
				}
			} else {
				throw new AssertionError("Trying to actionise rendezvous receive before all others!");
			}
		} else { //unsupported action
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+a.getClass().getName());
		}
	}
	
	private List<LTSminTransition> createRendezVousTransitions(
				Transition t, Transition n, ChannelSendAction csa) {
		Identifier id = csa.getIdentifier();
		ChannelVariable cv = (ChannelVariable)id.getVariable();
		List<LTSminTransition> set = new ArrayList<LTSminTransition>();
		List<ReadAction> readActions = spec.getReadActions(cv);
		if (null == readActions) {
			debug.say("No reads found for "+ csa);
			return set;
		}
		for (ReadAction ra : readActions) {
			SendAction sa = new SendAction(csa, t, (ProcInstance)t.getProc());
			LTSminTransition lt = createRendezVousTransition(n, sa, ra);
			if (null != lt) set.add(lt);
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
	private LTSminTransition createRendezVousTransition(
								Transition n, SendAction sa, ReadAction ra) {
		if (sa.p == ra.p) return null; // skip impotent matches
		ChannelSendAction csa = sa.csa;
		ChannelReadAction cra = ra.cra;
		List<Expression> csa_exprs = csa.getExprs();
		List<Expression> cra_exprs = cra.getExprs();
		Identifier sendId = csa.getIdentifier();
		Identifier recvId = ra.cra.getIdentifier();
		Expression array1 = null, array2 = null;
		if (sendId.getVariable().getArraySize() > -1) { // array of channels
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
					return null;
			} catch (ParseException e) {}
		}
		for (int i = 0; i < cra_exprs.size(); i++) {
			final Expression csa_expr = csa_exprs.get(i);
			final Expression cra_expr = cra_exprs.get(i);
			try { // we skip creating transitions for impotent matches:
				if (csa_expr.getConstantValue() != cra_expr.getConstantValue())
					return null;
			} catch (ParseException pe) {}
		}
		LTSminTransition lt = new LTSminTransition(sa.t, n);
		lt.setSync(ra.t);

		lt.addGuard(pcGuard(model, sa.t.getFrom(), sa.p));
		lt.addGuard(pcGuard(model, ra.t.getFrom(), ra.p));
		if (sendId.getVariable().getArraySize() > -1) { // array of channels
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

        // create (accepting) self loop (no actions) if never is dying
        if  (n != null && n.getTo() == null) {
            if (!n.getFrom().isAcceptState())
                n.getFrom().addLabel("accept_never_deadlock");
            if (lt.getActions().size() != 0) throw new AssertionError("Supposed to have no actions!");
            return lt;
        }

        addNever(lt, n); // never executes first

        /* Channel reads */
        for (int i = 0; i < cra_exprs.size(); i++) {
            final Expression csa_expr = csa_exprs.get(i);
            final Expression cra_expr = cra_exprs.get(i);
            if (cra_expr instanceof Identifier) {
                lt.addAction(assign((Identifier)cra_expr,csa_expr));
            }
        }

		// Change process counter of sender
		lt.addAction(assign(model.sv.getPC(sa.p), sa.t.getTo().getStateId()));
		// Change process counter of receiver
		lt.addAction(assign(model.sv.getPC(ra.p), ra.t.getTo().getStateId()));

		for (int i = 1; i < ra.t.getActionCount(); i++)
			lt.addAction(ra.t.getAction(i));
		if (sa.t.getActionCount() > 1) throw new AssertionError("Rendez-vous send action in d_step.");
		return lt;
	}
}

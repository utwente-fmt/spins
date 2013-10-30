package spins.promela.compiler.ltsmin.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import spins.promela.compiler.Proctype;
import spins.promela.compiler.Specification;
import spins.promela.compiler.actions.Action;
import spins.promela.compiler.automaton.State;
import spins.promela.compiler.automaton.Transition;
import spins.promela.compiler.expression.CompareExpression;
import spins.promela.compiler.expression.Expression;
import spins.promela.compiler.expression.Identifier;
import spins.promela.compiler.ltsmin.matrix.LTSminGuard;
import spins.promela.compiler.ltsmin.matrix.LTSminGuardBase;
import spins.promela.compiler.ltsmin.matrix.LTSminGuardContainer;
import spins.promela.compiler.ltsmin.matrix.LTSminPCGuard;
import spins.promela.compiler.parser.PromelaConstants;

/**
 *
 * @author Freark van der Berg, Alfons Laarman
 */
public class LTSminTransition implements LTSminGuardContainer {

	private int group = -1;
	
	public LTSminTransition() {}

	public int getGroup() {
	    if (group == -1)
	        throw new AssertionError("Transition index requested before added to the model.");
		return group;
	}

	public void setGroup(int trans) {
		this.group = trans;
	}
	
	private String name;
	private	List<LTSminGuardBase> guards;
	private List<Action> actions;
	private Transition original;
	private Transition never;
	private Transition sync;
	private LTSminState begin;
	private LTSminState end;
	public boolean buchi = false;
	
	public Set<LTSminTransition> getTransitions() {
		return null == end ? new HashSet<LTSminTransition>() : end.getTransitions();
	}

	public LTSminTransition(Transition t, Transition never) {
		this();
		assert (t != null);
		this.original = t;
		this.sync = null;
		this.setNever(never);
		this.guards = new LinkedList<LTSminGuardBase>();
		this.actions = new ArrayList<Action>();
	}

	public String toString() {
		return group +"";
	}

	public Specification getSpecification() {
		return original.getProc().getSpecification();
	}

	public List<Action> getActions() {
		return actions;
	}

	public void setActions(List<Action> actions) {
		this.actions = actions;
	}

	public List<LTSminGuardBase> getGuards() {
		return guards;
	}

	public void setGuards(List<LTSminGuardBase> guards) {
		this.guards = guards;
	}

	public void addGuard(Expression e) {
	    if (e instanceof CompareExpression) {
	        CompareExpression ce = (CompareExpression) e;
	        if (ce.getToken().equals(PromelaConstants.EQ)) {
	            if (ce.getExpr1() instanceof Identifier) {
	                Identifier id = (Identifier) ce.getExpr1();
	                if (id.isPC())
	                    throw new AssertionError("Add PCguards using pcGuard function");
	            }
	        }
	    }
		addGuard(new LTSminGuard(e));
	}

    private List<LTSminPCGuard> pcGuard = new LinkedList<LTSminPCGuard>();
	public void addGuard(LTSminGuardBase guard) {
        if (guard instanceof LTSminPCGuard) {
            pcGuard.add((LTSminPCGuard) guard);
        }
		if(!guard.isDefinitelyTrue()) {
			guards.add(guard);
		}
	}

	public void addAction(Action action) {
		actions.add(action);
	}

	public boolean leavesAtomic() {
		return begin.isAtomic() && !isAtomic();
	}
	
	public boolean isAtomic() {
		return null != end && end.isAtomic();
	}

	public Transition getTransition() {
		return original;
	}

	public Transition getNever() {
		return never;
	}

	public void setNever(Transition never) {
		this.never = never;
	}

	public Transition getSync() {
		return sync;
	}

	public void setSync(Transition sync) {
		this.sync = sync;
	}
	
	public String makeTranstionName(Transition t) {
		String t_name = t.getFrom().getAutomaton().getProctype().getName();
		t_name += "("+ t.getFrom().getStateId() +"-->";
		return t_name + (t.getTo()== null ? "end" : t.getTo().getStateId()) +")";
	}

	public String getName() {
		if (null != name)
			return name;
		String name = makeTranstionName(original);
		if (sync != null)
			name += " X "+ makeTranstionName(sync);
		if (never != null)
			name += " X "+ makeTranstionName(never);
		try {
			name += "\t["+ original.getAction(0);
		} catch (IndexOutOfBoundsException iobe) {
			name += "\t[tau";
		}
		if (sync != null) {
			try {
				name += " X "+ sync.getAction(0) +"";
			} catch (IndexOutOfBoundsException iobe) {
				name += " X tau";
			}
		}
		if (never != null) {
			try {
				name += " X "+ never.getAction(0) +"";
			} catch (IndexOutOfBoundsException iobe) {
				name += " X tau";
			}
		}
		name += "]";
		return this.name = name;
	}

	public String setName(String name) {
		String old = this.name;
		this.name = name;
		return old;
	}

	public Iterator<LTSminGuardBase> iterator() {
		return guards.iterator();
	}

	public int guardCount() {
		return guards.size();
	}

	public Proctype getProcess() {
		return original.getProc();
	}

	public LTSminState getEnd() {
		return end;
	}

	public LTSminState getBegin() {
		return begin;
	}

	public void setEnd(LTSminState end) {
		this.end = end;
		end.addIn(this);
	}

	public void setBegin(LTSminState s) {
		this.begin = s;
		begin.addOut(this);
	}

	public int getEndId() {
		return null == end.getProc() ?
			begin.getProc().getID() :
			end.getProc().getID();
	}

    public List<LTSminPCGuard> getPCGuards() {
        if (pcGuard.size() == 0) throw new AssertionError("No PC guard for "+ this);
        return pcGuard;
    }

    public boolean isProgress() {
        return begin.isProgress() ||
               State.hasLabelPrefix(original.getLabels(), State.LABEL_PROGRESS);
    }
}

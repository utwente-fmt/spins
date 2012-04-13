package spinja.promela.compiler.ltsmin.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import spinja.promela.compiler.Proctype;
import spinja.promela.compiler.actions.Action;
import spinja.promela.compiler.actions.ChannelReadAction;
import spinja.promela.compiler.automaton.Transition;
import spinja.promela.compiler.expression.Expression;
import spinja.promela.compiler.ltsmin.LTSminTreeWalker;
import spinja.promela.compiler.ltsmin.matrix.LTSminGuard;
import spinja.promela.compiler.ltsmin.matrix.LTSminGuardBase;
import spinja.promela.compiler.ltsmin.matrix.LTSminGuardContainer;
import spinja.util.StringWriter;

/**
 *
 * @author Freark van der Berg, Alfons Laarman
 */
public class LTSminTransition implements LTSminGuardContainer {

	private int trans;
	
	public LTSminTransition(int group) {
		trans = group;
	}

	public int getGroup() {
		return trans;
	}

	public void setGroup(int trans) {
		this.trans = trans;
	}
	
	private String name;
	private Proctype process;
	private	List<LTSminGuardBase> guards;
	private List<Action> actions;
	private Transition original;
	private Transition never;
	private Transition sync;
	private Transition passControl = null;
	private Set<LTSminTransition> transitions = new HashSet<LTSminTransition>();
	
	public void addTransition(LTSminTransition t) {
		transitions.add(t);
	}

	public Set<LTSminTransition> getTransitions() {
		return transitions;
	}

	public LTSminTransition(int group, Transition t, Transition sync,
							Transition never, Proctype process) {
		this(group);
		assert (t != null);
		this.original = t;
		this.process = process;
		this.sync = sync;
		this.setNever(never);
		this.guards = new LinkedList<LTSminGuardBase>();
		this.actions = new ArrayList<Action>();
	}
/*
	public LTSminTransition(int group, Proctype process) {
		this(group, process.getName(), process);
	}

	public LTSminTransition(int group, Proctype process, List<LTSminGuardBase> guards, List<Action> actions) {
		this(group, process.getName(), process);
		this.process = process;
		this.guards = guards;
		this.actions = actions;
	}
*/
	public String toString() {
		return trans +"";
	}

	public Proctype getProcess() {
		return process;
	}

	public void setProcess(Proctype process) {
		this.process = process;
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

	public void addGuard(int index, Expression e) {
		addGuard(index, new LTSminGuard(e));
	}
	
	public void addGuard(int index, LTSminGuardBase guard) {
		//if(!guard.isDefinitelyTrue()) {
			guards.add(index, guard);
		//}
	}

	public void addGuard(Expression e) {
		addGuard(new LTSminGuard(e));
	}
	
	public void addGuard(LTSminGuardBase guard) {
		//if(!guard.isDefinitelyTrue()) {
			guards.add(guard);
		//}
	}

	public void addAction(Action action) {
		actions.add(action);
	}

	public void prettyPrint(StringWriter w, LTSminTreeWalker printer) {
		w.appendLine("[",name,"]");
		w.indent();
		w.appendLine("Guards:");
		w.indent();
		for(LTSminGuardBase g: guards) {
			g.prettyPrint(w);
		}
		w.outdent();
		w.appendLine("Actions:");
		w.indent();
		for(Action a: actions) {
			w.appendLine(a.toString());
		}
		w.outdent();
		w.outdent();

	}

	public boolean leavesAtomic() {
		if (null != sync) {
			Action a = sync.iterator().next();
			if (a instanceof ChannelReadAction) {
				ChannelReadAction csa = (ChannelReadAction)a;
				if (csa.isRendezVous()) {
					return original.getFrom().isInAtomic() && !sync.getTo().isInAtomic(); 
				}
			}
		}
		return original.getFrom().isInAtomic() && (original.getTo() == null || !original.getTo().isInAtomic());
	}
	
	public boolean isAtomic() {
		if (null != sync) {
			Action a = sync.iterator().next();
			if (a instanceof ChannelReadAction) {
				ChannelReadAction csa = (ChannelReadAction)a;
				if (csa.isRendezVous()) {
					return sync.getTo().isInAtomic(); 
				}
			}
		}
		return original.getTo() != null && original.getTo().isInAtomic();
	}

	public Transition passesControlAtomically() {
		return passControl;
	}

	public void passesControlAtomically(Transition t) {
		passControl = t;
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

	@Override
	public Iterator<LTSminGuardBase> iterator() {
		return guards.iterator();
	}

	@Override
	public int size() {
		return guards.size();
	}
}

package spinja.promela.compiler.ltsmin;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import spinja.promela.compiler.Proctype;
import spinja.promela.compiler.actions.Action;
import spinja.promela.compiler.expression.Expression;
import spinja.util.StringWriter;

/**
 *
 * @author Freark van der Berg
 */
public class LTSminTransition extends LTSminTransitionBase implements LTSminGuardContainer {
	private String name;
	private Proctype process;
	private	List<LTSminGuardBase> guards;
	private List<Action> actions;
	private boolean leavesAtomic = false;
	private boolean entersAtomic;
	
	public LTSminTransition(int group) {
		super(group);
		this.name = "-";
		this.process = null;
		this.guards = new LinkedList<LTSminGuardBase>();
		this.actions = new ArrayList<Action>();
	}

	public LTSminTransition(int group, String name) {
		this(group);
		this.name = name;
	}

	public LTSminTransition(int group, Proctype process) {
		this(group, process.getName());
	}

	public LTSminTransition(int group, Proctype process, List<LTSminGuardBase> guards, List<Action> actions) {
		this(group, process.getName());
		this.process = process;
		this.guards = guards;
		this.actions = actions;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
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
//			try {
//				printer.generateAction(w,process,a,9000000,null);
//			// Handle parse exceptions
//			} catch(ParseException e) {
//				e.printStackTrace();
//				System.exit(0);
//			}
		}
		w.outdent();
		w.outdent();

	}

	public void leavesAtomic(boolean b) {
		leavesAtomic = b;		
	}
	
	public boolean leavesAtomic() {
		return leavesAtomic;		
	}

	public void entersAtomic(boolean entersAtomic) {
		this.entersAtomic = entersAtomic;
	}

	public boolean entersAtomic() {
		return entersAtomic;
	}

	public boolean isAtomic() {
		return this instanceof LTSminTransitionCombo;
	}
}

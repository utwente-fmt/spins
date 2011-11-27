package spinja.promela.compiler.ltsmin;

import java.util.ArrayList;
import java.util.List;

import spinja.promela.compiler.Proctype;
import spinja.promela.compiler.actions.Action;
import spinja.util.StringWriter;

/**
 *
 * @author Freark van der Berg
 */
public class LTSminTransition implements LTSminTransitionBase, LTSminGuardContainer {
	private String name;
	private Proctype process;
	private	List<LTSminGuardBase> guards;
	private List<Action> actions;

	public LTSminTransition() {
		this.name = "-";
		this.process = null;
		this.guards = new ArrayList<LTSminGuardBase>();
		this.actions = new ArrayList<Action>();
	}

	public LTSminTransition(String name) {
		this.name = name;
		this.process = null;
		this.guards = new ArrayList<LTSminGuardBase>();
		this.actions = new ArrayList<Action>();
	}

	public LTSminTransition(Proctype process) {
		this.name = process.getName();
		this.process = process;
		this.guards = new ArrayList<LTSminGuardBase>();
		this.actions = new ArrayList<Action>();
	}

	public LTSminTransition(Proctype process, List<LTSminGuardBase> guards, List<Action> actions) {
		this.name = process.getName();
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

}

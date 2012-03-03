package spinja.promela.compiler.ltsmin.model;

import java.util.HashSet;
import java.util.Set;

import spinja.promela.compiler.Proctype;
import spinja.promela.compiler.automaton.Transition;
import spinja.promela.compiler.ltsmin.LTSminTreeWalker;
import spinja.util.StringWriter;

/**
 *
 * @author FIB
 */
public class LTSminTransitionCombo extends LTSminTransition {
	public Set<LTSminTransition> transitions;
	
	private String name;

	public LTSminTransitionCombo(Transition t, int group, String name,Proctype p) {
		super(t, group, name, p);
		transitions = new HashSet<LTSminTransition>();
	}

	public void addTransition(LTSminTransition t) {
		transitions.add(t);
	}

	public void prettyPrint(StringWriter w, LTSminTreeWalker printer) {
		w.appendLine("[",name,"]");
		w.indent();
		for (LTSminTransition t : transitions) {
			t.prettyPrint(w,printer);
		}
		w.outdent();
	}
}

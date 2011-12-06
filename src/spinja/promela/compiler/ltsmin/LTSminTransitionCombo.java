package spinja.promela.compiler.ltsmin;

import java.util.ArrayList;
import java.util.List;

import spinja.promela.compiler.automaton.Transition;
import spinja.util.StringWriter;

/**
 *
 * @author FIB
 */
public class LTSminTransitionCombo extends LTSminTransition {
	public List<LTSminTransition> transitions;
	
	private String name;
	private Transition realTransition;

	public LTSminTransitionCombo(int group, String name,Transition real) {
		super(group, name);
		transitions = new ArrayList<LTSminTransition>();
		this.setRealTransition(real);
	}	
	public void addTransition(LTSminTransition t) {
		transitions.add(t);
	}

	public void prettyPrint(StringWriter w, LTSminTreeWalker printer) {
		w.appendLine("[",name,"]");
		w.indent();
		for(LTSminTransition t : transitions) {
			t.prettyPrint(w,printer);
		}
		w.outdent();
	}

	public Transition getRealTransition() {
		return realTransition;
	}

	public void setRealTransition(Transition realTransition) {
		this.realTransition = realTransition;
	}
}

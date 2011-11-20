package spinja.promela.compiler.ltsmin;

import java.util.ArrayList;
import java.util.List;
import spinja.util.StringWriter;

/**
 *
 * @author FIB
 */
public class LTSminTransitionCombo implements LTSminTransitionBase {
	List<LTSminTransitionBase> transitions;
	private String name;

	public LTSminTransitionCombo() {
		this("-");
	}
	
	public LTSminTransitionCombo(String name) {
		this.name = name;
		transitions = new ArrayList<LTSminTransitionBase>();
	}

	public String getName() {
		return name;
	}

	public void addTransition(LTSminTransitionBase transition) {
		transitions.add(transition);
	}

	public void prettyPrint(StringWriter w, LTSminTreeWalker printer) {
		w.appendLine("[",name,"]");
		w.indent();
		for(LTSminTransitionBase t: transitions) {
			t.prettyPrint(w,printer);
		}
		w.outdent();
	}
}

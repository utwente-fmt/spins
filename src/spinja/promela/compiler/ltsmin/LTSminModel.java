package spinja.promela.compiler.ltsmin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import spinja.promela.compiler.Specification;
import spinja.promela.compiler.ltsmin.instr.DepMatrix;
import spinja.promela.compiler.ltsmin.instr.GuardMatrix;
import spinja.promela.compiler.variable.Variable;
import spinja.util.StringWriter;

/**
 *
 * @author Freark van der Berg
 */
public class LTSminModel {

	private String name;

	private List<LTSminType> types;
	private List<LTSminTransitionBase> transitions;
	public LTSminStateVector sv;
	private HashMap<Variable,Integer> variables;

	DepMatrix depMatrix;
	GuardMatrix guardMatrix;

	StringWriter header;
	StringWriter structs;
	StringWriter model;

	public LTSminModel(String name) {
		this.name = name;
		this.transitions = new ArrayList<LTSminTransitionBase>();
		this.types = new ArrayList<LTSminType>();
		this.variables = new HashMap<Variable, Integer>();
		sv = new LTSminStateVector(this);
	}

	public String getName() {
		return name;
	}

	void prettyPrint(StringWriter w, LTSminTreeWalker printer) {
		w.appendLine("Types:");
		w.indent();
		for(LTSminType t: types) {
			t.prettyPrint(w);
		}
		w.outdent();
		w.appendLine("State vector:");
		w.indent();
		for(LTSminStateElement e : sv) {
			e.prettyPrint(w);
			w.removePostfix();
			w.append(" (").append(variables.get(e.getVariable())).append(")");
			w.appendPostfix();
		}
		w.outdent();
		w.appendLine("Transitions:");
		w.indent();
		for(LTSminTransitionBase t: transitions) {
			LTSminPrinter.generateATransition(w,t, this);
		}
		w.outdent();
	}

	public DepMatrix getDepMatrix() {
		return depMatrix;
	}

	public void setDepMatrix(DepMatrix depMatrix) {
		this.depMatrix = depMatrix;
	}

	public GuardMatrix getGuardMatrix() {
		return guardMatrix;
	}

	public void setGuardMatrix(GuardMatrix guardMatrix) {
		this.guardMatrix = guardMatrix;
	}

	public List<LTSminTransitionBase> getTransitions() {
		return transitions;
	}

	public void setTransitions(List<LTSminTransitionBase> transitions) {
		this.transitions = transitions;
	}

	public List<LTSminType> getTypes() {
		return types;
	}

	public void setTypes(List<LTSminType> types) {
		this.types = types;
	}

	public void addType(LTSminType type) {
		types.add(type);
	}

	public HashMap<Variable, Integer> getVariables() {
		return variables;
	}

	public void createVectorStructs(Specification spec, LTSminDebug debug) {
		sv.createVectorStructs(spec, debug);
	}
}

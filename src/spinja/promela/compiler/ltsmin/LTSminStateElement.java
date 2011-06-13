package spinja.promela.compiler.ltsmin;

import spinja.promela.compiler.variable.Variable;
import spinja.util.StringWriter;

/**
 *
 * @author FIB
 */
public class LTSminStateElement {
	Variable variable;
	String access;

	public LTSminStateElement(Variable variable) {
		this(variable,variable.getName());
	}

	public LTSminStateElement(Variable variable, String access) {
		this.variable = variable;
		this.access = access;
	}

	public Variable getVariable() {
		return variable;
	}
	
	public void prettyPrint(StringWriter w) {
		w.appendLine(variable.toString());
	}
}

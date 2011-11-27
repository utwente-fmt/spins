/**
 * 
 */
package spinja.promela.compiler.ltsmin.instr;

import spinja.promela.compiler.Proctype;
import spinja.promela.compiler.actions.Action;
import spinja.promela.compiler.parser.ParseException;
import spinja.promela.compiler.parser.PromelaConstants;
import spinja.promela.compiler.parser.Token;
import spinja.promela.compiler.variable.Variable;
import spinja.util.StringWriter;

public class ResetProcessAction extends Action {
	private Proctype process;
	private Variable procVar;

	public ResetProcessAction(Proctype process, Variable procVar) {
		super(new Token(PromelaConstants.ASSIGN, "=reset="));
		this.process = process;
		this.procVar = procVar;
	}

	public Proctype getProcess() {
		return process;
	}

	public Variable getProcVar() {
		return procVar;
	}

	@Override
	public String toString() {
		return "";
	}

	@Override
	public String getEnabledExpression() throws ParseException {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public void printTakeStatement(StringWriter w) throws ParseException {
		throw new UnsupportedOperationException("Not supported yet.");
	}

}
/**
 * 
 */
package spins.promela.compiler.ltsmin.model;

import spins.promela.compiler.Proctype;
import spins.promela.compiler.actions.Action;
import spins.promela.compiler.parser.ParseException;
import spins.promela.compiler.parser.PromelaConstants;
import spins.promela.compiler.parser.Token;
import spins.util.StringWriter;

public class ResetProcessAction extends Action {
	private Proctype process;

	public ResetProcessAction(Proctype process) {
		super(new Token(PromelaConstants.ASSIGN, "=reset="));
		this.process = process;
	}

	public Proctype getProcess() {
		return process;
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
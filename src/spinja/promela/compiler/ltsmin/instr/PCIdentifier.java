/**
 * 
 */
package spinja.promela.compiler.ltsmin.instr;

import spinja.promela.compiler.Proctype;
import spinja.promela.compiler.expression.Identifier;
import spinja.promela.compiler.ltsmin.LTSminTreeWalker;
import spinja.promela.compiler.parser.PromelaConstants;
import spinja.promela.compiler.parser.Token;

public class PCIdentifier extends Identifier {
	private Proctype process;

	public Proctype getProcess() {
		return process;
	}

	public PCIdentifier(Proctype process) {
		//super(new Token(PromelaConstants.PC_VALUE, C_STATE_TMP + "." + wrapName(process.getName())),new Variable(VariableType.INT, C_STATE_TMP + "." + wrapName(process.getName()), 1));
		super(new Token(PromelaConstants.PC_VALUE, LTSminTreeWalker.C_STATE_TMP + "." + LTSminTreeWalker.wrapName(process.getName())),LTSminTreeWalker.processIdentifiers.get(process));
		this.process = process;
	}

	
}
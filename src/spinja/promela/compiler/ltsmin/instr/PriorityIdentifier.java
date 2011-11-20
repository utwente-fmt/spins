/**
 * 
 */
package spinja.promela.compiler.ltsmin.instr;

import spinja.promela.compiler.expression.Identifier;
import spinja.promela.compiler.ltsmin.LTSminTreeWalker;
import spinja.promela.compiler.parser.PromelaConstants;
import spinja.promela.compiler.parser.Token;
import spinja.promela.compiler.variable.Variable;
import spinja.promela.compiler.variable.VariableType;

public class PriorityIdentifier extends Identifier {

	public static Variable priorVar = new Variable(VariableType.INT, 
									LTSminTreeWalker.C_STATE_TMP + "." +
									LTSminTreeWalker.C_PRIORITY, 1);
	
	public PriorityIdentifier() {
		super(new Token(PromelaConstants.PRIORITY, LTSminTreeWalker.C_STATE_TMP
						+ "." + LTSminTreeWalker.C_PRIORITY), priorVar);
	}
	
	@Override
	public boolean equals(Object o) {
		if (o == null)
			return false;
		return o instanceof PriorityIdentifier;
	}
}
/**
 * 
 */
package spinja.promela.compiler.ltsmin.instr;

import static spinja.promela.compiler.ltsmin.LTSminStateVector.C_PRIORITY;
import static spinja.promela.compiler.ltsmin.LTSminStateVector.C_STATE_TMP;
import spinja.promela.compiler.expression.Identifier;
import spinja.promela.compiler.parser.PromelaConstants;
import spinja.promela.compiler.parser.Token;
import spinja.promela.compiler.variable.Variable;
import spinja.promela.compiler.variable.VariableType;

public class PriorityIdentifier extends Identifier {

	public static Variable priorVar = new Variable(VariableType.INT, 
									C_STATE_TMP + "." +	C_PRIORITY, 1);
	
	public PriorityIdentifier() {
		super(new Token(PromelaConstants.PRIORITY, C_STATE_TMP
						+ "." + C_PRIORITY), priorVar);
	}
	
	@Override
	public boolean equals(Object o) {
		if (o == null)
			return false;
		return o instanceof PriorityIdentifier;
	}
}
package spinja.promela.compiler.ltsmin;

import spinja.promela.compiler.expression.Identifier;
import spinja.promela.compiler.parser.PromelaTokenManager;
import spinja.promela.compiler.parser.Token;
import spinja.promela.compiler.variable.Variable;

/**
 *	A simple identifier that is only used locally and not inside the state vector
 *
 * @author Alfons Laarman
 */
public class LTSminIdentifier extends Identifier {

	public LTSminIdentifier(Variable var) {
		super(new Token(PromelaTokenManager.IDENTIFIER,var.getName()), var);
	}
}

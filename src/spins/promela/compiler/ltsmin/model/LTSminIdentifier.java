package spins.promela.compiler.ltsmin.model;

import spins.promela.compiler.expression.Identifier;
import spins.promela.compiler.parser.PromelaTokenManager;
import spins.promela.compiler.parser.Token;
import spins.promela.compiler.variable.Variable;

/**
 *	A simple identifier that is only used locally and not inside the state vector
 *
 * @author Alfons Laarman
 */
public class LTSminIdentifier extends Identifier {
	private boolean isPointer;

	public LTSminIdentifier(Variable var) {
		this(var, false);
	}

	public LTSminIdentifier(Variable var, boolean isPointer) {
		super(new Token(PromelaTokenManager.IDENTIFIER,var.getName()), var, null);
		this.setPointer(isPointer);
	}

	public boolean isPointer() {
		return isPointer;
	}

	public void setPointer(boolean isPointer) {
		this.isPointer = isPointer;
	}
}

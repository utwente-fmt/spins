package spinja.promela.compiler.ltsmin;

import spinja.promela.compiler.expression.Identifier;
import spinja.promela.compiler.ltsmin.LTSminPrinter.ExprPrinter;

public interface LTSminTypeI {

	public abstract String getName();

	public abstract String toString();

	public abstract String printIdentifier(ExprPrinter p, Identifier id);

	/**
	 * Calculate offset of variables within structs
	 */
	public abstract void fix();

	/**
	 * Get variables length.
	 * SIDE EFFECT: make class unmodifiable.
	 * @return length
	 */
	public abstract int length();

}
package spinja.promela.compiler.ltsmin.state;

import spinja.promela.compiler.expression.Identifier;
import spinja.promela.compiler.ltsmin.LTSminPrinter.ExprPrinter;

/**
 * A type is either a native type (directly encoded in C code) or a struct type
 * for which a C struct is created. A statevector is also a struct.
 * Structs contain a number of variables.
 * 
 * @see LTSminVariable
 * 
 * @author laarman
 */
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
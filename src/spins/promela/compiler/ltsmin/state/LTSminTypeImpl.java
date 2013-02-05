package spins.promela.compiler.ltsmin.state;

import spins.promela.compiler.expression.Identifier;
import spins.promela.compiler.ltsmin.LTSminPrinter.ExprPrinter;

/**
 * @see LTSminTypeI
 * 
 * @author FIB, Alfons Laarman
 */
public abstract class LTSminTypeImpl implements LTSminTypeI {
	static protected final String TYPE_PREFIX = "sj_";
	protected boolean unmodifiable = false; 
	protected int length;

	/* (non-Javadoc)
	 * @see spinja.promela.compiler.ltsmin.LTSminTypeI#getName()
	 */
	@Override
	public abstract String getName();
	protected abstract int length_();

	/* (non-Javadoc)
	 * @see spinja.promela.compiler.ltsmin.LTSminTypeI#toString()
	 */
	@Override
	public abstract String toString();

	/* (non-Javadoc)
	 * @see spinja.promela.compiler.ltsmin.LTSminTypeI#printIdentifier(spinja.promela.compiler.ltsmin.LTSminPrinter.ExprPrinter, spinja.promela.compiler.expression.Identifier)
	 */
	@Override
	public abstract String printIdentifier(ExprPrinter p, Identifier id);

	/* (non-Javadoc)
	 * @see spinja.promela.compiler.ltsmin.LTSminTypeI#fix()
	 */
	@Override
	public abstract void fix();
	
	/* (non-Javadoc)
	 * @see spinja.promela.compiler.ltsmin.LTSminTypeI#length()
	 */
	@Override
	public int length() {
		int l = length_();
		unmodifiable = true;
		return l;
	}
}

package spinja.promela.compiler.ltsmin.matrix;

import spinja.promela.compiler.expression.Expression;

/**
 * A local guard used by ltsmin, which does not end up in the state vector
 * 
 * @author laarman
 *
 */
public class LTSminLocalGuard extends LTSminGuard {

	public LTSminLocalGuard(Expression expr) {
		super(expr);
	}

}

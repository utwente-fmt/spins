package spinja.promela.compiler.ltsmin.matrix;

import spinja.promela.compiler.expression.Expression;

/**
 *
 * @author FIB
 */
public interface LTSminGuardContainer extends Iterable<LTSminGuardBase> {
	abstract public void addGuard(LTSminGuardBase guard);
	abstract public void addGuard(Expression guard);
	abstract int size();
}

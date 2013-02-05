package spins.promela.compiler.ltsmin.matrix;

import spins.promela.compiler.expression.Expression;

/**
 *
 * @author FIB
 */
public interface LTSminGuardContainer extends Iterable<LTSminGuardBase> {
	abstract public void addGuard(LTSminGuardBase guard);
	abstract public void addGuard(Expression guard);
	abstract int guardCount();
}

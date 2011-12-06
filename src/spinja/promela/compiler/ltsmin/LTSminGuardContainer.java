package spinja.promela.compiler.ltsmin;

import spinja.promela.compiler.expression.Expression;

/**
 *
 * @author FIB
 */
public interface LTSminGuardContainer {
	abstract public void addGuard(LTSminGuardBase guard);
	abstract public void addGuard(Expression guard);
}

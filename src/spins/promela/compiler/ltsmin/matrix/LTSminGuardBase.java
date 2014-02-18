package spins.promela.compiler.ltsmin.matrix;

import spins.promela.compiler.expression.Expression;
import spins.util.StringWriter;

/**
 *
 * @author FIB
 */
public abstract class LTSminGuardBase {
    private boolean deadlock = false;
    
	abstract public void prettyPrint(StringWriter w);
	abstract public boolean isDefinitelyTrue();
	abstract public boolean isDefinitelyFalse();

    public boolean isDeadlock() {
        return deadlock;
    }
    public void setDeadlock() {
        this.deadlock = true;
    }

    public abstract Expression getExpression();
}

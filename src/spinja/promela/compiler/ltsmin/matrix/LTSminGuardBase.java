package spinja.promela.compiler.ltsmin.matrix;

import static spinja.promela.compiler.ltsmin.util.LTSminUtil.and;
import static spinja.promela.compiler.ltsmin.util.LTSminUtil.not;
import static spinja.promela.compiler.ltsmin.util.LTSminUtil.or;
import spinja.promela.compiler.expression.Expression;
import spinja.util.StringWriter;

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

    public Expression getExpression() {
        LTSminGuardBase gb = this;
        Expression e = null;
        if (gb instanceof LTSminGuard) {
            e = ((LTSminGuard)gb).getExpr();
        } else if (gb instanceof LTSminGuardAnd) {
            LTSminGuardAnd g = (LTSminGuardAnd)gb;
            for (LTSminGuardBase sub : g) {
                Expression sube = sub.getExpression();
                if (sube == null) continue;
                if (e == null) e = sube;
                else           e = and(sube, e);
            }
        } else if (gb instanceof LTSminGuardOr) {
            LTSminGuardOr g = (LTSminGuardOr)gb;
            for (LTSminGuardBase sub : g) {
                Expression sube = sub.getExpression();
                if (sube == null) continue;
                if (e == null) e = sube;
                else           e = or(sube, e);
            }
        } else if (gb instanceof LTSminGuardNor) {
            LTSminGuardNor g = (LTSminGuardNor)gb;
            for (LTSminGuardBase sub : g) {
                Expression sube = sub.getExpression();
                if (sube == null) continue;
                if (e == null) e = sube;
                else           e = or(sube, e);
            }
            if (e != null)
                e = not(e);
        } else if (gb instanceof LTSminGuardNand) {
            LTSminGuardNand g = (LTSminGuardNand)gb;
            for (LTSminGuardBase sub : g) {
                Expression sube = sub.getExpression();
                if (sube == null) continue;
                if (e == null) e = sube;
                else           e = and(sube, e);
            }
            if (e != null)
                e = not(e);
        } else {
            throw new AssertionError("UNSUPPORTED " + gb.getClass().getSimpleName());                 
        }
        return e;
    }
}

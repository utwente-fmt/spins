package spinja.promela.compiler.ltsmin.matrix;

import static spinja.promela.compiler.ltsmin.model.LTSminUtil.and;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.bool;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.not;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.or;

import java.util.NoSuchElementException;

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
    
    public LTSminGuardBase negate() {
        LTSminGuardBase gb = this;
        if (gb instanceof LTSminGuard) {
            LTSminGuard gb2 = (LTSminGuard) gb;
            /*if (gb2.getExpr() instanceof BooleanExpression) { // law of double negation
                BooleanExpression be = (BooleanExpression)gb2.getExpr();
                if (PromelaConstants.LNOT == be.getToken().kind) {
                    return new LTSminGuard(be.getExpr1());
                }
            }*/
            Expression e = not(gb2.getExpr());
            return new LTSminGuard(e);
        } else if (gb instanceof LTSminGuardOr) {
            LTSminGuardOr g = (LTSminGuardOr)gb;
            LTSminGuardNor nor = new LTSminGuardNor();
            nor.guards.addAll(g.guards);
            return nor;
        } else if (gb instanceof LTSminGuardAnd) {
            LTSminGuardAnd g = (LTSminGuardAnd)gb;
            LTSminGuardNand nand = new LTSminGuardNand();
            nand.guards.addAll(g.guards);
            return nand;
        } else if (gb instanceof LTSminGuardNor) {
            LTSminGuardNor g = (LTSminGuardNor)gb;
            LTSminGuardOr or = new LTSminGuardOr();
            or.guards.addAll(g.guards);
            return or;
        } else if (gb instanceof LTSminGuardNand) {
            LTSminGuardNand g = (LTSminGuardNand)gb;
            LTSminGuardAnd and = new LTSminGuardAnd();
            and.guards.addAll(g.guards);
            return and;
        } else {
            throw new AssertionError("UNSUPPORTED " + gb.getClass().getSimpleName());                 
        }
    }

    public Expression getExpression() {
        LTSminGuardBase gb = this;
        Expression e = bool(true);
        try {
            if (gb instanceof LTSminGuard) {
                e = ((LTSminGuard)gb).getExpr();
            } else if (gb instanceof LTSminGuardAnd) {
                LTSminGuardAnd g = (LTSminGuardAnd)gb;
                e = g.iterator().next().getExpression();
                for (LTSminGuardBase sub : g.guards.subList(1, g.guardCount()))
                    e = and(e, sub.getExpression());
            } else if (gb instanceof LTSminGuardOr) {
                LTSminGuardOr g = (LTSminGuardOr)gb;
                e = g.iterator().next().getExpression();
                for (LTSminGuardBase sub : g.guards.subList(1, g.guardCount()))
                    e = or(e, sub.getExpression());
            } else if (gb instanceof LTSminGuardNor) {
                LTSminGuardNor g = (LTSminGuardNor)gb;
                e = g.iterator().next().getExpression();
                for (LTSminGuardBase sub : g.guards.subList(1, g.guardCount()))
                    e = or(e, sub.getExpression());
                e = not(e);
            } else if (gb instanceof LTSminGuardNand) {
                LTSminGuardNand g = (LTSminGuardNand)gb;
                e = g.iterator().next().getExpression();
                for (LTSminGuardBase sub : g.guards.subList(1, g.guardCount()))
                    e = and(e, sub.getExpression());
                e = not(e);
            } else {
                throw new AssertionError("UNSUPPORTED " + gb.getClass().getSimpleName());                 
            }
        } catch (NoSuchElementException nse) {}
        if (e == null) throw new AssertionError();
        return e;
    }
}

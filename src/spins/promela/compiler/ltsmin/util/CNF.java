package spins.promela.compiler.ltsmin.util;

import static spins.promela.compiler.ltsmin.util.LTSminUtil.and;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.negate;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.or;

import java.util.ArrayList;
import java.util.LinkedList;

import spins.promela.compiler.expression.BooleanExpression;
import spins.promela.compiler.expression.Expression;
import spins.promela.compiler.ltsmin.LTSminDMWalker;
import spins.promela.compiler.ltsmin.LTSminTreeWalker.Options;
import spins.promela.compiler.ltsmin.matrix.DepMatrix;
import spins.promela.compiler.ltsmin.model.LTSminModel;
import spins.promela.compiler.ltsmin.util.CNF.D;
import spins.promela.compiler.parser.PromelaConstants;


/**
 * Conjunctive Normal Form: A list of Conjuncts
 * 
 * Class Invariant: The conjuncts are independent.
 * 
 * To maintain the invariant, we use the law of distribution on disjuncts.
 * Therefore, disjuncts do not purely consists of literals (simple equalities/
 * inequalities), but may be more complex expressions themselves.
 * 
 * 
 * @author laarman
 */
public class CNF extends ArrayList<D> {

    private static final long serialVersionUID = 1L;

    LTSminModel m = null;

    public CNF(LTSminModel m) {
        this.m = m;
    }

    public CNF merge(Expression e) {
        return merge(new Disjunct(e));
    }

    public CNF merge(CNF... cnfs) {
        for (CNF cnf : cnfs)
            for (D d : cnf)
                merge(d);
        return this;
    }

    public CNF merge(D x) {
        for (D d : this) {
            if (d.isDependent(x)) {
                d.and(x);
                return this; // see class invariant
            }
        }
        add(x);
        return this;
    }

    public Expression getExpression() {
        Expression e = null;
        for (D sub : this) {
            if (e == null) e = sub.getExpression();
            else           e = and(e, sub.getExpression());
        }
        return e;
    }

    public abstract class D {
        private DepMatrix matrix = null;
        protected abstract DepMatrix createMatrix();
        public abstract void and(D x);
        public boolean isDependent(D x) {
            return getMatrix().isDependent(0, x.getMatrix(), 0);
        }
        public abstract Expression getExpression();
        public DepMatrix getMatrix() {
            if (matrix == null)
                matrix = createMatrix();
            return matrix;
        }
        protected DepMatrix depMatrix(Expression e) {
            DepMatrix temp = new DepMatrix(1, m.sv.size());
            LTSminDMWalker.walkOneGuard(m, temp, e, 0);
            return temp;
        }
    }

    class Disjuncts extends D {
        private LinkedList<Expression> disjunct = new LinkedList<Expression>();
        public Disjuncts(D... dd) {
            for (D d : dd) {
                mergeAll(d);
            }
        }
        public Disjuncts(Expression... ee) {
            for (Expression e : ee) {
                mergeAll(new Disjunct(e));
            }
        }
        public Expression getExpression() {
            Expression e = null;
            for (Expression sub : disjunct) {
                if (e == null) e = sub;
                else           e = or(e, sub);
            }
            return e;
        }
        public DepMatrix createMatrix() {
            DepMatrix m = null;
            for (Expression sub : disjunct) {
                DepMatrix m2 = depMatrix(sub);
                if (m == null) {
                    m = m2;
                } else {
                    m.orRow(0, m2, 0);
                }
            }
            return m;
        }
        public D merge(D d) {
            mergeAll(d);
            return this;
        }
        private void mergeAll(D d) {
            if (d instanceof Disjuncts) {
                disjunct.addAll(((Disjuncts)d).disjunct);
            } else {
                disjunct.add(((Disjunct)d).e);
            }
            getMatrix().orRow(0, d.getMatrix(), 0);
        }
        public void and(D d) {
            Expression e = this.getExpression();
            getMatrix().orRow(0, d.getMatrix(), 0);
            disjunct.clear();
            disjunct.add(LTSminUtil.and(e,  d.getExpression()));
        }
    }

    class Disjunct extends D {
        private Expression e;
        public Disjunct(Expression e) {
            this.e = e;
        }
        public Expression getExpression() {
            return e;
        }
        public DepMatrix createMatrix() {
            return depMatrix(e);
        }
        public void and(D d) {
            getMatrix().orRow(0, d.getMatrix(), 0);
            this.e = LTSminUtil.and(this.e,  d.getExpression());
        }
    }

    // Law of distribution
    private void distribute(Expression e1, Expression e2, Options opts) {
        if (!opts.cnf) {
            this.merge(new Disjuncts(e1, e2));
            return;
        }

        CNF left = new CNF(this.m);
        CNF rght = new CNF(this.m); 
        left.walkGuard(e1, false, opts);
        rght.walkGuard(e2, false, opts);
        for (D d1 : left) {
            for (D d2 : rght) {
                D x = new Disjuncts(d1).merge(d2);
                this.merge(x);
            }
        }
    }

    public void walkGuard(Expression e, boolean invert, Options opts) {
        if (e instanceof BooleanExpression) {
            BooleanExpression be = (BooleanExpression)e;
            switch (be.getToken().kind) {
            case PromelaConstants.LNOT:
                walkGuard(be.getExpr1(), !invert, opts);
                return;
            case PromelaConstants.LOR:
                if (invert) {
                    // DeMorgan: NOT OR -->  AND NOT
                    walkGuard(be.getExpr1(), invert, opts);
                    walkGuard(be.getExpr2(), invert, opts);
                } else {
                    distribute(be.getExpr1(), be.getExpr2(), opts);
                }
                return;
            case PromelaConstants.LAND:
                if (!invert) {
                    walkGuard(be.getExpr1(), invert, opts);
                    walkGuard(be.getExpr2(), invert, opts);
                } else {
                    // DeMorgan: NOT AND -->  OR NOT
                    distribute(negate(be.getExpr1()), negate(be.getExpr2()), opts);
                }
                return;
            default:
                this.merge(invert ?  negate(e) : e);
            }
        } else {
            this.merge(invert ?  negate(e) : e);
        }
    }
}

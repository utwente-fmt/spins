package spins.promela.compiler.ltsmin.matrix;

import spins.promela.compiler.expression.CompareExpression;
import spins.promela.compiler.expression.Expression;
import spins.promela.compiler.expression.Identifier;
import spins.promela.compiler.ltsmin.model.LTSminModelFeature;
import spins.promela.compiler.parser.ParseException;
import spins.promela.compiler.parser.PromelaConstants;
import spins.util.StringWriter;

/**
 *
 * @author Freark van der Berg
 */
public class LTSminGuard extends LTSminGuardBase implements LTSminModelFeature {
	public Expression expr;

	public LTSminGuard(Expression expr) {
		this.expr = expr;
	}

	public Expression getExpr() {
		return expr;
	}
	
	public boolean equals(Object o) {
		if (o == null)
			return false;
		if (!(o instanceof LTSminGuard))
			return false;
		LTSminGuard g = (LTSminGuard)o;
		return expr.equals(g.getExpr());
	}

	public void setExpr(Expression expr) {
		this.expr = expr;
	}

	public boolean isDefinitelyTrue() {
		try {
			// Try to sieve out the true statements
			return expr.getConstantValue()!=0;
		} catch(ParseException e) {
		}
		return false;
	}

	public boolean isDefinitelyFalse() {
		try {
			// Try to sieve out the true statements
			return expr.getConstantValue()==0;
		} catch(ParseException e) {
		}
		return false;
	}

	public void prettyPrint(StringWriter w) {
		w.appendLine(expr.toString());
	}

	public String toString() {
		return "G: "+ expr;
	}
	
	public static boolean isPC(LTSminGuard g) {
        if (g instanceof LTSminPCGuard) return true;
        if (!(g.getExpr() instanceof CompareExpression)) return false;
        CompareExpression ce = (CompareExpression) g.getExpr();
        if (ce.getToken().kind != PromelaConstants.EQ) return false;
        if (!(ce.getExpr1() instanceof Identifier)) return false;
        Identifier id = (Identifier)ce.getExpr1();
        return id.isPC(); 
    }

	private int index = -2;
    public void setIndex(int index) {
        this.index = index;
    }

    public int getIndex() {
        return index;
    }
}
package spinja.promela.compiler.ltsmin.matrix;

import spinja.promela.compiler.expression.Expression;
import spinja.promela.compiler.parser.ParseException;
import spinja.util.StringWriter;

/**
 *
 * @author Freark van der Berg
 */
public class LTSminGuard extends LTSminGuardBase {
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
}

// Copyright 2010, University of Twente, Formal Methods and Tools group
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package spins.promela.compiler.expression;

import java.util.HashSet;
import java.util.Set;

import spins.promela.compiler.parser.ParseException;
import spins.promela.compiler.parser.PromelaConstants;
import spins.promela.compiler.parser.Token;
import spins.promela.compiler.variable.VariableAccess;
import spins.promela.compiler.variable.VariableType;

/**
 * The aritmic expression can be any expression that should return an integer value, e.g. any
 * mathematical calculation, binary expression or condition expression.
 * 
 * @author Marc de Jonge
 */
public class AritmicExpression extends NAryExpression {
	@SuppressWarnings("unused")
	private static final long serialVersionUID = -4022528945025403911L;

	private final Expression ex1, ex2, ex3;

	/**
	 * Creates a new AritmicExpression from only one subexpression.
	 * 
	 * @param token
	 *            The token that is used to determine what kind of calculation is does.
	 * @param only
	 *            The only subexpression.
	 */
	public AritmicExpression(final Token token, final Expression only) {
		this(token, only, null, null);
	}

	/**
	 * Creates a new AritmicExpression from two subexpressions.
	 * 
	 * @param token
	 *            The token that is used to determine what kind of calculation is does.
	 * @param left
	 *            The left part of the expression
	 * @param right
	 *            The right part of the expression.
	 */
	public AritmicExpression(final Token token, final Expression left, final Expression right) {
		this(token, left, right, null);
	}

	/**
	 * Creates a new AritmicExpression from three subexpressions.
	 * 
	 * @param token
	 *            The token that is used to determine what kind of calculation is does.
	 * @param first
	 *            The left part of the expression
	 * @param second
	 *            The middle part of the expression.
	 * @param third
	 *            The right part of the expression
	 */
	public AritmicExpression(final Token token, final Expression first, final Expression second,
		final Expression third) {
		super(token);
		ex1 = first;
		ex2 = second;
		ex3 = third;
	}

	public AritmicExpression(AritmicExpression ae, int kind) {
        super(new Token(kind, PromelaConstants.tokenImage[kind]));
	    this.ex1 = ae.ex1;
        this.ex2 = ae.ex2;
        this.ex3 = ae.ex3;
    }

    @Override
	public String getBoolExpression() throws ParseException {
		if (ex2 == null) {
			return "(" + getToken().image + ex1.getIntExpression() + " != 0)";
		} else if (ex3 == null) {
			return "(" + ex1.getIntExpression() + " " + getToken().image + " "
					+ ex2.getIntExpression() + " != 0)";
		} else { // Can only happen with the x?1:0 expression
			return "(" + ex1.getBoolExpression() + " ? " + ex2.getBoolExpression() + " : "
					+ ex3.getBoolExpression() + ")";
		}
	}

	@Override
	public int getConstantValue() throws ParseException {
		try {
			int lhs = ex1.getConstantValue();
			switch (getToken().kind) {
				case PromelaConstants.BAND:
					if (lhs == 0) return 0;
					return lhs & ex2.getConstantValue();
				case PromelaConstants.BOR:
					return lhs | ex2.getConstantValue();
				case PromelaConstants.BNOT:
					return ~lhs;
				case PromelaConstants.MINUS:
					if (ex2 == null) {
						return -lhs;
					} else {
						return lhs - ex2.getConstantValue();
					}
				case PromelaConstants.TIMES:
					if (lhs == 0) return 0;
					return lhs * ex2.getConstantValue();
				case PromelaConstants.DIVIDE:
					if (lhs == 0) return 0;
					return lhs / ex2.getConstantValue();
				case PromelaConstants.MODULO:
					if (lhs == 0) return 0;
					return lhs % ex2.getConstantValue();
				case PromelaConstants.PLUS:
					return lhs + ex2.getConstantValue();
				case PromelaConstants.XOR:
					return lhs ^ ex2.getConstantValue();
				case PromelaConstants.LSHIFT:
					if (lhs == 0) return 0;
					return lhs << ex2.getConstantValue();
				case PromelaConstants.RSHIFT:
					if (lhs == 0) return 0;
					return lhs >>> ex2.getConstantValue();
				case PromelaConstants.RARROW:
					return (lhs == 0 ? ex3.getConstantValue() : ex2.getConstantValue() );
			}
		} catch (ParseException pe ) {
			if (null == ex2 || PromelaConstants.RARROW == getToken().kind) {
				throw pex;
			}
			int rhs = ex2.getConstantValue();
			switch (getToken().kind) {
				case PromelaConstants.BAND:
					if (rhs == 0) return 0;
					return ex1.getConstantValue() & rhs;
				case PromelaConstants.BOR:
					return ex1.getConstantValue() | rhs;
				case PromelaConstants.MINUS:
					return ex1.getConstantValue() - rhs;
				case PromelaConstants.TIMES:
					if (rhs == 0) return 0;
					return ex1.getConstantValue() * rhs;
				case PromelaConstants.DIVIDE:
					return ex1.getConstantValue() / rhs;
				case PromelaConstants.MODULO:
					if (rhs <= 0) return 0;
					return ex1.getConstantValue() % rhs;
				case PromelaConstants.PLUS:
					return ex1.getConstantValue() + rhs;
				case PromelaConstants.XOR:
					return ex1.getConstantValue() ^ rhs;
				case PromelaConstants.LSHIFT:
					return ex1.getConstantValue() << rhs;
				case PromelaConstants.RSHIFT:
					return ex1.getConstantValue() >>> rhs;
			}
		}
		throw new AssertionError("Unimplemented aritmic type: " + getToken().image);
	}

	@Override
	public String getIntExpression() throws ParseException {
		if (ex2 == null) {
			return "(" + getToken().image + ex1.getIntExpression() + ")";
		} else if (ex3 == null) {
			if (getToken().image.equals("%")) {
				// Modulo takes a special notation to make sure that it
				// returns a positive value
				return "Math.abs(" + ex1.getIntExpression() + " % " + ex2.getIntExpression() + ")";
			} else {
				return "(" + ex1.getIntExpression() + " " + getToken().image + " "
						+ ex2.getIntExpression() + ")";
			}
		} else {
			return "(" + ex1.getBoolExpression() + " ? " + ex2.getIntExpression() + " : "
					+ ex3.getIntExpression() + ")";
		}
	}

	@Override
	public final boolean equals(Object o) {
		if (o == null)
			return false;
		if (!(o instanceof AritmicExpression))
			return false;
		AritmicExpression ae = (AritmicExpression)o;
		if (getToken().kind != ae.getToken().kind)
			return false;
		if (!ex1.equals(ae.getExpr1()))
			return false;
		if (ex2 == null || ae.ex2 == null)
			return ex2 == ae.ex2;
		if (!ex2.equals(ae.ex2))
			return false;
		if (ex3 == null || ae.ex3 == null)
			return ex3 == ae.ex3;
		return ex3.equals(ae.ex3);
	}

    public final int hashCode() {
        return getToken().kind * 37 +
                ex1.hashCode() * 13 +
                (ex2 == null ? 0 : ex2.hashCode() * 59) +
                (ex3 == null ? 0 : ex3.hashCode());
    }

	@Override
	public VariableType getResultType() {
		return VariableType.INT;
	}

	@Override
	public String getSideEffect() {
		String effect = ex1.getSideEffect();
		if (ex2 != null) {
			if ((effect == null) || (ex2.getSideEffect() != null)) {
				effect = ex2.getSideEffect();
			}
		}
		if (ex3 != null) {
			if ((effect == null) || (ex3.getSideEffect() != null)) {
				effect = ex3.getSideEffect();
			}
		}
		return effect;
	}

	@Override
	public Set<VariableAccess> readVariables() {
		final Set<VariableAccess> rv = new HashSet<VariableAccess>();
		if (ex1 != null) {
			rv.addAll(ex1.readVariables());
		}
		if (ex2 != null) {
			rv.addAll(ex2.readVariables());
		}
		if (ex3 != null) {
			rv.addAll(ex3.readVariables());
		}
		return rv;
	}

	@Override
	public String toString() {
		if (ex2 == null) {
			return "(" + getToken().image + ex1.toString() + ")";
		} else if (ex3 == null) {
			return "(" + ex1.toString() + " " + getToken().image + " " + ex2.toString() + ")";

		} else {
			return "(" + ex1.toString() + " ? " + ex2.toString() + " : " + ex3.toString() + ")";
		}
	}

	public Expression getExpr1() {
		return ex1;
	}
	public Expression getExpr2() {
		return ex2;
	}
	public Expression getExpr3() {
		return ex3;
	}

    @Override
    protected int totalExpressions() {
        return (ex2 == null ? 1 :
               (ex3 == null ? 2 : 3));
    }

    @Override
    protected Expression getExpression(int i) {
        switch (i) {
        case 0: return ex1;
        case 1: return ex2;
        case 2: return ex3;
        }
        throw new AssertionError("Wrong number of expressions.");
    }
}

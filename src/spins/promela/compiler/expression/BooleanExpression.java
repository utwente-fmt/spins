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

import spins.promela.compiler.parser.MyParseException;
import spins.promela.compiler.parser.ParseException;
import spins.promela.compiler.parser.PromelaConstants;
import spins.promela.compiler.parser.Token;
import spins.promela.compiler.variable.VariableAccess;
import spins.promela.compiler.variable.VariableType;

/**
 * The boolean expression can be any expression that takes two boolean values and returns a boolean
 * value, e.g. the AND expression.
 * 
 * @author Marc de Jonge
 */
public class BooleanExpression extends NAryExpression {
	@SuppressWarnings("unused")
	private static final long serialVersionUID = -4022528945025403911L;

	private final Expression ex1, ex2;

	/**
	 * Creates a new BooleanExpression from only one subexpression.
	 * 
	 * @param token
	 *            The token that is used to determen what kind of calculation is does.
	 * @param only
	 *            The only subexpression.
	 */
	public BooleanExpression(final Token token, final Expression only) {
		this(token, only, null);
	}

	/**
	 * Creates a new BooleanExpression from two subexpressions.
	 * 
	 * @param token
	 *            The token that is used to determen what kind of calculation is does.
	 * @param left
	 *            The left part of the expression
	 * @param right
	 *            The right part of the expression.
	 */
	public BooleanExpression(final Token token, final Expression left, final Expression right) {
		super(token);
		ex1 = left;
		ex2 = right;
	}

	@Override
	public final boolean equals(Object o) {
		if (o == null)
			return false;
		if (!(o instanceof BooleanExpression))
			return false;
		BooleanExpression ce = (BooleanExpression)o;
		return getToken().kind == ce.getToken().kind && ex1.equals(ce.getExpr1()) &&
				(ex2==null && ce.getExpr2()==null || ex2.equals(ce.getExpr2()));
	}

    public final int hashCode() {
        return getToken().kind * 37 +
                ex1.hashCode() * 13 +
                (ex2 == null ? 0 : ex2.hashCode());
    }

	@Override
	public String getBoolExpression() throws ParseException {
		if (ex2 == null) {
			return "(" + getToken().image + ex1.getBoolExpression() + ")";
		} else {
			return "(" + ex1.getBoolExpression() + " " + getToken().image + " "
					+ ex2.getBoolExpression() + ")";
		}
	}

	@Override
	public int getConstantValue() throws ParseException {
		switch (getToken().kind) {
			case PromelaConstants.LNOT:
				return ex1.getConstantValue() == 0 ? 1 : 0;
			case PromelaConstants.LOR:
				return (ex1.getConstantValue() != 0) || (ex2.getConstantValue() != 0) ? 1 : 0;
			case PromelaConstants.LAND:
				return (ex1.getConstantValue() != 0) && (ex2.getConstantValue() != 0) ? 1 : 0;
		}
		throw pex;
	}

	@Override
	public String getIntExpression() throws ParseException {
		if (ex2 == null) {
			return "(" + getToken().image + ex1.getIntExpression() + " ? 1 : 0)";
		} else {
			return "(" + ex1.getBoolExpression() + " " + getToken().image + " "
					+ ex2.getBoolExpression() + " ? 1 : 0)";
		}
	}

	@Override
	public VariableType getResultType() {
		return VariableType.BOOL;
	}

	@Override
	public String getSideEffect() {
		if ((ex1.getSideEffect() != null) || ((ex2 != null) && (ex2.getSideEffect() != null))) {
			return "Boolean expression has some side effect!";
		}
		return null;
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
		return rv;
	}

	@Override
	public String toString() {
		try {
			return getBoolExpression();
		} catch (final ParseException ex) {
			return "";
		}
	}

	public Expression getExpr1() {
		return ex1;
	}
	public Expression getExpr2() {
		return ex2;
	}

    @Override
    protected int totalExpressions() {
        return (ex2 == null ? 1 : 2);
    }

    @Override
    protected Expression getExpression(int i) {
        switch (i) {
        case 0: return ex1;
        case 1: return ex2;
        }
        throw new AssertionError("Wrong number of expressions.");
    }
}

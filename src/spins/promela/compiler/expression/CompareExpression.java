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

import static spins.promela.compiler.ltsmin.util.LTSminUtil.getName;

import java.util.HashSet;
import java.util.Set;

import spins.promela.compiler.parser.MyParseException;
import spins.promela.compiler.parser.ParseException;
import spins.promela.compiler.parser.PromelaConstants;
import spins.promela.compiler.parser.Token;
import spins.promela.compiler.variable.VariableAccess;
import spins.promela.compiler.variable.VariableType;

/**
 * The compare expression can be any expression that compares to integer values and returns a
 * boolean.
 * 
 * @author Marc de Jonge
 */
public class CompareExpression extends Expression {
	@SuppressWarnings("unused")
	private static final long serialVersionUID = -7625932622450298223L;

	private final Expression ex1, ex2;

	/**
	 * Creates a new BooleanExpression from two subexpressions.
	 * 
	 * @param token
	 *            The token that is used to determen what kind of calculation is does.
	 * @param left
	 *            The left part of the expression
	 * @param right
	 *            The right part of the expression.
	 * @throws ParseException
	 *             When something went wrong while creating the net AritmicExpression.
	 */
	public CompareExpression(final Token token, final Expression left, final Expression right) {
		super(token);
		ex1 = left;
		ex2 = right;
	}

    public CompareExpression(CompareExpression ae, int kind) {
        super(new Token(kind, getName(kind)));
        this.ex1 = ae.ex1;
        this.ex2 = ae.ex2;
    }

    @Override
	public final boolean equals(Object o) {
		if (o == null)
			return false;
		if (!(o instanceof CompareExpression))
			return false;
		CompareExpression ce = (CompareExpression)o;
		return getToken().kind == ce.getToken().kind &&
				ex1.equals(ce.ex1) && ex2.equals(ce.ex2);
	}

    public final int hashCode() {
        return getToken().kind * 37 +
               ex1.hashCode() * 13 +  ex2.hashCode();
    }
	
	@Override
	public String getBoolExpression() throws ParseException {
		return "(" + ex1.getIntExpression() + " " + getToken().image + " " + ex2.getIntExpression()
				+ ")";
	}

	@Override
	public int getConstantValue() throws ParseException {
		switch (getToken().kind) {
			case PromelaConstants.LT:
				return ex1.getConstantValue() < ex2.getConstantValue() ? 1 : 0;
			case PromelaConstants.GT:
				return ex1.getConstantValue() > ex2.getConstantValue() ? 1 : 0;
			case PromelaConstants.LTE:
				return ex1.getConstantValue() <= ex2.getConstantValue() ? 1 : 0;
			case PromelaConstants.GTE:
				return ex1.getConstantValue() >= ex2.getConstantValue() ? 1 : 0;
			case PromelaConstants.EQ:
				return ex1.getConstantValue() == ex2.getConstantValue() ? 1 : 0;
			case PromelaConstants.NEQ:
				return ex1.getConstantValue() != ex2.getConstantValue() ? 1 : 0;
		}
		throw new MyParseException("Unimplemented compare type: " + getToken().image, getToken());
	}

	@Override
	public String getIntExpression() throws ParseException {
		return "(" + ex1.getIntExpression() + " " + getToken().image + " " + ex2.getIntExpression()
				+ " ? 1 : 0)";
	}

	@Override
	public VariableType getResultType() {
		return VariableType.BOOL;
	}

	@Override
	public String getSideEffect() {
		if ((ex1.getSideEffect() != null) || (ex2.getSideEffect() != null)) {
			return "size effect!";
		}
		return null;
	}

	@Override
	public Set<VariableAccess> readVariables() {
		final Set<VariableAccess> rv = new HashSet<VariableAccess>();
		rv.addAll(ex1.readVariables());
		rv.addAll(ex2.readVariables());
		return rv;
	}

	@Override
	public String toString() {
		return "(" + ex1 + " " + getToken().image + " " + ex2
					+ ")";
	}

	public Expression getExpr1() {
		return ex1;
	}
	public Expression getExpr2() {
		return ex2;
	}
}

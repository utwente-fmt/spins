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

package spins.promela.compiler.actions;

import spins.promela.compiler.Proctype;
import spins.promela.compiler.expression.AritmicExpression;
import spins.promela.compiler.expression.Expression;
import spins.promela.compiler.expression.Identifier;
import spins.promela.compiler.parser.PromelaConstants;
import spins.promela.compiler.parser.Token;

public class AssignAction extends Action {
	private final Identifier id;

	private Expression expr;

	public AssignAction(final Token token, final Identifier id) {
		this(token, id, null);
	}

	public AssignAction(final Token token, final Identifier id, final Expression expr) {
		super(token);
		this.id = id;
		this.expr = expr;
	}

	@Override
	public String getEnabledExpression() {
		return null;
	}

	public Expression getExpr() {
		return expr;
	}

	@Override
	public boolean isLocal(final Proctype proc) {
		if (expr != null && !(new ExprAction(expr)).isLocal(proc))
		    return false;
        return proc.hasVariable(id.getVariable().getName()) && super.isLocal(proc);
	}

	@Override
	public String toString() {
		switch (getToken().kind) {
			case PromelaConstants.ASSIGN:
				return id.toString() + " = " + expr.toString();
			case PromelaConstants.INCR:
				return id.toString() + "++";
			case PromelaConstants.DECR:
				return id.toString() + "--";
			default:
				return "unknown assignment type";
		}
	}

	public Identifier getIdentifier() {
		return id;
	}

	public void setExpr(AritmicExpression calc) {
		expr = calc;
	}
}

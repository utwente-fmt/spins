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
import spins.promela.compiler.expression.Expression;
import spins.promela.compiler.parser.Token;
import spins.promela.compiler.variable.VariableAccess;

public class AssertAction extends Action {
	private final Expression expr;

	public AssertAction(final Token token, final Expression expr) {
		super(token);
		this.expr = expr;
	}

	@Override
	public String getEnabledExpression() {
		return null;
	}

	@Override
	public boolean isLocal(final Proctype proc) {
		for (final VariableAccess va : expr.readVariables()) {
			if (!proc.hasVariable(va.getVar().getName())) {
				return false;
			}
		}
		return super.isLocal(proc);
	}

	@Override
	public String toString() {
		return "assert " + expr.toString();
	}

	public Expression getExpr() {
		return expr;
	}
}

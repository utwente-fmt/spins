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

import spins.promela.compiler.parser.Token;
import spins.promela.compiler.variable.VariableAccess;
import spins.promela.compiler.variable.VariableType;

public class TimeoutExpression extends Expression {

    Expression deadlock = null;

	public TimeoutExpression(final Token token) {
		super(token);
	}

	@Override
	public String getBoolExpression() {
		return "_timeout";
	}

	@Override
	public String getIntExpression() {
		return "(_timeout ? 1 : 0)";
	}

	@Override
	public VariableType getResultType() {
		return VariableType.BOOL;
	}

	@Override
	public String getSideEffect() {
		return null;
	}

	@Override
	public Set<VariableAccess> readVariables() {
		return new HashSet<VariableAccess>();
	}

	@Override
	public String toString() {
		return "timeout";
	}

    public void setDeadlock(Expression deadlock) {
        this.deadlock = deadlock;
    }

    public Expression getDeadlock() {
        return deadlock;
    }
}

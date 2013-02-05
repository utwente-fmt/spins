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

import java.util.Set;

import spins.promela.compiler.parser.MyParseException;
import spins.promela.compiler.parser.ParseException;
import spins.promela.compiler.parser.PromelaConstants;
import spins.promela.compiler.parser.Token;
import spins.promela.compiler.variable.ChannelType;
import spins.promela.compiler.variable.VariableAccess;
import spins.promela.compiler.variable.VariableType;

public class ChannelOperation extends Expression {
	private final Expression expr;

	public ChannelOperation(final Token token, final Expression expr) throws ParseException {
		super(token);
		this.expr = expr;
		if (!expr.getResultType().canConvert(ChannelType.UNASSIGNED_CHANNEL)) {
			throw new MyParseException("Cannot convert type in the channel operation to a channel.",
				getToken());
		}
	}

	@Override
	public String getBoolExpression() throws ParseException {
		return "_channels[" + expr.getIntExpression() + "]." + getMethodName();
	}

	private String getMethodName() throws ParseException {
		switch (getToken().kind) {
			case PromelaConstants.EMPTY:
				return "isEmpty()";
			case PromelaConstants.FULL:
				return "isFull()";
			case PromelaConstants.NEMPTY:
				return "isNotEmpty()";
			case PromelaConstants.NFULL:
				return "isNotFull()";
			default:
				throw new MyParseException("Unknow kind of operation on the channel", getToken());
		}
	}

	@Override
	public VariableType getResultType() {
		return VariableType.BOOL;
	}

	@Override
	public Set<VariableAccess> readVariables() {
		return expr.readVariables();
	}

	public Expression getExpression() {
		return expr;
	}
}

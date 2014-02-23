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

import static spins.promela.compiler.ltsmin.util.LTSminUtil.bool;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.chanLength;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.compare;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.constant;
import static spins.promela.compiler.parser.PromelaConstants.EQ;
import static spins.promela.compiler.parser.PromelaConstants.NEQ;

import java.util.Set;

import spins.promela.compiler.parser.MyParseException;
import spins.promela.compiler.parser.ParseException;
import spins.promela.compiler.parser.PromelaConstants;
import spins.promela.compiler.parser.Token;
import spins.promela.compiler.variable.ChannelType;
import spins.promela.compiler.variable.VariableAccess;
import spins.promela.compiler.variable.VariableType;

public class ChannelOperation extends Expression
                              implements TranslatableExpression {
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
				throw new MyParseException("Unknown kind of operation on the channel", getToken());
		}
	}

    @Override
    public final boolean equals(Object o) {
        if (o == null)
            return false;
        if (!(o instanceof ChannelOperation))
            return false;
        ChannelOperation e = (ChannelOperation)o;
        if (e.getToken().kind != getToken().kind)
            return false;
        return e.expr.equals(expr);
    }

    public final int hashCode() {
        return getToken().kind * 37 + expr.hashCode();
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

    public Expression translate() {
        String name = getToken().image;
        Identifier id = (Identifier)getExpression();
        if (((ChannelType)id.getVariable().getType()).isRendezVous())
            return bool(true); // Spin returns true in this case (see garp model)
        VariableType type = id.getVariable().getType();
        int buffer = ((ChannelType)type).getBufferSize();
        Expression left = chanLength(id);
        Expression right = null;
        int op = -1;
        if (name.equals("empty")) {
            op = EQ;
            right = constant (0);
        } else if (name.equals("nempty")) {
            op = NEQ;
            right = constant (0);
        } else if (name.equals("full")) {
            op = EQ;
            right = constant (buffer);
        } else if (name.equals("nfull")) {
            op = NEQ;
            right = constant (buffer);
        } else {
            throw new AssertionError();
        }
        return compare(op, left, right);
    }
   
}

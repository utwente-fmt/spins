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

import java.util.ArrayList;
import java.util.List;

import spins.promela.compiler.Proctype;
import spins.promela.compiler.expression.CompoundExpression;
import spins.promela.compiler.expression.Expression;
import spins.promela.compiler.expression.Identifier;
import spins.promela.compiler.parser.Token;
import spins.promela.compiler.variable.ChannelVariable;
import spins.promela.compiler.variable.Variable;
import spins.promela.compiler.variable.VariableAccess;
import spins.util.StringWriter;

public class ChannelSendAction extends Action implements CompoundExpression {
	private final Identifier id;

	private boolean sorted = false;
	
	private final List<Expression> exprs;

	public ChannelSendAction(final Token token, final Identifier id) {
		super(token);
		this.id = id;
		exprs = new ArrayList<Expression>();
	}

    public ChannelSendAction(final Token token, final Identifier id, boolean sorted) {
        this(token, id);
        this.setSorted(sorted);
    }

	public void addExpression(final Expression expr) {
		exprs.add(expr);
	}

	public boolean isRendezVous() {
		Variable v = id.getVariable();
		if (!(v instanceof ChannelVariable)) throw new AssertionError("Channel operation on non-channel "+ id);
		ChannelVariable cv = (ChannelVariable)v;
		return cv.getType().isRendezVous();
	}

	@Override
	public String getEnabledExpression() {
		return id + " != -1 && !_channels[" + id + "].isRendezVous() && _channels[" + id
				+ "].canSend()";
	}

	@Override
	public boolean isLocal(final Proctype proc) {
		if (!proc.isXS(id.getVariable())) {
			return false;
		}
		for (final Expression expr : exprs) {
			for (final VariableAccess va : expr.readVariables()) {
				if (!proc.hasVariable(va.getVar().getName())) {
					return false;
				}
			}
		}
		return super.isLocal(proc);
	}

	@Override
	public String toString() {
		final StringWriter w = new StringWriter();
		w.append(id).append("!");
		for (final Expression expr : exprs) {
			w.append(expr.toString()).append(",");
		}
		w.setLength(w.length() - 1);
		return w.toString();
	}

	public Identifier getIdentifier() {
		return id;
	}

	public List<Expression> getExprs() {
		return exprs;
	}

    public boolean isSorted() {
        return sorted;
    }

    public void setSorted(boolean sorted) {
        this.sorted = sorted;
    }
}

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

public class ChannelReadAction extends Action implements CompoundExpression {
	private final Identifier id;

	private final List<Expression> exprs;

	private final boolean poll;
	private final boolean random;
	
	public ChannelReadAction(final Token token, final Identifier id, boolean poll, boolean random) {
		super(token);
		this.id = id;
		exprs = new ArrayList<Expression>();
		this.poll = poll;
		this.random = random;
	}

	public ChannelReadAction(final Token token, final Identifier id) {
		this(token, id, false, false);
	}

	public void addExpression(final Expression expr) {
		exprs.add(expr);
	}

	@Override
	public String getEnabledExpression() {
		return "false";
	}

	public boolean isLocal(final Proctype proc) {
		if (!proc.isXR(id.getVariable())) { //TODO: optimize using dependencies
			return false;
		}
		for (final Expression expr : exprs) {
			if (expr instanceof Identifier) {
				final Variable var = ((Identifier) expr).getVariable();
				if (!proc.hasVariable(var.getName())) {
					return false;
				}
			} else {
				for (final VariableAccess va : expr.readVariables()) {
					if (!proc.hasVariable(va.getVar().getName())) {
						return false;
					}
				}
			}
		}
		return true;
	}

	@Override
	public String toString() {
		final StringWriter w = new StringWriter();
		w.append(id).append("?");
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

	public boolean isPoll() {
		return poll;
	}

	public boolean isNormal() {
		return !poll;
	}

	public boolean isRendezVous() {
		Variable v = id.getVariable();
		if (!(v instanceof ChannelVariable)) throw new AssertionError("Channel operation on non-channel "+ id);
		ChannelVariable cv = (ChannelVariable)v;
		return cv.getType().isRendezVous();
	}

	public boolean isRandom() {
		return random;
	}
}

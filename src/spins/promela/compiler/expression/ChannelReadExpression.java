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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import spins.promela.compiler.parser.ParseException;
import spins.promela.compiler.parser.Token;
import spins.promela.compiler.variable.VariableAccess;
import spins.promela.compiler.variable.VariableType;
import spins.util.StringWriter;

public class ChannelReadExpression extends Expression implements CompoundExpression {
	private final Identifier id;

	private final List<Expression> exprs;
	
	private final boolean random; 

	public ChannelReadExpression(final Token token, final Identifier id, boolean random) {
		super(token);
		this.id = id;
		this.random = random;
		exprs = new ArrayList<Expression>();
	}

	public void addExpression(final Expression expr) {
		exprs.add(expr);
	}

	@Override
	public String getBoolExpression() throws ParseException {
		return "_channels[SPINJA].length()"; //SJTODO: SpinJa semantics
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

    @Override
    public final boolean equals(Object o) {
        if (o == null)
            return false;
        if (!(o instanceof ChannelReadExpression))
            return false;
        ChannelReadExpression e = (ChannelReadExpression)o;
        if (e.random != random)
            return false;
        if (!id.equals(e.id))
            return false;
        return e.exprs.equals(exprs);
    }

    public final int hashCode() {
        return (random? 1 : 37) * exprs.hashCode() + id.hashCode() * 37;
    }

	public Identifier getIdentifier() {
		return id;
	}

	public List<Expression> getExprs() {
		return exprs;
	}

	@Override
	public VariableType getResultType() {
		return VariableType.BOOL;
	}

	@Override
	public Set<VariableAccess> readVariables() {
		final Set<VariableAccess> rv = new HashSet<VariableAccess>();
		for (final Expression e : exprs)
			rv.addAll(e.readVariables());
		rv.addAll(id.readVariables());
		return rv;
	}

	public boolean isRandom() {
		return random;
	}
}

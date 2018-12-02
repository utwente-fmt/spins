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

import static spins.promela.compiler.parser.Promela.C_STATE_PROC_COUNTER;

import java.util.HashSet;
import java.util.Set;

import spins.promela.compiler.ltsmin.LTSminPrinter;
import spins.promela.compiler.ltsmin.LTSminPrinter.ExprPrinter;
import spins.promela.compiler.ltsmin.model.LTSminModel;
import spins.promela.compiler.ltsmin.state.LTSminPointer;
import spins.promela.compiler.parser.ParseException;
import spins.promela.compiler.parser.Token;
import spins.promela.compiler.variable.Variable;
import spins.promela.compiler.variable.VariableAccess;
import spins.promela.compiler.variable.VariableType;

/**
 * An Identifiers is a reference to a variable. It can possibly also contain an expression which
 * calculated the number in the array of values it must have.
 * 
 * @author Marc de Jonge
 */
public class Identifier extends Expression {
	@SuppressWarnings("unused")
	private static final long serialVersionUID = -5928789117017713005L;

	private Variable var;

	private Expression arrayExpr;

	private int instanceIndex = -1; // no instance index 

	private Identifier sub = null;

	public Identifier getSub() {
		return sub;
	}

	public void setSub(Identifier sub) {
		this.sub = sub;
	}

	/**
	 * Creates a new Identifier that refers to the specified variable.
	 * 
	 * @param token
	 *            The token that is stored for debug reasons.
	 * @param var
	 *            The variable to which this identifier points.
	 */
	public Identifier(final Token token, final Variable var, Identifier sub) {
		super(token);
		this.var = var;
        if (var == null) {
            throw new AssertionError("Identifier without variable: "+ var);
        }
		arrayExpr = null;
		this.sub = sub;
	}

	/**
	 * Creates a new Identifier that refers to the specified variable on a specified place in the
	 * array (calculated using the expression).
	 * 
	 * @param token
	 *            The token that is stored for debug reasons.
	 * @param var
	 *            The variable to which this identifier points.
	 * @param arrayExpr
	 *            The expression that calculates the index in the array.
	 */
	public Identifier(final Token token, final Variable var, final Expression arrayExpr,
			Identifier sub) {
		super(token);
		this.var = var;
        if (var == null) {
            throw new AssertionError("Identifier without variable: "+ var);
        }
		this.arrayExpr = arrayExpr;
		this.sub = sub;
	}

	public Identifier(final Variable var) {
		super(null);
		this.var = var;
        if (var == null) {
            throw new AssertionError("Identifier without variable: "+ var);
        }
		arrayExpr = null;
		this.sub = null;
	}

	public Identifier(Identifier id) {
		this(id.getToken(), id.var, id.getArrayExpr(), id.sub);
	}

	public Identifier(Identifier id, Identifier sub) {
		this(id.getToken(), id.var, id.getArrayExpr(), sub);
	}

	public Identifier(Identifier id, Variable sub) {
		this(id.getToken(), id.var, id.getArrayExpr(), new Identifier(sub));
	}

	public int getConstantValue() throws ParseException {
		return var.getConstantValue();
	}

	/**
	 * @return The expression that is used for the array
	 */
	public Expression getArrayExpr() {
        if ((null == arrayExpr) != (-1 == var.getArraySize()))
            throw new AssertionError("Invalid array semantics in expression: "+ this+ ". Array size is: "+ var.getArraySize());
		return arrayExpr;
	}

	@Override
	public String getIntExpression() throws ParseException {
		if (var.getArraySize() > -1) {
			if (arrayExpr != null) {
				return var.getName() + "[" + arrayExpr.getIntExpression() + "]";
			} else {
				assert (false);
				return var.getName() + "[0]";
			}
		} else {
			return var.getName();
		}
	}

	@Override
	public VariableType getResultType() {
		return var.getType();
	}

	@Override
	public String getSideEffect() {
		if (arrayExpr != null) {
			return arrayExpr.getSideEffect();
		} else {
			return null;
		}
	}

	/**
	 * @return The variable to which this identifier points.
	 */
	public Variable getVariable() {
		return var;
	}

	@Override
	public Set<VariableAccess> readVariables() {
		final Set<VariableAccess> set = new HashSet<VariableAccess>();
		set.add(new VariableAccess(var, arrayExpr));
		return set;
	}

	@Override
	public String toString() {
		String res = "";
        if (getInstanceIndex() != -1) {
            res += var.getOwner().getName();
            res += "[" + getInstanceIndex() + "]";
            res += ":";
            res += var.toString();
            if (var.getArraySize() > -1) {
                if (arrayExpr != null) {
                    res += "[" + arrayExpr.toString() + "]";
                } else {
                    assert (false);
                    res += "[0]";
                }
            }
            return res;
        }
		if (var.getArraySize() > -1) {
			if (arrayExpr != null) {
				res = var.toString() + "[" + arrayExpr.toString() + "]";
			} else {
				assert (false);
				res = var.toString() + "[??]";
			}
		} else {
			res = var.toString();
		}
		if (null != sub)
			res += "."+ sub.toString();
		return res;
	}
	
	public final boolean equals(Object o) {
		if (!(o instanceof Identifier)) 
			return false;
		Identifier oi = (Identifier)o;
		if (!var.equals(oi.var))
			return false;
		if (arrayExpr == null || oi.arrayExpr == null)
			return arrayExpr == oi.arrayExpr;
		return arrayExpr.equals(oi.arrayExpr);
	}

    public final int hashCode() {
        return var.hashCode() * 37 +
               (arrayExpr == null ? 0 : arrayExpr.hashCode());
    }

	/**
	 * For remote variable ref
	 * @return
	 */
    public int getInstanceIndex() {
        return instanceIndex;
    }

    public void setInstanceIndex(int instanceIndex) {
        this.instanceIndex = instanceIndex;
    }

    public boolean isPC() {
        return getVariable().getName().equals(C_STATE_PROC_COUNTER);
    }

	public boolean isConstant() {
		try {
			getConstantValue ();
			return true;
		} catch (ParseException e) {
			return false;
		}
	}

	public void setVariable(Variable v) {
		this.var = v;
	}

	public void setArrayIndex(Expression e) {
		arrayExpr = e;
	}
	
    public String ref = null;

    public String getRef(LTSminModel model) {
        if (null!= ref)
            return ref;
        LTSminPointer svp = new LTSminPointer(model.sv, "");
        ExprPrinter p = new ExprPrinter(svp);
        ref = p.print(this);
        assert (!ref.equals(LTSminPrinter.SCRATCH_VARIABLE)); // write-only
        return ref;
    }
}

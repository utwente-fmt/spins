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

package spins.promela.compiler.variable;

import spins.promela.compiler.Proctype;
import spins.promela.compiler.expression.Expression;
import spins.promela.compiler.parser.ParseException;

/**
 * Breaks the equals contract to allow late rebinding of variables that are 
 * pointer to by identifiers.
 * 
 * @author laarman
 *
 */
public class Variable {

	private String name;

	private final int arraySize;

    private Proctype owner;

	private Expression initExpr = null;

	private VariableType type;

	private String displayName = null;

	private boolean assignedTo = false;

	private boolean hidden = false;

    private int arrayIndex = -1;

	public Variable(final VariableType type, final String name, final int arraySize) {
		this(type, name, arraySize, null);
	}

	public Variable(final VariableType type, final String name, final int arraySize, Proctype owner) {
		this.name = name;
		this.arraySize = arraySize;
		this.type = type;
		this.owner = owner;
	}

    public Variable(final Variable var) {
        this(var.type, var.name, var.arraySize, var.owner);
        this.assignedTo = var.assignedTo;
        this.arrayIndex = var.arrayIndex;
        this.hidden = var.hidden;
        this.displayName = var.displayName;
        this.initExpr = var.initExpr;
    }

	public int getArraySize() {
		return arraySize;
	}

	public void setType(VariableType type) {
		this.type = type;
	}

	public Proctype getOwner() {
		return owner;
	}

    public void setOwner(Proctype o) {
        this.owner = o;
    }

    public void setName(String name) {
        this.name = name;
    }

	public Expression getInitExpr() {
		return initExpr;
	}

	public boolean isStatic() {
		if (null == initExpr)
			return false;
		try {
			initExpr.getConstantValue();
		} catch (ParseException e) {
			return false;
		}
		return true;
	}

	public final boolean equals(Object o) {
		if (o == null)
			return false;
		if (!(o instanceof Variable))
			return false;
		Variable ov = (Variable)o;
		return this == ov || (name.equals(ov.name) &&
				(owner == ov.owner || owner != null && owner.equals(ov.owner))); 
	}

    public final int hashCode() {
        return name.hashCode() * 37 + (owner == null ? 0 : owner.hashCode()); 
    }

	public String getName() {
		return name;
	}

	public VariableType getType() {
        return type;
	}
	
	public void unsetInitExpr() {
		this.initExpr = null;
	}
	
	public void setInitExpr(final Expression initExpr) throws ParseException {
		if (!type.canConvert(initExpr.getResultType())) {
			throw new ParseException("Can not convert initializing expression to desired type for "+ this);
		}
		this.initExpr = initExpr;
	}

	/**
	 * Used for feedback to user, so we use oldName
	 */
	@Override
	public String toString() {
	    return owner == null ? getDisplayName() : owner.getName() +"."+ getDisplayName();
	}

	public void setDisplayName(String name2) {
		this.displayName = name2;
	}
	
	public String getDisplayName() {
		return (null == displayName ? name : displayName);
	}

	public Variable setAssignedTo() {
		assignedTo = true;
		return this;
	}
	public boolean isNotAssignedTo() {
		return !assignedTo;
	}

	public int getConstantValue() throws ParseException {
		if (assignedTo || getType() instanceof CustomVariableType || getType() instanceof ChannelType)
			throw new ParseException("Variable "+ this +" is not a constant in process "+ owner);
		if (initExpr == null) {
			return 0;
		} else {
			return initExpr.getConstantValue();
		}
	}

	public boolean isHidden() {
		return hidden;
	}

	public void setHidden(boolean hidden) {
		this.hidden = hidden;
	}

    public int getArrayIndex() {
        return arrayIndex;
    }

    public void setArrayIndex(int c) {
        arrayIndex = c;
    }
}

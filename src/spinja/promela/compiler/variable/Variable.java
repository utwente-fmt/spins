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

package spinja.promela.compiler.variable;

import spinja.promela.compiler.Proctype;
import spinja.promela.compiler.expression.Expression;
import spinja.promela.compiler.parser.ParseException;
import spinja.util.StringWriter;

public class Variable {

	private String name;

	private final int arraySize;

	private Expression initExpr;

	private VariableType type;

	private boolean isRead, isWritten;

	private Proctype owner;

	private String realName;

	private boolean assignedTo = false;

	private Integer constant = null;

	public Variable(final VariableType type, final String name, final int arraySize) {
		this(type,name,arraySize,null);
	}
	public Variable(final VariableType type, final String name, final int arraySize, Proctype owner) {
		this.name = name;
		this.arraySize = arraySize;
		this.type = type;
		this.owner = owner;
		isRead = false;
		isWritten = false;
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

	public Expression getInitExpr() {
		return initExpr;
	}
	
	public boolean equals(Object o) {
		if (o == null)
			return false;
		if (!(o instanceof Variable))
			return false;
		Variable ov = (Variable)o;
		return this == ov || (name.equals(ov.name) &&
				(owner == ov.owner || owner.equals(ov.owner))); 
	}

	public int hashCode() {
		return name.hashCode() * 37 + (owner == null ? 0 : owner.hashCode()); 
	}
	
	public void printInitExpr(final StringWriter w) throws ParseException {
		if (arraySize > 1) {
			w.appendLine(name, " = new ", type.getJavaName(), "[", arraySize, "];");
			if (initExpr != null) {
				w.appendLine("for(int i = 0; i < ", arraySize, "; i++) {");
				w.indent();
				w.appendLine(name, "[i] = ", initExpr.getIntExpression(), ";");
				w.outdent();
				w.appendLine("}");
			}
		} else if (initExpr != null) {
			w.appendLine(name, " = ", initExpr.getIntExpression(), ";");
		}
	}

	public String getName() {
		return name;
	}

	public VariableType getType() {
		return type;
	}

	public boolean isRead() {
		return isRead;
	}

	public boolean isWritten() {
		return isWritten;
	}

	public void unsetInitExpr() {
		this.initExpr = null;
	}
	
	public void setInitExpr(final Expression initExpr) throws ParseException {
		if (!type.canConvert(initExpr.getResultType())) {
			throw new ParseException("Can not convert initializing expression to desired type");
		}
		this.initExpr = initExpr;
	}

	public void setRead(final boolean isRead) {
		this.isRead = isRead;
	}

	public void setWritten(final boolean isWritten) {
		this.isWritten = isWritten;
	}

	@Override
	public String toString() {
		return owner == null ? getRealName() : owner.getName() +"."+ getRealName();
	}
	
	public void setOwner(Proctype owner) {
		this.owner = owner;
	}
	
	public void setName(String name) {
		this.name = name;
	}

	public void setRealName(String name2) {
		this.realName = name2;
	}
	
	public String getRealName() {
		return (null == realName ? name : realName);
	}

	public void setAssignedTo() {
		assignedTo = true;
	}
	public boolean isNotAssignedTo() {
		return !assignedTo;
	}

	public void setConstantValue(int constantValue) {
		constant  = constantValue;
	}
	
	public int getConstantValue() throws ParseException {
		if (constant == null)
			throw new ParseException("Variable "+ this +" is not a constant in process "+ owner);
		return constant;
	}
}

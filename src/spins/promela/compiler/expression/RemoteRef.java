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

import static spins.promela.compiler.ltsmin.util.LTSminUtil.compare;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.or;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.constant;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.id;
import static spins.promela.compiler.parser.PromelaConstants.EQ;

import java.util.HashSet;
import java.util.Set;

import spins.promela.compiler.ProcInstance;
import spins.promela.compiler.Proctype;
import spins.promela.compiler.automaton.State;
import spins.promela.compiler.ltsmin.model.LTSminModel;
import spins.promela.compiler.parser.ParseException;
import spins.promela.compiler.parser.Token;
import spins.promela.compiler.variable.Variable;
import spins.promela.compiler.variable.VariableAccess;
import spins.promela.compiler.variable.VariableType;

public class RemoteRef extends Expression {
	
	private final String processName;
	private Proctype process;
	private final String label;
	private final Expression expr;

	public RemoteRef(final Token id, final Token label, Expression e) {
		super(id);
		this.processName = id.image;
		this.label = label.image;
		this.expr = e;
	}

	public RemoteRef(Token t, Proctype process, String label, Expression expr) {
		super(t);
		this.setProcess(process);
		this.processName = process.getName();
		this.label = label;
		this.expr = expr;
	}

	@Override
	public String getIntExpression() throws ParseException {
		if (null == expr)
			return processName +"@"+ label;
		return processName +"["+ expr.getIntExpression() +"]@"+ label;
	}

	@Override
	public String toString() {
		if (null == expr)
			return processName +"@"+ label;
		return processName +"["+ expr.toString() +"]@"+ label;
	}

	public final boolean equals(Object o) {
	    if (!(o instanceof RemoteRef))
	        return false;
	    RemoteRef other = (RemoteRef)o;
	    return processName.equals(other.processName) &&
	           label.equals(other.label) && 
	           (expr == other.expr ||
	           (expr != null && other.expr != null && expr.equals(other.expr)));
	}

    public final int hashCode() {
        return processName.hashCode() * 37 +
               label.hashCode() * 13 +
               (expr == null ? 0 : expr.hashCode());
    }

	@Override
	public VariableType getResultType() {
		return VariableType.BOOL;
	}

	@Override
	public Set<VariableAccess> readVariables() {
		if (expr == null)
			return new HashSet<VariableAccess>();
		return expr.readVariables();
	}

	public Expression getExpr() {
		return expr;
	}

	public String getLabel() {
		return label;
	}

	public String getProcessName() {
		return processName;
	}

	public void setProcess(Proctype process) {
		this.process = process;
	}

	public Proctype getProcess() {
		return process;
	}

	private Variable pc = null;
	public Variable getPC(LTSminModel model) {
		if (null != pc)
			return pc;
		getInstance();
		pc = model.sv.getPC(instance);
		return pc;
	}

	ProcInstance instance = null;
	public ProcInstance getInstance() {
		if (null != instance)
			return instance;
		if (1 == process.getInstances().size()) {
			instance = process.getInstances().get(0);
		} else if (null != getExpr()) {
			int val;
			try {
				val = getExpr().getConstantValue();
				for (ProcInstance i : process.getInstances()) {
					if (i.getID() == val) instance = i;
				}
			} catch (ParseException e1) {}
		}
		if (null == instance) throw new AssertionError("Cannot statically determine instance for "+ this);
		return instance;
	}

	Expression labelExpr = null;
	public Expression getLabelExpression(LTSminModel model) {

        if (labelExpr != null)
            return labelExpr;

        getInstance();
        Variable pc = getPC(model);

	    
		for (State s : instance.getAutomaton()) {
			if (s.hasLabelPrefix(getLabel() +"_")) {
			    int num = s.getStateId();
			    Expression comp = compare(EQ, id(pc), constant(num));
			    if (labelExpr == null) {
			        labelExpr = comp;
			    } else {
			        labelExpr = or(labelExpr, comp);
			    }
			}
		}
		if (labelExpr == null)
			throw new AssertionError("Wrong label: "+ this +
								     " not found in proc "+ instance);
		return labelExpr;
	}
}

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

package spins.promela.compiler;

import static spins.promela.compiler.parser.Promela.C_STATE_PID;
import static spins.promela.compiler.parser.Promela.C_STATE_PROC_COUNTER;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import spins.promela.compiler.automaton.Automaton;
import spins.promela.compiler.automaton.State;
import spins.promela.compiler.expression.Expression;
import spins.promela.compiler.expression.Identifier;
import spins.promela.compiler.variable.Variable;
import spins.promela.compiler.variable.VariableContainer;
import spins.promela.compiler.variable.VariableStore;

/**
 * This object represents a process in the Promela language. It contains a number of 'local'
 * variables, of which the first couple are the arguments. Also it contains a starting Node which
 * points to a complete graph. This graph represents all the actions that can be executed by this
 * process.
 * 
 * @author Marc de Jonge
 */
public class Proctype implements VariableContainer {
	/**
	 * The specification to which this {@link Proctype} belongs
	 */
	protected final Specification specification;

	/**
	 * The ID number of this {@link Proctype}.
	 */
	protected final int id;

	/**
	 * The number of active processes that are started when the model checking begins
	 */
	protected int nrActive;

	/**
	 * The priority that is only used when ran randomly.
	 */
	protected int priority;

	/**
	 * The name of the process in the Model.
	 */
	protected final String name;

	/**
	 * The starting Node which points to the complete graph.
	 */
	protected final Automaton automaton;

	/**
	 * The store where all the variables are stored.
	 */
	protected final VariableStore varStore;
    
    public void addVariableMapping(String s, String v) {
    	varStore.addVariableMapping(s, v);
    }

    public String getVariableMapping(String s) {
    	return varStore.getVariableMapping(s);
    }

    public Map<String, String> getVariableMappings() {
    	return varStore.getVariableMappings();
    }

	/**
	 * The expression which can enable or disable all actions.
	 */
	private Expression enabler;

	/**
	 * While this boolean is true, each variable that is added to this {@link Proctype} is assumed
	 * to be an argument.
	 */
	private boolean isArgument;

	/**
	 * Here all the arguments are store. Please note that these are also stored in the
	 * VariableStore!
	 */
	private final List<Variable> arguments;

	private final List<Variable> channelXR = new ArrayList<Variable>();

	private final List<Variable> channelXS = new ArrayList<Variable>();

	/**
	 * Creates a new {@link Proctype} object.
	 * 
	 * @param specification
	 *            The specification in which this {@link Proctype} is defined.
	 * @param id
	 *            The identifying number of this {@link Proctype}
	 * @param nrActive
	 *            The number of processes that should be started when the model checking starts.
	 * @param name
	 *            The name of the {@link Proctype}.
	 */
	public Proctype(final Specification specification, final int id, final int nrActive,
		final String name) {
		this.specification = specification;
		this.id = id;
		this.nrActive = nrActive;
		this.name = name;
		automaton = new Automaton(this);
		varStore = new VariableStore();
		priority = 0;
		enabler = null;
		isArgument = true;
		arguments = new ArrayList<Variable>();
	}
	
	public boolean equals(Object o) {
        if (o == null || !(o instanceof Proctype))
            return false;
        Proctype p = (Proctype)o;
        return p.canEqual(this) && p.name.equals(name);
	}

    public boolean canEqual(Object other) {
        return (other instanceof Proctype);
    }

	public int hashCode() {
		return name.hashCode(); 
	}

	/**
	 * Adds a new variable to this {@link Proctype}. While the lastArgument() function is not
	 * called this function assumes that every variable that is added is also an argument.
	 * 
	 * @param var
	 *            The variable that is to be added.
	 */
	public void addVariable(final Variable var) {
		addVariable(var, this.isArgument);
	}

	public void addVariable(final Variable var, boolean isArgument) {
		varStore.addVariable(var);
		if (isArgument) {
			arguments.add(var);
		}
	}

	public void prependVariable(final Variable var) {
		varStore.prependVariable(var);
		if (isArgument) {
			arguments.add(var);
		}
	}

	/**
	 * Adds an identifier that points to a channel to the list of eXclusive Reads
	 * @param id
	 *            The identifier.
	 */
	public void addXR(final Identifier id) {
		channelXR.add(id.getVariable());
	}

	/**
	 * Adds an identifier that points to a channel to the list of eXclusive Sends
	 * @param id
	 *            The identifier.
	 */
	public void addXS(final Identifier id) {
		channelXS.add(id.getVariable());
	}

	/**
	 * @return The Automaton that describes the actions of this {@link Proctype}
	 */
	public Automaton getAutomaton() {
		return automaton;
	}

	/**
	 * @return The name of this {@link Proctype}.
	 */
	public String getName() {
		return name;
	}

	/* Here all the generating code is places */

	/**
	 * @return The number of active processes of this type that have to be started when the model
	 *         checking starts.
	 */
	public int getNrActive() {
		return nrActive;
	}

	/**
	 * @return The {@link Specification} to which this {@link Proctype} belongs.
	 */
	public Specification getSpecification() {
		return specification;
	}

	/**
	 * @return The starting node which points to the complete graph with all the options.
	 */
	public State getStartState() {
		return automaton.getStartState();
	}

	/**
	 * @see spins.promela.compiler.variable.VariableContainer#getVariable(java.lang.String)
	 */
	public Variable getVariable(final String name) {
		return varStore.getVariable(name);
	}

	/**
	 * @see spins.promela.compiler.variable.VariableContainer#getVariables()
	 */
	public List<Variable> getVariables() {
		return varStore.getVariables();
	}

	public Variable getPID() {
		return getVariable(C_STATE_PID);
	}

	public Variable getPC() {
		return getVariable(C_STATE_PROC_COUNTER);
	}

	/**
	 * @see spins.promela.compiler.variable.VariableContainer#hasVariable(java.lang.String)
	 */
	public boolean hasVariable(final String name) {
		return varStore.hasVariable(name);
	}

	/* Exclusive send and read functions */

	/**
	 * @param var
	 *            The Variable that has to be tested.
	 * @return True when the given variable is set to be exclusively read by this {@link Proctype}.
	 */
	public boolean isXR(final Variable var) {
		return channelXR.contains(var);
	}

	/**
	 * @param var
	 *            The Variable that has to be tested.
	 * @return True when the given variable is set to be exclusively send by this {@link Proctype}.
	 */
	public boolean isXS(final Variable var) {
		return channelXS.contains(var);
	}

	/**
	 * Indicates that all variables that are added from now on are no longer arguments of this
	 * {@link Proctype}.
	 */
	public void lastArgument() {
		isArgument = false;
	}

	public boolean isArgument() {
		return isArgument;
	}

	/**
	 * Changes the enabler expression of the process.
	 * 
	 * @param enabler
	 *            The enabler expression.
	 */
	public void setEnabler(final Expression enabler) {
		this.enabler = enabler;
	}

	public Expression getEnabler() {
		return enabler;
	}

	/**
	 * Changes the priority of the process.
	 * 
	 * @param priority
	 *            The new priority.
	 */
	public void setPriority(final int priority) {
		this.priority = priority;
	}

	/**
	 * Returns the name of the process.
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return getName();
	}

	public int getID() {
		return id;
	}

	public List<Variable> getArguments() {
		return arguments;
	}

	public void setNrActive(int nrActive) {
		this.nrActive = nrActive;
	}

	List<ProcInstance> instances = new ArrayList<ProcInstance>();

	public void addInstance(ProcInstance instance) {
		instances.add(instance);
	}

	public List<ProcInstance> getInstances() {
		return instances;
	}
}

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * A variable container is an object that can hold one or more variables 
 * (e.g. a specification or a proctype is Promela). This container can 
 * then be used to create the encode and decode functions easily.
 *
 * @author Marc de Jonge
 */
public class VariableStore implements VariableContainer {
    public static boolean canSkipVar(Variable var) {
        return !(var.isWritten() || var.getType() instanceof ChannelType);
    }

    private final List<Variable> vars;

    private final Map<String, String> mapping;
    
    public void addVariableMapping(String s, String v) {
    	mapping.put(s, v);
    }

    public String getVariableMapping(String s) {
    	return mapping.get(s);
    }

    public Map<String, String> getVariableMappings() {
    	return mapping;
    }

    /**
     * Creates a new VariableStore.
     */
    public VariableStore() {
        vars = new LinkedList<Variable>();
        mapping = new HashMap<String, String>();
    }

    /**
     * Adds the given variable to this store. The variable may not be null, 
     * otherwise an IllegalArgumentException will be thrown.
     *
     * @param var
     *            The variable that is to be added to this store.
     */
    public void addVariable(final Variable var) {
        if (var == null) {
            throw new IllegalArgumentException();
        }
        vars.add(var);
    }
    
    public void prependVariable(final Variable var) {
        if (var == null) {
            throw new IllegalArgumentException();
        }
        vars.add(0, var);
    }

    /**
     * Returns the variable that is defined by the name that is given.
     *
     * @param name
     *            The name that is used to find a variable.
     * @return The variable that is defined by the name that is given or null if there was no such
     *         variable accesable.
     */
    public Variable getVariable(final String name) {
        for (final Variable var : vars) {
            if (var.getName().equals(name)) {
                return var;
            }
        }
    	String to = getVariableMapping(name);
    	if (null == to) return null;
        return getVariable(to);
    }

    /**
     * @return A new list with all the variables that are stored here.
     */
    public List<Variable> getVariables() {
        return new ArrayList<Variable>(vars);
    }

    /**
     * Checks if a variable with the given name exists in the current store.
     *
     * @param name
     *            The name that is to be checked.
     * @return True when there was already any variable with that name, otherwise false.
     */
    public boolean hasVariable(final String name) {
        for (final Variable var : vars) {
            if (var.getName().equals(name)) {
                return true;
            }
        }
        return null != getVariableMapping(name);
    }
}

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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import spins.promela.compiler.expression.RemoteRef;
import spins.promela.compiler.expression.RunExpression;
import spins.promela.compiler.ltsmin.model.ReadAction;
import spins.promela.compiler.ltsmin.model.SendAction;
import spins.promela.compiler.ltsmin.util.LTSminUtil;
import spins.promela.compiler.parser.ParseException;
import spins.promela.compiler.variable.CustomVariableType;
import spins.promela.compiler.variable.Variable;
import spins.promela.compiler.variable.VariableStore;
import spins.promela.compiler.variable.VariableType;

public class Specification implements Iterable<ProcInstance> {
	private final String name;

    public static final String C_STATE_NR_PR = "_nr_pr";
    public static final Variable _NR_PR = new Variable(VariableType.INT, C_STATE_NR_PR, -1);

	private final List<Proctype> procs;

	private final Map<String, CustomVariableType> userTypes;

	private Proctype never;

	private final VariableStore varStore;

	public List<RunExpression> runs = new ArrayList<RunExpression>();

	private final List<String> mtypes;

	private HashMap<Variable, List<ReadAction>> channelReads;
    private HashMap<Variable, List<SendAction>> channelWrites;

	public Specification(final String name) {
		this.name = name;
		procs = new ArrayList<Proctype>();
		userTypes = new HashMap<String, CustomVariableType>();
		varStore = new VariableStore();
		channelReads = new HashMap<Variable, List<ReadAction>>();
		channelWrites = new HashMap<Variable, List<SendAction>>();
		Variable hidden = new Variable(VariableType.INT, "_", -1);
		hidden.setHidden(true);
		varStore.addVariable(hidden);
	    varStore.addVariable(_NR_PR);
		mtypes = new ArrayList<String>();
	}

	public List<String> getMTypes() {
		return mtypes;
	}

	public String getName() {
		return name;
	}

	public void clearReadActions() {
		channelReads.clear();
	}

    public void clearWriteActions() {
        channelWrites.clear();
    }

	public void addReadAction(ReadAction cr) {
		if (!LTSminUtil.isRendezVousReadAction(cr.cra)) return;

		Variable cv = cr.cra.getIdentifier().getVariable();
		List<ReadAction> raw = channelReads.get(cv);
		if (raw == null) {
			raw = new ArrayList<ReadAction>();
			channelReads.put(cv, raw);
		}
		raw.add(cr);
	}

    public void addWriteAction(SendAction cs) {
        if (!LTSminUtil.isRendezVousSendAction(cs.csa)) return;

        Variable cv = cs.csa.getIdentifier().getVariable();
        List<SendAction> raw = channelWrites.get(cv);
        if (raw == null) {
            raw = new ArrayList<SendAction>();
            channelWrites.put(cv, raw);
        }
    }

	/**
	 * Creates a new Custom type for in this Specification.
	 * 
	 * @param bufferSize
	 * @return The new ChannelType
	 */
	public CustomVariableType newCustomType(String name) throws ParseException {
		if (userTypes.containsKey(name))
			throw new ParseException("Duplicate type declaration with name: "+ name);
		CustomVariableType type = new CustomVariableType(name);
		userTypes.put(name, type);
		return type;
	}

	public void addMType(final String name) {
		mtypes.add(name);
	}

	public void addProc(final Proctype proc) throws ParseException {
		if (getProcess(proc.getName()) != null) {
			throw new ParseException("Duplicate proctype with name: " + proc.getName());
		}
		procs.add(proc);
	}

	/**
	 * Returns mtype constant for an identifier. If there is no corresponding
	 * MType, 0 is returned
	 * @param name, the name of the identifier
	 * @return 0 or the number of the MType
	 */
	public int getMType(final String name) {
		int index = mtypes.indexOf(name);
		if (-1 == index) return 0;
		return mtypes.size() - index; // SPIN does reverse numbering of mtypes
	}

	public Proctype getNever() {
		return never;
	}

	public Proctype getProcess(final String name) {
		for (final Proctype proc : procs) {
			if (proc.getName().equals(name)) {
				return proc;
			}
		}
		return null;
	}

	public CustomVariableType getCustomType(final String name) throws ParseException {
		if (userTypes.containsKey(name)) {
			return userTypes.get(name);
		} else {
			throw new ParseException("Could not find a type with name: " + name);
		}
	}

	public Collection<CustomVariableType> getUserTypes() {
		return userTypes.values();
	}

	public VariableStore getVariableStore() {
		return varStore;
	}

	private List<ProcInstance> instances = null;

    public Set<RemoteRef> remoteRefs = new HashSet<RemoteRef>();
	
	public Iterator<ProcInstance> iterator() {
		if (null == instances)
			throw new AssertionError("Processes were not instantiated");
		return instances.iterator();
	}

	public void setNever(final Proctype never) {
		this.never = never;
	}

	public void setInstances(List<ProcInstance> instances) {
		this.instances = instances;
	}

    public int instances() {
        return this.instances.size();
    }
	
	public List<Proctype> getProcs() {
		return procs;
	}

	public List<ReadAction> getReadActions(Variable cv) {
		return channelReads.get(cv);
	}

    public List<SendAction> getWriteActions(Variable cv) {
        return channelWrites.get(cv);
    }
}

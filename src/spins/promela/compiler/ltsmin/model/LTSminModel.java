package spins.promela.compiler.ltsmin.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import spins.promela.compiler.Specification;
import spins.promela.compiler.actions.Action;
import spins.promela.compiler.expression.Expression;
import spins.promela.compiler.ltsmin.matrix.LTSminGuard;
import spins.promela.compiler.ltsmin.matrix.RWMatrix;
import spins.promela.compiler.ltsmin.model.LTSminModelFeature.ModelFeature;
import spins.promela.compiler.ltsmin.state.LTSminSlot;
import spins.promela.compiler.ltsmin.state.LTSminStateVector;
import spins.promela.compiler.ltsmin.util.LTSminUtil.Pair;
import spins.promela.compiler.variable.Variable;
import spins.promela.compiler.variable.VariableType;
import spins.util.IndexedSet;

/**
 * An LTSmin model consists is derived from a SpinJa Specification and
 * encapsulates transitions (which are transition groups), a state vector
 * consisting physically of slots and dependency information. 
 * 
 * Transitions of the model are mapped to transition groups (LTSminTransition)
 * with guard and action expressions.
 * Variables of the model are mapped to state vector slots.
 * The dependency metric records the dependencies between transition groups
 * state vector slots, where the action can be write dependencies and the guards
 * represent only read dependencies.
 * Finally, the guard info class stores dependencies amongst guards and between
 * guards and transitions that are needed for partial-order reduction. 
 * 
 * @see LTSminStateVector
 * 
 * @author Freark van der Berg, Alfons Laarman
 */
public class LTSminModel implements Iterable<LTSminTransition> {

    private String name;
    private RWMatrix depMatrix;
    private RWMatrix atomicDepMatrix;
    private RWMatrix actionDepMatrix;
    private GuardInfo guardInfo;
    private List<String> mtypes;
    public boolean hasAtomicCycles = false;

    // local variables
	public final Variable index = new Variable(VariableType.INT, "i", -1);
	public final Variable jndex = new Variable(VariableType.INT, "j", -1);
    private List<Variable> locals = Arrays.asList(index, jndex);

    // state vector
    public LTSminStateVector sv;

    // next-state assertions
    public List<Pair<Expression,String>> assertions = new LinkedList<Pair<Expression,String>>();

    // states and transitions
    private List<LTSminTransition> transitions = new ArrayList<LTSminTransition>();
	private Map<LTSminState, LTSminState> states = new HashMap<LTSminState, LTSminState>();

	// types
    private IndexedSet<String> types = new IndexedSet<String>();
    private List<IndexedSet<String>> values = new ArrayList<IndexedSet<String>>();

    // edge labels
    private IndexedSet<String> edgeLabels = new IndexedSet<String>();
    private List<String> edgeTypes = new ArrayList<String>();

    // state labels
    private Map<String, LTSminGuard> stateLabels = new HashMap<String, LTSminGuard>();

	public LTSminModel(String name, LTSminStateVector sv, Specification spec) {
		this.name = name;
		mtypes = spec.getMTypes();
		this.sv = sv;
	}

	public List<Variable> getLocals() {
		return locals;
	}

	public List<String> getMTypes() {
		return mtypes;
	}

	public String getName() {
		return name;
	}

	public RWMatrix getDepMatrix() {
		return depMatrix;
	}

	public void setDepMatrix(RWMatrix depMatrix) {
		this.depMatrix = depMatrix;
	}

    public RWMatrix getActionDepMatrix() {
        return actionDepMatrix;
    }

    public void setActionDepMatrix(RWMatrix depMatrix) {
        this.actionDepMatrix = depMatrix;
    }

    public RWMatrix getAtomicDepMatrix() {
        return atomicDepMatrix;
    }

    public void setAtomicDepMatrix(RWMatrix depMatrix) {
        this.atomicDepMatrix = depMatrix;
    }

	public GuardInfo getGuardInfo() {
		return guardInfo;
	}

	public void setGuardInfo(GuardInfo guardMatrix) {
		this.guardInfo = guardMatrix;
	}

	public boolean hasAtomic() {
		for (LTSminTransition t : this)
			if (t.isAtomic()) return true;
		return false;
	}

	public LTSminState getOrAddState(LTSminState state) {
		LTSminState begin = states.get(state);
		if (null != begin) {
			return begin;
		} else {
			states.put(state, state);
			return state;
		}
	}

	public List<LTSminTransition> getTransitions() {
		return transitions;
	}

	public Iterator<LTSminTransition> iterator() {
		return transitions.iterator();
	}

	public void addTransition(LTSminTransition lt) {
	    lt.setGroup(transitions.size());
		transitions.add(lt);
	}

    public void addType(String string) {
        if (types.get(string) != null) throw new AssertionError("Adding type twice: "+ string);
        types.add(string);
        values.add(new IndexedSet<String>());
    }

    public void addTypeValue(String string, String value) {
        getType(string).add(value);
    }

    public int getTypeIndex(String string) {
        return types.get(string);
    }
    
    public IndexedSet<String> getType(String string) {
        Integer index = types.get(string);
        if (index == null) return null;
        return values.get(index);
    }

    public String getType(int index) {
        return types.getIndex(index);
    }

    public IndexedSet<String> getTypeValues(int index) {
        return values.get(index);
    }

    public int getTypeValueIndex(String type, String string) {
        Integer typeno = types.get(type);
        IndexedSet<String> values = this.values.get(typeno);
        return values.get(string);
    }

    public IndexedSet<String> getTypes() {
        return types;
    }

    public List<IndexedSet<String>> getTypeValues() {
        return values;
    }

    public void addEdgeLabel(String name, String type) {
        edgeLabels.add(name);
        edgeTypes.add(type);
    }

    public IndexedSet<String> getEdges() {
        return edgeLabels;
    }

    public String getEdgeType(String string) {
        int index = edgeLabels.get(string);
        return edgeTypes.get(index);
    }

    public String getEdgeType(int index) {
        return edgeTypes.get(index);
    }
    
    public void addStateLabel(String string, LTSminGuard g) {
        stateLabels.put(string, g);
    }
    
    public Set<Entry<String, LTSminGuard>> getLabels() {
       return stateLabels.entrySet();
    }

    public void addTypeValue(String name2, String string, int i) {
        if (i != getType(name2).size()) throw new AssertionError("Out of order adding type value "+ name2 +"."+ string +" at "+ i);
        addTypeValue(name2, string);
    }

    public int getEdgeIndex(String edge) {
        return edgeLabels.get(edge);
    }

    private List<Action> actions = null;

    public List<Action> getActions() {
        if (actions == null) {
            // Number actions (filters duplicates and establishes count)
            int nActions = 0;
            actions = new ArrayList<Action>();
            for(LTSminTransition t : getTransitions()) {
                for (Action a : t.getActions()) {
                    if (a.getIndex() == -1) {
                        a.setIndex(nActions++);
                        actions.add(a);
                    }
                }
            }
        }
        return actions;
    }

    public final ModelFeature<LTSminGuard> GUARDS =
            new ModelFeature<LTSminGuard>("Guard") {
                public List<LTSminGuard> getInstances() {
                    return guardInfo.getLabels();
                }
            };

    public final ModelFeature<LTSminTransition> TRANSITIONS =
            new ModelFeature<LTSminTransition>("Transition") {
                public List<LTSminTransition> getInstances() {
                    return getTransitions();
                }
            };

    public final ModelFeature<LTSminSlot> SLOTS =
            new ModelFeature<LTSminSlot>("Slot") {
                public List<LTSminSlot> getInstances() {
                    return sv.getSlots();
                }
            };

    public final ModelFeature<Action> ACTIONS =
            new ModelFeature<Action>("Action") {
                public List<Action> getInstances() {
                    return getActions();
                }
            };
}

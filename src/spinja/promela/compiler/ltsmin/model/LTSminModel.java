package spinja.promela.compiler.ltsmin.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static spinja.promela.compiler.ltsmin.model.LTSminUtil.*;

import spinja.promela.compiler.Proctype;
import spinja.promela.compiler.Specification;
import spinja.promela.compiler.automaton.State;
import spinja.promela.compiler.ltsmin.matrix.DepMatrix;
import spinja.promela.compiler.ltsmin.matrix.GuardInfo;
import spinja.promela.compiler.ltsmin.matrix.LTSminGuardOr;
import spinja.promela.compiler.ltsmin.state.LTSminStateVector;
import spinja.promela.compiler.variable.Variable;
import spinja.promela.compiler.variable.VariableType;

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
	private List<LTSminTransition> transitions;
	private LTSminGuardOr accepting_conditions;
	public LTSminStateVector sv;
	private DepMatrix depMatrix;
	private GuardInfo guardInfo;
	private List<String> mtypes;
	public final Variable index = new Variable(VariableType.INT, "i", -1);
	private List<Variable> locals = Arrays.asList(index);

	public LTSminModel(String name, LTSminStateVector sv, Specification spec) {
		this.name = name;
		mtypes = spec.getMTypes();
		this.transitions = new ArrayList<LTSminTransition>();
		this.sv = sv;
		this.accepting_conditions = new LTSminGuardOr();
		Proctype never = spec.getNever();
		if (null != never) {
			 for (State s : never.getAutomaton()) {
				 if (s.isAcceptState()) {
					 accepting_conditions.addGuard(pcGuard(this, s, never));
				 }
			 }
		} else {
			accepting_conditions.addGuard(constant(0));
		}
	}

	public List<Variable> getLocals() {
		return locals;
	}

	public List<String> getMTypes() {
		return mtypes;
	}

	public LTSminGuardOr getAcceptingConditions() {
		return accepting_conditions;
	}

	public String getName() {
		return name;
	}

	public DepMatrix getDepMatrix() {
		return depMatrix;
	}

	public void setDepMatrix(DepMatrix depMatrix) {
		this.depMatrix = depMatrix;
	}

	public GuardInfo getGuardInfo() {
		return guardInfo;
	}

	public void setGuardInfo(GuardInfo guardMatrix) {
		this.guardInfo = guardMatrix;
	}

	public List<LTSminTransition> getTransitions() {
		return transitions;
	}

	public void setTransitions(List<LTSminTransition> transitions) {
		this.transitions = transitions;
	}

	@Override
	public Iterator<LTSminTransition> iterator() {
		return transitions.iterator();
	}

	public boolean hasAtomic() {
		for (LTSminTransition t : this)
			if (t.isAtomic()) return true;
		return false;
	}
}

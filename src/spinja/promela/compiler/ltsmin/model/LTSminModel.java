package spinja.promela.compiler.ltsmin.model;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import spinja.promela.compiler.ltsmin.matrix.DepMatrix;
import spinja.promela.compiler.ltsmin.matrix.GuardInfo;
import spinja.promela.compiler.ltsmin.state.LTSminStateVector;

/**
 * An LTSmin model consists is dirived from a SpinJa Specification and
 * encapsulates transitions (which are transition groups), a state vector
 * consisting physically of slots and depenency information. 
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
	public LTSminStateVector sv;
	private DepMatrix depMatrix;
	private GuardInfo guardInfo;

	public LTSminModel(String name, LTSminStateVector sv) {
		this.name = name;
		this.transitions = new ArrayList<LTSminTransition>();
		this.sv = sv;
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
}

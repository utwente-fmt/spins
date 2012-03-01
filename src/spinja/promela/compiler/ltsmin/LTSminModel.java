package spinja.promela.compiler.ltsmin;

import java.util.ArrayList;
import java.util.List;

import spinja.promela.compiler.ltsmin.instr.DepMatrix;
import spinja.promela.compiler.ltsmin.instr.GuardInfo;
import spinja.util.StringWriter;

/**
 *
 * @author Freark van der Berg
 */
public class LTSminModel {

	private String name;
	private List<LTSminTransitionBase> transitions;
	public LTSminStateVector sv;

	DepMatrix depMatrix;
	GuardInfo guardInfo;

	StringWriter header;
	StringWriter structs;
	StringWriter model;

	public LTSminModel(String name, LTSminStateVector sv) {
		this.name = name;
		this.transitions = new ArrayList<LTSminTransitionBase>();
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

	public List<LTSminTransitionBase> getTransitions() {
		return transitions;
	}

	public void setTransitions(List<LTSminTransitionBase> transitions) {
		this.transitions = transitions;
	}
}

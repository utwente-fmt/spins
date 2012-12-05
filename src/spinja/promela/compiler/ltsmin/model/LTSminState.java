package spinja.promela.compiler.ltsmin.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import spinja.promela.compiler.Proctype;
import spinja.promela.compiler.automaton.State;

public class LTSminState {

	private State state;
	private State never;
	private State sync;
	private Set<LTSminTransition> transitions = new HashSet<LTSminTransition>();
	
	public LTSminState(State state, State never) {
		this.state = state;
		this.never = never;
		this.sync = null;
		in = new ArrayList<LTSminTransition>();
		out = new ArrayList<LTSminTransition>();
	}

	public boolean equals(Object o) {
		if (!(o instanceof LTSminState)) return false;
		LTSminState other = (LTSminState)o;
		return 
		(state == other.state || (null != state && state.equals(other.state))) &&
		(never == other.never || (null != never && never.equals(other.never))) &&
		(sync == other.sync || (null != sync && sync.equals(other.sync)));
	}

	public void addTransition(LTSminTransition t) {
		transitions.add(t);
	}

	public Set<LTSminTransition> getTransitions() {
		return transitions;
	}

	public int hashCode() {
		return ((null != state ? state.hashCode() : 0) * 7621 + 
				(null != never ? never.hashCode() : 0)) * 37 + 
				(null != sync ? sync.hashCode() : 0);
	}

	public State getSync() {
		return sync;
	}

	public void setSync(State sync) {
		this.sync = sync;
	}

	public boolean isAtomic() {
		return state != null && state.isInAtomic();
	}

	public void addTransitions(Set<LTSminTransition> ts) {
		transitions.addAll(ts);
	}

	public Proctype getProc() {
		if (null == state) return null;
 		return state.getAutomaton().getProctype();
	}

	private final List<LTSminTransition> out, in;
	public void addOut(LTSminTransition t) {
		out.add(t);
	}

	public void addIn(LTSminTransition t) {
		in.add(t);
	}

	public List<LTSminTransition> getOut() {
		return out;
	}

    public boolean isProgress() {
        return state != null && state.isProgressState() ||
                sync != null &&  sync.isProgressState() ||
               never != null && never.isProgressState();
    }
}

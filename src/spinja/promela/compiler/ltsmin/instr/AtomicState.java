/**
 * 
 */
package spinja.promela.compiler.ltsmin.instr;

import spinja.promela.compiler.Proctype;
import spinja.promela.compiler.automaton.State;

public class AtomicState {
	public State s;
	public Proctype p;

	public AtomicState(State s, Proctype p) {
		this.s = s;
		this.p = p;
	}
}
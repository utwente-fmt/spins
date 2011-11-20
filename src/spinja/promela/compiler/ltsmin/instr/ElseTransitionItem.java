/**
 * 
 */
package spinja.promela.compiler.ltsmin.instr;

import spinja.promela.compiler.Proctype;
import spinja.promela.compiler.automaton.ElseTransition;

public class ElseTransitionItem {
	public int trans;
	public ElseTransition t;
	public Proctype p;

	public ElseTransitionItem(int trans, ElseTransition t, Proctype p) {
		this.trans = trans;
		this.t = t;
		this.p = p;
	}

}
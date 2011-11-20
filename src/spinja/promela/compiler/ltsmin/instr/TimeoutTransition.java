/**
 * 
 */
package spinja.promela.compiler.ltsmin.instr;

import spinja.promela.compiler.Proctype;
import spinja.promela.compiler.ltsmin.LTSminTransition;

public class TimeoutTransition {
	public int trans;
	public Proctype p;
	public LTSminTransition lt;

	public TimeoutTransition(int trans, Proctype p, LTSminTransition lt) {
		this.trans = trans;
		this.p = p;
		this.lt = lt;
	}

}
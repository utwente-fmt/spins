package spinja.promela.compiler.ltsmin.model;

import spinja.promela.compiler.ProcInstance;
import spinja.promela.compiler.actions.ChannelReadAction;
import spinja.promela.compiler.automaton.Transition;

/**
 * A class containing three variables.
 * It is used to describe a channel read operation.
 */
public class ReadAction {
	/// The channel read action.
	public ChannelReadAction cra;

	/// The transition the channel read action is in.
	public Transition t;

	/// The position the channel read action is in.
	public ProcInstance p;

	/**
	 * Create a new ReadAction using the specified variables.
	 */
	public ReadAction(ChannelReadAction cra, Transition t, ProcInstance p) {
		this.cra = cra;
		this.t = t;
		this.p = p;
	}

}
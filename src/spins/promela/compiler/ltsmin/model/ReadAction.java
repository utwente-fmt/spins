package spins.promela.compiler.ltsmin.model;

import spins.promela.compiler.Proctype;
import spins.promela.compiler.actions.ChannelReadAction;
import spins.promela.compiler.automaton.Transition;

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
	public Proctype p;

	/**
	 * Create a new ReadAction using the specified variables.
	 */
	public ReadAction(ChannelReadAction cra, Transition t, Proctype p) {
		this.cra = cra;
		this.t = t;
		this.p = p;
	}

}
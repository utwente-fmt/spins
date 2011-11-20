package spinja.promela.compiler.ltsmin.instr;

import spinja.promela.compiler.Proctype;
import spinja.promela.compiler.actions.ChannelSendAction;
import spinja.promela.compiler.automaton.Transition;

/**
 * A class containing three variables.
 * It is used to describe a channel send operation.
 */
public class SendAction {

	/// The channel send action.
	public ChannelSendAction csa;

	/// The transition the channel send action is in.
	public Transition t;

	/// The position the channel send action is in.
	public Proctype p;

	/**
	 * Create a new SendAction using the specified variables.
	 */
	public SendAction(ChannelSendAction csa, Transition t, Proctype p) {
		this.csa = csa;
		this.t = t;
		this.p = p;
	}

}
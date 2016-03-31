// Copyright 2010, University of Twente, Formal Methods and Tools group
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package spins.promela.compiler.automaton;


/**
 * @author Marc de Jonge
 */
public class UselessTransition extends Transition {
	private final String text;

	/**
	 * Constructor of UselessTransition.
	 * 
	 * @param from
	 *            The from state
	 * @param to
	 *            The to state
	 * @param text
	 *            The text for this useless transition
	 */
	public UselessTransition(Transition t, final State from, final State to, final String text) {
		super(t, from, to);
		this.text = text;
	}
	public UselessTransition(final State from, final State to, final String text) {
		super(from, to);
		this.text = text;
	}

	/**
	 * @see spins.promela.compiler.automaton.Transition#duplicateFrom(State)
	 */
	@Override
	public Transition duplicateFrom(State from) {
		final Transition t = new UselessTransition(this, from, getTo(), text);
		return t;
	}

	/**
	 * @see spins.promela.compiler.automaton.Transition#isLocal()
	 */
	@Override
	public boolean isLocal() {
		return true;
	}

	/**
	 * @see spins.promela.compiler.automaton.Transition#isUseless()
	 */
	@Override
	public boolean isUseless() {
		return true;
	}

	/**
	 * @see spins.promela.compiler.automaton.Transition#getText()
	 */
	@Override
	public String getText() {
		return text;
	}
}

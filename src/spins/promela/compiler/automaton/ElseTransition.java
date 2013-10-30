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


public class ElseTransition extends Transition {

	/**
	 * Constructor of ElseTransition using the given states.
	 * 
	 * @param from
	 *            The starting state.
	 * @param to
	 *            The destination state.
	 */
	public ElseTransition(final State from, final State to) {
		super(from, to);
	}

	/**
	 * @see spins.promela.compiler.automaton.Transition#duplicateFrom(State)
	 */
	@Override
	public Transition duplicateFrom(State from) {
		final Transition t = new ElseTransition(from, getTo());
		return t;
	}

	/**
	 * @see spins.promela.compiler.automaton.Transition#isLocal()
	 */
	@Override
	public boolean isLocal() {
		return false;
	}

	/**
	 * @see spins.promela.compiler.automaton.Transition#isUseless()
	 */
	@Override
	public boolean isUseless() {
		return false;
	}

	/**
	 * @see spins.promela.compiler.automaton.Transition#getText()
	 */
	@Override
	public String getText() {
		return "else";
	}
}

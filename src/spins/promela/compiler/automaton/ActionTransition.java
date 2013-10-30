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

import java.util.Iterator;

import spins.promela.compiler.actions.Action;
import spins.promela.compiler.actions.ExprAction;
import spins.promela.compiler.actions.Sequence;
import spins.promela.compiler.expression.Expression;
import spins.promela.compiler.parser.ParseException;
import spins.util.StringWriter;

/**
 * @author Marc de Jonge
 */
public class ActionTransition extends Transition {
	private final Sequence sequence;

	/**
	 * Constructor of ActionTransition.
	 * 
	 * @param from
	 * @param to
	 */
	public ActionTransition(final State from, final State to) {
		super(from, to);
		sequence = new Sequence();
	}

	/**
	 * @see spins.promela.compiler.automaton.Transition#addAction(spins.promela.compiler.actions.Action)
	 */
	@Override
	public void addAction(final Action action) {
		sequence.addAction(action);
	}

	/**
	 * @see spins.promela.compiler.automaton.Transition#duplicateFrom(State)
	 */
	@Override
	public Transition duplicateFrom(State from) {
		final ActionTransition t = new ActionTransition(from, getTo());
		t.sequence.add(sequence);
		return t;
	}

	/**
	 * @see spins.promela.compiler.automaton.Transition#getActionCount()
	 */
	@Override
	public int getActionCount() {
		return sequence.getNrActions();
	}

	/**
	 * @see spins.promela.compiler.automaton.Transition#getText()
	 */
	@Override
	public String getText() {
		final StringWriter w = new StringWriter();
		boolean first = true;
		for (final Action a : this) {
			w.appendIf(!first, "; ").append(a.toString());
			first = false;
		}
		return w.toString();
	}

	/**
	 * @see spins.promela.compiler.automaton.Transition#isLocal()
	 */
	@Override
	public boolean isLocal() {
		if (takesAtomicToken() || (getFrom() != null && getFrom().isInAtomic())) {
			return false;
		}
		for (final Action a : sequence) {
			if (!a.isLocal(getFrom().getAutomaton().getProctype())) {
				return false;
			}
		}
		return true;
	}

	/**
	 * @see spins.promela.compiler.automaton.Transition#isUseless()
	 */
	@Override
	public boolean isUseless() {
		return false;
	}

	/**
	 * @see spins.promela.compiler.automaton.Transition#getAction(int)
	 */
	@Override
	public Action getAction(int index) {
		return sequence.getAction(index);
	}

	/**
	 * @see spins.promela.compiler.automaton.Transition#iterator()
	 */
	@Override
	public Iterator<Action> iterator() {
		return sequence.iterator();
	}

	@Override
	public boolean isAlwaysEnabled() {
		if (sequence.getNrActions() >= 1) {
			try {
				Action action = sequence.getAction(0);

				Expression expr = null;
				if (action instanceof ExprAction) {
					expr = ((ExprAction) action).getExpression();
				}
				if (expr != null && expr.getSideEffect() != null) {
					return false;
				}

				return action.getEnabledExpression() == null;
			} catch (ParseException e) {
				return false;
			}
		} else {
			return true;
		}
	}
}
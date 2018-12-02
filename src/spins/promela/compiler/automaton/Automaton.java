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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import spins.promela.compiler.Proctype;
import spins.util.StringWriter;

/**
 * An Automaton is a simple LTS that holds {@link State}s and {@link Transition}s. It can be used
 * to generate the Transition table for each {@link Proctype}.
 * 
 * @author Marc de Jonge
 */
public class Automaton implements Iterable<State> {
	private State startState;

	private final Proctype proctype;

	/**
	 * Constructor of Automaton for a specified {@link Proctype}.
	 * 
	 * @param proctype
	 *            The {@link Proctype} to which this {@link Automaton} belongs.
	 */
	public Automaton(final Proctype proctype) {
		this.proctype = proctype;
		startState = new State(this, false);
	}

	/**
	 * @return The {@link Proctype} to which this {@link Automaton} belongs.
	 */
	public Proctype getProctype() {
		return proctype;
	}

	/**
	 * @return The current starting state of this {@link Automaton}.
	 */
	public State getStartState() {
		return startState;
	}

	/**
	 * Sets the new starting state to the given state.
	 * @param startState
	 *            The new starting state.
	 */
	public void setStartState(State startState) {
		if (startState.getAutomaton() != this) {
			throw new IllegalArgumentException("The state must belong to this automaton!");
		}
		this.startState = startState;
	}

	/**
	 * @return True when one of the states that is held by this {@link Automaton} uses the atomic
	 *         token.
	 */
	public boolean hasAtomic() {
		for (final State s : this) {
			if (s.isInAtomic()) {
				return true;
			}
		}
		return false;
	}

	private class Explorer implements Iterator<State> {

        private Iterator<State> it;

        private State init;
        private State end;

        public Explorer() {
            this(startState, null);
        }

        public Explorer(State startState, State end) {
            init = startState;
            this.end = end;
            init();
        }

        public boolean hasNext() { return it.hasNext(); }
        public State next()      { return it.next(); }

        public void init() {
            List<State> list = new ArrayList<State>();
            Stack<State> stack = new Stack<State>();
            Set<State> states = new HashSet<State>(Collections.singleton(startState));

            stack.push(init);
            while (!stack.isEmpty()) {
                State next = stack.pop();
                list.add(next);
                for (final Transition out : next.output) {
                    State to = out.getTo();
                    if (to != end && to != null && states.add(to)) {
                        stack.push(to);
                    }
                }
            }

            states = null;
            stack = null;
            it = list.listIterator();
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    };
	
	/**
	 * Return a new Iterator that can be used to go over all States.
	 * 
	 * @see java.lang.Iterable#iterator()
	 */
	public Iterator<State> iterator() {
		return new Explorer();
	}
	
	public void addUnless(final State start, final State end, State escape,
						  int priority) {
	    Iterable<State> main = new Iterable<State>() {
	        public Iterator<State> iterator() {
	            return new Explorer(start, end);
	        }
	    };

	    for (State s : main) {
	    	for (Transition t : escape.output) {
	    		Transition n = t.duplicateFrom(s);
	    		n.setUnlessPriority(priority);
	    	}
	    	s.nextUnless();
	    }
    	escape.delete();
	}

	public void finalize() {
	    for (State s : this) {
	    	s.finalize();
	    }
	}

	/**
	 * @return The number of states that are reachable from the current starting state.
	 */
	public int size() {
		int cnt = 0;
		for (@SuppressWarnings("unused")
		final State state : this) {
			cnt++;
		}
		return cnt;
	}

	/**
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		final StringWriter w = new StringWriter();
		w.appendLine("Graph for proctype ", proctype);
		w.indent();
		for (final State state : this) {
			w.appendLine(state);
		}
		return w.toString();
	}
}

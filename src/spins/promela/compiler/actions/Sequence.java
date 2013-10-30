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

package spins.promela.compiler.actions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import spins.promela.compiler.Proctype;
import spins.promela.compiler.parser.ParseException;
import spins.promela.compiler.parser.Token;

public class Sequence extends Action implements ActionContainer {
	private final List<Action> actions;

	public Sequence(Token t) {
		super(t);
		actions = new ArrayList<Action>();
	}

	public Sequence() {
		super(null);
		actions = new ArrayList<Action>();
	}

	/**
	 * @see spins.promela.compiler.actions.Action#getToken()
	 */
	@Override
	public Token getToken() {
		if (actions.isEmpty()) {
			return null;
		} else {
			return actions.get(0).getToken();
		}
	}

	public boolean startsWithElse() {
		return !actions.isEmpty() && actions.get(0) instanceof ElseAction;
	}

	public void addAction(Action sub) {
		actions.add(sub);
	}

	public Iterator<Action> iterator() {
		return Collections.unmodifiableList(actions).iterator();
	}

	public int getNrActions() {
		return actions.size();
	}
	
	@Override
	public boolean isLocal(Proctype proc) {
		for(Action action : actions) {
			if(!action.isLocal(proc)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public String getEnabledExpression() throws ParseException {
		if (actions.isEmpty()) {
			return null;
		} else {
			return actions.get(0).getEnabledExpression();
		}
	}

	public Action getAction(int index) {
		return actions.get(index);
	}

	@Override
	public String toString() {
		if (actions.size() == 1) {
			return actions.get(0).toString();
		} else {
			return "d_step";
		}
	}

    public List<Action> getActions() {
        return actions;
    }
}

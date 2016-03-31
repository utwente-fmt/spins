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

import spins.promela.compiler.Proctype;
import spins.promela.compiler.ltsmin.model.LTSminModelFeature;
import spins.promela.compiler.ltsmin.model.LTSminTransition;
import spins.promela.compiler.parser.ParseException;
import spins.promela.compiler.parser.Token;

public abstract class Action implements LTSminModelFeature  {

	private final Token token;

	// private Transition transition;

	public Action(final Token token) {
		this.token = token;
	}

	public abstract String getEnabledExpression() throws ParseException;

	public Token getToken() {
		return token;
	}

	public boolean isLocal(final Proctype proc) {
		return false;
	}

	@Override
	public abstract String toString();

	private int index = -1;

	public void setIndex(int i) {
	    if (index != -1)
	        throw new AssertionError("Doubly used action: "+ this);
        index = i;
    }

    public int getIndex() {
        return index;
    }

    private LTSminTransition transition = null; 

    public void setTransition(LTSminTransition t) {
        if (transition != null)
            throw new AssertionError("Doubly used action: "+ this);
        transition = t;
    }

    public LTSminTransition getTransition() {
        return transition;
    }
}

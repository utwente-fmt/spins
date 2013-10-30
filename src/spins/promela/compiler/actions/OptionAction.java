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
import java.util.Iterator;
import java.util.List;

import spins.promela.compiler.Proctype;
import spins.promela.compiler.parser.ParseException;
import spins.promela.compiler.parser.Token;

public class OptionAction extends Action implements Iterable<Sequence> {
	private final boolean loops;
	
	private String label = null;

	private final List<Sequence> options;

	private boolean hasSuccessor = false;
	static int number = 0;

	public OptionAction(Token token, boolean loops) {
		super(token);
		this.loops = loops;
		if (loops) {
			label = "do_label_"+ number++;
		}
		options = new ArrayList<Sequence>();
	}

	@Override
	public String getEnabledExpression() throws ParseException {
		return null;
	}

	public Sequence startNewOption(Sequence seq) {
		options.add(seq);
		return seq;
	}

	public Sequence startNewOption() {
		Sequence seq = new Sequence();
		return startNewOption(seq);
	}

	public boolean loops() {
		return loops;
	}

	@Override
	public boolean isLocal(Proctype proc) {
		for (Sequence seq : options) {
			if (!seq.isLocal(proc)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public String toString() {
		return loops ? "do" : "if";
	}

	public Iterator<Sequence> iterator() {
		return options.iterator();
	}
	
	public String getLabel() {
		return label;
	}

	public boolean hasSuccessor() {
		return hasSuccessor;
	}

	public void hasSuccessor(boolean hasSuccessor) {
		this.hasSuccessor = hasSuccessor;
	}
}

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

package spins.promela.compiler.variable;

import java.util.ArrayList;
import java.util.List;

public class ChannelType extends VariableType {
	public static final VariableType UNASSIGNED_CHANNEL = new ChannelType(-1, -1);

	public static boolean rendezvousUsed = false;

	public static boolean isRendezvousUsed() {
		return rendezvousUsed;
	}

	private final int bufferSize;

	private final VariableStore vars;

	private final List<VariableType> types;

	private final int id;

	public ChannelType(int id, int bufferSize) {
		super("chan", "int", 8);
		vars = new VariableStore();
		types = new ArrayList<VariableType>();
		this.id = id;
		this.bufferSize = bufferSize;
	}

	@Override
	public boolean canConvert(VariableType type) {
		return type instanceof ChannelType;
	}

	public int getId() {
		return id;
	}

	public int getBufferSize() {
		return bufferSize;
	}

	public void addType(final VariableType type) {
		final Variable var = new Variable(type, "buffer[(i+first)%buffer.length][" + types.size()
												+ "]", -1);
		var.setRead(true);
		var.setWritten(true);
		vars.addVariable(var);
		types.add(type);
	}

	public VariableStore getVariableStore() {
		return vars;
	}

	public List<VariableType> getTypes() {
		return types;
	}

	public boolean isRendezVous() {
		return getBufferSize() == 0;
	}
}

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

import spins.promela.compiler.Proctype;

public class ChannelVariable extends Variable {


    public ChannelVariable(final String name, final int arraySize, Proctype owner) {
        super(ChannelType.UNASSIGNED_CHANNEL, name, arraySize, owner);
    }

	public ChannelVariable(VariableType ct, final String name, final int arraySize, Proctype owner) {
		super(ct, name, arraySize, owner);
	}

    public ChannelVariable(final Variable var) {
        super(var);
    }

	@Override
	public ChannelType getType() {
		return (ChannelType) super.getType();
	}

	@Override
	public void setType(VariableType type) {
		if (!(type instanceof ChannelType)) {
			throw new IllegalArgumentException("Type must be a ChannelType");
		}
		super.setType(type);
	}
}

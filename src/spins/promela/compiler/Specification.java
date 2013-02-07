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

package spins.promela.compiler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import spins.promela.compiler.actions.ChannelReadAction;
import spins.promela.compiler.actions.ChannelSendAction;
import spins.promela.compiler.automaton.Transition;
import spins.promela.compiler.expression.RemoteRef;
import spins.promela.compiler.expression.RunExpression;
import spins.promela.compiler.ltsmin.model.ReadAction;
import spins.promela.compiler.ltsmin.model.SendAction;
import spins.promela.compiler.ltsmin.util.LTSminUtil;
import spins.promela.compiler.parser.ParseException;
import spins.promela.compiler.variable.ChannelType;
import spins.promela.compiler.variable.ChannelVariable;
import spins.promela.compiler.variable.CustomVariableType;
import spins.promela.compiler.variable.Variable;
import spins.promela.compiler.variable.VariableStore;
import spins.promela.compiler.variable.VariableType;
import spins.util.StringWriter;

public class Specification implements Iterable<ProcInstance> {
	private final String name;

	private final List<Proctype> procs;

	private final List<ChannelType> channelTypes;

	private final Map<String, CustomVariableType> userTypes;

	private Proctype never;

	private final VariableStore varStore;

	public List<RunExpression> runs = new ArrayList<RunExpression>();

	private final List<String> mtypes;

	private HashMap<Variable, List<ReadAction>> channelReads;
    private HashMap<Variable, List<SendAction>> channelWrites;

	public Specification(final String name) {
		this.name = name;
		procs = new ArrayList<Proctype>();
		channelTypes = new ArrayList<ChannelType>();
		userTypes = new HashMap<String, CustomVariableType>();
		varStore = new VariableStore();
		channelReads = new HashMap<Variable, List<ReadAction>>();
		channelWrites = new HashMap<Variable, List<SendAction>>();
		Variable hidden = new Variable(VariableType.INT, "_", -1);
		hidden.setHidden(true);
		varStore.addVariable(hidden);
		mtypes = new ArrayList<String>();
	}

	public List<String> getMTypes() {
		return mtypes;
	}

	public String getName() {
		return name;
	}

	public void clearReadActions() {
		channelReads.clear();
	}

    public void clearWriteActions() {
        channelWrites.clear();
    }

	public void addReadAction(ChannelReadAction cra, Transition t) {
		if (!LTSminUtil.isRendezVousReadAction(cra)) return;
		Variable cv = cra.getIdentifier().getVariable();
		List<ReadAction> raw = channelReads.get(cv);
		if (raw == null) {
			raw = new ArrayList<ReadAction>();
			channelReads.put(cv, raw);
		}
		raw.add(new ReadAction(cra, t, t.getProc()));
	}

    public void addWriteAction(ChannelSendAction cwa, Transition t) {
        if (!LTSminUtil.isRendezVousSendAction(cwa)) return;
        Variable cv = cwa.getIdentifier().getVariable();
        List<SendAction> raw = channelWrites.get(cv);
        if (raw == null) {
            raw = new ArrayList<SendAction>();
            channelWrites.put(cv, raw);
        }
        raw.add(new SendAction(cwa, t, t.getProc()));
    }

	/**
	 * Creates a new Channel type for in this Specification.
	 * 
	 * @param bufferSize
	 * @return The new ChannelType
	 */
	public ChannelType newChannelType(int bufferSize) {
		ChannelType type = new ChannelType(channelTypes.size(), bufferSize);
		channelTypes.add(type);
		return type;
	}

	/**
	 * Creates a new Custom type for in this Specification.
	 * 
	 * @param bufferSize
	 * @return The new ChannelType
	 */
	public CustomVariableType newCustomType(String name) throws ParseException {
		if (userTypes.containsKey(name))
			throw new ParseException("Duplicate type declaration with name: "+ name);
		CustomVariableType type = new CustomVariableType(name);
		userTypes.put(name, type);
		return type;
	}
	
	public boolean usesRendezvousChannel() {
		for (ChannelType t : channelTypes) {
			if (t.getBufferSize() < 1) {
				return true;
			}
		}
		return false;
	}

	public void addMType(final String name) {
		mtypes.add(name);
	}

	public void addProc(final Proctype proc) throws ParseException {
		if (getProcess(proc.getName()) != null) {
			throw new ParseException("Duplicate proctype with name: " + proc.getName());
		}
		procs.add(proc);
	}

	private void generateConstructor(final StringWriter w) throws ParseException {
		w.appendLine("public ", name, "Model() throws SpinJaException {").indent();
		w.appendLine("super(\"", name, "\", ", varStore.getBufferSize() + 1
												+ (ChannelVariable.isChannelsUsed() ? 1 : 0)
												+ (usesAtomic() ? 1 : 0), ");");
		w.appendLine();
		w.appendLine("// Initialize the default values");
		for (final Variable var : varStore.getVariables()) {
			var.printInitExpr(w);
		}
		w.appendLine();
		w.appendLine("// Initialize the starting processes");
		for (final Proctype proc : procs) {
			for (int i = 0; i < proc.getNrActive(); i++) {
				w.appendLine("addProcess(new ", proc.getName(), "());");
			}
		}
		w.appendLine();
		w.outdent().appendLine("}");
		w.appendLine();
	}

	private void generateMain(final StringWriter w) {
		w.appendLine("public static void main(String[] args) {").indent();
		w.appendLine("Run run = new Run();");
		w.appendLine("run.parseArguments(args,\"" + name + "\");");
		w.appendLine("run.search(" + name + "Model.class);");
		w.outdent().appendLine("}");
		w.appendLine();
	}

	/**
	 * Generates the complete Model object and returns it as a String.
	 * 
	 * @return A String that holds the complete Model for this Specification.
	 * @throws ParseException
	 *             When something went wrong while parsing the promela file.
	 */
	public String generateModel() throws ParseException {
		final StringWriter w = new StringWriter();

		// The header
		w.appendLine("package spinja;");
		// [07-Apr-2010 12:10 ruys] was: w.appendLine("package spinja.generated;");
		w.appendLine();
		w.appendLine("import spinja.util.DataReader;");
		w.appendLine("import spinja.util.DataWriter;");
		w.appendLine("import spinja.Run;");
		w.appendLine("import spinja.promela.model.*;");
		w.appendLine("import spinja.exceptions.*;");
		w.appendLine();
		w.appendLine("public class ", name, "Model extends PromelaModel {").indent();
		w.appendLine();

		generateMain(w);
		generateCustomTypes(w);
		generateVariables(w);
		generateConstructor(w);
		// genarateNextTransition(w);
		generateStore(w);
		generateToString(w);
		generateGetChannelCount(w);
		generateProctypes(w);

		w.outdent().appendLine("}");

		return w.toString();
	}

	private void generateCustomTypes(StringWriter w) {
		for (final ChannelType type : channelTypes) {
			type.generateClass(w);
		}
	}

	private void generateProctypes(final StringWriter w) throws ParseException {
		for (final Proctype proc : procs) {
			w.appendLine();
			proc.generateCode(w);
		}

		// Generate never claim
		if (never != null) {
			w.appendLine("public PromelaProcess getNever() throws ValidationException {").indent();
			w.appendLine("return new never();");
			w.outdent().appendLine("}").appendLine();
			never.generateCode(w);
		}
	}

	private void generateStore(final StringWriter w) {
		w.appendLine("public void encode(DataWriter _writer) {").indent();
		// w.appendLine("_buffer[_cnt++] = (byte)_nrProcs;");
		// w.appendLine("_buffer[_cnt++] = (byte)_exclusive;");
		w.appendLine("_writer.writeByte(_nrProcs);");
		if (usesAtomic()) {
			w.appendLine("_writer.writeByte(_exclusive);");
		}
		if (ChannelVariable.isChannelsUsed()) {
			// w.appendLine("_buffer[_cnt++] = (byte)_nrChannels;");
			w.appendLine("_writer.writeByte(_nrChannels);");
		}
		varStore.printEncode(w);
		if (ChannelVariable.isChannelsUsed()) {
			w.appendLine("for(int _i = 0; _i < _nrChannels; _i++) {");
			w.indent();
			// w.appendLine("_cnt = _channels[_i].encode(_buffer, _cnt);");
			w.appendLine("_channels[_i].encode(_writer);");
			w.outdent();
			w.appendLine("}");
		}
		w.appendLine("for(int _i = 0; _i < _nrProcs; _i++) {");
		w.indent();
		// w.appendLine("_cnt = _procs[_i].encode(_buffer, _cnt);");
		w.appendLine("_procs[_i].encode(_writer);");
		w.outdent();
		w.appendLine("}");
		// w.appendLine("return _cnt;");
		w.outdent().appendLine("}");
		w.appendLine();

		// w.appendLine("public int decode(byte[] _buffer, int _cnt) {").indent();
		w.appendLine("public boolean decode(DataReader _reader) {").indent();
		// w.appendLine("int _start = _cnt;");
		// w.appendLine("_nrProcs = _buffer[_cnt++] & 0xff;");
		// w.appendLine("_exclusive = _buffer[_cnt++] & 0xff;");
		w.appendLine("_nrProcs = _reader.readByte();");
		if (usesAtomic()) {
			w.appendLine("_exclusive = _reader.readByte();");
		}
		if (ChannelVariable.isChannelsUsed()) {
			// w.appendLine("_nrChannels = _buffer[_cnt++] & 0xff;");
			w.appendLine("_nrChannels = _reader.readByte();");
		}
		varStore.printDecode(w);
		if (ChannelVariable.isChannelsUsed()) {
			w.appendLine();
			w.appendLine("for(int _i = 0; _i < _nrChannels; _i++) {");
			{
				w.indent();
				// w.appendLine("int _newCnt = _channels[_i].decode(_buffer, _cnt);");
				w.appendLine("_reader.storeMark();");
				// w.appendLine("if(_newCnt == -1) {");
				w.appendLine("if(_channels[_i] == null || !_channels[_i].decode(_reader)) {");
				{
					w.indent();
					w.appendLine("_reader.resetMark();");
					// w.appendLine("switch(_buffer[_cnt]) {");
					w.appendLine("switch(_reader.peekByte()) {");
					{
						w.indent();
						for (int i = 0; i < channelTypes.size(); i++) {
							w.appendLine("case ", i, ": _channels[_i] = new Channel", channelTypes.get(
								i).getId(), "(); break;");
						}
						w.appendLine("default: return false;");
						w.outdent();
					}
					w.appendLine("}");
					// w.appendLine("_cnt = _channels[_i].decode(_buffer, _cnt);");
					w.appendLine("if(!_channels[_i].decode(_reader)) return false;");
					// w.appendLine("assert(_cnt >= 0);");
					w.outdent();
				}
				// w.appendLine("} else {");
				// {
				// w.indent();
				// w.appendLine("_cnt = _newCnt;");
				// w.outdent();
				// }
				w.appendLine("}");
				w.outdent();
			}
			w.appendLine("}");
		}
		w.appendLine();
		w.appendLine("int _start = _reader.getMark();");
		w.appendLine("for(int _i = 0; _i < _nrProcs; _i++) {");
		{
			w.indent();
			// w.appendLine("int _newCnt = _procs[_i].decode(_buffer, _cnt);");
			w.appendLine("_reader.storeMark();");
			// w.appendLine("if(_newCnt == -1) {");
			w.appendLine("if(_procs[_i] == null || !_procs[_i].decode(_reader)) {");
			{
				w.indent();
				w.appendLine("_reader.resetMark();");
				// w.appendLine("switch(_buffer[_cnt]) {");
				w.appendLine("switch(_reader.peekByte()) {");
				{
					w.indent();
					for (int i = 0; i < procs.size(); i++) {
						w.appendLine("case ", i, ": _procs[_i] = new ", procs.get(i).getName(),
							"(true, _i); break;");
					}
					w.appendLine("default: return false;");
					w.outdent();
				}
				w.appendLine("}");
				// w.appendLine("_cnt = _procs[_i].decode(_buffer, _cnt);");
				// w.appendLine("assert(_cnt >= 0);");
				w.appendLine("if(!_procs[_i].decode(_reader)) return false;");
				w.outdent();
			}
			// w.appendLine("} else {");
			// {
			// w.indent();
			// w.appendLine("_cnt = _newCnt;");
			// w.outdent();
			// }
			w.appendLine("}");
			w.outdent();
		}
		w.appendLine("}");
		// w.appendLine("_buffer_size = _cnt - _start;");
		w.appendLine("_process_size = _reader.getMark() - _start;");
		// w.appendLine("return _cnt;");
		w.appendLine("return true;");
		w.outdent().appendLine("}");
		w.appendLine();
	}

	private void generateToString(final StringWriter w) {
		w.appendLine("public String toString() {").indent();
		w.appendLine("StringBuilder sb = new StringBuilder();");
		w.appendLine("sb.append(\"", name, "Model: \");");
		varStore.printToString(w);
		w.appendLine("for(int i = 0; i < _nrProcs; i++) {");
		w.appendLine("  sb.append(\'\\n\').append(_procs[i]);");
		w.appendLine("}");
		w.appendLine("for(int i = 0; i < _nrChannels; i++) {");
		w.appendLine("  sb.append(\'\\n\').append(_channels[i]);");
		w.appendLine("}");
		w.appendLine("return sb.toString();");
		w.outdent().appendLine("}");
		w.appendLine();
	}
	
	protected void generateGetChannelCount(final StringWriter w) {
		w.appendLine("public int getChannelCount() {");
		w.indent();
		w.appendLine("return ", varStore.getChannelCount(), ";");
		w.outdent();
		w.appendLine("}");
	}

	private void generateVariables(final StringWriter w) {
		// Create the variables
		for (final Variable var : varStore.getVariables()) {
			if (var.getName().charAt(0) != '_') {
				w.appendLine(var.getType().getJavaName(), (var.getArraySize() > -1 ? "[]" : ""),
					" ", var.getName(), ";");
			}
		}

		w.appendLine();

		// // Create the variable classes
		// for (final VariableType c : usertypes.values()) {
		// if (c != null) {
		// c.generateClass(w);
		// }
		// }
	}

	/**
	 * Returns mtype constant for an identifier. If there is no corresponding
	 * MType, 0 is returned
	 * @param name, the name of the identifier
	 * @return 0 or the number of the MType
	 */
	public int getMType(final String name) {
		int index = mtypes.indexOf(name);
		if (-1 == index) return 0;
		return mtypes.size() - index; // SPIN does reverse numbering of mtypes
	}

	public Proctype getNever() {
		return never;
	}

	public Proctype getProcess(final String name) {
		for (final Proctype proc : procs) {
			if (proc.getName().equals(name)) {
				return proc;
			}
		}
		return null;
	}

	public CustomVariableType getCustomType(final String name) throws ParseException {
		if (userTypes.containsKey(name)) {
			return userTypes.get(name);
		} else {
			throw new ParseException("Could not find a type with name: " + name);
		}
	}

	public Collection<CustomVariableType> getUserTypes() {
		return userTypes.values();
	}

	public VariableStore getVariableStore() {
		return varStore;
	}

	public boolean usesAtomic() {
		for (final Proctype p : procs) {
			if (p.getAutomaton().hasAtomic()) {
				return true;
			}
		}
		return false;
	}


	private List<ProcInstance> instances = null;

    public Set<RemoteRef> remoteRefs = new HashSet<RemoteRef>();
	
	public Iterator<ProcInstance> iterator() {
		if (null == instances)
			throw new AssertionError("Processes were not instantiated");
		return instances.iterator();
	}

	public void setNever(final Proctype never) throws ParseException {
		//if (this.never != null) {
		//	throw new ParseException("Duplicate never claim");
		//}
		this.never = never;
	}

	public void setInstances(List<ProcInstance> instances) {
		this.instances = instances;
	}

	public List<Proctype> getProcs() {
		return procs;
	}

	public List<ReadAction> getReadActions(Variable cv) {
		return channelReads.get(cv);
	}

    public List<SendAction> getWriteActions(Variable cv) {
        return channelWrites.get(cv);
    }
}

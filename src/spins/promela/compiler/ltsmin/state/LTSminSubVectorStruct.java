package spins.promela.compiler.ltsmin.state;

import spins.promela.compiler.expression.Identifier;
import spins.promela.compiler.ltsmin.LTSminDMWalker.IdMarker;

public class LTSminSubVectorStruct extends LTSminSubVector {
	private LTSminTypeStruct type;
	
	protected LTSminSubVectorStruct(LTSminSubVector sv, LTSminTypeI type, int offset) {
		super(sv, offset);
		if (!(type instanceof LTSminTypeStruct))
			throw new AssertionError("Trying to initialize a native type as struct!");
		this.type = (LTSminTypeStruct)type;
	}

	protected LTSminSubVectorStruct() {}

	@Override
	protected LTSminSubVectorArray getSubVector(String name) {
		LTSminVariable lvar = type.getMember(name);
		return new LTSminSubVectorArray(this, lvar, lvar.getOffset());
	}

	public LTSminTypeStruct getType() {
		return type;
	}

	protected void setType(LTSminTypeStruct type) {
		this.type = type;
	}

	@Override
	public int length() {
		return type.length();
	}

	@Override
	protected LTSminSubVector follow() {
		throw new AssertionError("Chose member of struct: "+ type);
	}

	@Override
	public void mark(IdMarker idMarker, Identifier id)  {
		if (null == id) {
		    if (idMarker.isStrict() && !type.isStructinChan) {
		        throw new AssertionError("Inconclusive identifier for: "+ type);
		    } else {
		        if (idMarker.params.opts.must_write && idMarker.isMayMustWrite()) 
	                throw new AssertionError("Inconclusive WRITE for: "+ type);
		        for (LTSminSlot slot : this) {
		            slot.mark(idMarker, id);
		        }
		    }
		} else {
	    		LTSminSubVector sub = getSubVector(id.getVariable().getName());
	    		sub.mark(idMarker, id);
		}
	}
}

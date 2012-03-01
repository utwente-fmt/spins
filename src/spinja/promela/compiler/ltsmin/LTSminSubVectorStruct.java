package spinja.promela.compiler.ltsmin;

public class LTSminSubVectorStruct extends LTSminSubVector {
	private LTSminTypeStruct type;
	
	protected LTSminSubVectorStruct(LTSminSubVector sv, LTSminType type, int offset) {
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
}

package spinja.promela.compiler.ltsmin;


public class LTSminSlot extends LTSminSubVector {
	private LTSminVariable var;
	private int index;
	String fullName;
	
	public LTSminSlot (LTSminVariable var, String fullName, int index) {
		if (!(var.getType() instanceof LTSminTypeNative))
			throw new AssertionError("Non-native type added as slot!");
		this.var = var;
		this.fullName = fullName;
		this.index = index;
	}
	
	public LTSminVariable getVariable() {
		return var;
	}

	public int getIndex() {
		return index;
	}

	public String fullName() {
		return fullName;
	}

	@Override
	public int length() {
		return 1;
	}

	@Override
	protected LTSminSubVector follow() {
		throw new AssertionError("Native type not navigable!");
	}
}

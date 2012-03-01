package spinja.promela.compiler.ltsmin;



public class LTSminSubVectorArray extends LTSminSubVector {
	private LTSminVariable var;

	protected LTSminSubVectorArray(LTSminSubVector sv, LTSminVariable var, int offset) {
		super(sv, offset);
		this.var = var;
	}

	protected LTSminSubVector getSubVector(int index) {
		if (index > (var.array()>0?var.array():1))
			throw new AssertionError("Array index out of bound for: "+ var);
		return follow(index);
	}

	@Override
	protected LTSminSubVector follow() {
		if (var.array() > 1)
			throw new AssertionError("Array variable requires index: "+ var);
		return follow(0);
	}
	
	private LTSminSubVector follow(int index) {
		if (var.getType() instanceof LTSminTypeNative)
			return slot(index);
		int offset = index * var.getType().length();
		return new LTSminSubVectorStruct(this, var.getType(), offset);
	}

	@Override
	public int length() {
		return var.length();
	}
}

package spinja.promela.compiler.ltsmin.state;

import spinja.promela.compiler.expression.Expression;
import spinja.promela.compiler.expression.Identifier;
import spinja.promela.compiler.ltsmin.LTSminDMWalker.IdMarker;
import spinja.promela.compiler.ltsmin.LTSminDMWalker.MarkAction;
import spinja.promela.compiler.parser.ParseException;



public class LTSminSubVectorArray extends LTSminSubVector {
	private LTSminVariable var;

	protected LTSminSubVectorArray(LTSminSubVector sv, LTSminVariable var, int offset) {
		super(sv, offset);
		this.var = var;
	}

	protected LTSminSubVector getSubVector(int index) {
		if (index > (var.array() > -1 ? var.array() : 1))
			throw new AssertionError("Array index out of bound for: "+ var);
		return follow(index);
	}

	@Override
	protected LTSminSubVector follow() {
		if (var.array() > -1)
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
	
	@Override
	public void mark(IdMarker idMarker, Identifier id) {
		Expression arrayExpr = id.getArrayExpr();
		int first = 0;
		int last = 0;
		if (arrayExpr != null) {
			new IdMarker(idMarker, MarkAction.READ).mark(arrayExpr); // array expr is only read!
			if (-1 == id.getVariable().getArraySize()) throw new AssertionError("Index a non-array: "+ var);
			last = id.getVariable().getArraySize() - 1;
			try {
				first = last = arrayExpr.getConstantValue();
			} catch(ParseException pe) {}
		}
		for (int i = first; i < last + 1; i++) {
			LTSminSubVector sub = follow(i);
			sub.mark(idMarker, id.getSub());
		}
	}
}

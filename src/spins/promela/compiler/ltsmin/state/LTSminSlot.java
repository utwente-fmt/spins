package spins.promela.compiler.ltsmin.state;

import spins.promela.compiler.expression.Expression;
import spins.promela.compiler.expression.Identifier;
import spins.promela.compiler.ltsmin.LTSminDMWalker.IdMarker;
import spins.promela.compiler.ltsmin.model.LTSminModelFeature;


public class LTSminSlot extends LTSminSubVector implements LTSminModelFeature {
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

	@Override
	public void mark(IdMarker idMarker, Identifier id) {
		if (id != null) throw new AssertionError("Variable "+ var +" has no member "+ id);
		idMarker.doMark(this);
	}

	public Expression getInitExpr() {
		LTSminVariable var = getParent();
		if (null == var) throw new AssertionError("Slot without variable.");
		if (var.getInitExpr() != null) return var.getInitExpr();
		LTSminTypeI parent = var.getParent();
		if (null == parent || !(parent instanceof LTSminTypeStruct)) throw new AssertionError("Variable with wrong parent struct: "+ parent);
		LTSminTypeStruct struct = (LTSminTypeStruct)parent;
		var = struct.getParent();
		if (null == var ) return null;
		return var.getInitExpr();
	}

	protected LTSminVariable getParent() {
		return var;
	}
}

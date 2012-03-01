package spinja.promela.compiler.ltsmin;

import spinja.promela.compiler.expression.Expression;
import spinja.promela.compiler.variable.Variable;


/**
 *
 * @author FIB, Alfons Laarman
 */
public class LTSminVariable {
	private LTSminType type;
	private String name;
	private int size;
	private Variable var = null;
	private Integer offset = null;

	public String getName() {
		return name;
	}

	protected LTSminVariable(LTSminType type, String name, int size) {
		this.type = type;
		this.name = name;
		this.size = size;
	}

	public LTSminVariable(LTSminType type, Variable v) {
		this(type, v.getName(), v.getArraySize());
		this.type = type;
		this.var = v;
	}

	public LTSminVariable(LTSminType type, String name) {
		this(type, name, 0);
	}

	public LTSminVariable(Variable var) {
		this(new LTSminTypeNative(var), var.getName(), var.getArraySize());
		this.var = var;
	}

	public LTSminType getType() {
		return type;
	}

	public int array() {
		return size;
	}

	public Variable getVariable() {
		return var;
	}
	
	public String toString() {
		return name;
	}

	/**
	 * @return variable's length in number of slots
	 */
	public int length() {
		int s = size > 1 ? size : 1;
		return s * type.length();
	}

	public int getOffset() {
		if (offset == null) throw new AssertionError("Offset not set for type: "+ this);
		return offset;
	}

	public void setOffset(int offset) {
		this.offset = offset;
	}

	public Expression getInitExpr() {
		if (var == null) return null;//throw new AssertionError("SpinJa Variable not set for type: "+ this);
		return var.getInitExpr();
	}
}

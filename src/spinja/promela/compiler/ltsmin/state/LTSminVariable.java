package spinja.promela.compiler.ltsmin.state;

import spinja.promela.compiler.expression.Expression;
import spinja.promela.compiler.expression.Identifier;
import spinja.promela.compiler.ltsmin.LTSminPrinter.ExprPrinter;
import spinja.promela.compiler.variable.Variable;


/**
 * The member of a struct.
 * Variables have multiplicity (for encoding arrays), a name and a type.
 *
 * @see LTSminTypeI
 *
 * @author FIB, Alfons Laarman
 */
public class LTSminVariable {
	protected static final String DEREF = ".";

	private LTSminTypeI type;
	private String name;
	private int size;
	private Variable var = null;
	private Integer offset = null;
	private LTSminTypeI parent = null;

	protected LTSminVariable(LTSminTypeI type, String name, int size, LTSminTypeI parent) {
		this.type = type;
		this.name = name;
		this.size = size;
		this.parent = parent;
		if (type instanceof LTSminTypeStruct) {
			((LTSminTypeStruct)type).setParent(this);
		}
	}

	public LTSminVariable(LTSminTypeI type, Variable v, LTSminTypeI parent) {
		this(type, v.getName(), v.getArraySize(), parent);
		this.type = type;
		this.var = v;
	}

	public LTSminVariable(LTSminTypeI type, String name, LTSminTypeI parent) {
		this(type, name, -1, parent);
	}

	public LTSminVariable(Variable var, LTSminTypeI parent) {
		this(new LTSminTypeNative(var), var.getName(), var.getArraySize(), parent);
		this.var = var;
	}

	public LTSminTypeI getType() {
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

	public LTSminTypeI getParent() {
		return parent;
	}

	public String getName() {
		return name;
	}

	/**
	 * @return variable's length in number of slots
	 */
	public int length() {
		int s = size > -1 ? size : 1;
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

	public String printIdentifier(ExprPrinter p, Identifier id) {
		String res = ""; 
		if (null != id.getArrayExpr()) {
			res = "["+ p.print(id.getArrayExpr()) +"]";
		} else {
			if (size > 1) {
				throw new AssertionError("No array expression for array: "+ this);
			}
		}
		res += DEREF;
		return res + getType().printIdentifier(p, id.getSub());
	}
}

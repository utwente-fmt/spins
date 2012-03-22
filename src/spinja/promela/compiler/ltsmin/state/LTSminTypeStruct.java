package spinja.promela.compiler.ltsmin.state;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import spinja.promela.compiler.expression.Identifier;
import spinja.promela.compiler.ltsmin.LTSminPrinter.ExprPrinter;

/**
 * Encodes structs that logically subdivide the LTSmin state vector.
 *
 * @author FIB
 */
public class LTSminTypeStruct extends LTSminTypeImpl implements LTSminTypeStructI<LTSminVariable> {
	
	private static final String STRUCT_PREFIX = "struct_";
	protected String name;
	private List<LTSminVariable> members;

	public String getName() {
		return name;
	}

	protected LTSminTypeStruct(String name) {
		this.members = new ArrayList<LTSminVariable>();
		this.name = wrapName(name);
	}

	protected static String wrapName(String name) {
		return TYPE_PREFIX + STRUCT_PREFIX + name +"_t";
	}

	public void addMember(LTSminVariable var) {
		if (unmodifiable) throw new AssertionError("Modifying sealed structure.");
		members.add(var);
	}

	@Override
	protected int length_() {
		if (unmodifiable) return length;
		length = 0;
		for (LTSminVariable v : members)
			length += v.length();
		return length;
	}

	@Override
	public Iterator<LTSminVariable> iterator() {
		return members.iterator();
	}

	@Override
	public String toString() {
		return name;
	}

	public void fix() {
		int offset = 0;
		for (LTSminVariable v : this) {
			v.getType().fix();
			v.setOffset(offset);
			offset += v.length();
		}
	}

	public LTSminVariable getMember(String name) {
		for (LTSminVariable v : this)
			if (v.getName().equals(name)) return v;
		throw new AssertionError("Struct "+ this +" has no member "+ name);
	}

	public String printIdentifier(ExprPrinter p, Identifier id) {
		if (null == id)
			throw new AssertionError("Inconclusive identifier for: "+ this);
		LTSminVariable var = getMember(id.getVariable().getName());
		return var.getName() + var.printIdentifier(p, id);
	}

	public boolean equals(Object o) {
		if (!(o instanceof LTSminTypeStruct)) return false;
		LTSminTypeStruct other = (LTSminTypeStruct)o;
		return name.equals(other.name);
	}
	
	public int hashCode() {
		return name.hashCode();
	}
}

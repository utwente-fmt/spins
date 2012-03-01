package spinja.promela.compiler.ltsmin;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 *
 * @author FIB
 */
public class LTSminTypeStruct extends LTSminType implements Iterable<LTSminVariable> {
	private static final String STRUCT_PREFIX = "struct_";
	protected String name;
	private List<LTSminVariable> members;

	public String getName() {
		return name;
	}

	public LTSminTypeStruct(String name) {
		this();
		this.name = wrapName(name);
	}

	protected LTSminTypeStruct() {
		this.members = new ArrayList<LTSminVariable>();
	}

	protected String wrapName(String name) {
		return TYPE_PREFIX + STRUCT_PREFIX + name +"_t";
	}

	public void addMember(LTSminVariable ltSminVariable) {
		if (unmodifiable) throw new AssertionError("Modifying sealed structure.");
		members.add(ltSminVariable);
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
}

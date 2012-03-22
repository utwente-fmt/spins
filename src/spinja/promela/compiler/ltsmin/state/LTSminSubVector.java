package spinja.promela.compiler.ltsmin.state;

import java.util.Iterator;

import spinja.promela.compiler.expression.Identifier;
import spinja.promela.compiler.ltsmin.LTSminDMWalker.IdMarker;

/**
 * Encodes sub vectors of vectors. Each sub vector can be split according to:
 *  - type struct information (LTSminSubVectorStruct)
 *  - array variables (LTSminSubVectorArray)
 * The latter is optional if the variable in the struct has an array length < 2
 * and is not a the "buffer" of a {@link LTSminTypeChanStruct}.
 * Atomic sub vectors are returned as (unique) LTSminSlots.
 * 
 * A simple OCL-like query language encoded in this class supports queries like:
 * a.b[1].c[2].d 
 *   
 * @author laarman
 */
public abstract class LTSminSubVector implements Iterable<LTSminSlot> {
	private LTSminStateVector root;
	private int offset;
	
	protected LTSminSubVector() {}

	protected LTSminSubVector(LTSminStateVector root) {
		this(root, 0);
	}

	protected LTSminSubVector(LTSminSubVector sv, int offset) {
		this(sv.root, sv.offset + offset);
	}
	
	private LTSminSubVector(LTSminStateVector root, int offset) {
		this.root = root;
		this.offset = offset;
	}

	protected LTSminSubVectorArray getSubVector(String name) {
		throw new AssertionError("Cannot get member '"+ name +"' of a "+ this);
	}

	protected LTSminSubVector getSubVector(int index) {
		throw new AssertionError("Cannot get index "+ index +" of a "+ this);
	}

	public abstract int length();
	protected abstract LTSminSubVector follow();


	public LTSminSubVector step() {
		try {
			return follow(); // end with try eager
		} catch (AssertionError ae) {
			return this;
		}
	}

	/**
	 * 
	 * @param query a query string, like: "a.b.c[1].d[*].x" 
	 * @return a subvector
	public LTSminSubVector sub(String query) {
		LTSminSubVector next;
		int len = query.length();
		if (query.equals("")) {
			return this;
		} else if (query.startsWith("[")) {
			int brace = query.indexOf(']');
			if (brace == -1)
				throw new AssertionError("Malformed query: "+ query);
			String path = query.substring(1, brace);
			int index;
			try {
				index = Integer.parseInt(path);
			} catch (NumberFormatException nfe) {
				throw new AssertionError("Malformed query: "+ query);
			}
			next = getSubVector(index);
			return next.sub(query.substring(brace < len ? brace+1 : len));
		} else {
			int dot = query.indexOf('.');
			int brace = query.indexOf('[');
			if (dot == -1 || (brace != -1 && brace < dot))
				dot = brace;
			if (dot == -1)
				dot = len;
			String path = query.substring(0, dot);
			try {
				next = getSubVector(path);
			} catch (AssertionError err) {
				try {
					next = follow();
				} catch (AssertionError ae) {
					throw new AssertionError("Query failed: "+ query +". "+ ae.getMessage()+". "+ err.getMessage());
				}
				next = next.sub(path);
			};
			return next.sub(query.substring(dot < len ? (dot == brace ? dot : dot+1 ) : len));
		}
	}
	 */

	protected LTSminSlot slot(int offset) {
		return root.get(this.offset + offset);
	}
	
	protected void setRoot(LTSminStateVector root) {
		this.root = root;
	}
	
	protected int getOffset() {
		return offset;
	}

	@Override
	public Iterator<LTSminSlot> iterator() {
		return new Iterator<LTSminSlot>() {
			int index = 0;

			@Override
			public boolean hasNext() {
				return index < length();
			}

			@Override
			public LTSminSlot next() {
				return slot(index++);
			}

			@Override
			public void remove() {
				throw new AssertionError("Remove not allowed on fixed-length state vector.");
			}
		};
	}

	abstract public void mark(IdMarker idMarker, Identifier id);
}

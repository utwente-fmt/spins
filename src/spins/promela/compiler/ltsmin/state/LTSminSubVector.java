package spins.promela.compiler.ltsmin.state;

import java.util.Iterator;

import spins.promela.compiler.expression.Identifier;
import spins.promela.compiler.ltsmin.LTSminDMWalker.IdMarker;

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

	protected LTSminSlot slot(int offset) {
		return root.get(this.offset + offset);
	}
	
	protected void setRoot(LTSminStateVector root) {
		this.root = root;
	}
	
	protected int getOffset() {
		return offset;
	}

	public Iterator<LTSminSlot> iterator() {
		return new Iterator<LTSminSlot>() {
			int index = 0;

			public boolean hasNext() {
				return index < length();
			}

			public LTSminSlot next() {
				return slot(index++);
			}

			public void remove() {
				throw new AssertionError("Remove not allowed on fixed-length state vector.");
			}
		};
	}

	abstract public void mark(IdMarker idMarker, Identifier id);
}

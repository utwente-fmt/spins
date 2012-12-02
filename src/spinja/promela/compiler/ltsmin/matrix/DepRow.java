package spinja.promela.compiler.ltsmin.matrix;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Describes a row in the dependency matrix. It holds the dependency for
 * both read and write operations per transition.
 */
public class DepRow {
	private List<Integer> reads;
	private List<Integer> writes;

	private List<Integer> readsDense = null;
    private List<Integer> writesDense = null;
    private boolean fixed = false;
	/**
	 * Creates a new dependency row.
	 * @param size The size of the row, the number of variables to keep
	 * track of
	 */
	DepRow(int size) {
		reads = new ArrayList<Integer>(size);
		writes = new ArrayList<Integer>(size);
		for(int i=0;i<size;++i) {
			reads.add(0);
			writes.add(0);
		}
	}

	/**
	 * Increase the number of reads of the specified dependency by one.
	 * @param dep The dependency to increase.
	 */
	public void incRead(int dep) {
	    if (fixed) throw new AssertionError("Changing dependency matrix after sealing it");
		reads.set(dep, reads.get(dep)+1);
	}

	/**
	 * Increase the number of writes of the specified dependency by one.
	 * @param dep The dependency to increase.
	 */
	public void incWrite(int dep) {
        if (fixed) throw new AssertionError("Changing dependency matrix after sealing it");
		writes.set(dep, writes.get(dep)+1);
	}

	/**
	 * Decrease the number of reads of the specified dependency by one.
	 * @param dep The dependency to decrease.
	 */
	public void decrRead(int dep) {
        if (fixed) throw new AssertionError("Changing dependency matrix after sealing it");
		reads.set(dep, reads.get(dep)-1);
	}

	/**
	 * Decrease the number of writes of the specified dependency by one.
	 * @param dep The dependency to decrease.
	 */
	public void decrWrite(int dep) {
        if (fixed) throw new AssertionError("Changing dependency matrix after sealing it");
		writes.set(dep, writes.get(dep)-1);
	}

	/**
	 * Returns the number of reads of the given dependency.
	 * @param state The dependency of which the number of reads is wanted.
	 * @return The number of reads of the given dependency.
	 */
	public int getRead(int state) {
		return reads.get(state);
	}

	/**
	 * Returns the number of writes of the given dependency.
	 * @param state The dependency of which the number of writes is wanted.
	 * @return The number of writes of the given dependency.
	 */
	public int getWrite(int state) {
		return writes.get(state);
	}

	/**
	 * Returns whether the number of reads of the given dependency is
	 * higher than 0.
	 * @param state The dependency of which the number of reads is wanted.
	 * @return 1: #reads>0, 0: #reads<=0
	 */
	public int getReadB(int state) {
		return reads.get(state)>0?1:0;
	}

	/**
	 * Returns whether the number of writes of the given dependency is
	 * higher than 0.
	 * @param state The dependency of which the number of writes is wanted.
	 * @return 1: #writes>0, 0: #writes<=0
	 */
	public int getWriteB(int state) {
		return writes.get(state)>0?1:0;
	}

	/**
	 * Returns the size of the row.
	 * @return The size of the row.
	 */
	public int getSize() {
		return reads.size();
	}

    public List<Integer> getReads() {
        if (readsDense == null) {
            fixed = true;
            readsDense = new LinkedList<Integer>();
            for (int i = 0; i < reads.size(); i++) {
                if (reads.get(i) > 0)
                    readsDense.add(i);
            }
        }
        return readsDense;
    }

    public List<Integer> getWrites() {
        if (writesDense == null) {
            fixed = true;
            writesDense = new LinkedList<Integer>();
            for (int i = 0; i < writes.size(); i++) {
                if (writes.get(i) > 0)
                    writesDense.add(i);
            }
        }
        return writesDense;
    }

    public void clear() {
        fixed = false;
        readsDense = null;
        writesDense = null;
        for (int j = 0 ; j < reads.size(); j++) {
            reads.set(j, 0);
            writes.set(j, 0);
        }
    }
}
package spins.promela.compiler.ltsmin.matrix;

import java.util.Arrays;
import java.util.BitSet;
import java.util.ConcurrentModificationException;
import java.util.Iterator;

import spins.util.UnModifiableIterator;

/**
 * A sparse K x N matrix implementation.
 *
 * Let k,n be the cardinality of the rows resp. columns in the matrix.
 * Memory use is as follows:
 *  - with an array, a row is stored using 8n + 16 bytes
 *    (8 bytes per integer + List class overhead)
 *  - with a bitset, a row is stored using  N / 8 bytes + 16 bytes, i.e.
 *    constant wrt to this class (8 bits in a byte + BitSet class overhead).
 *
 */
public class DepMatrix {

    private class AList implements Iterable<Integer> {
        private int size = 0;
        private int array[];

        public AList(int initialSize) {
            array = new int[initialSize];
        }

        public AList(AList other) {
            array = other.array.clone();
            size = other.size();
        }

        public void add(int e) {
            grow (e);
            array[size++] = e;
        }

        public boolean get(int e) {
            if (isLocked())
                return binarySearch(e);
            for (int i = 0; i < size(); i++)
                if (array[i] == e) return true;
            return false;
        }

        public boolean binarySearch(int e) {
            return Arrays.binarySearch(array, 0, size(), e) >= 0;
        }

        public Iterable<Integer> sorted = new Iterable<Integer>() {
            public Iterator<Integer> iterator() {
                AList.this.sort();
                return AList.this.iterator();
            }
        };

        private void sort() {
            if (isLocked())
                return;
            Arrays.sort(array, 0, size());
            lock();
        }

        private void lock() {
            size = -size - 1;
        }

        private boolean isLocked() {
            return size < 0;
        }

        private int size() {
            return isLocked() ? -size - 1 : size;
        }

        private void grow (int e) {
            if (isLocked())
                throw new ConcurrentModificationException();
            if (size() == array.length) {
                array = Arrays.copyOf(array, size() << 1);
            }
        }

        public void clear(int initialSize) { // no resize
            if (array.length != initialSize)
                array = new int[initialSize];
            size = 0;
        }

        public Iterator<Integer> iterator() {
             return new UnModifiableIterator<Integer>() {
                private int index = 0;
                private int oldSize;
                private void check()     { if (oldSize != size()) throw new ConcurrentModificationException("size = "+ size() +", oldSize = "+ oldSize); }
                public boolean hasNext() { check(); return index < size(); }
                public Integer next()    { check(); return array[index++]; }
                public void init()       { oldSize = size(); }
            };
        }
    }

    // We maintain _solely_ a sparse array up to its cardinality reaches SPARSE_MIN
    // Searching arrays is more expensive, but
    // the arrays reduce space complexity to K x c for sparse rows
    private final int SPARSE_MIN = 64;

    // We maintain an additional sparse array up to its cardinality reaches SPARSE_MAX
    // Iterating over bitsets is expensive, but lookups in bitsets are fast, and
    // bitsets also offer the most space efficient way to store densely populated rows
    private final int SPARSE_MAX = 128;

	private final int rows;
	private final int cols;

    private final BitSet vector[];
    private final AList sparse[];

	@SuppressWarnings({ "unused" })
    public DepMatrix(int rows, int cols) {
	    if (SPARSE_MIN >= SPARSE_MAX)
	        throw new AssertionError("SPARSEMIN >= SPARSE_MIN");
        this.rows = rows;
        this.cols = cols;
        this.vector = new BitSet[rows];
        this.sparse = new AList[rows];
		for (int i = 0; i < rows; i++) {
		    sparse[i] = new AList(SPARSE_MIN);
		}
	}

    public DepMatrix(DepMatrix org) {
        this.rows = org.rows;
        this.cols = org.cols;
        this.vector = new BitSet[rows];
        this.sparse = new AList[rows];
        for (int i = 0; i < rows; i++) {
            if (org.sparse[i] != null) {
                sparse[i] = new AList(org.sparse[i]);
            }
            if (org.vector[i] != null) {
                vector[i] = (BitSet)org.vector[i].clone();
            }
        }
    }

    private void check(int row, int col) {
        if (row < 0 || row >= rows || col < 0 || col >= cols) {
            throw new AssertionError("Invalid matrix access ("+row+","+col+") for "+rows +"X"+cols +"matrix");
        }
    }

    public int getNrRows() {
        return rows;
    }

    public int getNrCols() {
        return cols;
    }

	public void setDependent(int row, int col) {
        check(row, col);
	    if (vector[row] != null) {
	        if (sparse[row] != null) {
	            if (!vector[row].get(col)) {
    	            if (sparse[row].size() < SPARSE_MAX) {
    	                sparse[row].add(col);
    	            } else {
    	                sparse[row] = null; // remove sparse array
    	            }
	            }
	        }
	        vector[row].set(col);
	    } else {
	        if (!sparse[row].get(col)) {
                sparse[row].add(col);
                if (sparse[row].size() == SPARSE_MIN) { // initiate bitvector
                    vector[row] = new BitSet(cols);
                    for (int x : sparse[row]) {
                        vector[row].set(x);
                    }
                    vector[row].set(col);
                }
	        }
	    }
	}

    public boolean isDependent(int row, int col) {
        check(row, col);
        if (vector[row] != null) {
            return vector[row].get(col);
        } else {
            return sparse[row].get(col);
        }
    }

    public void clear() {
        for (int i = 0; i < rows; i++) {
            if (sparse[i] == null) {
                sparse[i] = new AList(SPARSE_MIN);
            } else {
                sparse[i].clear(SPARSE_MIN);
            }
        }
        Arrays.fill(vector, null);
    }

    public boolean rowsDepenendent(int g1, int g2) {
        return getRow(g1).isDependent(getRow(g2));
    }

    public void orRow(int row, DepRow depRow) {
        for (int col : depRow) {
            setDependent(row, col);
        }
    }

	public DepRow getRow(int row) {
	    if (sparse[row] != null) {
	        vector[row] = null;
	        sparse[row].sort();
	    }
        return new DepRow(this, row);
	}

    public class DepRow implements Iterable<Integer> {
        private DepMatrix matrix;
        private int row;

        public DepRow(DepMatrix m, int row) {
            this.matrix = m;
            this.row = row;
        }

        private BitSet getBitSet() {
            return this.matrix.vector[this.row];
        }

        private Iterable<Integer> getSorted() {
            AList list = matrix.sparse[row];
            if (list == null)
                return null;
            return list.sorted;
        }

        public boolean isDependent(DepRow other) { // prefer lists:
            if (other.getSorted() != null && this.getSorted() != null) {
                return sortedIterableIntersect(getSorted(), other.getSorted());
            } else if (other.getSorted() != null) {
                return sortedIterableIntersect(this, other.getSorted());
            } else if (this.getSorted() != null) {
                return sortedIterableIntersect(other, this.getSorted());
            } else {
                return other.getBitSet().intersects(this.getBitSet());
            }
        }

        public int getCardinality() {
            AList list = matrix.sparse[row];
            return list != null ? list.size() : getBitSet().cardinality();
        }

        private boolean sortedIterableIntersect(Iterable<Integer> list1,
                                                Iterable<Integer> list2) {
            int b = -1;
            Iterator<Integer> L2 = list2.iterator();
            for (int a : list1) {
                while (b < a) {
                    if (!L2.hasNext())
                        return false;
                    b = L2.next();
                };
                if (a == b) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Make Bitset iterable
         */
        public Iterator<Integer> iterator() {
            if (getSorted() != null) {
                return getSorted().iterator();
            }

            return new Iterator<Integer>() {
                int i = 0;
                public boolean hasNext() {
                    i = matrix.vector[row].nextSetBit(i);
                    return i >= 0;
                }

                public Integer next() {
                    int ret = matrix.vector[row].nextSetBit(i);
                    i = ret + 1;
                    return ret;
                }

                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        }

        public int getNrCols() {
            return matrix.cols;
        }

        public boolean isDependent(int col) {
            return matrix.isDependent(row, col);
        }

        public int intDependent(int col) {
            return isDependent(col) ? 1 : 0;
        }
    }
}
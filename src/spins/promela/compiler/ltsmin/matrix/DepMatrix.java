package spins.promela.compiler.ltsmin.matrix;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;

/**
 *
 */
public class DepMatrix{

	private BitSet vector[];
	private int rows;
	private int cols;

    private DepRow sparsed[];

	public DepMatrix(int rows, int cols) {
		vector = new BitSet[rows];
		for (int i = 0; i < rows; i++)
		    vector[i] = new BitSet(cols);
		this.rows = rows;
		this.cols = cols;
        sparsed = new DepRow[rows];
        Arrays.fill(sparsed, null);
	}

    public DepMatrix(DepMatrix org) {
        this.rows = org.rows;
        this.cols = org.cols;
        vector = new BitSet[rows];
        for (int i = 0; i < rows; i++)
            vector[i] = (BitSet) org.vector[i].clone();
        sparsed = new DepRow[rows];
        Arrays.fill(sparsed, null);  
    }

    private void check(int row, int col) {
        if (row < 0 || row >= rows || col < 0 || col >= cols) {
            throw new AssertionError("Invalid matrix access ("+row+","+col+") for "+rows +"X"+cols +"matrix");
        }
        if (sparsed[row] != null) {
            throw new AssertionError("Changing fixed matrix row "+ row);
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
	    vector[row].set(col);
	}

    public boolean isDependent(int row, int col) {
        check(row, col);
        return vector[row].get(col);
    }

    public void clear() {
        for (int i = 0; i < rows; i++) {
            vector[i].clear();
        }
        Arrays.fill(sparsed, null);
    }

    public boolean rowsDepenendent(int g1, int g2) {
        return getRow(g1).isDependent(getRow(g2));
    }

    public void orRow(int row, DepRow read) {
        vector[row].or(read.getBitSet());
    }
    
    private static final int SPARSE_FACTOR = 6 + 2; // 64 bit in a word +
                                                    // an extra factor 4
	/**
	 * Returns a dependency row in the dependency matrix.
	 * @param trans The index of the dependency row to return.
	 * @return The dependency row at the requested index.
	 */
	public DepRow getRow(int row) {
        DepRow mRow = sparsed[row];
        if (mRow == null) {
            int c = vector[row].cardinality();
            if (c < (cols >> SPARSE_FACTOR)) {
                List<Integer> list = new ArrayList<Integer>(c);
                for (int i = vector[row].nextSetBit(0); i >= 0; i = vector[row].nextSetBit(i+1)) {
                    list.add(i);
                }
                mRow = new DepRow(this, c, row, list);
            } else {
                mRow = new DepRow(this, c, row);
            }
        }
        return mRow;
	}

    public class DepRow implements Iterable<Integer> {
        private DepMatrix matrix;
        private List<Integer> list = null;
        private int cardinality;
        private int row;

        public DepRow(DepMatrix m, int c, int row) {
            this.matrix = m;
            this.cardinality = c;
            this.row = row;
        }

        public BitSet getBitSet() {
            return this.matrix.vector[this.row];
        }

        public DepRow(DepMatrix m, int c, int row, List<Integer> list) {
            this(m , c, row);
            this.list = list;
        }

        public boolean isDependent(DepRow other) {
            if (other.list != null && this.list != null) {
                return sortedIterableIntersect(list, other.list);
            } else if (other.list != null) {
                return sortedIterableIntersect(this, other.list);
            } else if (this.list != null) {
                return sortedIterableIntersect(other, this.list);
            } else {
                return other.getBitSet().intersects(this.getBitSet());
            }
        }
        
        public int getCardinality() {
            return cardinality;
        }

        private boolean sortedIterableIntersect(Iterable<Integer> list1,
                                             List<Integer> list2) {  
            int j = 0;
            int b = -1;
            for (int a : list1) {
                while (b < a) {
                    if (list2.size() == j)
                        return false;
                    b = list2.get(j);
                    j++;
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
            if (list != null) {
                return list.listIterator();           
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
            return matrix.vector[row].get(col);
        }

        public int intDependent(int col) {
            return isDependent(col) ? 1 : 0;
        }
    }
}
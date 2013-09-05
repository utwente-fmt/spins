package spins.promela.compiler.ltsmin.matrix;

import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class DepMatrix {
	private ArrayList<DepRow> dep_matrix;
	private int row_length;


	public DepMatrix(int rows, int cols) {
		dep_matrix = new ArrayList<DepRow>();
		row_length = cols;
		for (int i = 0; i < rows; ++i) {
			dep_matrix.add(i,new DepRow(cols));
		}
	}

   public DepMatrix(DepMatrix org) {
       int rows = org.getNrRows();
       int cols = org.getNrCols();
       dep_matrix = new ArrayList<DepRow>();
       row_length = cols;
       for (int i = 0; i < rows; ++i) {
           dep_matrix.add(i, new DepRow(org.getRow(i)));
       }
    }
	   
	/**
	 * Increase the number of reads of the specified dependency by one.
	 * @param col The dependency to increase.
	 */
	public void incRead(int row, int col) {
		if(row<0 || row>=dep_matrix.size()) return;
		DepRow dr = dep_matrix.get(row);
		assert(dr!=null);
		dr.incRead(col);
	}

	/**
	 * Increase the number of writes of the specified dependency by one.
	 * @param col The dependency to increase.
	 */
	public void incWrite(int row, int col) {
		if(row<0 || row>=dep_matrix.size()) return;
		DepRow dr = dep_matrix.get(row);
		assert(dr!=null);
		dr.incWrite(col);
	}

	/**
	 * Decrease the number of reads of the specified dependency by one.
	 * @param col The dependency to decrease.
	 */
	public void decrRead(int row, int col) {
		if(row<0 || row>=dep_matrix.size()) return;
		DepRow dr = dep_matrix.get(row);
		assert(dr!=null);
		dr.decrRead(col);
	}

	/**
	 * Decrease the number of writes of the specified dependency by one.
	 * @param col The dependency to decrease.
	 */
	public void decrWrite(int row, int col) {
		if(row<0 || row>=dep_matrix.size()) return;
		DepRow dr = dep_matrix.get(row);
		assert(dr!=null);
		dr.decrWrite(col);
	}

	/**
	 * Ensure the dependency matrix has the specified number of rows.
	 * Existing rows will not be modified.
	 * If the requested size is not higher than the current size, nothing
	 * is done.
	 * @param size The requested new size of the dependency matrix.
	 * @ensures getRows()>=size
	 */
	public void ensureSize(int size) {
		for(int i=dep_matrix.size();i<size;++i) {
			dep_matrix.add(i,new DepRow(row_length));
		}
	}

	/**
	 * Returns the number of rows in the dependency matrix.
	 * @return The number of rows in the dependency matrix.
	 */
	public int getNrRows() {
		return dep_matrix.size();
	}

	public int getNrCols() {
		return row_length;
	}
	
	/**
	 * Returns a dependency row in the dependency matrix.
	 * @param trans The index of the dependency row to return.
	 * @return The dependency row at the requested index.
	 */
	public DepRow getRow(int trans) {
		return dep_matrix.get(trans);
	}

    public boolean isRead(int row, int col) {
        return getRow(row).getRead(col) > 0;
    }

    public boolean isWrite(int row, int col) {
        return getRow(row).getWrite(col) > 0;
    }

    public List<Integer> getReads(int row) {
        return getRow(row).getReads();
    }

    public List<Integer> getWrites(int row) {
        return getRow(row).getWrites();
    }

    public List<Integer> getDeps(int row) {
        return getRow(row).getDeps();
    }

    public boolean isWrite(int row, List<Integer> cols) {
        for (int i : cols)
            if (isWrite(row, i)) return true;
        return false;
    }

    public void clear() {
        for (int i = 0 ; i < dep_matrix.size(); i++)
            dep_matrix.get(i).clear();
    }

    public boolean isRead(int row, List<Integer> cols) {
        for (int i : cols)
            if (isRead(row, i)) return true;
        return false;
    }

}
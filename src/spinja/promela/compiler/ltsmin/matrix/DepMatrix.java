package spinja.promela.compiler.ltsmin.matrix;

import java.util.ArrayList;

/**
 *
 */
public class DepMatrix {
	private ArrayList<DepRow> dep_matrix;
	private int row_length;


	public DepMatrix(int trans, int size) {
		dep_matrix = new ArrayList<DepRow>();
		row_length = size;
		for(int i=0;i<trans;++i) {
			dep_matrix.add(i,new DepRow(size));
		}
	}

	/**
	 * Increase the number of reads of the specified dependency by one.
	 * @param dep The dependency to increase.
	 */
	public void incRead(int transition, int dep) {
		if(transition<0 || transition>=dep_matrix.size()) return;
		DepRow dr = dep_matrix.get(transition);
		assert(dr!=null);
		dr.incRead(dep);
	}

	/**
	 * Increase the number of writes of the specified dependency by one.
	 * @param dep The dependency to increase.
	 */
	public void incWrite(int transition, int dep) {
		if(transition<0 || transition>=dep_matrix.size()) return;
		DepRow dr = dep_matrix.get(transition);
		assert(dr!=null);
		dr.incWrite(dep);
	}

	/**
	 * Decrease the number of reads of the specified dependency by one.
	 * @param dep The dependency to decrease.
	 */
	public void decrRead(int transition, int dep) {
		if(transition<0 || transition>=dep_matrix.size()) return;
		DepRow dr = dep_matrix.get(transition);
		assert(dr!=null);
		dr.decrRead(dep);
	}

	/**
	 * Decrease the number of writes of the specified dependency by one.
	 * @param dep The dependency to decrease.
	 */
	public void decrWrite(int transition, int dep) {
		if(transition<0 || transition>=dep_matrix.size()) return;
		DepRow dr = dep_matrix.get(transition);
		assert(dr!=null);
		dr.decrWrite(dep);
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
	public int getRows() {
		return dep_matrix.size();
	}

	public int getRowLength() {
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
}
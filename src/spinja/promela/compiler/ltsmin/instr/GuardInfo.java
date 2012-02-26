/**
 * 
 */
package spinja.promela.compiler.ltsmin.instr;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import spinja.promela.compiler.ltsmin.LTSminGuard;

public class GuardInfo implements Iterable<LTSminGuard> {
	/**
	 * guards ...
	 *   v    ...
	 */
	private List<LTSminGuard> guards;

	/**
	 *        guards >
	 * guards ...    ...
	 *   v    ...    ...
	 */
	private DepMatrix co_matrix;

	/**
	 *        guards >
	 * trans  ...    ...
	 *   v    ...    ...
	 */
	private List< List<Integer> > trans_matrix;

	/**
	 *        state >
	 * guards ...    ...
	 *   v    ...    ...
	 */
	private DepMatrix dm;

	public GuardInfo(int width) {
		guards = new ArrayList<LTSminGuard>();
		trans_matrix = new ArrayList< List<Integer> >();
		for (int i = 0; i < width; i++) {
			trans_matrix.add(new ArrayList<Integer>());
		}
	}

	public void addGuard(int trans, LTSminGuard g) {
		int idx = getGuard(g);
		if (idx == -1) {
			guards.add(g);
			idx = guards.size() - 1;
		}
		trans_matrix.get(trans).add(idx);
	}

	public int getGuard(LTSminGuard g) { //TODO: HashSet + equals() + hash()
		for (int i = 0; i < guards.size(); i++) {
			LTSminGuard other = get(i);
			if(other.equals(g))
				return i;
		}
		return -1;
	}

	public DepMatrix getDepMatrix() {
		return dm;
	}

	public DepMatrix getCoMatrix() {
		return co_matrix;
	}

	public void setCoMatrix(DepMatrix co) {
		co_matrix = co;
	}

	public List< List<Integer> > getTransMatrix() {
		return trans_matrix;
	}

	public List<LTSminGuard> getGuards() {
		return guards;
	}

	public void setDepMatrix(DepMatrix dm) {
		this.dm = dm;
	}

	@Override
	public Iterator<LTSminGuard> iterator() {
		return guards.iterator();
	}

	public LTSminGuard get(int i) {
		return guards.get(i);
	}

	public int size() {
		return guards.size();
	}

}

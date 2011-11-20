/**
 * 
 */
package spinja.promela.compiler.ltsmin.instr;

import java.util.ArrayList;
import java.util.List;

import spinja.promela.compiler.expression.CompareExpression;
import spinja.promela.compiler.expression.Expression;
import spinja.promela.compiler.ltsmin.LTSminGuard;
import spinja.promela.compiler.ltsmin.LTSminGuardBase;

public class GuardMatrix {
	/**
	 * guards ...
	 *   v    ...
	 */
	private List<LTSminGuardBase> guards;

	/**
	 *        guards >
	 * guards ...    ...
	 *   v    ...    ...
	 */
	private List< List<Integer> > co_matrix;

	/**
	 *        state >
	 * guards ...    ...
	 *   v    ...    ...
	 */
	private List< List<Integer> > dep_matrix;

	/**
	 *        guards >
	 * trans  ...    ...
	 *   v    ...    ...
	 */
	private List< List<LTSminGuardBase> > trans_matrix;

	private final int width;

	private DepMatrix dm;

	public GuardMatrix(int width) {
		this.width = width;
		guards = new ArrayList<LTSminGuardBase>();
		co_matrix = new ArrayList< List<Integer> >();
		dep_matrix = new ArrayList< List<Integer> >();
		trans_matrix = new ArrayList< List<LTSminGuardBase> >();
	}

	public int addGuard(int trans, LTSminGuardBase g) {

		int idx = getGuard(g);
		if(idx>=0) return idx;

		guards.add(g);
		for(int i=co_matrix.size(); i-->0;) {
			co_matrix.get(i).add(1);
		}

		{
			List<Integer> row = new ArrayList<Integer>();
			co_matrix.add(row);
			for(int i=guards.size(); i-->0;) {
				row.add(1);
			}

			for(int i=co_matrix.size(); i-->0;) {
				if(co_matrix.get(i).size()!=co_matrix.size()) throw new AssertionError("Co-Matrix is not square for row " + i + ": " + co_matrix.get(i).size() + " x " + co_matrix.size());
			}
		}

		{
			List<Integer> row = new ArrayList<Integer>(width);
			dep_matrix.add(row);
			for(int i=row.size(); i-->0;) {
				row.set(i,1);
			}
		}

		{
			for(int i=trans_matrix.size();i<=trans;++i) {
				trans_matrix.add(i,new ArrayList<LTSminGuardBase>());
			}
			trans_matrix.get(trans).add(g);
		}

		return co_matrix.size()-1;
	}

	public int getGuard(LTSminGuardBase g) {
		for(int i = guards.size(); i-->0;) {
			if(guards.get(i).equals(g)) return i;
		}
		return -1;
	}

	public List< List<Integer> > getDepMatrix() {
		return dep_matrix;
	}

	public DepMatrix getDepMatrix2() {
		return dm;
	}

	public List< List<Integer> > getCoMatrix() {
		return co_matrix;
	}

	public List< List<LTSminGuardBase> > getTransMatrix() {
		return trans_matrix;
	}

	public List<LTSminGuardBase> getGuards() {
		return guards;
	}

	public void setDepMatrix2(DepMatrix dm) {
		this.dm = dm;
	}

	public boolean canBeCoEnabled(Expression e1, Expression e2) {
//		HashTable<Identifier,List<CompareExpression>> s1;
//		HashTable<Identifier,List<CompareExpression>> s2;

		if(e1 instanceof CompareExpression) {
			CompareExpression ce1 = (CompareExpression)e1;
		}

		return true;
	}

	public void optimize() {
		for(int g=0; g<guards.size(); ++g) {
			LTSminGuardBase guard_ = guards.get(g);
			for(int g2=g+1; g2<guards.size(); ++g2) {
				LTSminGuardBase guard2_ = guards.get(g2);

				// Can guard and guard2 be co-enabled?
				if(guard_ instanceof LTSminGuard) {
					LTSminGuard guard = (LTSminGuard)guard_;
					if(guard2_ instanceof LTSminGuard) {
						LTSminGuard guard2 = (LTSminGuard)guard2_;
						Expression e1 = guard.getExpr();
						Expression e2 = guard2.getExpr();

					}
				}

			}
		}
	}

}

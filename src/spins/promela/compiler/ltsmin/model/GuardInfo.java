/**
 * 
 */
package spins.promela.compiler.ltsmin.model;

import static spins.promela.compiler.ltsmin.util.LTSminUtil.negate;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import spins.promela.compiler.expression.BooleanExpression;
import spins.promela.compiler.expression.Expression;
import spins.promela.compiler.ltsmin.LTSminDMWalker;
import spins.promela.compiler.ltsmin.matrix.DepMatrix;
import spins.promela.compiler.ltsmin.matrix.LTSminGuard;
import spins.promela.compiler.ltsmin.matrix.RWMatrix;
import spins.promela.compiler.parser.PromelaConstants;
import spins.util.IndexedSet;

/**
 * Guards are (boolean) state labels
 * 
 * @author laarman
 *
 */
public class GuardInfo implements Iterable<Entry<String, LTSminGuard>> {

    /**
	 * labels ...
	 *   v    ...
	 */
	private IndexedSet<LTSminGuard> labels;
    private List<String> label_names;
    private int         nguards = 0;
    private boolean fixed = false;

    private Map<String, DepMatrix> matrices = new HashMap<String, DepMatrix>();
    
	/**
	 *        guards >
	 * guards ...    ...
	 *   v    ...    ...
	 */
	private DepMatrix co_matrix;

    private DepMatrix ico_matrix;

	/**
     *        trans >
     * trans ...    ...
     *   v    ...    ...
     */
    private DepMatrix dna_matrix;

    /**
     *        trans >
     * trans ...    ...
     *   v    ...    ...
     */
    private DepMatrix commutes_matrix;

	/**
	 *        trans >
	 * guards ...    ...
	 *   v    ...    ...
	 */
	private DepMatrix nes_matrix;
	
	/**
	 *        trans >
	 * guards ...    ...
	 *   v    ...    ...
	 */
	private DepMatrix nds_matrix;

	/**
	 *        guards >
	 * trans  ...    ...
	 *   v    ...    ...
	 */
	private List< List<Integer> > trans_guard_matrix;

	/**
	 *        state >
	 * guards ...    ...
	 *   v    ...    ...
	 */
	private DepMatrix dm;
    
    /**
     * Test sets of transitions
     * 
     *        slots >
     * trans  ...    ...
     *   V    ...    ...
     */
    private DepMatrix testset;
    private LTSminModel m;

	public GuardInfo(LTSminModel m) {
		labels = new IndexedSet<LTSminGuard>();
		label_names = new ArrayList<String>();
		this.m = m;
		trans_guard_matrix = new ArrayList< List<Integer> >();
		for (int i = 0; i < m.getTransitions().size(); i++) {
			trans_guard_matrix.add(new ArrayList<Integer>());
		}
	}

    private boolean isDependent(Expression e1, Expression e2) {
        DepMatrix temp1 = new DepMatrix(1, m.sv.size());
        RWMatrix dummy1 = new RWMatrix(temp1, null);
        LTSminDMWalker.walkOneGuard(m, dummy1, e1, 0);

        DepMatrix temp2 = new DepMatrix(1, m.sv.size());
        RWMatrix dummy2 = new RWMatrix(temp2, null);
        LTSminDMWalker.walkOneGuard(m, dummy2, e2, 0);
        return temp1.getRow(0).isDependent(temp2.getRow(0));
    }

    public void walkGuard(int trans, Expression e, boolean invert) {
        if (e instanceof BooleanExpression) {
             BooleanExpression be = (BooleanExpression)e;
             switch (be.getToken().kind) {
             case PromelaConstants.LNOT:
                 walkGuard(trans, be.getExpr1(), !invert);
                 break;
             case PromelaConstants.LOR:
                 if (invert && !isDependent(be.getExpr1(), be.getExpr2())) {
                     // DeMorgan: NOT OR -->  AND NOT
                     walkGuard(trans, be.getExpr1(), !invert);
                     walkGuard(trans, be.getExpr2(), !invert);
                 } else {
                     if (invert)
                         e = negate(e); // invert
                     addGuard(trans, new LTSminGuard(e));
                 }
                 break;
             case PromelaConstants.LAND:
                 if (!invert && !isDependent(be.getExpr1(), be.getExpr2())) {
                     walkGuard(trans, be.getExpr1(), invert);
                     walkGuard(trans, be.getExpr2(), invert);
                 } else {
                     if (invert)
                         e = negate(e); // invert
                    addGuard(trans, new LTSminGuard(e));
                 }
                 break;
             }
	    } else {
	        if (invert)
	            e = negate(e); // invert
	        addGuard(trans, new LTSminGuard(e));
	    }
	}

    public void addGuard(int trans, Expression e) {
        walkGuard(trans, e, false);
    }

	public void addGuard(int trans, LTSminGuard g) {
	    if (fixed)
	        throw new AssertionError("Mixing guards and other state labels!");
		int idx = labels.get2(g);
	    if (idx == -1) {
		    idx = labels.addGet(g);
		    nguards++;
            g.setIndex(idx);
            label_names.add("guard_"+ idx);
		}
		trans_guard_matrix.get(trans).add(idx);
	}

    public void addLabel(String name, LTSminGuard g) {
        fixed = true;
        labels.add2(g); // allow duplicates
        label_names.add(name);
    }

	public int getGuard(LTSminGuard g) {
		Integer result = labels.get(g);
		return result == null ? -1 : result.intValue();
	}

	public DepMatrix getDepMatrix() {
		return dm;
	}

	public void setDepMatrix(DepMatrix dm) {
		this.dm = dm;
	}

	public DepMatrix getCoMatrix() {
		return co_matrix;
	}

	public void setCoMatrix(DepMatrix co) {
		co_matrix = co;
	}

   public DepMatrix getICoMatrix() {
        return ico_matrix;
    }

    public void setICoMatrix(DepMatrix ico) {
        ico_matrix = ico;
    }
	
	public List< List<Integer> > getTransMatrix() {
		return trans_guard_matrix;
	}

	public int getNumberOfGuards() {
		return nguards;
	}

    public int getNumberOfLabels() {
        return labels.size();
    }

    public LTSminGuard getLabel(int i) {
        return labels.get(i);
    }

    public String getLabelName(int i) {
        return label_names.get(i);
    }

    public DepMatrix getNESMatrix() {
		return nes_matrix;
	}

	public void setNESMatrix(DepMatrix nes_matrix) {
		this.nes_matrix = nes_matrix;
	}

	public DepMatrix getNDSMatrix() {
		return nds_matrix;
	}

	public void setNDSMatrix(DepMatrix nds_matrix) {
		this.nds_matrix = nds_matrix;
	}

	public Iterator<Entry<String,LTSminGuard>> iterator() {
		return new Iterator<Entry<String,LTSminGuard>>() {
		    Iterator<LTSminGuard> it = labels.iterator();
            Iterator<String> it2 = label_names.iterator();
		    
            public boolean hasNext() {
                return it.hasNext();
            }

            public Entry<String,LTSminGuard> next() {
                return new SimpleEntry<String,LTSminGuard>(it2.next(), it.next());
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }
		};
	}

	public LTSminGuard get(int i) {
		return labels.get(i);
	}

    public void setTestSetMatrix(DepMatrix dm2) {
        testset = dm2;
    }
    
    public DepMatrix getTestSetMatrix() {
        return testset;
    }


    public DepMatrix getDNAMatrix() {
        return dna_matrix;
    }

    public void setDNAMatrix(DepMatrix dna) {
        dna_matrix = dna;
    }

    public DepMatrix getCommutesMatrix() {
        return commutes_matrix;
    }

    public void setCommutesMatrix(DepMatrix c) {
        commutes_matrix = c;
    }

    public void setMatrix(String mName, DepMatrix m) {
        DepMatrix x = matrices.put(mName, m);
        if (x != null) {
            throw new AssertionError("Matrix already set: "+ mName);
        }
    }

    public DepMatrix getMatrix(String mName) {
        return matrices.get(mName);
    }

    public List<LTSminGuard> getLabels() {
        return labels.list();
    }
}

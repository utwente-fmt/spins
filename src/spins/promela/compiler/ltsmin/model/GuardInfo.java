/**
 * 
 */
package spins.promela.compiler.ltsmin.model;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import spins.promela.compiler.expression.Expression;
import spins.promela.compiler.ltsmin.LTSminTreeWalker.Options;
import spins.promela.compiler.ltsmin.matrix.DepMatrix;
import spins.promela.compiler.ltsmin.matrix.LTSminGuard;
import spins.promela.compiler.ltsmin.util.CNF;
import spins.promela.compiler.ltsmin.util.CNF.D;
import spins.promela.compiler.ltsmin.util.LTSminDebug;
import spins.promela.compiler.ltsmin.util.LTSminDebug.MessageKind;
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
    private List<String> export_matrices = new LinkedList<String>();

    public Iterable<String> exports = new Iterable<String>() {
        public Iterator<String> iterator() {
            return export_matrices.iterator();
        }
    };

    public int getNrExports() {
        return export_matrices.size();
    }

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

    public void addGuard(int trans, Expression e, LTSminDebug debug,
                         Options opts) {
        CNF cnf = new CNF(m);
        cnf.walkGuard(e, false, opts);
        for (D disjunct : cnf) {
            Expression ed = disjunct.getExpression();
            //SimplePredicate.extract_conjunct_predicates(m, sps, ed, false); 
            addGuard(trans, new LTSminGuard(ed));
        }
        if (debug.isVerbose()) {
            Expression cnfExpr = cnf.getExpression();
            if (!e.equals(cnfExpr)) {
                debug.add(MessageKind.DEBUG, e +"  ==>  ");
                debug.say(MessageKind.DEBUG, cnfExpr);
            }
        }
    }

	private void addGuard(int trans, LTSminGuard g) {
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

    private List< List<Integer> > guard_trans_matrix = null;
	public List< List<Integer> > getGuardMatrix() {
	    if (guard_trans_matrix == null) {
	        guard_trans_matrix = new ArrayList< List<Integer> >();
	        for (int i = 0; i < getNumberOfLabels(); i++) {
	            guard_trans_matrix.add(new ArrayList<Integer>());
	        }
	        for (int i = 0; i < m.getTransitions().size(); i++) {
	            for (int j : trans_guard_matrix.get(i)) {
	                guard_trans_matrix.get(j).add(i);
	            }
	        }
	    }
        return guard_trans_matrix;
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

    public void setMatrix(String mName, DepMatrix m, boolean export) {
        DepMatrix x = matrices.put(mName, m);
        if (export)
            export_matrices.add(mName);
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

    public void setMatrix(String mName, DepMatrix m) {
        setMatrix(mName, m, false);
    }
}

/**
 * 
 */
package spins.promela.compiler.ltsmin.matrix;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import spins.promela.compiler.expression.Expression;

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
	private List<LTSminGuard> labels;
    private List<String> label_names;
    private int nguards = 0;
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

	public GuardInfo(int width) {
		labels = new ArrayList<LTSminGuard>();
		label_names = new ArrayList<String>();
		trans_guard_matrix = new ArrayList< List<Integer> >();
		for (int i = 0; i < width; i++) {
			trans_guard_matrix.add(new ArrayList<Integer>());
		}
	}

    public void addGuard(int trans, Expression e) {
        addGuard(trans, new LTSminGuard(e));
    }

	public void addGuard(int trans, LTSminGuard g) {
	    if (fixed)
	        throw new AssertionError("Mixing guards and otgher state labels!");
		int idx = getGuard(g);
		if (idx == -1) {
			labels.add(g);
			idx = labels.size() - 1;
            label_names.add("guard_"+ idx);
            nguards++;
		}
		trans_guard_matrix.get(trans).add(idx);
	}

    public void addLabel(String name, LTSminGuard g) {
        fixed = true;
        labels.add(g);
        label_names.add(name);
    }

	public int getGuard(LTSminGuard g) { //TODO: HashSet + equals() + hash()
		for (int i = 0; i < labels.size(); i++) {
			LTSminGuard other = get(i);
			if (other.equals(g))
				return i;
		}
		return -1;
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

    public boolean maybeCoEnabled(int trans, int guard) {
        for (int g2 : trans_guard_matrix.get(trans)) {
            if (!co_matrix.isDependent(guard, g2)) {
                return false;
            }
        }
        return true;
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
}

/**
 * 
 */
package spins.promela.compiler.ltsmin.matrix;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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

	/**
	 *        guards >
	 * guards ...    ...
	 *   v    ...    ...
	 */
	private DepMatrix co_matrix;

	/**
     *        guards >
     * guards ...    ...
     *   v    ...    ...
     */
    private DepMatrix codis_matrix;

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
	 * Label visibility matrix
	 */
    private DepMatrix visibility;

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

	@Override
	public Iterator<Entry<String,LTSminGuard>> iterator() {
		return new Iterator<Entry<String,LTSminGuard>>() {
		    Iterator<LTSminGuard> it = labels.iterator();
            Iterator<String> it2 = label_names.iterator();
		    
		    @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public Entry<String,LTSminGuard> next() {
                return new SimpleEntry<String,LTSminGuard>(it2.next(), it.next());
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
		};
	}

	public LTSminGuard get(int i) {
		return labels.get(i);
	}

    public void setCoDisMatrix(DepMatrix codis) {
        codis_matrix = codis;
    }
    
    public DepMatrix getCoDisMatrix() {
        return codis_matrix;
    }

    public boolean maybeCoDisabled(int trans, int guard) {
        for (int g2 : trans_guard_matrix.get(trans)) {
            if (codis_matrix.isRead(guard, g2)) {
                return true;
            }
        }
        return false;
    }

    public boolean maybeCoEnabled(int trans, int guard) {
        for (int g2 : trans_guard_matrix.get(trans)) {
            if (!co_matrix.isRead(guard, g2)) {
                return false;
            }
        }
        return true;
    }

    public void setVisibilityMatrix(DepMatrix vis) {
        visibility = vis;
    }
    
    public DepMatrix getVisibilityMatrix() {
        return visibility;
    }
}

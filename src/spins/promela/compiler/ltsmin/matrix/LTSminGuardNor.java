package spins.promela.compiler.ltsmin.matrix;

import static spins.promela.compiler.ltsmin.util.LTSminUtil.not;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.or;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import spins.promela.compiler.expression.Expression;
import spins.util.StringWriter;

/**
 *
 * @author FIB
 */
public class LTSminGuardNor extends LTSminGuardBase implements LTSminGuardContainer {
	public List<LTSminGuardBase> guards;

	public LTSminGuardNor() {
		guards = new ArrayList<LTSminGuardBase>();
	}

	public void addGuard(Expression e) {
		addGuard(new LTSminGuard(e));
	}

	public void addGuard(LTSminGuardBase guard) {
//		if(!guard.isDefinitelyFalse()) {
			guards.add(guard);
//		}
	}

	public void prettyPrint(StringWriter w) {
		if(guards.size()>0) {
			boolean first = true;
			w.appendLine("!(");
			w.indent();
			for(LTSminGuardBase g: guards) {
				if(!first) w.append(" || ");
				first = false;
				g.prettyPrint(w);
			}
			w.outdent();
			w.appendLine(")");
		} else {
			w.appendLine("false _GNOR_ ");
		}
	}

	public boolean isDefinitelyTrue() {
		for(LTSminGuardBase g: guards) {
			if(!g.isDefinitelyFalse()) {
				return false;
			}
		}
		return true;
	}

	public boolean isDefinitelyFalse() {
		for(LTSminGuardBase g: guards) {
			if(g.isDefinitelyTrue()) {
				return true;
			}
		}
		return false;
	}

	public Iterator<LTSminGuardBase> iterator() {
		return guards.iterator();
	}

	public int guardCount() {
		return guards.size();
	}

    public Expression getExpression() {
        Expression e = null;
        for (LTSminGuardBase sub : this) {
            Expression sube = sub.getExpression();
            if (sube == null) continue;
            if (e == null) e = sube;
            else           e = or(sube, e);
        }
        if (e != null)
            e = not(e);
        return e;
    }
}

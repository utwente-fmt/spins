package spinja.promela.compiler.ltsmin;

import java.util.ArrayList;
import java.util.List;
import spinja.promela.compiler.parser.ParseException;
import spinja.util.StringWriter;

/**
 *
 * @author FIB
 */
public class LTSminGuardOr implements LTSminGuardBase, LTSminGuardContainer {
	List<LTSminGuardBase> guards;

	public LTSminGuardOr() {
		guards = new ArrayList<LTSminGuardBase>();
	}

	public void addGuard(LTSminGuardBase guard) {
//		if(!guard.isDefinitelyFalse()) {
			guards.add(guard);
//		}
	}

	public void prettyPrint(StringWriter w) {
		if(guards.size()>0) {
			boolean first = true;
			w.appendLine("(");
			w.indent();
			for(LTSminGuardBase g: guards) {
				if(!first) w.append(" || ");
				first = false;
				g.prettyPrint(w);
			}
			w.outdent();
			w.appendLine(")");
		} else {
			w.appendLine("false _GOR_ ");
		}
	}

	public boolean isDefinitelyTrue() {
		for(LTSminGuardBase g: guards) {
			if(g.isDefinitelyTrue()) {
				return true;
			}
		}
		return false;
	}

	public boolean isDefinitelyFalse() {
		for(LTSminGuardBase g: guards) {
			if(!g.isDefinitelyFalse()) {
				return false;
			}
		}
		return true;
	}



}

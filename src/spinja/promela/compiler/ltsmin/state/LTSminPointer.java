package spinja.promela.compiler.ltsmin.state;

import spinja.promela.compiler.Proctype;
import spinja.promela.compiler.expression.Identifier;
import spinja.promela.compiler.ltsmin.LTSminPrinter;
import spinja.promela.compiler.ltsmin.LTSminPrinter.ExprPrinter;
import spinja.promela.compiler.variable.Variable;

/**
 * A pointer to a state vector. 
 * 
 * @author laarman
 */
public class LTSminPointer extends LTSminVariable {
	private static final String DEREF = "->";
	private LTSminStateVector sv;

	public Variable getPC(Proctype process) {
		return sv.getPC(process);
	}

	public Variable getPID(Proctype p) {
		return sv.getPID(p);
	}

	public LTSminPointer(LTSminStateVector sv, String name) {
		super(sv.getType(), name, sv);
		this.sv = sv;
	}

	public String printIdentifier(ExprPrinter p, Identifier id) {
		LTSminVariable member = sv.getMember(id.getVariable().getOwner());
		return getName() + DEREF + member.getName() + LTSminVariable.DEREF + 
				member.getType().printIdentifier(p, id);
	}
}

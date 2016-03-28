package spins.promela.compiler.ltsmin.state;

import spins.promela.compiler.expression.Identifier;
import spins.promela.compiler.ltsmin.LTSminPrinter.ExprPrinter;

/**
 * A pointer to a state vector. 
 * 
 * @author laarman
 */
public class LTSminPointer extends LTSminVariable {
	private static final String DEREF = "->";
	private LTSminStateVector sv;

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

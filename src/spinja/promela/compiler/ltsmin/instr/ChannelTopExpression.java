/**
 * 
 */
package spinja.promela.compiler.ltsmin.instr;

import java.util.Set;

import spinja.promela.compiler.actions.ChannelReadAction;
import spinja.promela.compiler.expression.Expression;
import spinja.promela.compiler.parser.ParseException;
import spinja.promela.compiler.parser.PromelaConstants;
import spinja.promela.compiler.parser.Token;
import spinja.promela.compiler.variable.VariableAccess;
import spinja.promela.compiler.variable.VariableType;

public class ChannelTopExpression extends Expression {
	/**
	 * 
	 */
	private ChannelReadAction cra;
	private int elem;

	public ChannelTopExpression(ChannelReadAction cra, int elem) {
		super(new Token(PromelaConstants.NUMBER,"[" + cra.getIdentifier().getVariable().getName() +".nextRead].m"+elem));
		this.cra = cra;
		this.elem = elem;
	}
	public Set<VariableAccess> readVariables() {
		return null;
	}
	public VariableType getResultType() throws ParseException {
		return null;
	}

	public ChannelReadAction getChannelReadAction() {
		return cra;
	}

	public int getElem() {
		return elem;
	}

}
/**
 * 
 */
package spinja.promela.compiler.ltsmin.instr;

import static spinja.promela.compiler.ltsmin.LTSminStateVector.C_STATE_TMP;

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
	private String name;

	public ChannelTopExpression(ChannelReadAction cra, String name, int elem) {
		super(new Token(PromelaConstants.NUMBER,"[" + name +".nextRead].m"+elem));
		this.cra = cra;
		this.elem = elem;
		this.name = name;
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

	@Override
	public String getIntExpression() {
		return "(["+C_STATE_TMP + "." + name +".nextRead].m" + elem + ")";
	}

}
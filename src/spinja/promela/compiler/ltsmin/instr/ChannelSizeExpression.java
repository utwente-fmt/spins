/**
 * 
 */
package spinja.promela.compiler.ltsmin.instr;

import java.util.Set;

import spinja.promela.compiler.expression.Expression;
import spinja.promela.compiler.ltsmin.LTSminTreeWalker;
import spinja.promela.compiler.parser.ParseException;
import spinja.promela.compiler.parser.PromelaConstants;
import spinja.promela.compiler.parser.Token;
import spinja.promela.compiler.variable.ChannelVariable;
import spinja.promela.compiler.variable.Variable;
import spinja.promela.compiler.variable.VariableAccess;
import spinja.promela.compiler.variable.VariableType;

public class ChannelSizeExpression extends Expression {
	/**
	 * 
	 */
	private ChannelVariable var;

	public ChannelSizeExpression(ChannelVariable var) {
		super(new Token(PromelaConstants.NUMBER,LTSminTreeWalker.wrapNameForChannelDesc(LTSminTreeWalker.state_var_desc.get(var))+".filled"));
		this.var = var;
	}
	public Set<VariableAccess> readVariables() {
		return null;
	}
	public VariableType getResultType() throws ParseException {
		return null;
	}

	public Variable getVariable() {
		return var;
	}

	@Override
	public String getIntExpression() {
		return "("+LTSminTreeWalker.C_STATE_TMP + "." + LTSminTreeWalker.wrapNameForChannelDesc(LTSminTreeWalker.state_var_desc.get(var))+".filled)";
	}

}
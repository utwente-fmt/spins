/**
 * 
 */
package spinja.promela.compiler.ltsmin.instr;

import java.util.Set;

import spinja.promela.compiler.expression.Expression;
import spinja.promela.compiler.parser.ParseException;
import spinja.promela.compiler.parser.PromelaConstants;
import spinja.promela.compiler.parser.Token;
import spinja.promela.compiler.variable.Variable;
import spinja.promela.compiler.variable.VariableAccess;
import spinja.promela.compiler.variable.VariableType;

public class ChannelSizeExpression extends Expression {
	/**
	 * 
	 */
	private Variable var;

	public ChannelSizeExpression(Variable var) {
		super(new Token(PromelaConstants.NUMBER, var.getName() +".filled"));
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
}
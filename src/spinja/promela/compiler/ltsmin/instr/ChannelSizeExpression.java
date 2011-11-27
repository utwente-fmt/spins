/**
 * 
 */
package spinja.promela.compiler.ltsmin.instr;

import static spinja.promela.compiler.ltsmin.LTSminStateVector.C_STATE_TMP;

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
	private String name;

	public ChannelSizeExpression(Variable var, String name) {
		super(new Token(PromelaConstants.NUMBER,name +".filled"));
		this.var = var;
		this.name = name;
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
		return "("+C_STATE_TMP + "." + name +".filled)";
	}

}
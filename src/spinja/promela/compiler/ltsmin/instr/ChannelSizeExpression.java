/**
 * 
 */
package spinja.promela.compiler.ltsmin.instr;

import java.util.Set;

import spinja.promela.compiler.expression.Expression;
import spinja.promela.compiler.expression.Identifier;
import spinja.promela.compiler.parser.ParseException;
import spinja.promela.compiler.parser.PromelaConstants;
import spinja.promela.compiler.parser.Token;
import spinja.promela.compiler.variable.VariableAccess;
import spinja.promela.compiler.variable.VariableType;

public class ChannelSizeExpression extends Expression {
	/**
	 * 
	 */
	private Identifier id;

	public ChannelSizeExpression(Identifier id) {
		super(new Token(PromelaConstants.NUMBER, id +".filled"));
		this.id = id;
	}
	public Set<VariableAccess> readVariables() {
		return null;
	}
	public VariableType getResultType() throws ParseException {
		return null;
	}

	public Identifier getIdentifier() {
		return id;
	}
}
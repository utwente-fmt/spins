/**
 * 
 */
package spinja.promela.compiler.ltsmin.model;

import java.util.HashSet;
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

	@Override
	public Set<VariableAccess> readVariables() {
		return new HashSet<VariableAccess>();
	}

	public VariableType getResultType() throws ParseException {
		return null;
	}

	public Identifier getIdentifier() {
		return id;
	}
}
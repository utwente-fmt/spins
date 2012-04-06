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

public class ChannelTopExpression extends Expression {
	/**
	 * 
	 */
	private int elem;
	private Identifier id;

	public ChannelTopExpression(Identifier id, int elem) {
		super(new Token(PromelaConstants.NUMBER,"[" + id.getVariable().getName() +".nextRead].m"+elem));
		this.id = id;
		this.elem = elem;
	}

	@Override
	public Set<VariableAccess> readVariables() {
		return new HashSet<VariableAccess>();
	}

	public VariableType getResultType() throws ParseException {
		return null;
	}

	public int getElem() {
		return elem;
	}

	public boolean equals(Object o) {
		if (o == null || !(o instanceof ChannelTopExpression))
			return false;
		ChannelTopExpression other = (ChannelTopExpression)o;
		return elem == other.elem && id.equals(other.id);
	}

	public Identifier getIdentifier() {
		return id;
	}
}
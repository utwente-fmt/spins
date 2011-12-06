package spinja.promela.compiler.ltsmin;

import spinja.promela.compiler.expression.Expression;
import spinja.util.StringWriter;

/**
 *
 * @author FIB
 */
public abstract class LTSminTransitionBase implements LTSminGuardContainer {
	private int trans;
	
	public LTSminTransitionBase(int group) {
		trans = group;
	}
	abstract public void prettyPrint(StringWriter w, LTSminTreeWalker printer);
	abstract public String getName();

	public int getGroup() {
		return trans;
	}

	public void addGuard(Expression e) {
		addGuard(new LTSminGuard(e));
	}
	
	public void setGroup(int trans) {
		this.trans = trans;
	}
}

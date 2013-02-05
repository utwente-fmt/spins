package spins.promela.compiler.ltsmin.state;

import spins.promela.compiler.expression.Identifier;
import spins.promela.compiler.ltsmin.LTSminPrinter.ExprPrinter;


public interface LTSminTypeStructI<T> extends LTSminTypeI, Iterable<T> {

	public String getName();

	public LTSminVariable getMember(String name);
	
	public void addMember(LTSminVariable var);

	public void fix();

	public String printIdentifier(ExprPrinter p, Identifier id);
}
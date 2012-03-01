package spinja.promela.compiler.ltsmin;

import spinja.promela.compiler.expression.Identifier;
import spinja.promela.compiler.ltsmin.LTSminPrinter.ExprPrinter;


public interface LTSminTypeStructI<T> extends LTSminTypeI, Iterable<T> {

	public String getName();

	public LTSminVariable getMember(String name);
	
	public void addMember(LTSminVariable var);

	public void fix();

	public String printIdentifier(ExprPrinter p, Identifier id);
}
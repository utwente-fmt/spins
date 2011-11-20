package spinja.promela.compiler.ltsmin;

import spinja.util.StringWriter;

/**
 *
 * @author FIB
 */
public interface LTSminTransitionBase {
	abstract public void prettyPrint(StringWriter w, LTSminTreeWalker printer);
	abstract public String getName();
}

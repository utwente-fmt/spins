package spinja.promela.compiler.ltsmin;

import spinja.util.StringWriter;

/**
 *
 * @author FIB
 */
public interface LTSminType {
	abstract public void prettyPrint(StringWriter w);
	abstract public String getName();
}

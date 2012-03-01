package spinja.promela.compiler.ltsmin.matrix;

import spinja.util.StringWriter;

/**
 *
 * @author FIB
 */
public interface LTSminGuardBase {
	abstract public void prettyPrint(StringWriter w);
	abstract public boolean isDefinitelyTrue();
	abstract public boolean isDefinitelyFalse();
}

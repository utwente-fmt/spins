package spinja.promela.compiler.ltsmin;

import java.util.ArrayList;
import java.util.List;
import spinja.util.StringWriter;

/**
 *
 * @author FIB
 */
public class LTSminTypeStruct implements LTSminType {
	public String name;
	public List<LTSminType> members;

	public String getName() {
		return name;
	}

	public LTSminTypeStruct(String name) {
		this.name = name;
		this.members = new ArrayList<LTSminType>();
	}

	public void prettyPrint(StringWriter w) {
		w.appendLine("struct ",name," {");
		w.indent();
		for(LTSminType m: members) {
			m.prettyPrint(w);
		}
		w.outdent();
		w.appendLine("}");
	}
}

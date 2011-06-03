package spinja.promela.compiler.ltsmin;

import spinja.util.StringWriter;

/**
 *
 * @author FIB
 */
public class LTSminTypeBasic implements LTSminType {
	String type_name;
	String name;
	int size;

	public String getName() {
		return name;
	}

	public LTSminTypeBasic(String type_name, String name, int size) {
		this.type_name = type_name;
		this.name = name;
		this.size = size;
	}

	public LTSminTypeBasic(String type_name, String name) {
		this.type_name = type_name;
		this.name = name;
		this.size = 0;
	}

	public void prettyPrint(StringWriter w) {
		w.appendPrefix();
		w.append(type_name).append(" ").append(name);
		if(size>1) w.append("[").append(size).append("]");
		w.appendPostfix();
	}

}

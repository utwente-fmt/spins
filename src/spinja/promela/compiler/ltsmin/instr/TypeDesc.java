package spinja.promela.compiler.ltsmin.instr;

/**
 * A class describing a variable type. It uses two strings to depict the
 * type name and an optional array component.
 * Instances are returned by getCTypeOfVar().
 */
public class TypeDesc {
	public String type;
	public String array;
	public TypeDesc() {
		this("","");
	}
	public TypeDesc(String type) {
		this(type,"");
	}
	public TypeDesc(String type, String array) {
		this.type = type;
		this.array = array;
	}
}
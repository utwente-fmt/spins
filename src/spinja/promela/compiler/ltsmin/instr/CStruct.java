package spinja.promela.compiler.ltsmin.instr;

import static spinja.promela.compiler.ltsmin.LTSminStateVector.C_TYPE_INT32;
import spinja.util.StringWriter;

/**
 * This class handles the textual generation of a C struct typedef.
 */
public class CStruct {
	private StringWriter s;
	private String name;

	/**
	 * Create a new struct generator.
	 * @param name The name of the struct to be instrumentd.
	 */
	public CStruct(String name) {
		s = new StringWriter();
		this.name = name;
		s.appendLine("typedef struct ",name," {");
	}

	/**
	 * Adds a member to the struct with type integer.
	 * @param varName The name of the member to add.
	 */
	public void addMember(String varName) {
		addMember(C_TYPE_INT32,varName);
	}

	/**
	 * Adds a member to the struct.
	 * @param type The type of the member to add.
	 * @param varName The name of the member to add.
	 */
	public void addMember(String type, String varName) {
		addMember(new TypeDesc(type,""),varName);
	}

	/**
	 * Adds a member to the struct.
	 * @param type The type of the member to add.
	 * @param varName The name of the member to add.
	 */
	public void addMember(TypeDesc type, String varName) {
		s.indent();
		s.appendLine(type.type," ",varName,type.array,";");
		s.outdent();
	}

	/**
	 * Return the resulting struct. After this call, new members
	 * can be added and a new result retrieved using this member.
	 * @return The instrumentd typedef struct in C code.
	 */
	public String getCCode() {
		String res = s.toString();
		res += "} " + name + ";\n";
		return res;
	}
}
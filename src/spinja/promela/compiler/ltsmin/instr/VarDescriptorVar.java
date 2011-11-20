package spinja.promela.compiler.ltsmin.instr;

import java.util.ArrayList;
import java.util.List;


/**
 * Describes the name of a variable.
 * The returned descriptions will look like this:
 *   specified_name
 * The returned declaration will look like this:
 *   specified_name
 */
public class VarDescriptorVar extends VarDescriptor {
	private String name;

	/**
	 * Creates a new variable name descriptor.
	 * @param name The name of the variable
	 */
	public VarDescriptorVar(String name) {
		this.name = name;
	}


	/**
	 * Returns the descriptions of this node.
	 * The returned descriptions will look like this:
	 *   specified_name
	 * @return The descriptions.
	 */
	public List<String> extractDescription() {
		List<String> descs = new ArrayList<String>();
		descs.add(name);
		return descs;
	}

	/**
	 * Returns the declaration of this node.
	 * The returned declaration will look like this:
	 *   specified_name
	 * @return The declaration.
	 */
	public String extractDeclaration() {
		return name;
	}
}
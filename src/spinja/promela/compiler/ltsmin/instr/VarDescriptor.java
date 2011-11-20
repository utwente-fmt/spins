package spinja.promela.compiler.ltsmin.instr;

import java.util.List;

/**
 * VarDescriptor classes are used to describe the access to one or more
 * variables. Multiple can be combined in a tree-like structure. On a node,
 * the function extractDescription() can be called to obtain all the
 * possibilities. The function extractDeclaration() is used to obtain how
 * all the variables are declared.
 * For example, using these classes one can describe an array called foo of
 * 10 elements by using:
 * (new VarDescriptorArray(new VarDescriptorVar("foo"),10)).setType("int");
 * The function extractDeclaration() will return:
 *   "int foo[10]"
 * The function extractDescription() will return:
 *   foo[0]
 *   foo[1]
 *   ...
 *   foo[9]
 */
public abstract class VarDescriptor {
	protected String type;

	/**
	 * Returns the type of this variable descriptor.
	 * @return The type of this variable descriptor.
	 */
	public String getType() {
		return type;
	}

	/**
	 * Sets the type of this variable descriptor.
	 * @param type The new type of this variable descriptor.
	 */
	public void setType(String type) {
		this.type = type;
	}

	/**
	 * Returns all the possible variable descriptors of the tree starting
	 * at this node.
	 * @return Variable descriptors.
	 */
	abstract public List<String> extractDescription();

	/**
	 * Returns the declaration of the tree starting at this node.
	 * @return Declaration string.
	 */
	abstract public String extractDeclaration();
}
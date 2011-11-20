package spinja.promela.compiler.ltsmin.instr;

import java.util.ArrayList;
import java.util.List;


/**
 * VarDescriptorArray
 * The returned descriptions will look like this:
 *   child's_descriptions "[" 0..length "]"
 * The returned declaration will look like this:
 *   child's_declaration "[" length "]"
 */
public class VarDescriptorArray extends VarDescriptor {
	private VarDescriptor child;
	private int length;

	/**
	 * Creates a array descriptor.
	 * @param child The child of this node.
	 * @param length The length of the array to describe.
	 */
	public VarDescriptorArray(VarDescriptor child, int length) {
		this.child = child;
		this.length = length;
	}

	/**
	 * Returns the length of the array being described.
	 * @return The length of the array being described.
	 */
	public int getLength() {
		return length;
	}

	/**
	 * Sets the length of the array being described.
	 * @param length The new length of the array being described.
	 */
	public void setLength(int length) {
		this.length = length;
	}

	/**
	 * Sets the child of this node.
	 * @param child
	 */
	public void setChild(VarDescriptor child) {
		this.child = child;
	}

	/**
	 * Returns the child of this node.
	 * @return The child of this node.
	 */
	public VarDescriptor getChild() {
		return child;
	}

	/**
	 * Returns the descriptions of this node.
	 * The returned descriptions will look like this:
	 *   child's_descriptions "[" 0..length "]"
	 * @return The descriptions.
	 */
	public List<String> extractDescription() {
		List<String> descs = new ArrayList<String>();
		if(child!=null) {
			for(int i=0; i<length; ++i) {
				List<String> descs_child = child.extractDescription();
				for(String s: descs_child) {
					descs.add(s + "[" + i + "]");
				}
			}
		} else {
			throw new AssertionError("Failed to extract description: childless array");
		}
		return descs;
	}

	/**
	 * Returns the declaration of this node.
	 * The returned declaration will look like this:
	 *   child's_declaration "[" length "]"
	 * @return The declaration.
	 */
	public String extractDeclaration() {
		return child.extractDeclaration() + "[" + length + "]";
	}
}
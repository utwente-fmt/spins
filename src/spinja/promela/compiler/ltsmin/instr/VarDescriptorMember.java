package spinja.promela.compiler.ltsmin.instr;

import java.util.ArrayList;
import java.util.List;


/**
 * Describes a struct and its members.
 * The returned descriptions will look like this:
 *   struct's_descriptions "." members
 * The returned declaration will look like this:
 *   struct's_declaration
 */
public class VarDescriptorMember extends VarDescriptor {

	private VarDescriptor struct;
	private List<VarDescriptor> members;

	/**
	 * Creates a new struct descriptor.
	 * Add members to the struct with addMember()
	 * @param struct The descriptor for the struct.
	 */
	public VarDescriptorMember(VarDescriptor struct) {
		this.struct = struct;
		this.members = new ArrayList<VarDescriptor>();
	}

	/**
	 * Sets the struct descriptor of this node.
	 * @param child
	 */
	public void setStruct(VarDescriptor struct) {
		this.struct = struct;
	}

	/**
	 * Returns the struct descriptor of this node.
	 * @return The struct descriptor of this node.
	 */
	public VarDescriptor getStruct() {
		return struct;
	}

	/**
	 * Add a member to the list of members.
	 * @param member
	 */
	public void addMember(VarDescriptor member) {
		this.members.add(member);
	}

	/**
	 * Returns the descriptions of this node.
	 * The returned descriptions will look like this:
	 *   struct's_descriptions "." members
	 * @return The descriptions.
	 */
	public List<String> extractDescription() {
		List<String> descs = new ArrayList<String>();
		if(struct==null) {
			throw new AssertionError("Failed to extract description: invalid struct");
		}
		if(members==null) {
			throw new AssertionError("Failed to extract description: invalid member");
		}
		List<String> descs_struct = struct.extractDescription();
		for(String s_s: descs_struct) {
			for(VarDescriptor member: members) {
				List<String> descs_member = member.extractDescription();
				for(String m_s: descs_member) {
					descs.add(s_s + "." + m_s);
				}
			}
		}
		return descs;
	}

	/**
	 * Returns the declaration of this node.
	 * The returned declaration will look like this:
	 *   struct's_declaration
	 * @return The declaration.
	 */
	public String extractDeclaration() {
		return struct.extractDeclaration();
	}

}
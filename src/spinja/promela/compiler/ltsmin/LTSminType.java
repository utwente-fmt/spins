package spinja.promela.compiler.ltsmin;

/**
 *
 * @author FIB
 */
public abstract class LTSminType {
	static protected final String TYPE_PREFIX = "type_";
	protected boolean unmodifiable = false; 
	protected int length;

	abstract public String getName();
	abstract protected int length_();
	abstract public String toString();
	/**
	 * Calculate offset of variables within structs
	 */
	public abstract void fix();
	
	/**
	 * Get variables length.
	 * SIDE EFFECT: make class unmodifiable.
	 * @return length
	 */
	public int length() {
		int l = length_();
		unmodifiable = true;
		return l;
	}
}

package spinja.promela.compiler.ltsmin;

import spinja.promela.compiler.variable.Variable;

public class LTSminTypeNative extends LTSminType {

	private static final String C_TYPE_INT8   = "sj_int8";
	private static final String C_TYPE_INT16  = "sj_int16";
	private static final String C_TYPE_INT32  = "sj_int32";
	private static final String C_TYPE_UINT1  = "sj_uint1";
	private static final String C_TYPE_UINT8  = "sj_uint8";
	private static final String C_TYPE_UINT16 = "sj_uint16";
	private static final String C_TYPE_UINT32 = "sj_uint32";

	public static final LTSminTypeNative TYPE_BOOL = new LTSminTypeNative(C_TYPE_UINT1); 
	public static final LTSminTypeNative TYPE_INT8 = new LTSminTypeNative(C_TYPE_INT8); 
	public static final LTSminTypeNative TYPE_INT16 = new LTSminTypeNative(C_TYPE_INT16); 
	public static final LTSminTypeNative TYPE_INT32 = new LTSminTypeNative(C_TYPE_INT32); 
	public static final LTSminTypeNative TYPE_UINT8 = new LTSminTypeNative(C_TYPE_UINT8); 
	public static final LTSminTypeNative TYPE_UINT16 = new LTSminTypeNative(C_TYPE_UINT16); 
	public static final LTSminTypeNative TYPE_UINT32 = new LTSminTypeNative(C_TYPE_UINT32); 

	Variable var = null;
	String name;
	
	private LTSminTypeNative(String name) {
		this.name = name;
	}

	public LTSminTypeNative(Variable var) {
		this(getCType(var));
		this.var = var;
	}

	public String toString() {
		return name;
	}
	
	@Override
	public String getName() {
		return name;
	}
	
	public static String getCType(Variable var)
			throws AssertionError {
		switch(var.getType().getBits()) {
			case 1:
				return C_TYPE_UINT1;
			case 8:
				return C_TYPE_UINT8;
			case 16:
				return C_TYPE_INT16;
			case 32:
				return C_TYPE_INT32;
			default:
				throw new AssertionError("ERROR: Unable to handle: " + var.getName());
		}
	}

	@Override
	protected int length_() {
		return 1; // always one slot
	}

	@Override
	public void fix() {}
}

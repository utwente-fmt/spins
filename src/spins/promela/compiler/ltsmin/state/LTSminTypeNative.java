package spins.promela.compiler.ltsmin.state;

import java.util.HashMap;
import java.util.Map;

import spins.promela.compiler.expression.Identifier;
import spins.promela.compiler.ltsmin.LTSminPrinter.ExprPrinter;
import spins.promela.compiler.variable.Variable;

/**
 * A native type.
 * 
 * @author laarman
 */
public class LTSminTypeNative extends LTSminTypeImpl {

	private static final String C_TYPE_INT8   = "sj_int8";
	private static final String C_TYPE_INT16  = "sj_int16";
	private static final String C_TYPE_INT32  = "sj_int32";
	private static final String C_TYPE_UINT1  = "sj_uint1";
	private static final String C_TYPE_UINT8  = "sj_uint8";
	private static final String C_TYPE_UINT16 = "sj_uint16";
	private static final String C_TYPE_UINT32 = "sj_uint32";

	public static Map<String, LTSminTypeNative> types = new HashMap<String, LTSminTypeNative>();

	public static final String ACCESS = "var";
	public static final LTSminTypeNative TYPE_BOOL = new LTSminTypeNative(C_TYPE_UINT1);
	public static final LTSminTypeNative TYPE_INT8 = new LTSminTypeNative(C_TYPE_INT8);
	public static final LTSminTypeNative TYPE_INT16 = new LTSminTypeNative(C_TYPE_INT16);
	public static final LTSminTypeNative TYPE_INT32 = new LTSminTypeNative(C_TYPE_INT32);
	public static final LTSminTypeNative TYPE_UINT8 = new LTSminTypeNative(C_TYPE_UINT8);
	public static final LTSminTypeNative TYPE_UINT16 = new LTSminTypeNative(C_TYPE_UINT16);
	public static final LTSminTypeNative TYPE_UINT32 = new LTSminTypeNative(C_TYPE_UINT32);
	public static final LTSminTypeNative TYPE_PC = TYPE_INT8;

	
	Variable var = null;
	String name;
	
	private LTSminTypeNative(String name) {
		this.name = name;
		types.put(name,  this);
	}

	public static LTSminTypeNative get(Variable var) {
		LTSminTypeNative t = types.get(getCType(var));
		if (t == null) throw new RuntimeException("");
		return t;
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
		if (var.getType().getJavaName().equals("int")) {
			switch(var.getType().getBits()) {
				case 16:
					return C_TYPE_INT16; // SHORT
				case 32:
					return C_TYPE_INT32; // INT
				default:
					throw new AssertionError("ERROR: Unable to handle: " + var.getName());
			}
		} else if (var.getType().getJavaName().equals("uint")) {
			switch(var.getType().getBits()) {
				case 1:
					return C_TYPE_UINT1; // BIT / BOOL
				case 8:
					return C_TYPE_UINT8; // BYTE
				case 16:
					return C_TYPE_UINT32; // PC
				default:
					throw new AssertionError("ERROR: Unable to handle: " + var.getName() +" ("+ var.getType().getJavaName() + ").");
			}
		} else {
			throw new AssertionError("ERROR: Unable to handle: " + var.getName());
		}
	}

	@Override
	protected int length_() {
		return 1; // always one slot
	}

	@Override
	public void fix() {}

	@Override
	public String printIdentifier(ExprPrinter p, Identifier id) {
		return ACCESS;
	}
}

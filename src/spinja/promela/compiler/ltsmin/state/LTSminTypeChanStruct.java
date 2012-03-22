package spinja.promela.compiler.ltsmin.state;

import spinja.promela.compiler.variable.ChannelVariable;
import spinja.promela.compiler.variable.Variable;
import spinja.promela.compiler.variable.VariableType;

/**
 * A type struct that has three fixed members.
 * 
 * @author laarman
 */
public class LTSminTypeChanStruct extends LTSminTypeStruct {

	public static final Variable CHAN_READ_VAR = new Variable(VariableType.SHORT, "nextRead", -1);
	public static final Variable CHAN_FILL_VAR = new Variable(VariableType.SHORT, "filled", -1);
	public static final String CHAN_BUF = "buffer";

	public static Variable bufferVar(ChannelVariable cv) {
		int size = cv.getType().getBufferSize();
		return new Variable(null, CHAN_BUF, size);
	}

	public static Variable elemVar(int index) {
		return new Variable(null, elemName(index), -1);
	}
	
	private static final String CHAN_PREFIX 	= "channel_";
	private static final String CHAN_BUF_PREFIX = "buffer_";
	private int elements = 0;
	
	public LTSminTypeChanStruct(ChannelVariable cv) {
		super();
		this.name = wrapName(cv.getName());
		addMember(new LTSminVariable(CHAN_READ_VAR, this));
		addMember(new LTSminVariable(CHAN_FILL_VAR, this));
		LTSminTypeStruct buf = new LTSminTypeStruct(CHAN_BUF_PREFIX + cv.getName());
		for (Variable var : cv.getType().getVariableStore().getVariables())
			buf.addMember(new LTSminVariable(new LTSminTypeNative(var), elemName(), this));
		addMember(new LTSminVariable(buf, CHAN_BUF, cv.getType().getBufferSize(), this));
	}

	private static String elemName(int index) {
		return "m"+ index;
	}
	
	private String elemName() {
		return elemName(elements++);
	}

	@Override
	protected String wrapName(String name) {
		return TYPE_PREFIX + CHAN_PREFIX + name +"_t";
	}
}

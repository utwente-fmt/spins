package spinja.promela.compiler.ltsmin;

import spinja.promela.compiler.variable.ChannelVariable;
import spinja.promela.compiler.variable.Variable;
import spinja.promela.compiler.variable.VariableType;

public class LTSminTypeChanStruct extends LTSminTypeStruct {

	public static final Variable CHAN_READ_VAR = new Variable(VariableType.SHORT, "nextRead", 0);
	public static final Variable CHAN_FILL_VAR = new Variable(VariableType.SHORT, "filled", 0);

	private static final String CHAN_PREFIX 	= "channel_";
	private static final String CHAN_BUF_PREFIX = "buffer_";
	int elements = 0;
	
	public LTSminTypeChanStruct(ChannelVariable cv) {
		super();
		this.name = wrapName(cv.getName());
		addMember(new LTSminVariable(CHAN_READ_VAR));
		addMember(new LTSminVariable(CHAN_FILL_VAR));
		LTSminTypeStruct buf = new LTSminTypeStruct(CHAN_BUF_PREFIX + cv.getName());
		for(Variable var : cv.getType().getVariableStore().getVariables())
			buf.addMember(new LTSminVariable(new LTSminTypeNative(var), elemName()));
		addMember(new LTSminVariable(buf, "buffer", cv.getType().getBufferSize()));
	}

	private String elemName() {
		return "m"+ elements++;
	}

	@Override
	protected String wrapName(String name) {
		return TYPE_PREFIX + CHAN_PREFIX + name +"_t";
	}
}

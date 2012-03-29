package spinja.promela.compiler.ltsmin;

public class LTSminDebug {
	
	public static enum MessageKind {
		DEBUG,
		NORMAL,
		ERROR,
		FATAL
	}

	public int say_indent = 0;
	private boolean verbose;

	public LTSminDebug(boolean verbose) {
		this.verbose = verbose;
	}

	public void say(String s) {
		say(MessageKind.NORMAL, s);
	}

	public void say(MessageKind k, String s) {
		switch (k) {
		case DEBUG:
			if (!verbose) break;
		case NORMAL:
			for(int n=say_indent; n-->0;)
				System.out.print("  ");
			System.out.println(s);
			break;
		case ERROR:
			for(int n=say_indent; n-->0;)
				System.err.print("  ");
			System.err.println(s);
			break;
		case FATAL:
			for(int n=say_indent; n-->0;)
				System.err.print("  ");
			System.err.println(s);
			System.exit(-100);
		default: throw new AssertionError("Unimplemented debug message kind: "+ k);
		}
	}
}
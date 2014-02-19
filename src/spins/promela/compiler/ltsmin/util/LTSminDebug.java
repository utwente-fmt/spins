package spins.promela.compiler.ltsmin.util;

import java.io.PrintStream;

public class LTSminDebug {
	static final String TAB = "   ";
	
	public static enum MessageKind {
		DEBUG,
		NORMAL,
		ERROR,
		FATAL,
		WARNING
	}

	public int say_indent = 0;
	private boolean verbose;
    private boolean said = true;
    private boolean silent = false;

	public LTSminDebug(boolean verbose) {
		this.verbose = verbose;
	}

    public LTSminDebug(boolean verbose, boolean silent) {
        this.verbose = verbose;
        this.silent = silent;
    }

	public LTSminDebug say(Object s) {
		say(MessageKind.NORMAL, s.toString());
		return this;
	}

	public LTSminDebug say(MessageKind k, Object s) {
	    if (silent) return this;
		switch (k) {
		case DEBUG:
			if (!verbose) break;
		case NORMAL:
            tabs(System.out);
			System.out.println(s);
			break;
		case WARNING:
		case ERROR:
			tabs(System.err);
			System.err.println(s);
			break;
		case FATAL:
			tabs(System.err);
			System.err.println(s);
			System.exit(-100);
		default: throw new AssertionError("Unimplemented debug message kind: "+ k);
		}
		said = true;
		return this;
	}

    private void tabs(PrintStream stream) {
        if (said) {
            for (int n = say_indent; n > 0; n--) {
                stream.print(TAB);
            }
        }
    }

    public LTSminDebug carReturn() {
        carReturn(MessageKind.NORMAL);
        return this;
    }

    public LTSminDebug carReturn(MessageKind k) {
        if (silent) return this;
        switch (k) {
        case DEBUG:
            if (!verbose) break;
        case NORMAL:
            System.out.print("\r");
            break;
        case WARNING:
        case ERROR:
        case FATAL:
            System.err.print("\r");
            break;
        default: throw new AssertionError("Unimplemented debug message kind: "+ k);
        }
        return this;
    }

    public LTSminDebug add(String s) {
        add(MessageKind.NORMAL, s);
        return this;
    }

    public LTSminDebug add(MessageKind k, String s) {
        if (silent) return this;
        switch (k) {
        case DEBUG:
            if (!verbose) break;
        case NORMAL:
            tabs(System.out);
            System.out.print(s);
            break;
        case WARNING:
        case ERROR:
        case FATAL:
            tabs(System.err);
            System.err.print(s);
            break;
        default: throw new AssertionError("Unimplemented debug message kind: "+ k);
        }
        said = false;
        return this;
    }

    public LTSminDebug addDone() {
        said = true;
        return this;
    }

    public LTSminDebug say(String string, Object ... objs) {
        return say(String.format(string, objs));
    }

    public LTSminDebug add(String string, Object ... objs) {
        return add(String.format(string, objs));
    }

    public boolean isVerbose() {
        return verbose;
    }
}
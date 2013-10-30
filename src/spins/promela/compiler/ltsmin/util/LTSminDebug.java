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
	}

    private void tabs(PrintStream stream) {
        if (said) {
            for (int n = say_indent; n > 0; n--) {
                stream.print(TAB);
            }
        }
    }

    public void carReturn() {
        carReturn(MessageKind.NORMAL);
    }

    public void carReturn(MessageKind k) {
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
    }

    public void add(String s) {
        add(MessageKind.NORMAL, s);
    }

    public void add(MessageKind k, String s) {
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
    }

    public void addDone() {
        said = true;
    }
}
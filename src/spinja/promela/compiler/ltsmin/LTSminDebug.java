package spinja.promela.compiler.ltsmin;

public class LTSminDebug {
	public int say_indent = 0;

	public LTSminDebug() {}

	public void say(String s) {
		for(int n=say_indent; n-->0;) {
			System.out.print("  ");
		}
		System.out.println(s);
	}
}
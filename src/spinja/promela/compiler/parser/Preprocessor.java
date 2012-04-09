package spinja.promela.compiler.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.Stack;

/**
 *
 * @author FIB
 */
public class Preprocessor {

	private static String dirName;
	private static String fileName;

	private static Stack<Map<String, DefineMapping>> defs = new Stack<Map<String, DefineMapping>>();
	private static Map<String, DefineMapping> defines = new HashMap<String, DefineMapping>();

	public static class DefineMapping {
		public DefineMapping() {
			this.parameters = new ArrayList<String>();
		}
		public boolean inline = false;
		public String name;
		public int length = 0;
		public int line = -1;
		public int column = -1;
		public String defineText;
		public List<String> parameters;
		public int size() {
			return parameters.size();
		}
		public String toString() {
			String str = "#define "+ name;
			if (inline || size() > 0) {
				str += "(";
				int i = 0;
				for (String param : parameters) {
					if (i++ > 0) str += ",";
					str += param;
				}
				str += ")";
			}
			str += "\t"+ defineText;
			return str;
		}
	}

	public static int level = 0; // inside comment counters

	// stacks of preprocessing reinitialized streams, defines and ifs.
    public static Stack<SimpleCharStream> preprocessing = new Stack<SimpleCharStream>();
	public static Stack<DefineMapping> current = new Stack<DefineMapping>();
	public static Stack<Boolean> ifs = new Stack<Boolean>();

	public static DefineMapping define = new DefineMapping();
	
	public static String getDirName() {
		return dirName;
	}

	public static void setDirname(String name) {
		dirName = name;
	}

	public static String getFileName() {
		return fileName;
	}

	public static DefineMapping defines(String s) {
		DefineMapping map = defines.get(s);
		if (map != null) {
			//System.out.println("> "+ map.toString());
			return map;
		}
		for (Map<String, DefineMapping> defines : defs) {
			map = defines.get(s);
			if (map != null) {
				//System.out.println("> "+ map.toString());
				return map;
			}
		}
		return null;
	}

	public static void setFilename(String fileName) {
		Preprocessor.fileName = fileName;
	}

	public static void addDefine(String text, boolean inline) {
		try {
			define.inline = inline;
			define.defineText = text;
			DefineMapping put = defines.put(define.name, define);
			if (null != put)
				System.err.println("Redefining preprocessor define "+ define.name +" --> '"+ put.defineText +"' with '"+ text +"'");
			//System.out.println("< "+ define.toString());
			define = new DefineMapping();
		} catch(NoSuchElementException e) {
			System.out.println("error parsing '"+ text +"'\n"+e);
		} catch(IllegalStateException e) {
			System.out.println("error parsing '"+ text +"'\n"+e);
		}
	}

	public static void pushDefines() {
		defs.push(defines);
		defines = new HashMap<String, DefineMapping>();
	}

	public static void removeDefines() {
		defines = defs.pop();
	}

	public static String parseFile(String s) {
	    Scanner sc = new Scanner(s);
	    String text = "";
	    while (sc.hasNext())
	        text += sc.nextLine();
	    text = text.trim();
	    if (!(text.startsWith("\"") && text.endsWith("\"")))
	        throw new AssertionError("Wrong include definition:"+ text);
	    return dirName +"/"+ text.substring(1, text.length()-1);
	}

	public static void line(SimpleCharStream input_stream, String s) {
		Scanner sc = new Scanner(s);
		try {
			int line = Integer.parseInt(sc.next());
			System.out.println("Setting line nr to " + line);
			input_stream.adjustBeginLineColumn(line-2,0);
		} catch(NoSuchElementException e) {
		} catch(IllegalStateException e) {
		}
	}

	public static void file(SimpleCharStream input_stream, String s) {
		Scanner sc = new Scanner(s);
		try {
			String file = sc.next();
			System.out.println("Setting file name to " + file);
			fileName = file;
		} catch(NoSuchElementException e) {
		} catch(IllegalStateException e) {
		}
	}
}

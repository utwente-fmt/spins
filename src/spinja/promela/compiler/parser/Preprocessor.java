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
	public static class DefineMapping {
		public DefineMapping(String text, List<String> parameters2) {
			this.defineText = text;
			this.parameters = parameters2;
		}
		public boolean inline = false;
		public String name;
		public String defineText;
		public List<String> parameters = new ArrayList<String>();
		public int size() {
			return parameters.size();
		}
	}
	
	private static String dirName;
	private static String fileName;
	private static Map<String, DefineMapping> defines = new HashMap<String, DefineMapping>();
    public static Stack<SimpleCharStream> preprocessing = new Stack<SimpleCharStream>();
	public static boolean parsing = false;
	public static String defineId;
	public static Stack<DefineMapping> current = new Stack<DefineMapping>();
	public static int parameterLength;
	public static List<String> parameters = new ArrayList<String>();
	public static Stack<Boolean> ifs = new Stack<Boolean>();

	public static int level = 0;
	
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
		return defines.get(s);
	}

	public static void setFilename(String fileName) {
		Preprocessor.fileName = fileName;
	}

	public static void addDefine(String text, boolean inline) {
		try {
			DefineMapping m = new DefineMapping(text, parameters);
			parameters = new ArrayList<String>();
			m.name = defineId;
			m.inline = inline;
			DefineMapping put = defines.put(defineId, m);
			if (null != put)
				System.err.println("Overwriting preprocessor define "+ defineId +" --> '"+ put.defineText +"' with '"+ text +"'\n");
		} catch(NoSuchElementException e) {
			System.out.println("error parsing '"+ text +"'\n"+e);
		} catch(IllegalStateException e) {
			System.out.println("error parsing '"+ text +"'\n"+e);
		}
	}

	public static void removeDefine(String s) {
		defines.remove(s);
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

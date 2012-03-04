package spinja.promela.compiler.parser;

import java.util.HashMap;
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
	private static Map<String, String> defines = new HashMap<String, String>();
    public static Stack<SimpleCharStream> preprocessing = new Stack<SimpleCharStream>();

	public static String getDirName() {
		return dirName;
	}

	public static void setDirname(String name) {
		dirName = name;
	}

	public static String getFileName() {
		return fileName;
	}

	public static String defines(String s) {
		return defines.get(s);
	}

	public static void setFilename(String fileName) {
		Preprocessor.fileName = fileName;
	}

	public static void addDefine(SimpleCharStream input_stream, String s) {
		Scanner sc = new Scanner(s);
		try {
			String id = sc.next();
			String text = "";
			while (sc.hasNext())
				text += sc.nextLine();
			defines.put(id, text.trim());
		} catch(NoSuchElementException e) {
			System.out.println("error parsing "+ s +"\n"+e);
		} catch(IllegalStateException e) {
			System.out.println("error parsing "+ s +"\n"+e);
		}
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

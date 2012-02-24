package spinja.promela.compiler.parser;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;

/**
 *
 * @author FIB
 */
public class Preprocessor {
	private static String fileName;
	private static Map<String, String> defines = new HashMap<String, String>();
	private static List<Promela> includes = new LinkedList<Promela>();

	public static String getFileName() {
		return fileName;
	}

	public static String defines(String s) {
		return defines.get(s);
	}

	public static void setFilename(String fileName) {
		Preprocessor.fileName = fileName;
	}
	
	public static void addInclude(Promela p) {
		includes.add(p);
	}
	
	public static List<Promela> getInclude() {
		return includes;
	}
	
	public static void process(SimpleCharStream input_stream, String s) {
		Scanner sc = new Scanner(s);

		String command = sc.next();

		if(command.equals("line")) {
			try {
				int line = Integer.parseInt(sc.next());
				System.out.println("Setting line nr to " + line);
				input_stream.adjustBeginLineColumn(line-2,0);
			} catch(NoSuchElementException e) {
			} catch(IllegalStateException e) {
			}
		} else if(command.equals("file")) {
			try {
				String file = sc.next();
				System.out.println("Setting file name to " + file);
				fileName = file;
			} catch(NoSuchElementException e) {
			} catch(IllegalStateException e) {
			}
		} else if(command.equals("define")) {
			try {
				String id = sc.next();
				String text = "";
				while (sc.hasNext())
					text += sc.nextLine();
				//System.out.println("Setting define "+ id +" name to '" + text.trim() +"'");
				defines.put(id, text.trim());
			} catch(NoSuchElementException e) {
				System.out.println("error parsing "+ s +"\n"+e);
			} catch(IllegalStateException e) {
				System.out.println("error parsing "+ s +"\n"+e);
			}
		} else {
			System.out.println("Unknown preprocessor command: "+ command);
		}
	}
}

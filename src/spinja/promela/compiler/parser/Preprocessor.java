package spinja.promela.compiler.parser;

import java.util.NoSuchElementException;
import java.util.Scanner;

/**
 *
 * @author FIB
 */
public class Preprocessor {
	private static String fileName;

	public static String getFileName() {
		return fileName;
	}

	public static void setFilename(String fileName) {
		Preprocessor.fileName = fileName;
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
		} else {
			System.out.println("Unknown preprocessor command");
		}
	}
}

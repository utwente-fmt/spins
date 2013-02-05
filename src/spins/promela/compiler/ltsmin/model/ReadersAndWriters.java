package spins.promela.compiler.ltsmin.model;

import java.util.ArrayList;
import java.util.List;

/**
 * The ReadersAndWriters class holds a list of SendActions and
 * ReadActions.
 */
public class ReadersAndWriters {
	public List<SendAction> sendActions;
	public List<ReadAction> readActions;

	/**
	 * Create a new ReadersAndWriters.
	 * The two lists will be initialised using an empty list.
	 */
	public ReadersAndWriters() {
		sendActions = new ArrayList<SendAction>();
		readActions = new ArrayList<ReadAction>();
	}

}
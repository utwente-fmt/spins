package spins.util;

import java.util.Iterator;

public class UnModifiableIterator <T> implements Iterator<T> {

    private final Iterator<T> it;
    
    public UnModifiableIterator(Iterator<T> it) {
        this.it = it;
    }

    public boolean hasNext() {
		return it.hasNext();
	}

	public T next() {
		return it.next();
	}

	public void remove() {
		throw new UnsupportedOperationException();
	}
}


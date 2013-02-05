package spins.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sun.xml.internal.ws.server.UnsupportedMediaException;

public class IndexedSet<T> implements Set<T> {

    private List<T> list;
    private Map<T, Integer> map;

    public IndexedSet() {
      this.map = new HashMap<T, Integer>();
      this.list = new ArrayList<T>();
    }

    @Override
    public int size() {
        return list.size();
    }

    @Override
    public boolean isEmpty() {
        return list.isEmpty();
    }

    /**
     * @param key
     * @return
     * @see java.util.Map#containsKey(java.lang.Object)
     */
    public boolean containsKey(Object key) {
        return map.containsKey(key);
    }

    /**
     * @param value
     * @return
     * @see java.util.Map#containsValue(java.lang.Object)
     */
    public boolean containsValue(Object value) {
        if (value instanceof Integer)
            return ((Integer)value).intValue() < list.size();
        else return false;
    }

    /**
     * @param key
     * @return
     * @see java.util.Map#get(java.lang.Object)
     */
    public Integer get(Object key) {
        return map.get(key);
    }

    /**
     * @param key
     * @return
     * @see java.util.Map#get(java.lang.Object)
     */
    public T getIndex(int index) {
        return list.get(index);
    }

    /**
     * @param key
     * @param value
     * @return
     * @see java.util.Map#put(java.lang.Object, java.lang.Object)
     */
    public boolean add(T key) {
        Integer n = map.put(key, list.size());
        if (n != null) throw new AssertionError("Adding the same key twice to indexed set:" +key);
        list.add(key);
        return true;
    }

    /**
     * @param key
     * @return
     * @see java.util.Map#remove(java.lang.Object)
     */
    public boolean remove(Object key) {
        throw new UnsupportedMediaException();
    }

    /**
     * @param m
     * @see java.util.Map#putAll(java.util.Map)
     */
    public boolean addAll(Collection<? extends T> m) {
        boolean changed = false;
        for (T t : m) {
            changed |= this.add(t);
        }
        return changed;
    }

    /**
     *
     * @see java.util.Map#clear()
     */
    public void clear() {
        map.clear();
        list.clear();
    }

    /**
     * @param o
     * @return
     * @see java.util.Map#equals(java.lang.Object)
     */
    public boolean equals(Object o) {
        return list.equals(o);
    }

    /**
     * @return
     * @see java.util.Map#hashCode()
     */
    public int hashCode() {
        return list.hashCode();
    }

    @Override
    public boolean contains(Object o) {
        return map.containsKey(o);
    }

    @Override
    public Iterator<T> iterator() {
        return list.iterator();
    }

    @Override
    public Object[] toArray() {
        return list.toArray();
    }

    @SuppressWarnings("hiding")
    @Override
    public <T> T[] toArray(T[] a) {
        return list.toArray(a);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        for (Object t : c) {
            if (!this.contains(t)) return false;
        }
        return true;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedMediaException();
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedMediaException();
    }
}

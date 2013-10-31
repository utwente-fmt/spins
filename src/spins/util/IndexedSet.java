package spins.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class IndexedSet<T> implements Set<T> {

    private List<T> list;
    private Map<T, Integer> map;

    public IndexedSet() {
      this.map = new HashMap<T, Integer>();
      this.list = new ArrayList<T>();
    }

    public int size() {
        return list.size();
    }

    public boolean isEmpty() {
        return list.isEmpty();
    }

    /**
     * @param key
     * @return
     * @see java.util.Map#containsKey(T)
     */
    public boolean containsKey(T key) {
        return map.containsKey(key);
    }

    /**
     * @param value
     * @return
     * @see java.util.Map#containsValue(T)
     */
    public boolean containsValue(T value) {
        if (value instanceof Integer)
            return ((Integer)value).intValue() < list.size();
        else return false;
    }

    /**
     * @param key
     * @return
     * @see java.util.Map#get(T)
     */
    public Integer get(T key) {
        return map.get(key);
    }

    public int get2(T key) {
        Integer result = map.get(key);
        return result == null ? -1 : result.intValue();
    }

    /**
     * @param key
     * @return
     * @see java.util.Map#get(index)
     */
    public T get(int index) {
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

    public boolean add2(T key) {
        map.put(key, list.size());
        list.add(key);
        return true;
    }

    public int addGet(T key) {
        int index = list.size();
        Integer result = map.put(key, index);
        if (result != null) throw new AssertionError("Adding the same key twice to indexed set:" +key);
        list.add(key);
        return index;
    }

    /**
     * @param key
     * @return
     * @see java.util.Map#remove(java.lang.Object)
     */
    public boolean remove(Object key) {
        throw new UnsupportedOperationException();
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

    public boolean contains(Object o) {
        return map.containsKey(o);
    }

    public Iterator<T> iterator() {
        return list.iterator();
    }

    public Object[] toArray() {
        return list.toArray();
    }

    @SuppressWarnings("hiding")
    public <T> T[] toArray(T[] a) {
        return list.toArray(a);
    }

    public boolean containsAll(Collection<?> c) {
        for (Object t : c) {
            if (!this.contains(t)) return false;
        }
        return true;
    }

    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    public List<T> list() {
        return list;
    }
}

package com.massimodz8.collaborativegrouporder;

import android.support.annotation.Nullable;

import java.util.ArrayList;

/**
 * Created by Massimo on 21/05/2016.
 * A stack which also allows to pull out elements from any position. Wait, what?
 * Ok, here's the deal. Ideally, there's a stack of Activity objects and we register some of their
 * callbacks to e.g. AdventuringService to be notified of changes.
 *
 * Fine! Except when it isn't.
 *
 * There's an option to destroy Activity objects as soon as the user leaves (not only when back) and
 * this option is active by default on some devices.
 * So there's no real activity stack but just the current activity!
 *
 * This means I need a stack which also allows activities to remove themsevles from the list. Ouch.
 */
public class PseudoStack<E> {
    /**
     * Add a new element and return its id. It is not necessarily an index. This number uniquely
     * identifies the element and is always at least 0. Use it to unregister.
     */
    public int put(E entry) {
        all.add(new Identified<E>(entry, nextId));
        return nextId++;
    }

    /**
     * Remove an id and return true. If ID not found do nothing and return false.
     */
    public boolean remove(int id) {
        int index = 0;
        for (Identified<E> test : all) {
            if(test.id == id) {
                all.remove(index);
                return true;
            }
            index++;
        }

        return false;
    }

    public E get() { return all.isEmpty()? null : all.get(all.size() - 1).object; }


    private ArrayList<Identified<E>> all = new ArrayList<>();

    private static class Identified<T> {
        final public T object;
        final int id;

        private Identified(T object, int id) {
            this.object = object;
            this.id = id;
        }
    }
    private int nextId;
}

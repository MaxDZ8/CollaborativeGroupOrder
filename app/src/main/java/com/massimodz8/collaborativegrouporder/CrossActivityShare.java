package com.massimodz8.collaborativegrouporder;

import android.app.Application;
import android.os.Bundle;

import java.util.HashMap;

/**
 * Created by Massimo on 05/02/2016.
 * In origin it was the (local) CrossActivityService.
 * It always felt like an hammer solution. A simpler solution does exist: just inherit from
 * Application class and use it to keep persistent state.
 */
public class CrossActivityShare extends Application {
    long store(Object payload) {
        long gen = ++dataKey;
        manage.put(gen, payload);
        return gen;
    }
    Object release(long key) {
        return manage.remove(key);
    }

    public static long pullKey(Bundle from, String name) {
        if(from == null) return 0;
        return from.getLong(name, 0);
    }

    long dataKey; /// counts number of bindings created to assign them unique ids.
    HashMap<Long, Object> manage = new HashMap<>();

    public PartyInfo getGroupByName(String groupName) {
        return null;
    }
}

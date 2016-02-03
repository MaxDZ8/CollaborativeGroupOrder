package com.massimodz8.collaborativegrouporder;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

import com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage;

import java.util.HashMap;

/**
 * Created by Massimo on 01/02/2016.
 * The initial problem to solve was to pass a non-parcelable object (an open, connected Socket) to
 * some other activity. Since I support API 17 devices I cannot pass arbitrary objects by either
 * Intent or Bundle so I needed to have something staying alive between different activities.
 *
 * This is the communication channel I need. A local service can be bound and then its IBinder can
 * be cast to derived. The result is I have arbitrary data passing.
 *
 * Also, operations such as "scan the network for groups" can be interrupted by orientation changes.
 * Therefore, it really makes sense to keep some state outside of the standard Activity lifecycle.
 */
public class CrossActivityService extends Service {
    @Override
    public IBinder onBind(Intent intent) {
        Binder ret = new Binder();
        return ret;
    }

    /// Do not use Binder interface, use the special functions instead! You likely want to register
    /// or get an existing DataPack object.
    public class Binder extends android.os.Binder {
        long store(Object payload) {
            long gen = ++dataKey;
            manage.put(gen, payload);
            return gen;
        }
        Object release(long key) {
            return manage.remove(key);
        }

        public PersistentStorage.PartyOwnerData getGroupByName(String groupName) {
            return null;
        }
    }

    long dataKey; /// counts number of bindings created to assign them unique ids.
    HashMap<Long, Object> manage = new HashMap<>();

    public static long pullKey(Bundle from, String name) {
        if(from == null) return 0;
        return from.getLong(name, 0);
    }
}

package com.massimodz8.collaborativegrouporder;

import android.app.Service;
import android.content.Intent;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Binder;
import android.os.IBinder;

import java.util.Collections;
import java.util.HashMap;
import java.util.Queue;
import java.util.Vector;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;

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
        ProxyBinder ret = new ProxyBinder();
        return ret;
    }

    /// Do not use Binder interface, use the special functions instead! You likely want to register
    /// or get an existing CrossActivityData object.
    public class ProxyBinder extends Binder {
        long generate() {
            long gen = ++dataKey;
            CrossActivityData mangle = new CrossActivityData();
            manage.put(gen, mangle);
            return gen;
        }
        CrossActivityData get(long key) {
            return manage.get(key);
        }
        void release(long key) {
            CrossActivityData which = get(key);
            if(which == null) return;
            if(which.refCount == 1) manage.remove(key);
            else which.refCount--;
        }
        void addRef(long key) {
            CrossActivityData which = get(key);
            if(which == null) return;
            which.refCount++;
        }
    }

    public static class CrossActivityData {
        public static final int EVENT_DISCOVERY_START_FAILED = 0;
        public static final int EVENT_DISCOVERY_STOP_FAILED = 1;
        public static final int EVENT_DISCOVERY_STARTED = 2;
        public static final int EVENT_DISCOVERY_STOPPED = 3;
        public static final int EVENT_DISCOVERY_FOUND = 4;
        public static final int EVENT_DISCOVERY_LOST = 5;

        /**
         * Start network discovery of available services matching the specified type.
         * This behaves just like NsdManager.discoverServices but upgrades lifetime to span across
         * different Activity objects. Most importantly, this means the operation won't stop
         * if the device is rotated portrait/landscape mode!
         *
         * Do not call this when already discovering something. Two calls must always be interleaved
         * by at least one stopDiscovery() call or leads to undefined resuls.
         */
        void beginDiscovery(String serviceType, NsdManager nsd) {
            CrossActivityData.AccumulatingDiscoveryListener temp = new CrossActivityData.AccumulatingDiscoveryListener();
            nsd.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, temp);
            this.nsd = nsd;
            this.serviceType = serviceType;
            discovering = temp;
        }
        String isDiscovering() { return serviceType; }
        void stopDiscovery() {
            if(discovering != null) {
                nsd.stopServiceDiscovery(discovering);
                discovering = null;
                nsd = null;
                serviceType = null;
            }
        }

        private class AccumulatingDiscoveryListener implements NsdManager.DiscoveryListener {
            private Queue<Object> pending = new LinkedBlockingQueue<>();

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                pending.add(EVENT_DISCOVERY_START_FAILED);
                //pending.add(serviceType); // no need to keep track of this, it's the same as CrossActivityData.serviceType
                pending.add(errorCode);
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                pending.add(EVENT_DISCOVERY_STOP_FAILED);
                //pending.add(serviceType);
                pending.add(errorCode);
            }

            @Override
            public void onDiscoveryStarted(String serviceType) {
                pending.add(EVENT_DISCOVERY_STARTED);
                //pending.add(serviceType); // no need to keep track of this, it's the same as CrossActivityData.serviceType
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                pending.add(EVENT_DISCOVERY_STOPPED);
                //pending.add(serviceType);
            }

            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {
                pending.add(EVENT_DISCOVERY_FOUND);
                pending.add(serviceInfo);
            }

            @Override
            public void onServiceLost(NsdServiceInfo serviceInfo) {
                pending.add(EVENT_DISCOVERY_LOST);
                pending.add(serviceInfo);
            }

            public void dispatch(NsdManager.DiscoveryListener real, String serviceType) {
                while(pending.size() > 0) {
                    final int type = (Integer) pending.element();
                    switch(type) {
                        case EVENT_DISCOVERY_START_FAILED:
                        case EVENT_DISCOVERY_STOP_FAILED: {
                            Object errCode = pending.element();
                            if(type == EVENT_DISCOVERY_START_FAILED) real.onStartDiscoveryFailed(serviceType, (Integer) errCode);
                            else real.onStopDiscoveryFailed(serviceType, (Integer) errCode);
                            break;
                        }
                        case EVENT_DISCOVERY_STARTED: real.onDiscoveryStarted(serviceType); break;
                        case EVENT_DISCOVERY_STOPPED: real.onDiscoveryStopped(serviceType); break;
                        case EVENT_DISCOVERY_FOUND:
                        case EVENT_DISCOVERY_LOST: {
                            Object info = pending.element();
                            if(type == EVENT_DISCOVERY_FOUND) real.onServiceFound((NsdServiceInfo)info);
                            else real.onServiceLost((NsdServiceInfo)info);
                        }
                        default: System.exit(MainMenuActivity.REALLY_BAD_EXIT_REASON_INCOHERENT_CODE); // never happens!
                    }
                }
            }
        }

        private int refCount = 1;
        private NsdManager nsd;
        private String serviceType;
        private AccumulatingDiscoveryListener discovering;
    }

    long dataKey; /// counts number of bindings created to assign them unique ids.
    HashMap<Long, CrossActivityData> manage = new HashMap<>();
}

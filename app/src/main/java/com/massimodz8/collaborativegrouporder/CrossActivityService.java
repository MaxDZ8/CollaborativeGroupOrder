package com.massimodz8.collaborativegrouporder;

import android.app.Service;
import android.content.Intent;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Binder;
import android.os.IBinder;

import java.net.Socket;
import java.util.HashMap;
import java.util.Queue;
import java.util.Vector;
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
    /// or get an existing DataPack object.
    public class ProxyBinder extends Binder {
        long generate() {
            long gen = ++dataKey;
            DataPack mangle = new DataPack();
            manage.put(gen, mangle);
            return gen;
        }
        DataPack get(long key) {
            return manage.get(key);
        }
        void release(long key) {
            DataPack which = get(key);
            if(which == null) return;
            if(which.refCount == 1) manage.remove(key);
            else which.refCount--;
        }
        void addRef(long key) {
            DataPack which = get(key);
            if(which == null) return;
            which.refCount++;
        }
    }

    public static class FoundService {
        final NsdServiceInfo info;
        public Socket persist;

        public FoundService(NsdServiceInfo info) {
            this.info = info;
        }
    }

    public static class DataPack {
        public static final int DISCOVERY_START_STATUS_FAIL = -1;
        public static final int DISCOVERY_START_STATUS_OK   =  1;
        public static final int DISCOVERY_STOP_STATUS_FAIL  = -1;
        public static final int DISCOVERY_STOP_STATUS_OK    =  1;

        public Vector<FoundService> foundServices = new Vector<>();

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
            DataPack.AccumulatingDiscoveryListener temp = new DataPack.AccumulatingDiscoveryListener();
            nsd.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, temp);
            this.nsd = nsd;
            this.serviceType = serviceType;
            discovering = temp;
            discoveryStartStatus = 0;
            discoveryStopStatus = 0;
        }
        String discoveryStartAttempted() { return serviceType; }
        void stopDiscovery() {
            if(discovering != null) {
                nsd.stopServiceDiscovery(discovering);
                discovering = null;
                nsd = null;
                serviceType = null;
            }
        }
        int getDiscoveryStartStatus() { return discoveryStartStatus; }
        int getDiscoveryStopStatus() { return discoveryStopStatus; }


        private class AccumulatingDiscoveryListener implements NsdManager.DiscoveryListener {
            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) { discoveryStartStatus = DISCOVERY_START_STATUS_FAIL; }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) { discoveryStopStatus = DISCOVERY_STOP_STATUS_FAIL; }

            @Override
            public void onDiscoveryStarted(String serviceType) {  discoveryStartStatus = DISCOVERY_START_STATUS_OK; }

            @Override
            public void onDiscoveryStopped(String serviceType) { discoveryStopStatus = DISCOVERY_STOP_STATUS_OK; }

            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) { foundServices.add(new FoundService(serviceInfo)); }

            @Override
            public void onServiceLost(NsdServiceInfo serviceInfo) {
                int match;
                for(match = 0; match < foundServices.size(); match++) {
                    if(foundServices.elementAt(match).info == serviceInfo) break;
                }
                if(match < foundServices.size()) foundServices.remove(match); // impossible to NOT happen
            }
        }

        private int refCount = 1;
        private NsdManager nsd;
        private String serviceType;
        private AccumulatingDiscoveryListener discovering;
        private int discoveryStartStatus, discoveryStopStatus;
    }

    long dataKey; /// counts number of bindings created to assign them unique ids.
    HashMap<Long, DataPack> manage = new HashMap<>();
}

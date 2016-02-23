package com.massimodz8.collaborativegrouporder;

import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;

import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

/**
 * Created by Massimo on 03/02/2016.
 * This can be used to keep a list of network services discovered kept by CrossActivityService.
 * Because this can exist across different activities (as it is the case when the layout changes)
 * its internal list must be routinely scanned to find if something is there or not.
 *
 * Updated to cantain its own timer checking, see PublishedService.
 */
public class AccumulatingDiscoveryListener implements NsdManager.DiscoveryListener {
    public static final int IDLE = 0;
    public static final int STARTING = 1;
    public static final int START_FAILED = 2;
    public static final int EXPLORING = 3;
    public static final int STOPPING = 4;
    public static final int STOPPED = 5;
    public static final int STOP_FAILED = 6;

    interface OnStatusChanged {
        /**  Called every time the state changes. Those are called from a different thread.
         * If the new status reported is START_FAILED or STOPPED then no more
         * notifications will be generated.
         * @param old state of the publisher at previous call or IDLE for first.
         * @param current the new state, which is already set.
         */
        void newStatus(int old, int current);
    }

    public static class FoundService {
        final NsdServiceInfo info;
        public Socket socket; /// very handy to have this here with no fuss added.

        public FoundService(NsdServiceInfo info) {
            this.info = info;
        }
    }
    public Vector<FoundService> foundServices = new Vector<>();

    @Override
    public void onStartDiscoveryFailed(String serviceType, int errorCode) { status = START_FAILED; }

    @Override
    public void onStopDiscoveryFailed(String serviceType, int errorCode) { status = STOP_FAILED; }

    @Override
    public void onDiscoveryStarted(String serviceType) {  status = EXPLORING; }

    @Override
    public void onDiscoveryStopped(String serviceType) { status = STOPPED; }

    @Override
    public void onServiceFound(NsdServiceInfo serviceInfo) { foundServices.add(new FoundService(serviceInfo)); }

    @Override
    public void onServiceLost(NsdServiceInfo serviceInfo) {
        int match;
        for(match = 0; match < foundServices.size(); match++) {
            if(foundServices.elementAt(match).info.equals(serviceInfo)) break;
        }
        if(match < foundServices.size()) foundServices.remove(match); // impossible to NOT happen
    }

    /**
     * Start network discovery of available services matching the specified type.
     * This behaves just like NsdManager.discoverServices but upgrades lifetime to span across
     * different Activity objects. Most importantly, this means the operation won't stop
     * if the device is rotated portrait/landscape mode!
     *
     * Do not call this when already discovering something. Two calls must always be interleaved
     * by at least one stopDiscovery() call or leads to undefined resuls.
     */
    void beginDiscovery(String serviceType, NsdManager nsd, OnStatusChanged onStatusChanged) {
        this.nsd = nsd;
        this.serviceType = serviceType;
        status = STARTING;
        callback = onStatusChanged;
        checker = new Timer("network publisher status check");
        checker.schedule(new TimerTask() {
            int prevStatus =IDLE;
            @Override
            public void run() {
                if(prevStatus != status) {
                    int old = prevStatus;
                    prevStatus = status;
                    OnStatusChanged call = callback;
                    if(null != call) call.newStatus(old, status);
                    if(START_FAILED == status || STOPPED == status) cancel();
                }
            }
        }, DISCOVERY_PROBE_DELAY, DISCOVERY_PROBE_INTERVAL);
        nsd.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, this);
    }
    String startAttempted() { return serviceType; }
    public void setCallback(OnStatusChanged newTarget) { callback = newTarget; }
    public void unregisterCallback() { callback = null; }
    void stopDiscovery() {
        if(nsd != null) {
            unregisterCallback();
            nsd.stopServiceDiscovery(this);
            nsd = null;
            serviceType = null;
            status = STOPPING;
        }
    }
    int getDiscoveryStatus() { return status; }

    private NsdManager nsd;
    private String serviceType;
    Timer checker;
    volatile OnStatusChanged callback;

    static final int DISCOVERY_PROBE_DELAY = 1000;
    static final int DISCOVERY_PROBE_INTERVAL = 250;

    private int status = IDLE;
}

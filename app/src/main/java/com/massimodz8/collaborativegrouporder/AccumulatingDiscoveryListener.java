package com.massimodz8.collaborativegrouporder;

import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;

import java.net.Socket;
import java.util.Vector;

/**
 * Created by Massimo on 03/02/2016.
 * This can be used to keep a list of network services discovered kept by CrossActivityService.
 * Because this can exist across different activities (as it is the case when the layout changes)
 * its internal list must be routinely scanned to find if something is there or not.
 */
public class AccumulatingDiscoveryListener implements NsdManager.DiscoveryListener {
    public static final int IDLE = 0;
    public static final int STARTING = 1;
    public static final int START_FAILED = 2;
    public static final int EXPLORING = 3;
    public static final int STOPPING = 4;
    public static final int STOPPED = 5;
    public static final int STOP_FAILED = 6;

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
    void beginDiscovery(String serviceType, NsdManager nsd) {
        this.nsd = nsd;
        this.serviceType = serviceType;
        status = STARTING;
        nsd.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, this);
    }
    String startAttempted() { return serviceType; }
    void stopDiscovery() {
        if(nsd != null) {
            nsd.stopServiceDiscovery(this);
            nsd = null;
            serviceType = null;
            status = STOPPING;
        }
    }
    int getDiscoveryStatus() { return status; }

    private NsdManager nsd;
    private String serviceType;

    private int status = IDLE;
}

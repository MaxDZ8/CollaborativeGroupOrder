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
    public static final int DISCOVERY_START_STATUS_FAIL = -1;
    public static final int DISCOVERY_START_STATUS_OK   =  1;
    public static final int DISCOVERY_STOP_STATUS_FAIL  = -1;
    public static final int DISCOVERY_STOP_STATUS_OK    =  1;

    public static class FoundService {
        final NsdServiceInfo info;
        public Socket socket; /// very handy to have this here with no fuss added.

        public FoundService(NsdServiceInfo info) {
            this.info = info;
        }
    }
    public Vector<FoundService> foundServices = new Vector<>();

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
        nsd.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, this);
        this.nsd = nsd;
        this.serviceType = serviceType;
        discoveryStartStatus = 0;
        discoveryStopStatus = 0;
    }
    String discoveryStartAttempted() { return serviceType; }
    void stopDiscovery() {
        if(nsd != null) {
            nsd.stopServiceDiscovery(this);
            nsd = null;
            serviceType = null;
        }
    }
    int getDiscoveryStartStatus() { return discoveryStartStatus; }
    int getDiscoveryStopStatus() { return discoveryStopStatus; }

    private NsdManager nsd;
    private String serviceType;

    private int discoveryStartStatus, discoveryStopStatus;
}

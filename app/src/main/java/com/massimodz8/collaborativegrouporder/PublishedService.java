package com.massimodz8.collaborativegrouporder;

import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;

import java.net.ServerSocket;

/**
 * Created by Massimo on 03/02/2016.
 * Even just publishing a network service is more difficult when something must be done independantly of Activity.
 * In this case, we hold state for the ongoing creation of a group, the initial part is publishing.
 * As with service discovery, just poll state and figure out what to do.
 */
public class PublishedService implements NsdManager.RegistrationListener {
    public PublishedService(NsdManager nsd) {
        this.nsd = nsd;
    }

    static final int STATUS_IDLE = 0; // just created, doing nothing.
    static final int STATUS_STARTING = 1; // we have a service name and type
    static final int STATUS_START_FAILED = 2; // as above, plus we got an error code
    static final int STATUS_PUBLISHING = 3;
    static final int STATUS_STOP_FAILED = 4;
    static final int STATUS_STOPPED = 5;

    public void beginPublishing(ServerSocket shared, String serviceName, String serviceType) {
        NsdServiceInfo servInfo  = new NsdServiceInfo();
        servInfo.setServiceName(serviceName);
        servInfo.setServiceType(serviceType);
        servInfo.setPort(shared.getLocalPort());
        nsd.registerService(servInfo, NsdManager.PROTOCOL_DNS_SD, this);
        name = serviceName;
        type = serviceType;
        landing = shared;
        status = STATUS_STARTING;
    }
    public void stopPublishing() {
        if(name != null) {
            nsd.unregisterService(this);
            name = null;
            type = null;
        }
    }
    ServerSocket getSocket() { return landing; }
    int getStatus() { return status; }
    int getErrorCode() { return error; }
    @Override
    public void onServiceRegistered(NsdServiceInfo info) { status = STATUS_PUBLISHING; }
    @Override
    public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) { status = STATUS_START_FAILED; }
    @Override
    public void onServiceUnregistered(NsdServiceInfo arg0) { status = STATUS_STOPPED; }
    @Override
    public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) { status = STATUS_STOP_FAILED; }

    String name, type;
    volatile int status = STATUS_IDLE, error;
    ServerSocket landing;
    final NsdManager nsd;
}

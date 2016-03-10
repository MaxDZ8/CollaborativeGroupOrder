package com.massimodz8.collaborativegrouporder;

import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;

import java.net.ServerSocket;
import java.util.Timer;
import java.util.TimerTask;

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

    public static final int STATUS_IDLE = 0; // just created, doing nothing.
    public static final int STATUS_STARTING = 1; // we have a service name and type
    public static final int STATUS_START_FAILED = 2; // as above, plus we got an error code
    public static final int STATUS_PUBLISHING = 3;
    public static final int STATUS_STOP_FAILED = 4;
    public static final int STATUS_STOPPED = 5;

    interface OnStatusChanged {
        /**  Called every time the state changes. Those are called from a different thread.
         * If the new status reported is STATUS_START_FAILED or STATUS_STOPPED then no more
         * notifications will be generated.
         * @param old state of the publisher at previous call or STATUS_IDLE for first.
         * @param current the new state, which is already set.
         */
        void newStatus(int old, int current);
    }

    public void beginPublishing(ServerSocket shared, String serviceName, String serviceType, OnStatusChanged onStatusChanged) {
        NsdServiceInfo servInfo  = new NsdServiceInfo();
        servInfo.setServiceName(serviceName);
        servInfo.setServiceType(serviceType);
        servInfo.setPort(shared.getLocalPort());
        name = serviceName;
        type = serviceType;
        landing = shared;
        status = STATUS_STARTING;
        callback = onStatusChanged;
        checker = new Timer("network publisher status check");
        checker.schedule(new TimerTask() {
            int prevStatus = STATUS_IDLE;
            @Override
            public void run() {
                if(prevStatus != status) {
                    int old = prevStatus;
                    prevStatus = status;
                    OnStatusChanged call = callback;
                    if(null != call) call.newStatus(old, status);
                    if(STATUS_START_FAILED == status || STATUS_STOPPED == status) cancel();
                }
            }
        }, PUBLISHING_PROBE_DELAY, PUBLISHING_PROBE_INTERVAL);
        nsd.registerService(servInfo, NsdManager.PROTOCOL_DNS_SD, this);
    }
    public void setCallback(OnStatusChanged newTarget) { callback = newTarget; }
    public void unregisterCallback() { callback = null; }
    public void stopPublishing() {
        if(name != null) {
            unregisterCallback();
            checker.cancel();
            checker = null;
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
    public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
        error = errorCode;
        status = STATUS_START_FAILED;
    }
    @Override
    public void onServiceUnregistered(NsdServiceInfo arg0) { status = STATUS_STOPPED; }
    @Override
    public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
        error = errorCode;
        status = STATUS_STOP_FAILED;
    }

    String name, type;
    volatile int status = STATUS_IDLE, error;
    ServerSocket landing;
    final NsdManager nsd;
    Timer checker;
    volatile OnStatusChanged callback;

    static final int PUBLISHING_PROBE_DELAY = 1000;
    static final int PUBLISHING_PROBE_INTERVAL = 250;
}

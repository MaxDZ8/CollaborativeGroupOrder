package com.massimodz8.collaborativegrouporder.master;

import android.app.Service;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.ServerSocket;
import java.util.Vector;

/**
 * Created by Massimo on 04/03/2016.
 * Base class for my two services. Provides management of a ServerSocket and publish status.
 */
public abstract class PublishAcceptService extends Service implements NsdManager.RegistrationListener {
    public static final int PUBLISHER_IDLE = 0; // just created, doing nothing.
    public static final int PUBLISHER_STARTING = 1; // we have a service name and type
    public static final int PUBLISHER_START_FAILED = 2; // as above, plus we got an error code
    public static final int PUBLISHER_PUBLISHING = 3;
    public static final int PUBLISHER_STOP_FAILED = 4;
    public static final int PUBLISHER_STOPPED = 5;

    public static final String PARTY_GOING_ADVENTURING_SERVICE_TYPE = "_partyInitiative._tcp";

    /* Section 1: managing the server socket. ------------------------------------------------------
    This involves pulling it up, closing it and...
    Managing an handler for new connections. The main reason to keep this around is that it's the
    only way to ensure client connection data is persistent. Otherwise, I might have to re-publish
    and similar. Poll based for the description in class doc.
    */
    public void startListening() throws IOException {
        rejectConnections = false;
        if(landing != null) return;
        final ServerSocket temp = new ServerSocket(0);
        acceptor = new Thread() {
            @Override
            public void run() {
                while(!isInterrupted()) {
                    MessageChannel newComer;
                    try {
                        newComer = new MessageChannel(landing.accept());
                    } catch (IOException e) {
                        if(!stoppingListener) listenErrors.add(e);
                        return;
                    }
                    if(rejectConnections) {
                        try {
                            newComer.socket.close();
                        } catch (IOException e) {
                            // We don't want this guy anyway.
                        }
                    }
                    else funnel.sendMessage(funnel.obtainMessage(MSG_NEW_CLIENT, newComer));
                }
                try {
                    landing.close();
                } catch (IOException e) {
                    // no idea what could be nice to do at this point, it's a goner anyway!
                }
            }
        };
        landing = temp;
        acceptor.start();
    }
    public @Nullable
    Vector<Exception> getNewAcceptErrors() {
        if(listenErrors.isEmpty()) return null;
        Vector<Exception> res = listenErrors;
        listenErrors = new Vector<>();
        return res;
    }
    public int getServerPort() { return landing == null? 0 : landing.getLocalPort(); }
    public void stopListening(boolean hard) {
        rejectConnections = true;
        if(!hard) return;
        stoppingListener = true;
        if(acceptor != null) { // it might happen we're really already called to stop.
            acceptor.interrupt();
            acceptor = null;
        }
        try {
            landing.close();
        } catch (IOException e) {
            // no idea what could be nice to do at this point, it's a goner anyway!
        }
        landing = null;
    }
    public @Nullable ServerSocket getLanding(boolean release) {
        ServerSocket ret = landing;
        if(release) landing = null;
        return ret;
    }

    protected abstract void onNewClient(@NonNull MessageChannel fresh);

    /* Section 2: managing publish status. ---------------------------------------------------------
    Publishing is necessary to let the other clients know about us. Once we have assigned the
    characters we are no more interested in letting other devices discover us. So the publisher
    can be disabled maintaining the socket open. Disconnected clients will attempt to reconnect
    automatically, someone will get their connection and handshake them.
    */
    /// Assumes a ServerSocket is there (usually by calling startListening)
    public void beginPublishing(@NonNull NsdManager nsd, @NonNull String serviceName) {
        if(null != servInfo) return;
        nsdMan = nsd;
        NsdServiceInfo temp = new NsdServiceInfo();
        temp.setServiceName(serviceName);
        temp.setServiceType(PARTY_GOING_ADVENTURING_SERVICE_TYPE);
        temp.setPort(landing.getLocalPort());
        nsd.registerService(temp, NsdManager.PROTOCOL_DNS_SD, this);
        publishStatus = PUBLISHER_STARTING;
        servInfo = temp;
    }
    public int getPublishStatus() { return publishStatus; }
    public int getPublishError() { return publishError; }
    public void stopPublishing() {
        if(servInfo != null) {
            nsdMan.unregisterService(this);
            servInfo = null;
            nsdMan = null;
        }
    }
    protected abstract void onNewPublishStatus(int now);

    // Service vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv
    @Override
    public void onCreate() {
        funnel = new MyHandler(this);
    }

    @Override
    public void onDestroy() {
        stopListening(true);
        stopPublishing();
    }
    // Service ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    // NsdManager.RegistrationListener vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv
    @Override
    public void onServiceRegistered(NsdServiceInfo info) {
        publishStatus = PUBLISHER_PUBLISHING;
        onNewPublishStatus(publishStatus);
    }
    @Override
    public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
        publishError = errorCode;
        publishStatus = PUBLISHER_START_FAILED;
        onNewPublishStatus(publishStatus);
    }
    @Override
    public void onServiceUnregistered(NsdServiceInfo arg0) {
        publishStatus = PUBLISHER_STOPPED;
        onNewPublishStatus(publishStatus);
    }
    @Override
    public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
        publishError = errorCode;
        publishStatus = PUBLISHER_STOP_FAILED;
        onNewPublishStatus(publishStatus);
    }
    // NsdManager.RegistrationListener ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

    private ServerSocket landing;
    private Thread acceptor;
    private volatile boolean stoppingListener, rejectConnections;
    private volatile Vector<Exception> listenErrors = new Vector<>();

    private NsdManager nsdMan;
    private NsdServiceInfo servInfo;
    private volatile int publishStatus = PUBLISHER_IDLE, publishError;

    private Handler funnel;
    private static final int MSG_NEW_CLIENT = 1;

    private static class MyHandler extends Handler {
        final WeakReference<PublishAcceptService> self;

        private MyHandler(PublishAcceptService self) {
            this.self = new WeakReference<>(self);
        }

        @Override
        public void handleMessage(Message msg) {
            final PublishAcceptService self = this.self.get();
            switch (msg.what) {
                case MSG_NEW_CLIENT:
                    self.onNewClient((MessageChannel) msg.obj);
                    break;
            }
        }
    }
}

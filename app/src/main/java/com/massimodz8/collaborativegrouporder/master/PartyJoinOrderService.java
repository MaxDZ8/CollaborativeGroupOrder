package com.massimodz8.collaborativegrouporder.master;

import android.app.Service;
import android.content.Intent;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Binder;
import android.os.IBinder;

import com.massimodz8.collaborativegrouporder.MainMenuActivity;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Vector;

/** Encapsulates states and manipulations involved in creating a socket and publishing it to the
 * network for the purposes of having player devices "join" the game.
 * The activity then becomes a 'empty' shell of UI only, while this is the model.
 * Joining an existing party and running the initiative order... the main problem is that we need
 * to be able to allow players losing connection to re-connect and then there's the publish status.
 *
 * There are therefore various things we are interested in knowing there and it all goes through...
 * polling? Yes, periodic polling is required. Why?
 * Handlers won't GC as long as I keep a reference to them but their state can be inconsistent...
 * e.g. try popping an AlertDialog while the activity is being destroyed...
 * long story short: I must keep track of those events and remember them. Meh!
 */
public class PartyJoinOrderService extends Service implements NsdManager.RegistrationListener {
    static final int PUBLISHER_IDLE = 0; // just created, doing nothing.
    static final int PUBLISHER_STARTING = 1; // we have a service name and type
    static final int PUBLISHER_START_FAILED = 2; // as above, plus we got an error code
    static final int PUBLISHER_PUBLISHING = 3;
    static final int PUBLISHER_STOP_FAILED = 4;
    static final int PUBLISHER_STOPPED = 5;

    public PartyJoinOrderService() {
    }

    /* Section 1: managing the server socket. ------------------------------------------------------
    This involves pulling it up, closing it and...
    Managing an handler for new connections. The main reason to keep this around is that it's the
    only way to ensure client connection data is persistent. Otherwise, I might have to re-publish
    and similar. Poll based for the description in class doc.
    */
    void startListening() throws IOException {
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
                    else newConn.add(newComer);
                }
                try {
                    landing.close();
                } catch (IOException e) {
                    // no idea what could be nice to do at this point, it's a goner anyway!
                }
            }
        };
        landing = temp;
    }
    Vector<MessageChannel> getNewClients() {
        if(newConn.isEmpty()) return null;
        Vector<MessageChannel> res = newConn;
        newConn = new Vector<>();
        return res;
    }
    Vector<Exception> getNewAcceptErrors() {
        if(listenErrors.isEmpty()) return null;
        Vector<Exception> res = listenErrors;
        listenErrors = new Vector<>();
        return res;
    }
    void stopListening(boolean hard) {
        rejectConnections = true;
        if(!hard) return;
        stoppingListener = true;
        acceptor.interrupt();
        acceptor = null;
        landing = null;
    }

    /* Section 2: managing publish status. ---------------------------------------------------------
    Publishing is necessary to let the other clients know about us. Once we have assigned the
    characters we are no more interested in letting other devices discover us. So the publisher
    can be disabled maintaining the socket open. Disconnected clients will attempt to reconnect
    automatically, someone will get their connection and handshake them.
    */
    /// Requires startListening() to have been called at least once (needs a listening socket).
    public void beginPublishing(NsdManager nsd, String serviceName) {
        if(null != servInfo) return;
        NsdServiceInfo temp = new NsdServiceInfo();
        temp.setServiceName(serviceName);
        temp.setServiceType(MainMenuActivity.PARTY_GOING_ADVENTURING_SERVICE_TYPE);
        temp.setPort(landing.getLocalPort());
        nsd.registerService(temp, NsdManager.PROTOCOL_DNS_SD, this);
        publishStatus = PUBLISHER_STARTING;
        servInfo = temp;
    }
    public int getPublishStatus() { return publishStatus; }
    public int getPublishError() { return publishError; }
    public void stopPublishing(NsdManager nsd) {
        if(servInfo != null) {
            nsd.unregisterService(this);
            servInfo = null;
        }
    }

    private LocalBinder uniqueBinder;

    private ServerSocket landing;
    private Thread acceptor;
    private volatile boolean stoppingListener, rejectConnections;
    private volatile Vector<MessageChannel> newConn = new Vector<>();
    private volatile Vector<Exception> listenErrors = new Vector<>();

    private NsdServiceInfo servInfo;
    private volatile int publishStatus = PUBLISHER_IDLE, publishError;

    // Service _____________________________________________________________________________________
    @Override
    public IBinder onBind(Intent intent) {
        if(null == uniqueBinder) uniqueBinder = new LocalBinder();
        return uniqueBinder;
    }

    public class LocalBinder extends Binder {
        PartyJoinOrderService getConcreteService() {
            return PartyJoinOrderService.this;
        }
    }

    // NsdManager.RegistrationListener _____________________________________________________________
    @Override
    public void onServiceRegistered(NsdServiceInfo info) { publishStatus = PUBLISHER_PUBLISHING; }
    @Override
    public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
        publishError = errorCode;
        publishStatus = PUBLISHER_START_FAILED;
    }
    @Override
    public void onServiceUnregistered(NsdServiceInfo arg0) { publishStatus = PUBLISHER_STOPPED; }
    @Override
    public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
        publishError = errorCode;
        publishStatus = PUBLISHER_STOP_FAILED;
    }
}

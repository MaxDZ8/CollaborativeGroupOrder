package com.massimodz8.collaborativegrouporder.master;

import android.app.Service;
import android.content.Intent;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;

import com.massimodz8.collaborativegrouporder.JoinVerificator;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.networkio.Pumper;
import com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
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
    public static final int PUBLISHER_IDLE = 0; // just created, doing nothing.
    public static final int PUBLISHER_STARTING = 1; // we have a service name and type
    public static final int PUBLISHER_START_FAILED = 2; // as above, plus we got an error code
    public static final int PUBLISHER_PUBLISHING = 3;
    public static final int PUBLISHER_STOP_FAILED = 4;
    public static final int PUBLISHER_STOPPED = 5;
    public static final String PARTY_GOING_ADVENTURING_SERVICE_TYPE = "_partyInitiative._tcp";
    private PersistentStorage.PartyOwnerData.Group partyOwnerData;

    public PartyJoinOrderService() {
    }

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
        acceptor.start();
    }
    public @Nullable Vector<Exception> getNewAcceptErrors() {
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
        landing = null;
    }

    /* Section 2: managing publish status. ---------------------------------------------------------
    Publishing is necessary to let the other clients know about us. Once we have assigned the
    characters we are no more interested in letting other devices discover us. So the publisher
    can be disabled maintaining the socket open. Disconnected clients will attempt to reconnect
    automatically, someone will get their connection and handshake them.
    */
    /// Requires startListening() to have been called at least once (needs a listening socket).
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

    private LocalBinder uniqueBinder;

    private ServerSocket landing;
    private Thread acceptor;
    private volatile boolean stoppingListener, rejectConnections;
    private volatile Vector<MessageChannel> newConn = new Vector<>();
    private volatile Vector<Exception> listenErrors = new Vector<>();

    private NsdManager nsdMan;
    private NsdServiceInfo servInfo;
    private volatile int publishStatus = PUBLISHER_IDLE, publishError;

    /* Section 3: identifying joining devices and binding characters. ------------------------------
    This goes in parallel with landing socket and publish management so you're better set this up ASAP.
    As usual, it can be initialized only once and then the service will have to be destroyed.
    */
    public void initializePartyManagement(@NonNull PersistentStorage.PartyOwnerData.Group party, @NonNull JoinVerificator keyMaster) {
        assignmentHelper = new PcAssignmentHelper(party, keyMaster);
    }

    public void shutdownPartyManagement() {
        if(null != assignmentHelper) assignmentHelper.shutdown();
    }

    public PersistentStorage.PartyOwnerData.Group getPartyOwnerData() {
        return assignmentHelper == null? null : assignmentHelper.party;
    }

    public <VH extends RecyclerView.ViewHolder> PcAssignmentHelper.AuthDeviceAdapter<VH> setNewAuthDevicesAdapter(PcAssignmentHelper.AuthDeviceHolderFactoryBinder<VH> factory) {
        PcAssignmentHelper.AuthDeviceAdapter<VH> gen = null;
        if(factory != null) gen = new PcAssignmentHelper.AuthDeviceAdapter<>(factory, assignmentHelper);
        assignmentHelper.authDeviceAdapter = gen;
        return gen;
    }

    public <VH extends RecyclerView.ViewHolder> PcAssignmentHelper.UnassignedPcsAdapter<VH> setNewUnassignedPcsAdapter(PcAssignmentHelper.UnassignedPcHolderFactoryBinder<VH> factory) {
        PcAssignmentHelper.UnassignedPcsAdapter<VH> gen = null;
        if(factory != null) gen = new PcAssignmentHelper.UnassignedPcsAdapter<>(factory, assignmentHelper);
        assignmentHelper.unboundPcAdapter = gen;
        return gen;
    }

    public ArrayList<PersistentStorage.ActorDefinition> getUnboundedPcs() {
        return assignmentHelper.getUnboundedPcs();
    }

    public int getNumIdentifiedClients() {
        return assignmentHelper.getNumIdentifiedClients();
    }

    /// Marks the given character to be managed locally. Will trigger ownership change.
    public void local(PersistentStorage.ActorDefinition actor) {
        assignmentHelper.local(actor);

    }

    /// Promotes freshly connected clients to anonymous handshaking clients.
    public void promoteNewClients() {
        while(!newConn.isEmpty()) {
            final MessageChannel client = newConn.remove(newConn.size() - 1);
            assignmentHelper.pump(client);
        }
    }


    public void kickNewClients() {
        new Thread() {
            @Override
            public void run() {
                while(!newConn.isEmpty()) {
                    final MessageChannel client = newConn.remove(newConn.size() - 1);
                    try {
                        client.socket.close();
                    } catch (IOException e) {
                        // Ignore. I'm kicking them.
                    }
                }
            }
        }.start();
    }

    /// Promotes freshly connected clients to anonymous handshaking clients.
    public void pumpClients(@Nullable Pumper.MessagePumpingThread[] existing) {
        if(existing == null) return;
        for(Pumper.MessagePumpingThread worker : existing) assignmentHelper.pump(worker);
    }

    /* Section 4: given current character bindings, start the real deal. ---------------------------
    TODO
     */
    public void adventuring() {
        /*

        final ArrayList<MessageChannel> target = new ArrayList<>();
        final ArrayList<ArrayList<Network.PlayingCharacterDefinition>> payload = new ArrayList<>();
        for (PlayingDevice playa : myState.playerDevices) {
            target.add(playa.pipe);
            ArrayList<Network.PlayingCharacterDefinition> matched = new ArrayList<>();
            for (int loop = 0; loop < myState.assignment.size(); loop++) {
                Integer owner = myState.assignment.get(loop);
                if(null == owner) continue; // impossible, really
                if(owner < 0) continue;
                if(myState.playerDevices.get(owner) == playa) {
                    matched.add(simplify(myState.party.usually.party[loop], loop));
                }
            }
            payload.add(matched);
        }
        new Thread(){
            @Override
            public void run() {
                Network.GroupReady send = new Network.GroupReady();
                for(int loop = 0; loop < target.size(); loop++) {
                    ArrayList<Network.PlayingCharacterDefinition> matched = payload.get(loop);
                    send.yours = new Network.PlayingCharacterDefinition[matched.size()];
                    for(int cp = 0; cp < matched.size(); cp++) send.yours[cp] = matched.get(cp);
                    try {
                        target.get(loop).writeSync(ProtoBufferEnum.GROUP_READY, send);
                    } catch (IOException e) {
                        // uhm... someone else will check this in the future... hopefully.
                    }
                }
            }
        }.start();
        new AlertDialog.Builder(this).setMessage("TODO: at this point the clients are in sequence mode. BUT... I need to refactor my architecture as this activity needs to stay afloat and go back there on need.").setTitle("TODO").show();
         */
    }

    private PcAssignmentHelper assignmentHelper;

    // Service _____________________________________________________________________________________
    @Override
    public IBinder onBind(Intent intent) {
        if(null == uniqueBinder) uniqueBinder = new LocalBinder();
        return uniqueBinder;
    }

    @Override
    public void onDestroy() {
        stopListening(true);
        stopPublishing();
        kickNewClients();
        shutdownPartyManagement();
    }

    public class LocalBinder extends Binder {
        public PartyJoinOrderService getConcreteService() {
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

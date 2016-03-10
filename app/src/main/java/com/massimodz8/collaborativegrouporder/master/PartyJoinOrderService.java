package com.massimodz8.collaborativegrouporder.master;

import android.content.Intent;
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
import java.util.ArrayList;

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
public class PartyJoinOrderService extends PublishAcceptService {
    /* Section 3: identifying joining devices and binding characters. ------------------------------
    This goes in parallel with landing socket and publish management so you're better set this up ASAP.
    As usual, it can be initialized only once and then the service will have to be destroyed.
    */
    public void initializePartyManagement(@NonNull PersistentStorage.PartyOwnerData.Group party, @NonNull JoinVerificator keyMaster) {
        assignmentHelper = new PcAssignmentHelper(party, keyMaster);
    }

    public void shutdownPartyManagement() {
        if(null != assignmentHelper) {
            assignmentHelper.shutdown();
            assignmentHelper = null;
        }
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
    public void pumpClients(@Nullable Pumper.MessagePumpingThread[] existing) {
        if(existing == null) return;
        for(Pumper.MessagePumpingThread worker : existing) assignmentHelper.pump(worker);
    }

    public void setUnassignedPcsCountListener(@Nullable PcAssignmentHelper.OnBoundPcCallback listener) {
        assignmentHelper.onBoundPc = listener;
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

    // PublishAcceptService vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv
    @Override
    protected void onNewClient(final @NonNull MessageChannel fresh) {
        if(assignmentHelper == null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        fresh.socket.close();
                    } catch (IOException e) {
                        // suppress
                    }
                }
            }).start();
            return;
        }
        assignmentHelper.pump(fresh);
    }
    // PublishAcceptService ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

    // Service _____________________________________________________________________________________
    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder(); // this is called once by the OS when first bind is received.
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        shutdownPartyManagement();
    }

    public class LocalBinder extends Binder {
        public PartyJoinOrderService getConcreteService() {
            return PartyJoinOrderService.this;
        }
    }
}

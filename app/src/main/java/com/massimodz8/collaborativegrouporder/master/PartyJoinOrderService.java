package com.massimodz8.collaborativegrouporder.master;

import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;

import com.massimodz8.collaborativegrouporder.AbsLiveActor;
import com.massimodz8.collaborativegrouporder.CharacterActor;
import com.massimodz8.collaborativegrouporder.JoinVerificator;
import com.massimodz8.collaborativegrouporder.PersistentDataUtils;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.networkio.Pumper;
import com.massimodz8.collaborativegrouporder.protocol.nano.MonsterData;
import com.massimodz8.collaborativegrouporder.protocol.nano.StartData;

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
    public void initializePartyManagement(@NonNull StartData.PartyOwnerData.Group party, PersistentDataUtils.SessionStructs live, @NonNull JoinVerificator keyMaster, MonsterData.MonsterBook monsterBook) {
        assignmentHelper = new PcAssignmentHelper(party, keyMaster);
        ArrayList<AbsLiveActor> byDef = new ArrayList<>();
        for(StartData.ActorDefinition el : party.party) byDef.add(makeLiveActor(el, true));
        for(StartData.ActorDefinition el : party.npcs) byDef.add(makeLiveActor(el, false));
        sessionHelper = new SessionHelper(assignmentHelper.party, live, byDef, monsterBook);
    }

    public void shutdownPartyManagement() {
        if(null != assignmentHelper) {
            assignmentHelper.shutdown();
            assignmentHelper = null;
        }
    }

    public StartData.PartyOwnerData.Group getPartyOwnerData() {
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

    public ArrayList<StartData.ActorDefinition> getUnboundedPcs() {
        return assignmentHelper.getUnboundedPcs();
    }

    public int getNumIdentifiedClients() {
        return assignmentHelper.getNumIdentifiedClients();
    }

    /// Marks the given character to be managed locally. Will trigger ownership change.
    public void local(StartData.ActorDefinition actor) { assignmentHelper.local(actor); }
    public MessageChannel getMessageChannel(StartData.ActorDefinition actor) { return assignmentHelper.getMessageChannel(actor); }

    /// Promotes freshly connected clients to anonymous handshaking clients.
    public void pumpClients(@Nullable Pumper.MessagePumpingThread[] existing) {
        if(existing == null) return;
        for(Pumper.MessagePumpingThread worker : existing) assignmentHelper.pump(worker);
    }

    public void setUnassignedPcsCountListener(@Nullable PcAssignmentHelper.OnBoundPcCallback listener) {
        assignmentHelper.onBoundPc = listener;
    }

    public SessionHelper.PlayState getPlaySession() { return sessionHelper.getSession(); }

    PcAssignmentHelper assignmentHelper;
    private SessionHelper sessionHelper;

    private AbsLiveActor makeLiveActor(StartData.ActorDefinition definition, boolean playingCharacter) {
        CharacterActor build = new CharacterActor(definition.name, playingCharacter, definition);
        build.initiativeBonus = definition.stats[0].initBonus;
        build.currentHealth = build.maxHealth = definition.stats[0].healthPoints;
        build.experience = definition.experience;
        return build;
    }

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

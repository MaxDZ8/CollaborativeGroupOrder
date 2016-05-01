package com.massimodz8.collaborativegrouporder.master;

import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;

import com.massimodz8.collaborativegrouporder.AbsLiveActor;
import com.massimodz8.collaborativegrouporder.CharacterActor;
import com.massimodz8.collaborativegrouporder.InitiativeScore;
import com.massimodz8.collaborativegrouporder.JoinVerificator;
import com.massimodz8.collaborativegrouporder.PersistentDataUtils;
import com.massimodz8.collaborativegrouporder.networkio.Events;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.networkio.ProtoBufferEnum;
import com.massimodz8.collaborativegrouporder.networkio.PumpTarget;
import com.massimodz8.collaborativegrouporder.networkio.Pumper;
import com.massimodz8.collaborativegrouporder.protocol.nano.MonsterData;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;
import com.massimodz8.collaborativegrouporder.protocol.nano.StartData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;

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
        assignmentHelper = new PcAssignmentHelper(party, keyMaster) {
            @Override
            protected StartData.ActorDefinition getActorData(int unique) {
                final StartData.PartyOwnerData.Group group = getPartyOwnerData();
                return group.party[unique];
            }
        };
        ArrayList<AbsLiveActor> byDef = new ArrayList<>();
        for(StartData.ActorDefinition el : party.party) byDef.add(CharacterActor.makeLiveActor(el, true));
        for(StartData.ActorDefinition el : party.npcs) byDef.add(CharacterActor.makeLiveActor(el, false));
        sessionHelper = new SessionHelper(assignmentHelper.party, live, byDef);
        sessionHelper.session = new SessionHelper.PlayState(sessionHelper, monsterBook, assignmentHelper) {
            @Override
            void onRollReceived() {
                while(!sessionHelper.session.rollResults.isEmpty()) {
                    final Events.Roll got = sessionHelper.session.rollResults.pop();
                    matchRoll(got.from, got.payload);
                }
            }

            @Override
            int getActorId(@NonNull AbsLiveActor active) {
                return actorId.get(active);
            }
        };
        battleHandler = new MyBattleHandler(this);
        battlePumper = new Pumper(battleHandler, MyBattleHandler.MSG_DISCONNECTED, MyBattleHandler.MSG_DETACHED)
                .add(ProtoBufferEnum.ROLL, new PumpTarget.Callbacks<Network.Roll>() {
                    @Override
                    public Network.Roll make() { return new Network.Roll(); }

                    @Override
                    public boolean mangle(MessageChannel from, Network.Roll msg) throws IOException {
                        battleHandler.sendMessage(battleHandler.obtainMessage(MyBattleHandler.MSG_ROLL, new Events.Roll(from, msg)));
                        return false;
                    }
                });
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

    /// Promotes freshly connected clients to anonymous handshaking clients.
    public void pumpClients(@Nullable Pumper.MessagePumpingThread[] existing) {
        if(existing == null) return;
        for(Pumper.MessagePumpingThread worker : existing) assignmentHelper.pump(worker);
    }

    public void setUnassignedPcsCountListener(@Nullable PcAssignmentHelper.OnBoundPcCallback listener) {
        assignmentHelper.onBoundPc = listener;
    }

    PcAssignmentHelper assignmentHelper;
    public SessionHelper sessionHelper;
    Pumper battlePumper;
    Handler battleHandler;
    /**
     * Roll requests are unique across all devices so we can be super sure of what to do.
     */
    int rollRequest;
    Runnable onRollReceived; // called when a roll has been matched to some updated state.


    private void matchRoll(MessageChannel from, Network.Roll dice) {
        // The first consumer of rolls is initiative building.
        if(sessionHelper != null && sessionHelper.initiatives != null) {
            for (Map.Entry<AbsLiveActor, SessionHelper.Initiative> el : sessionHelper.initiatives.entrySet()) {
                final SessionHelper.Initiative val = el.getValue();
                if(val.request != null && dice.unique == val.request.unique) {
                    val.rolled = dice.result + el.getKey().getInitiativeBonus();
                    if(onRollReceived != null) onRollReceived.run();
                    return;
                }
            }
        }
    }

    /**
     * Mapping actors to unique ids is big deal. IDs are unique across the group. This keeps an
     * unsorted list of actors and their corresponding ids. In theory I could have a stack but this
     * is searched relatively more often and I get extra flexibility.
     * In general, party playing characters are added here right away and will always stay here.
     * FreeRoamingActivity adds new monsters here as well as the session monsters list; somebody
     * will pop those monsters when the session is terminated. There is in fact a stack of lifetimes.
     * Note the next ID to use is not necessarily the size of this map as ID could still be deleted
     * explicitly by user swiping away one.
     */
    public final IdentityHashMap<AbsLiveActor, Integer> actorId = new IdentityHashMap<>();
    public int nextActorId;


    public boolean notifyBattleOrder_CHECK_ME() {
        if(!sessionHelper.session.battleState.orderChanged) return false;
        final InitiativeScore[] order = sessionHelper.session.battleState.ordered;
        int[] sequence = new int[order.length];
        int cp = 0;
        for (InitiativeScore score : order) {
            sequence[cp] = actorId.get(score.actor);
            cp++;
        }
        int devIndex = -1;
        for (PcAssignmentHelper.PlayingDevice dev : assignmentHelper.peers) {
            devIndex++;
            if(dev.pipe == null) continue;
            int actorKey = -1;
            for (Integer binding : assignmentHelper.assignment) {
                actorKey++; // keys here are just their order of definition.
                if(binding == null) continue; // should not be possible at this point
                if(binding != devIndex) continue;
                final Network.BattleOrder bo = new Network.BattleOrder();
                bo.asKnownBy = actorKey;
                bo.order = sequence;
                assignmentHelper.sendToRemote(dev, ProtoBufferEnum.BATTLE_ORDER, bo);
            }
        }
        sessionHelper.session.battleState.orderChanged = false;
        return true;
    }

    public void sendBattlingActorData() {
        for(int loop = 0; loop < assignmentHelper.party.party.length; loop++) {
            final Integer binding = assignmentHelper.assignment.get(loop);
            if(binding == null) continue; // impossible
            if(binding == PcAssignmentHelper.LOCAL_BINDING) continue; // no need to let it know, dialog will pull server data directly.
            final PcAssignmentHelper.PlayingDevice dev = assignmentHelper.peers.get(binding);
            final MessageChannel pipe = dev.pipe;
            if(pipe == null) continue; // connection lost
            for (InitiativeScore el : sessionHelper.session.battleState.ordered) {
                final Network.TurnControl tc = new Network.TurnControl();
                tc.type = Network.TurnControl.T_ACTORDATA_KEY;
                tc.peerKey = actorId.get(el.actor);
                final StartData.ActorDefinition ad = assignmentHelper.getActorData(tc.peerKey);
                assignmentHelper.sendToRemote(dev, ProtoBufferEnum.TURN_CONTROL, tc);
                assignmentHelper.sendToRemote(dev, ProtoBufferEnum.ACTOR_DATA, ad);
            }
            // TODO: it would be a better idea to resolve unique peerkey from order lists across all characters from a device.
            // This has quite some repetition instead but it's way easier for everybody.
        }
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

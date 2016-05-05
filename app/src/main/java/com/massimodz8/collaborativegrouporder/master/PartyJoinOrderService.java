package com.massimodz8.collaborativegrouporder.master;

import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;

import com.massimodz8.collaborativegrouporder.ActorId;
import com.massimodz8.collaborativegrouporder.InitiativeScore;
import com.massimodz8.collaborativegrouporder.JoinVerificator;
import com.massimodz8.collaborativegrouporder.PersistentDataUtils;
import com.massimodz8.collaborativegrouporder.SendRequest;
import com.massimodz8.collaborativegrouporder.networkio.Events;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.networkio.ProtoBufferEnum;
import com.massimodz8.collaborativegrouporder.networkio.PumpTarget;
import com.massimodz8.collaborativegrouporder.networkio.Pumper;
import com.massimodz8.collaborativegrouporder.protocol.nano.MonsterData;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;
import com.massimodz8.collaborativegrouporder.protocol.nano.StartData;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
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
        ArrayList<Network.ActorState> byDef = new ArrayList<>();
        for(StartData.ActorDefinition el : party.party) byDef.add(makeActorState(el, nextActorId++, Network.ActorState.T_PLAYING_CHARACTER));
        for(StartData.ActorDefinition el : party.npcs) byDef.add(makeActorState(el, nextActorId++, Network.ActorState.T_NPC));
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
            public void turnDone(MessageChannel from, int peerKey) {
                if(peerKey >= assignmentHelper.assignment.size()) return; // Discard rubbish first.
                int index = 0;
                for (PcAssignmentHelper.PlayingDevice dev : assignmentHelper.peers) {
                    if(dev.pipe == from) break;
                    index++;
                }
                final Integer bound = assignmentHelper.assignment.get(peerKey);
                if(bound == null || bound == PcAssignmentHelper.LOCAL_BINDING) return;
                if(bound != index) return; // you cannot control this turn you cheater!
                if(peerKey != battleState.currentActor) return; // that's not his turn anyway!
                // Don't do that. Might involve popping readied actions. Furthermore, BattleActivity wants to keep track of both previous and current actor.
                //battleState.tickRound();
                if(!onRemoteTurnCompleted.isEmpty()) onRemoteTurnCompleted.getLast().run();
            }

            @Override
            public void shuffle(MessageChannel from, @ActorId int peerKey, int newSlot) {
                if(peerKey >= assignmentHelper.assignment.size()) return; // Discard rubbish first.
                int index = 0;
                for (PcAssignmentHelper.PlayingDevice dev : assignmentHelper.peers) {
                    if(dev.pipe == from) break;
                    index++;
                }
                final Integer bound = assignmentHelper.assignment.get(peerKey);
                if(bound == null || bound == PcAssignmentHelper.LOCAL_BINDING) return;
                if(bound != index) return; // you cannot control this turn you cheater!
                if(peerKey != battleState.currentActor) return; // How did you manage to do that? Not currently allowed.
                sessionHelper.session.battleState.moveCurrentToSlot(newSlot);
                if(!onRemoteActorShuffled.isEmpty()) onRemoteActorShuffled.getLast().run();
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
                }).add(ProtoBufferEnum.TURN_CONTROL, new PumpTarget.Callbacks<Network.TurnControl>() {
                    @Override
                    public Network.TurnControl make() { return new Network.TurnControl(); }

                    @Override
                    public boolean mangle(MessageChannel from, Network.TurnControl msg) throws IOException {
                        if(msg.type == Network.TurnControl.T_FORCE_DONE) {
                            battleHandler.sendMessage(battleHandler.obtainMessage(MyBattleHandler.MSG_TURN_DONE, new Events.TurnDone(from, msg.peerKey)));
                        }
                        return false;
                    }
                }).add(ProtoBufferEnum.BATTLE_ORDER, new PumpTarget.Callbacks<Network.BattleOrder>() {
                    @Override
                    public Network.BattleOrder make() { return new Network.BattleOrder(); }

                    @Override
                    public boolean mangle(MessageChannel from, Network.BattleOrder msg) throws IOException {
                        if(msg.order.length == 1) { // otherwise discard ill formed
                            final Events.ShuffleMe ev = new Events.ShuffleMe(from, msg.asKnownBy, msg.order[0]);
                            battleHandler.sendMessage(battleHandler.obtainMessage(MyBattleHandler.MSG_SHUFFLE_ME, ev));
                        }
                        return false;
                    }
                });
    }

    public void shutdownPartyManagement() {
        if(null != assignmentHelper) {
            assignmentHelper.mailman.out.add(new SendRequest());
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
    public ArrayDeque<Runnable> onRemoteTurnCompleted = new ArrayDeque<>();
    public ArrayDeque<Runnable> onRemoteActorShuffled = new ArrayDeque<>();


    private void matchRoll(MessageChannel from, Network.Roll dice) {
        // The first consumer of rolls is initiative building.
        if(sessionHelper != null && sessionHelper.initiatives != null) {
            for (Map.Entry<Integer, SessionHelper.Initiative> el : sessionHelper.initiatives.entrySet()) {
                final SessionHelper.Initiative val = el.getValue();
                if(val.request != null && dice.unique == val.request.unique) {
                    val.rolled = dice.result + sessionHelper.session.getActorById(el.getKey()).peerKey;
                    if(onRollReceived != null) onRollReceived.run();
                    return;
                }
            }
        }
    }

    /**
     * Mapping actors to unique ids has always been a problem. Now I use the network representation
     * directly and they contain their own id so I just need a counter to keep those unique.
     * Those must start at 0. Why? So we can trivially map to actors created by loaded data from
     * party definitions: pcs and npcs
     * or session data loaded from previous stuff etc etc
     */
    public int nextActorId = 0;


    public boolean pushBattleOrder() {
        if(!sessionHelper.session.battleState.orderChanged) return false;
        final InitiativeScore[] order = sessionHelper.session.battleState.ordered;
        int[] sequence = new int[order.length];
        int cp = 0;
        for (InitiativeScore score : order) sequence[cp++] = score.actorID;
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
                assignmentHelper.mailman.out.add(new SendRequest(dev.pipe, ProtoBufferEnum.BATTLE_ORDER, bo));
            }
        }
        sessionHelper.session.battleState.orderChanged = false;
        return true;
    }

    public void pushKnownActorState(int id) {
        if(id >= assignmentHelper.assignment.size()) return;
        final Integer bound = assignmentHelper.assignment.get(id);
        if(bound == null || bound == PcAssignmentHelper.LOCAL_BINDING) return;
        final PcAssignmentHelper.PlayingDevice dev = assignmentHelper.peers.get(bound);
        if(dev.pipe == null) return;
        // For the time being, just send all actors, be coherent with pushBattleOrder
        Network.ActorState[] current = new Network.ActorState[sessionHelper.session.battleState.ordered.length];
        id = 0;
        for (InitiativeScore el : sessionHelper.session.battleState.ordered) current[id++] = sessionHelper.session.getActorById(el.actorID);
        assignmentHelper.mailman.out.add(new SendRequest(dev.pipe, ProtoBufferEnum.ACTOR_DATA_UPDATE, current));
    }

    private static Network.ActorState makeActorState(StartData.ActorDefinition el, int id, int type) {
        final Network.ActorState res = new Network.ActorState();
        res.peerKey = id;
        res.type = type;
        res.name = el.name;
        res.maxHP = res.currentHP = el.stats[0].healthPoints;
        res.initiativeBonus = el.stats[0].initBonus;
        return res;
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

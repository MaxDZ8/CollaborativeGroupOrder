package com.massimodz8.collaborativegrouporder.client;

import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;

import com.massimodz8.collaborativegrouporder.ActorId;
import com.massimodz8.collaborativegrouporder.Mailman;
import com.massimodz8.collaborativegrouporder.PseudoStack;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.networkio.ProtoBufferEnum;
import com.massimodz8.collaborativegrouporder.networkio.PumpTarget;
import com.massimodz8.collaborativegrouporder.networkio.Pumper;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;
import com.massimodz8.collaborativegrouporder.protocol.nano.StartData;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


/**
 * This service takes care of managing connection to remote server / master device after we got
 * our playing characters assigned and we're roaming our dream land.
 * We will eventually also fight but that's not much of a big deal for the client.
 */
public class Adventure {
    public StartData.PartyClientData party;
    @ActorId  int[] playedHere; // actors played here, added automatically to this.actors after flushed
    public HashMap<Integer, ActorWithKnownOrder> actors = new HashMap<>(); // ID -> struct, flushed every time a new battle starts
    public ArrayList<String> errors;

    public PseudoStack<Runnable> onActorUpdated = new PseudoStack<>();
    public PseudoStack<Runnable> onRollRequestPushed = new PseudoStack<>();
    public PseudoStack<Runnable> onCurrentActorChanged = new PseudoStack<>();
    public PseudoStack<Runnable> onSessionEnded = new PseudoStack<>();

    public @ActorId int currentActor = -1; // -1 = no current known actor
    final ArrayDeque<Network.Roll> rollRequests = new ArrayDeque<>();
    public int round = ROUND_NOT_FIGHTING; // 0 = waiting other players roll 1+ fighting
    public final Mailman mailman = new Mailman();
    public MessageChannel pipe;
    int ended = S_PLAYING; // set by wire using Network.PhaseControl with .type = T_SESSION_ENDED

    public static final int ROUND_NOT_FIGHTING = -1;
    public static final int S_PLAYING = 0, S_END_REQUEST_RECEIVED = 1;

    /**
     * This shouldn't really be there but it is. The current idea is that every time we're done
     * with an actor we increase this and if we have consumed enough ticks we show an interstitial.
     * The idea is that you get an ad each round, you need as many ticks as actors on this device.
     */
    public int ticksSinceLastAd;

    public static class ActorWithKnownOrder {
        public Network.ActorState actor; // 'owner'
        public @Nullable @ActorId int[] keyOrder;
        public boolean updated;

        // This is either zero or some accumulating value. It is usually updated at battle end
        // but not necessarily.
        public int xpReceived;
    }

    public Adventure() { mailman.start(); }

    private Handler handler = new MyHandler(this);
    Pumper netPump = new Pumper(handler, MSG_DISCONNECT, MSG_DETACH)
            .add(ProtoBufferEnum.ACTOR_DATA_UPDATE, new PumpTarget.Callbacks<Network.ActorState>() {
                @Override
                public Network.ActorState make() { return new Network.ActorState(); }

                @Override
                public boolean mangle(MessageChannel from, Network.ActorState msg) throws IOException {
                    handler.sendMessage(handler.obtainMessage(MSG_ACTOR_DATA, msg));
                    return false;
                }
            }).add(ProtoBufferEnum.ROLL, new PumpTarget.Callbacks<Network.Roll>() {
                @Override
                public Network.Roll make() { return new Network.Roll(); }

                @Override
                public boolean mangle(MessageChannel from, Network.Roll msg) throws IOException {
                    handler.sendMessage(handler.obtainMessage(MSG_ROLL, msg));
                    return false;
                }
            }).add(ProtoBufferEnum.BATTLE_ORDER, new PumpTarget.Callbacks<Network.BattleOrder>() {
                @Override
                public Network.BattleOrder make() { return new Network.BattleOrder(); }

                @Override
                public boolean mangle(MessageChannel from, Network.BattleOrder msg) throws IOException {
                    handler.sendMessage(handler.obtainMessage(MSG_BATTLE_ORDER, msg));
                    return false;
                }
            }).add(ProtoBufferEnum.TURN_CONTROL, new PumpTarget.Callbacks<Network.TurnControl>() {
                @Override
                public Network.TurnControl make() { return new Network.TurnControl(); }

                @Override
                public boolean mangle(MessageChannel from, Network.TurnControl msg) throws IOException {
                    handler.sendMessage(handler.obtainMessage(MSG_TURN_CONTROL, msg));
                    return false;
                }
            }).add(ProtoBufferEnum.PHASE_CONTROL, new PumpTarget.Callbacks<Network.PhaseControl>() {
                @Override
                public Network.PhaseControl make() { return new Network.PhaseControl(); }

                @Override
                public boolean mangle(MessageChannel from, Network.PhaseControl msg) throws IOException {
                    if(msg.type != Network.PhaseControl.T_SESSION_ENDED) return false; // ignore... or error?
                    handler.sendEmptyMessage(MSG_SESSION_ENDED);
                    return true;
                }
            });
    private static final int MSG_DISCONNECT = 0;
    private static final int MSG_DETACH = 1;
    private static final int MSG_ACTOR_DATA = 2;
    private static final int MSG_ROLL = 3;
    private static final int MSG_BATTLE_ORDER = 4;
    private static final int MSG_TURN_CONTROL = 5;
    private static final int MSG_SESSION_ENDED = 6;

    private static class MyHandler extends Handler {
        final WeakReference<Adventure> self;

        private MyHandler(Adventure self) {
            this.self = new WeakReference<>(self);
        }

        @Override
        public void handleMessage(Message msg) {
            // This network architecture is shitty. It must go.
            final Adventure self = this.self.get();
            switch(msg.what) {
                case MSG_DISCONNECT:
                    // TODO Where to signal?
                    break;
                case MSG_DETACH:
                    // Never happens now.
                    break;
                case MSG_ACTOR_DATA: {
                    Network.ActorState real = (Network.ActorState)msg.obj;
                    ActorWithKnownOrder known = self.actors.get(real.peerKey);
                    if(known != null) {
                        if(real.type != Network.ActorState.T_PARTIAL_PREPARE_CONDITION) {
                            known.xpReceived += real.experience - known.actor.experience;
                            known.actor = real;
                        }
                        else {
                            known.actor.prepareCondition = "";
                            known.actor.preparedTriggered = false;
                        }
                    }
                    else {
                        // When this is a partial condition that's quite odd and might produce bad listings so I just discard them.
                        if(real.type == Network.ActorState.T_PARTIAL_PREPARE_CONDITION) break;
                        known = new ActorWithKnownOrder();
                        known.actor = real;
                        self.actors.put(real.peerKey, known);
                    }
                    known.updated = true;
                    final Runnable runnable = self.onActorUpdated.get();
                    if(runnable != null) runnable.run();
                } break;
                case MSG_ROLL: {
                    final Network.Roll real = (Network.Roll) msg.obj;
                    if(real.type == Network.Roll.T_INITIATIVE) self.round = 0;
                    self.rollRequests.addLast(real);
                    final Runnable runnable = self.onRollRequestPushed.get();
                    if(runnable != null) runnable.run();
                } break;
                case MSG_BATTLE_ORDER: {
                    final Network.BattleOrder real = (Network.BattleOrder)msg.obj;
                    final ActorWithKnownOrder target = self.actors.get(real.asKnownBy);
                    if(target == null) break; // players must be defined before sending their order
                    target.keyOrder = real.order;
                    target.updated = true;
                    int knownOrder = 0;
                    for (Map.Entry<Integer, ActorWithKnownOrder> el : self.actors.entrySet()) {
                        if(el.getValue().keyOrder != null) knownOrder++;
                    }
                    if(self.round == 0 && knownOrder == self.actors.size()) self.round = 1;
                    final Runnable runnable = self.onActorUpdated.get();
                    if(runnable != null) runnable.run();
                } break;
                case MSG_TURN_CONTROL: {
                    final Network.TurnControl real = (Network.TurnControl) msg.obj;
                    if(real.type == Network.TurnControl.T_BATTLE_ENDED) { // clear list of actors, easier to just rebuild it.
                        self.rollRequests.clear(); // this can happen if a battle start is cancelled.
                        self.round = ROUND_NOT_FIGHTING;
                        ArrayList<ActorWithKnownOrder> reuse = new ArrayList<>();
                        for (int key : self.playedHere) {
                            final ActorWithKnownOrder exist = self.actors.get(key);
                            if(exist == null) continue; // we haven't received actor info yet. Super wrong but what else can I do? Server implementaton prevents this from happening
                            reuse.add(exist);
                        }
                        self.actors.clear();
                        for (ActorWithKnownOrder el : reuse) {
                            el.keyOrder = null;
                            el.updated = true;
                            self.actors.put(el.actor.peerKey, el);
                        }
                        self.currentActor = -1;
                        final Runnable runnable = self.onCurrentActorChanged.get();
                        if(runnable != null) runnable.run();
                        return; // otherwise real.peerKey might be default --> mapping to zero
                    }
                    self.round = real.round;
                    if(real.type != Network.TurnControl.T_BATTLE_ROUND) {
                        boolean here = false;
                        for (int check : self.playedHere) here |= check == real.peerKey;
                        if (!here) self.currentActor = -1;
                        else self.currentActor = real.peerKey;
                        if (real.type == Network.TurnControl.T_PREPARED_TRIGGERED) {
                            self.actors.get(real.peerKey).actor.preparedTriggered = true;
                        }
                    } // trigger actor changed anyway so we can update round count
                    final Runnable runnable = self.onCurrentActorChanged.get();
                    if(runnable != null) runnable.run();
                } break;
                case MSG_SESSION_ENDED:
                    self.ended = S_END_REQUEST_RECEIVED;
                    final Runnable runnable = self.onSessionEnded.get();
                    if(runnable != null) runnable.run();
                    break;
            }
            super.handleMessage(msg);
        }
    }
}

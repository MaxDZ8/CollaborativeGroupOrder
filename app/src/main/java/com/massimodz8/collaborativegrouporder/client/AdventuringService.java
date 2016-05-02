package com.massimodz8.collaborativegrouporder.client;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.Nullable;

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
import java.util.Deque;
import java.util.HashMap;


/**
 * This service takes care of managing connection to remote server / master device after we got
 * our playing characters assigned and we're roaming our dream land.
 * We will eventually also fight but that's not much of a big deal for the client.
 */
public class AdventuringService extends Service {
    public StartData.PartyClientData party;
    int[] playedHere; // actors played here, added automatically to this.actors after flushed
    HashMap<Integer, ActorWithKnownOrder> actors; // ID -> struct, flushed every time a new battle starts
    public ArrayList<String> errors;

    public ArrayDeque<Runnable> onActorUpdated = new ArrayDeque<>();
    public ArrayDeque<Runnable> onRollRequestPushed = new ArrayDeque<>();
    public ArrayDeque<Runnable> onCurrentActorChanged = new ArrayDeque<>();

    public ActorWithKnownOrder currentActor;
    ArrayDeque<Network.Roll> rollRequests = new ArrayDeque<>();
    public int round; // 0 == not fighting

    public static class ActorWithKnownOrder {
        public Network.ActorState actor;
        public @Nullable  int[] keyOrder; // peerkeys of known actors.
        public boolean updated;
    }

    public AdventuringService() {
    }

    public class LocalBinder extends Binder {
        public AdventuringService getConcreteService() { return AdventuringService.this; }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder(); // this is called once by the OS when first bind is received.
    }

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
            });
    private static final int MSG_DISCONNECT = 0;
    private static final int MSG_DETACH = 1;
    private static final int MSG_ACTOR_DATA = 2;
    private static final int MSG_ROLL = 3;
    private static final int MSG_BATTLE_ORDER = 4;
    private static final int MSG_TURN_CONTROL = 5;

    private static class MyHandler extends Handler {
        final WeakReference<AdventuringService> self;

        private MyHandler(AdventuringService self) {
            this.self = new WeakReference<>(self);
        }

        @Override
        public void handleMessage(Message msg) {
            // This network architecture is shitty. It must go.
            final AdventuringService self = this.self.get();
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
                    if(known != null)  known.actor = real;
                    else {
                        known = new ActorWithKnownOrder();
                        known.actor = real;
                        self.actors.put(real.peerKey, known);
                    }
                    known.updated = true;
                    if(self.onActorUpdated.size() > 0) self.onActorUpdated.getLast().run();
                } break;
                case MSG_ROLL: {
                    final Network.Roll real = (Network.Roll) msg.obj;
                    if(real.type == Network.Roll.T_BATTLE_START) self.round = 1;
                    self.rollRequests.push(real);
                    if(self.onRollRequestPushed.size() > 0) self.onRollRequestPushed.getLast().run();
                } break;
                case MSG_BATTLE_ORDER: {
                    final Network.BattleOrder real = (Network.BattleOrder)msg.obj;
                    final ActorWithKnownOrder target = self.actors.get(real.asKnownBy);
                    if(target == null) break; // players must be defined before sending their order
                    target.keyOrder = real.order;
                    target.updated = true;
                    if(self.onActorUpdated.size() > 0) self.onActorUpdated.getLast().run();
                } break;
                case MSG_TURN_CONTROL: {
                    final Network.TurnControl real = (Network.TurnControl) msg.obj;
                    if(real.type == Network.TurnControl.T_BATTLE_ENDED) { // clear list of actors, easier to just rebuild it.
                        self.round = 0;
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
                        if(self.onActorUpdated.size() > 0) self.onActorUpdated.getLast().run();
                    }
                    else self.round = real.round;
                    boolean here = false;
                    for (int check : self.playedHere) here |= check == real.peerKey;
                    if(!here) self.currentActor = null;
                    else self.currentActor = self.actors.get(real.peerKey);
                    if(self.onCurrentActorChanged.size() > 0) self.onCurrentActorChanged.getLast().run();
                } break;
            }
            super.handleMessage(msg);
        }
    }
}

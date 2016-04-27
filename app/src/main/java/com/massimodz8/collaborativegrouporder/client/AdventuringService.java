package com.massimodz8.collaborativegrouporder.client;

import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.NonNull;

import com.massimodz8.collaborativegrouporder.AbsLiveActor;
import com.massimodz8.collaborativegrouporder.CharacterActor;
import com.massimodz8.collaborativegrouporder.R;
import com.massimodz8.collaborativegrouporder.networkio.Events;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.networkio.ProtoBufferEnum;
import com.massimodz8.collaborativegrouporder.networkio.PumpTarget;
import com.massimodz8.collaborativegrouporder.networkio.Pumper;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;
import com.massimodz8.collaborativegrouporder.protocol.nano.StartData;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;


/**
 * This service takes care of managing connection to remote server / master device after we got
 * our playing characters assigned and we're roaming our dream land.
 * We will eventually also fight but that's not much of a big deal for the client.
 */
public class AdventuringService extends Service {
    public StartData.PartyClientData party;
    public int[] actorServerKey;            // actorServerKey[i] corresponds to playedHere[i]
    public CharacterActor[] playedHere;
    public ArrayList<String> errors;
    private Runnable onComplete;
    public CharacterActor currentActor; // One of the actors from playedHere or null.

    public AdventuringService() {
    }

    public void getActorData(final int[] playedHere, final Pumper.MessagePumpingThread server, final @NonNull Runnable onComplete) {
        this.onComplete = onComplete;
        this.playedHere = new CharacterActor[playedHere.length];
        this.actorServerKey = playedHere;

        netPump = new Pumper(handler, MSG_DISCONNECT, MSG_DETACH)
                .add(ProtoBufferEnum.ACTOR_DATA, new PumpTarget.Callbacks<StartData.ActorDefinition>() {
                    private int got;

                    @Override
                    public StartData.ActorDefinition make() { return new StartData.ActorDefinition(); }

                    @Override
                    public boolean mangle(MessageChannel from, StartData.ActorDefinition msg) throws IOException {
                        handler.sendMessage(handler.obtainMessage(MSG_ACTOR_DATA, new Events.ActorData(from, msg)));
                        got++;
                        return got == AdventuringService.this.playedHere.length;
                    }
                });
        netPump.pump(server);

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                Network.LiveActorDataRequest req = new Network.LiveActorDataRequest();
                req.peerKey = playedHere;
                try {
                    server.getSource().writeSync(ProtoBufferEnum.ACTOR_DATA_REQUEST, req);
                } catch (IOException e) {
                    if(errors == null) errors = new ArrayList<>();
                    errors.add(getString(R.string.as_failedDataRequestSend));
                }
                return null;
            }
        }.execute();
    }

    public class LocalBinder extends Binder {
        public AdventuringService getConcreteService() { return AdventuringService.this; }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder(); // this is called once by the OS when first bind is received.
    }

    private Pumper netPump;
    private Handler handler = new MyHandler(this);

    private static final int MSG_DISCONNECT = 0;
    private static final int MSG_DETACH = 1;
    private static final int MSG_ACTOR_DATA = 2;

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
                    self.onComplete.run();
                    break;
                case MSG_ACTOR_DATA: {
                    Events.ActorData real = (Events.ActorData)msg.obj;
                    int count;
                    for(count = 0; count < self.playedHere.length; count++) {
                        if(self.playedHere[count] == null) break;
                        count++;
                    }
                    self.playedHere[count] = CharacterActor.makeLiveActor(real.payload, true);
                } break;
            }
            super.handleMessage(msg);
        }
    }
}

package com.massimodz8.collaborativegrouporder.master;

import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.protobuf.nano.MessageNano;
import com.massimodz8.collaborativegrouporder.JoinVerificator;
import com.massimodz8.collaborativegrouporder.MainMenuActivity;
import com.massimodz8.collaborativegrouporder.networkio.Events;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.networkio.ProtoBufferEnum;
import com.massimodz8.collaborativegrouporder.networkio.PumpTarget;
import com.massimodz8.collaborativegrouporder.networkio.Pumper;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;
import com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Vector;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Created by Massimo on 01/03/2016.
 * Incapsulates machinery to build a list of connected devices which are later upgraded to an
 * identified status and then proceed to force a sequential assignment of playing characters.
 *
 * Devices are put in there by providing their MessageChannel. All async management happens
 * internally and being this designed to work with PartyJoinOrderService, it accumulates changes
 * leaving Activity (not guaranteed to exist at the time it's generated) to probe for results.
 */
public class PcAssignmentHelper {
    public PcAssignmentHelper(PersistentStorage.PartyOwnerData.Group party, JoinVerificator verifier) {
        this.party = party;
        this.verifier = verifier;
        assignment = new ArrayList<>(party.usually.party.length);
        for (PersistentStorage.Actor ignored : party.usually.party) assignment.add(null);
        mailman.start();
    }

    /// Not sure what should I put there, but it seems I might want to track state besides connection channel in the future.
    private static class PlayingDevice {
        public @Nullable MessageChannel pipe; /// This is usually not-null, but becomes null to signal disconnected. Keep those around anyway to allow players to reconnect with ease.
        public int clientVersion;
        public @Nullable byte[] doormat; /// next doormat to send or to consider for key verification.
        public int keyIndex = ANON; /// if isRemote, index of the matched device key --> bound to remote, otherwise check specials
        public Vector<Exception> errors = new Vector<>();

        /// Using null will create this in 'disconnected' mode. Does not make sense to me but w/e.
        public PlayingDevice(@Nullable MessageChannel pipe) {
            this.pipe = pipe;
        }

        public boolean isRemote() { return keyIndex >= 0; }
        //public boolean isLocal() { return keyIndex == LOCAL_BINDING; }
        public boolean isAnonymous() { return keyIndex == ANON; }

        private static final int ANON = -1;
        private static final int LOCAL_BINDING = -2;
    }


    /// Replies to clients must be sent in order! There's a dedicated thread for sending.
    private static class SendRequest {
        final PlayingDevice destination;
        final int type;
        final MessageNano one;
        final MessageNano[] many;

        public SendRequest(PlayingDevice destination, int type, MessageNano payload) {
            this.destination = destination;
            this.type = type;
            one = payload;
            this.many = null;
        }

        public SendRequest(PlayingDevice destination, int type, MessageNano[] payload) {
            this.destination = destination;
            this.type = type;
            one = null;
            many = payload;
        }

        private SendRequest() { // causes the mailman to shut down gracefully
            destination = null;
            type = -1;
            one = null;
            many = null;
        }
    }


    /*
    TODO: for the time being I don't have device keys so what do I do? I will just prethend everybody getting the group key is matching some key,
    TODO depending on the order they happen.
    TODO UGLY UGLY UGLY UGLY UGLY UGLY UGLY UGLY UGLY UGLY */
    private int TODO_shite_ugly_temp_hack;

    public void add(MessageChannel newConn) {
        peers.add(new PlayingDevice(newConn));
        netPump.pump(newConn);
    }

    public boolean hasClients() {
        int count = 0;
        for (PlayingDevice client : peers) {
            if(client.pipe != null & client.isRemote()) count++;
        }
        return count != 0;
    }


    private final PersistentStorage.PartyOwnerData.Group party;
    private final SecureRandom randomizer = new SecureRandom();
    private final JoinVerificator verifier;
    private ArrayList<Integer> assignment;
    private int nextValidRequest;
    private Handler handler = new MyHandler(this);
    private Pumper netPump = new Pumper(handler, MSG_DISCONNECTED, MSG_DETACHED)
            .add(ProtoBufferEnum.HELLO, new PumpTarget.Callbacks<Network.Hello>() {
                @Override
                public Network.Hello make() { return new Network.Hello(); }

                @Override
                public boolean mangle(MessageChannel from, Network.Hello msg) throws IOException {
                    int code = MSG_AUTH_HELLO;
                    if(msg.authorize.length == 0) { // will need a doormat! Reuse a buffer.
                        msg.authorize = random(DOORMAT_BYTES);
                        code = MSG_ANON_HELLO;
                    }
                    handler.sendMessage(handler.obtainMessage(code, new Events.Hello(from, msg)));
                    return false;
                }
            });
    private ArrayList<PlayingDevice> peers = new ArrayList<>();
    private BlockingQueue<SendRequest> out = new ArrayBlockingQueue<>(USUAL_CLIENT_COUNT * USUAL_AVERAGE_MESSAGES_PENDING_COUNT);
    private Thread mailman = new Thread() {
        @Override
        public void run() {
            while(!isInterrupted()) {
                final SendRequest req;
                try {
                    req = out.take();
                } catch (InterruptedException e) {
                    return;
                }
                if (req.destination == null) break;
                MessageChannel pipe = req.destination.pipe;
                if (pipe == null) continue; // impossible
                if (req.one != null) {
                    try {
                        pipe.write(req.type, req.one);
                    } catch (IOException e) {
                        req.destination.errors.add(e);
                    }
                }
                if(req.many != null) {
                    for (MessageNano msg : req.many) {
                        try {
                            pipe.write(req.type, msg);
                        } catch (IOException e) {
                            req.destination.errors.add(e);
                        }
                    }
                }
            }
        }
    };

    private static class MyHandler extends Handler {
        final WeakReference<PcAssignmentHelper> self;

        private MyHandler(PcAssignmentHelper self) {
            this.self = new WeakReference<>(self);
        }

        @Override
        public void handleMessage(Message msg) {
            final PcAssignmentHelper self = this.self.get();
            switch(msg.what) {
                case MSG_DISCONNECTED: {
                    self.disconnected((MessageChannel)msg.obj);
                    break;
                }
                case MSG_DETACHED: {
                    // Code incoherent, not currently thrown
                    break;
                }
                case MSG_ANON_HELLO:
                case MSG_AUTH_HELLO: {
                    Events.Hello real = (Events.Hello)(msg.obj);
                    if(msg.what == MSG_ANON_HELLO) self.helloAnon(real.origin, real.payload);
                    else self.helloAuth(real.origin, real.payload);
                    break;
                }
            }
        }
    }

    private static final int MSG_DISCONNECTED = 1;
    private static final int MSG_DETACHED = 2;
    private static final int MSG_ANON_HELLO = 3;
    private static final int MSG_AUTH_HELLO = 4;

    private static final int DOORMAT_BYTES = 64;
    private static final int USUAL_CLIENT_COUNT = 20; // not really! Usually 5 or less but that's for safety!
    private static final int USUAL_AVERAGE_MESSAGES_PENDING_COUNT = 10; // pretty a lot, those will be small!

    private synchronized byte[] random(int count) {
        if(count < 0) count = -count;
        byte[] blob = new byte[count];
        randomizer.nextBytes(blob);
        return blob;
    }

    private PlayingDevice getDevice(MessageChannel pipe) {
        for (PlayingDevice check : peers) {
            if(check.pipe == pipe) return check;
        }
        return null;
    }

    private void disconnected(final MessageChannel goner) {
        PlayingDevice dev = getDevice(goner);
        if(null == dev) return; // impossible

        netPump.forget(goner);
        new Thread() {
            @Override
            public void run() {
                try {
                    goner.socket.close();
                } catch (IOException e) {
                    // TCP cannot be closed cleanly, pretty much guaranteed. It's ok.
                }
            }
        }.start();
        dev.pipe = null;
        if(dev.isAnonymous()) peers.remove(dev);
    }

    private void helloAnon(@NonNull MessageChannel origin, @NonNull Network.Hello msg) {
        final PlayingDevice dev = getDevice(origin);
        if(null == dev || null == dev.pipe) return; // impossible!
        dev.clientVersion = msg.version;
        dev.doormat = msg.authorize; // that was made by our pumper thread for us!
        final Network.GroupInfo send = new Network.GroupInfo();
        send.forming = false;
        send.name = party.name;
        send.version = MainMenuActivity.NETWORK_VERSION;
        send.doormat = dev.doormat;
        out.add(new SendRequest(dev, ProtoBufferEnum.GROUP_INFO, send));
    }

    private void helloAuth(final @NonNull MessageChannel origin, @NonNull Network.Hello msg) {
        final PlayingDevice dev = getDevice(origin);
        if(null == dev || null == dev.pipe) return; // impossible!
        dev.clientVersion = msg.version;

        // For the time being, this must be the same for all devices.
        // TODO: upgrade to device-specific keys!
        byte[] hash = verifier.mangle(dev.doormat, party.salt);
        if(!Arrays.equals(hash, msg.authorize)) {
            // Device tried to authenticate but I couldn't recognize it.
            // Most likely using an absolete key -> kicked from party!
            // Very odd but why not? Better to just ignore and disconnect the guy.
            peers.remove(dev);
            netPump.forget(origin);
            new Thread() {
                @Override
                public void run() {
                    try {
                        origin.socket.close();
                    } catch (IOException e) {
                        // nothing. It's a goner anyway.
                    }
                }
            }.start();
            return;
        }
        dev.keyIndex = TODO_shite_ugly_temp_hack++;
        sendPlayingCharacterList(dev);
    }

    private void sendPlayingCharacterList(final PlayingDevice dev) {
        // TODO: for the time being I just send everything. In the future I might want to do that differently, for example send only a subset of characters depending on device key
        final Network.PlayingCharacterDefinition[] stream = new Network.PlayingCharacterDefinition[party.usually.party.length];
        for (int loop = 0; loop < party.usually.party.length; loop++) {
            stream[loop] = simplify(party.usually.party[loop], loop);
        }
        out.add(new SendRequest(dev, ProtoBufferEnum.PLAYING_CHARACTER_DEFINITION, stream));
        // Also send a notification for each character which is already assigned to someone else.
        final ArrayList<Integer> bound = new ArrayList<>(party.usually.party.length);
        for(int loop = 0; loop < assignment.size(); loop++) {
            if(assignment.get(loop) != null) bound.add(loop);
        }
        final Network.CharacterOwnership[] initial = new Network.CharacterOwnership[bound.size()];
        for (int loop = 0; loop < initial.length; loop++) {
            initial[loop] = new Network.CharacterOwnership();
            initial[loop].ticket = nextValidRequest;
            initial[loop].type = Network.CharacterOwnership.BOUND;
        }
        out.add(new SendRequest(dev, ProtoBufferEnum.CHARACTER_OWNERSHIP, initial));
    }

    private Network.PlayingCharacterDefinition simplify(PersistentStorage.Actor actor, int loop) {
        Network.PlayingCharacterDefinition res = new Network.PlayingCharacterDefinition();
        PersistentStorage.ActorStatistics currently = actor.stats[0];
        res.name = actor.name;
        res.initiativeBonus = currently.initBonus;
        res.healthPoints = currently.healthPoints;
        res.experience = currently.experience;
        res.level = actor.level;
        res.peerKey = loop;
        return res;
    }


}

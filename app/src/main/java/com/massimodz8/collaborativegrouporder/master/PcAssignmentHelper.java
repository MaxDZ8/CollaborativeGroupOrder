package com.massimodz8.collaborativegrouporder.master;

import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.ViewGroup;

import com.massimodz8.collaborativegrouporder.ActorId;
import com.massimodz8.collaborativegrouporder.JoinVerificator;
import com.massimodz8.collaborativegrouporder.Mailman;
import com.massimodz8.collaborativegrouporder.MainMenuActivity;
import com.massimodz8.collaborativegrouporder.SendRequest;
import com.massimodz8.collaborativegrouporder.networkio.Events;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.networkio.ProtoBufferEnum;
import com.massimodz8.collaborativegrouporder.networkio.PumpTarget;
import com.massimodz8.collaborativegrouporder.networkio.Pumper;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;
import com.massimodz8.collaborativegrouporder.protocol.nano.StartData;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Vector;

/**
 * Created by Massimo on 01/03/2016.
 * Incapsulates machinery to build a list of connected devices which are later upgraded to an
 * identified status and then proceed to force a sequential assignment of playing characters.
 *
 * Devices are put in there by providing their MessageChannel. All async management happens
 * internally and being this designed to work with PartyJoinOrderService, it accumulates changes
 * leaving Activity (not guaranteed to exist at the time it's generated) to probe for results.
 */
public abstract class PcAssignmentHelper {
    public final StartData.PartyOwnerData.Group party;

    public interface OnBoundPcCallback {
        void onUnboundCountChanged(int stillToBind);
    }
    public OnBoundPcCallback onBoundPc;

    public PcAssignmentHelper(StartData.PartyOwnerData.Group party, JoinVerificator verifier) {
        this.party = party;
        this.verifier = verifier;
        assignment = new ArrayList<>(party.party.length);
        for (StartData.ActorDefinition ignored : party.party) assignment.add(null);
        mailman.start();
    }

    public void pump(MessageChannel newConn) {
        peers.add(new PlayingDevice(newConn));
        netPump.pump(newConn);
    }


    public void pump(Pumper.MessagePumpingThread worker) {
        peers.add(new PlayingDevice(worker.getSource()));
        netPump.pump(worker);
    }

    public int getNumIdentifiedClients() {
        int count = 0;
        for (PlayingDevice client : peers) {
            if(client.pipe != null & !client.isAnonymous()) count++;
        }
        return count;
    }

    public ArrayList<StartData.ActorDefinition> getUnboundedPcs() {
        ArrayList<StartData.ActorDefinition> list = new ArrayList<>();
        for(int loop = 0; loop < party.party.length; loop++) {
            if(assignment.get(loop) != null) continue;
            list.add(party.party[loop]);
        }
        return list;
    }

    public int getNumUnboundedPcs() {
        int count = 0;
        for(int loop = 0; loop < party.party.length; loop++) {
            if(assignment.get(loop) != null) continue;
            count++;
        }
        return count;
    }

    public void local(StartData.ActorDefinition actor) {
        int match;
        for(match = 0; match < party.party.length; match++) {
            if(actor == party.party[match]) break;
        }
        if(match == party.party.length) return;
        final Integer ownerIndex = assignment.get(match);
        if(ownerIndex != null && ownerIndex == LOCAL_BINDING) return;
        assignment.set(match, LOCAL_BINDING);
        Network.CharacterOwnership rebound = new Network.CharacterOwnership();
        rebound.type = Network.CharacterOwnership.BOUND;
        rebound.ticket = nextValidRequest;
        rebound.character = match;
        for (PlayingDevice dst : peers) {
            if(dst.pipe != null) mailman.out.add(new SendRequest(dst.pipe, ProtoBufferEnum.CHARACTER_OWNERSHIP, rebound));
        }
        if(unboundPcAdapter != null) unboundPcAdapter.notifyDataSetChanged();
        if(authDeviceAdapter != null) authDeviceAdapter.notifyDataSetChanged();
        if(onBoundPc != null) onBoundPc.onUnboundCountChanged(getNumUnboundedPcs());
    }

    public MessageChannel getMessageChannelByPeerKey(int id) {
        if(id < assignment.size()) {
            final Integer ownerIndex = assignment.get(id);
            if(ownerIndex == null || ownerIndex == LOCAL_BINDING) return null;
            return peers.get(ownerIndex).pipe;
        }
        return null;
    }


    public void shutdown() {
        mailman.out.add(new SendRequest());
        final Pumper.MessagePumpingThread[] bye = netPump.move();
        if(bye == null || bye.length == 0) return;
        new Thread() {
            @Override
            public void run() {
                for (Pumper.MessagePumpingThread worker : bye) {
                    worker.interrupt();
                    try {
                        worker.getSource().socket.close();
                    } catch (IOException e) {
                        // simply suppress.
                    }
                }

            }
        }.start();
    }

    /**
     * This still keeps a reference to sockets and all mappings. The only data which is going
     * away is those of devices which were not mapped to any characters. They are useless.
     * @return Worker threads to be sent to the "adventuring" pumper.
     */
    public ArrayList<Pumper.MessagePumpingThread> getBoundKickOthers() {
        final ArrayList<PlayingDevice> goners = new ArrayList<>();
        final ArrayList<Pumper.MessagePumpingThread> players = new ArrayList<>();
        for (PlayingDevice dev : peers) {
            int count = 0;
            if(dev.pipe != null) {
                for (Integer check : assignment) {
                    if(check != null && check >= 0 && check < peers.size()) {
                        if(peers.get(check) == dev) count++;
                    }
                }
            }
            if(count == 0) goners.add(dev);
            else players.add(netPump.move(dev.pipe));
        }
        peers.removeAll(goners);
        final Pumper.MessagePumpingThread[] byebye = netPump.move();
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (Pumper.MessagePumpingThread worker : byebye) worker.interrupt();
                for (Pumper.MessagePumpingThread worker : byebye) {
                    MessageChannel pipe = worker.getSource();
                    if(pipe != null) try {
                        pipe.socket.close();
                    } catch (IOException e) {
                        // I don't care bro. You're nobody I care anymore.
                    }
                }
            }
        }).start();
        return players;
    }

    private final SecureRandom randomizer = new SecureRandom();
    private final JoinVerificator verifier;
    ArrayList<Integer> assignment;
    private int nextValidRequest;
    private Handler handler = new MyHandler(this);
    Pumper netPump = new Pumper(handler, MSG_DISCONNECTED, MSG_DETACHED)
            .add(ProtoBufferEnum.HELLO, new PumpTarget.Callbacks<Network.Hello>() {
                @Override
                public Network.Hello make() { return new Network.Hello(); }

                @Override
                public boolean mangle(MessageChannel from, Network.Hello msg) throws IOException {
                    int code = MSG_HELLO_AUTH;
                    if(msg.authorize.length == 0) { // will need a doormat! Reuse a buffer.
                        msg.authorize = random(DOORMAT_BYTES);
                        code = MSG_HELLO_ANON;
                    }
                    handler.sendMessage(handler.obtainMessage(code, new Events.Hello(from, msg)));
                    return false;
                }
            }).add(ProtoBufferEnum.CHARACTER_OWNERSHIP, new PumpTarget.Callbacks<Network.CharacterOwnership>() {
                @Override
                public Network.CharacterOwnership make() {
                    return new Network.CharacterOwnership();
                }

                @Override
                public boolean mangle(MessageChannel from, Network.CharacterOwnership msg) throws IOException {
                    if (msg.type != Network.CharacterOwnership.REQUEST)
                        return false; // non-requests are silently ignored.
                    handler.sendMessage(handler.obtainMessage(MSG_CHAR_OWNERSHIP_REQUEST, new Events.CharOwnership(from, msg)));
                    return false;
                }
            });
    ArrayList<PlayingDevice> peers = new ArrayList<>();

    /// Not sure what should I put there, but it seems I might want to track state besides connection channel in the future.
    static class PlayingDevice {
        public @Nullable MessageChannel pipe; /// This is usually not-null, but becomes null to signal disconnected. Keep those around anyway to allow players to reconnect with ease.
        public int clientVersion;
        public @Nullable byte[] doormat; /// next doormat to send or to consider for key verification.
        public int keyIndex = ANON; /// if isRemote, index of the matched device key --> bound to remote, otherwise check specials
        public Vector<Exception> errors = new Vector<>();
        public boolean movedToBattlePumper; // if this is set, device is already moved to battle pumper, somewhere else.
        public @ActorId int activeActor; // peerKey > 0 of active actor, otherwise -1

        /// Using null will create this in 'disconnected' mode. Does not make sense to me but w/e.
        public PlayingDevice(@Nullable MessageChannel pipe) {
            this.pipe = pipe;
        }

        public boolean isAnonymous() { return keyIndex == ANON; }

        private static final int ANON = -1;
    }


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
                    // Never happens now!
                    break;
                }
                case MSG_HELLO_ANON:
                case MSG_HELLO_AUTH: {
                    Events.Hello real = (Events.Hello)(msg.obj);
                    if(msg.what == MSG_HELLO_ANON) self.helloAnon(real.origin, real.payload);
                    else self.helloAuth(real.origin, real.payload);
                    break;
                }
                case MSG_CHAR_OWNERSHIP_REQUEST: {
                    final Events.CharOwnership real = (Events.CharOwnership)msg.obj;
                    self.charOwnership(real.origin, real.payload);
                    break;
                }
            }
        }
    }

    private static final int MSG_DISCONNECTED = 1;
    private static final int MSG_DETACHED = 2;
    private static final int MSG_HELLO_ANON = 3;
    private static final int MSG_HELLO_AUTH = 4;
    private static final int MSG_CHAR_OWNERSHIP_REQUEST = 5;

    /// Caution. This currently must match PartyCreationService.closeGroup
    public static final int DOORMAT_BYTES = 32;
    public static final int LOCAL_BINDING = -1;

    private synchronized byte[] random(int count) {
        if(count < 0) count = -count;
        byte[] blob = new byte[count];
        randomizer.nextBytes(blob);
        return blob;
    }

    public PlayingDevice getDevice(MessageChannel pipe) {
        for (PlayingDevice check : peers) {
            if(check.pipe == pipe) return check;
        }
        return null;
    }

    private PlayingDevice getDeviceByKeyIndex(int index) {
        for (PlayingDevice check : peers) {
            if(check.keyIndex == index) return check;
        }
        return null;
    }

    protected abstract StartData.ActorDefinition getActorData(int unique);

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
        mailman.out.add(new SendRequest(dev.pipe, ProtoBufferEnum.GROUP_INFO, send));
    }

    private void helloAuth(final @NonNull MessageChannel origin, @NonNull Network.Hello msg) {
        final PlayingDevice dev = getDevice(origin);
        if(null == dev || null == dev.pipe || null == dev.doormat) return; // impossible!
        dev.clientVersion = msg.version;

        // For the time being, this must be the same for all devices.
        final Integer match = verifier.match(dev.doormat, msg.authorize);
        if(match == null) {
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
        boolean signal = dev.isAnonymous();
        dev.keyIndex = match;
        sendPlayingCharacterList(dev);
        if(signal && authDeviceAdapter != null) authDeviceAdapter.notifyDataSetChanged(); // no guarantee about ordering of auths.
    }

    private void sendPlayingCharacterList(final PlayingDevice dev) {
        // TODO: for the time being I just send everything. In the future I might want to do that differently, for example send only a subset of characters depending on device key
        final Network.PlayingCharacterDefinition[] stream = new Network.PlayingCharacterDefinition[party.party.length];
        for (int loop = 0; loop < party.party.length; loop++) {
            stream[loop] = simplify(party.party[loop], loop);
        }
        if(dev.pipe != null) mailman.out.add(new SendRequest(dev.pipe, ProtoBufferEnum.PLAYING_CHARACTER_DEFINITION, stream));
        // Also send a notification for each character which is already assigned to someone else.
        final ArrayList<Integer> bound = new ArrayList<>(party.party.length);
        for(int loop = 0; loop < assignment.size(); loop++) {
            if(assignment.get(loop) != null) bound.add(loop);
        }
        final Network.CharacterOwnership[] initial = new Network.CharacterOwnership[bound.size()];
        for (int loop = 0; loop < initial.length; loop++) {
            initial[loop] = new Network.CharacterOwnership();
            initial[loop].ticket = nextValidRequest;
            initial[loop].type = Network.CharacterOwnership.BOUND;
        }
        mailman.out.add(new SendRequest(dev.pipe, ProtoBufferEnum.CHARACTER_OWNERSHIP, initial));
    }

    private Network.PlayingCharacterDefinition simplify(StartData.ActorDefinition actor, int loop) {
        Network.PlayingCharacterDefinition res = new Network.PlayingCharacterDefinition();
        StartData.ActorStatistics currently = actor.stats[0];
        res.name = actor.name;
        res.initiativeBonus = currently.initBonus;
        res.healthPoints = currently.healthPoints;
        res.experience = actor.experience;
        res.level = actor.level;
        res.peerKey = loop;
        return res;
    }

    private void charOwnership(MessageChannel origin, Network.CharacterOwnership payload) {
        final PlayingDevice requester = getDevice(origin);
        if(requester == null) return; // impossible
        final int ticket = payload.ticket;
        payload.ticket = nextValidRequest;
        if(ticket != nextValidRequest) {
            payload.type = Network.CharacterOwnership.OBSOLETE;
            if(requester.pipe != null)  mailman.out.add(new SendRequest(requester.pipe, ProtoBufferEnum.CHARACTER_OWNERSHIP, payload));
            return;
        }
        if(payload.character >= party.party.length) { // requester asked chars before providing key.
            payload.type = Network.CharacterOwnership.REJECTED;
            if(requester.pipe != null) mailman.out.add(new SendRequest(requester.pipe, ProtoBufferEnum.CHARACTER_OWNERSHIP, payload));
            return;
        }
        Integer currKeyIndex = assignment.get(payload.character);
        if(currKeyIndex != null && currKeyIndex == LOCAL_BINDING) { // bound to me, silently rejected as mapping to me is a very strong action
            payload.type = Network.CharacterOwnership.REJECTED;
            if(requester.pipe != null) mailman.out.add(new SendRequest(requester.pipe, ProtoBufferEnum.CHARACTER_OWNERSHIP, payload));
            return;
        }
        // taking ownership from AVAIL is silently accepted as common.
        // same for giving ownership away, cannot force someone to play
        final PlayingDevice currOwner = currKeyIndex != null ? getDeviceByKeyIndex(currKeyIndex) : null;
        if(currKeyIndex == null || currOwner == requester) {
            Integer newMapping = currKeyIndex == null? requester.keyIndex : null;
            assignment.set(payload.character, newMapping);
            payload.type = Network.CharacterOwnership.ACCEPTED;
            payload.ticket = ++nextValidRequest;
            if(requester.pipe != null) mailman.out.add(new SendRequest(requester.pipe, ProtoBufferEnum.CHARACTER_OWNERSHIP, payload));
            int type = newMapping == null? Network.CharacterOwnership.AVAIL : Network.CharacterOwnership.BOUND;
            sendAvailability(type, payload.character, origin, nextValidRequest);
            if(unboundPcAdapter != null) unboundPcAdapter.notifyDataSetChanged();
            if(authDeviceAdapter != null) authDeviceAdapter.notifyDataSetChanged();
            if(onBoundPc != null && currKeyIndex == null) onBoundPc.onUnboundCountChanged(getNumUnboundedPcs());
            return;
        }
        // Serious shit. We have a collision. In a first implementation I spawned a dialog message asking the master to choose
        // but now requests must be sequential, receiving a second request would make the ticket obsolete and
        // I don't want all the other requests to be somehow blocked or rejected in the meanwhile...
        // So reject this instead. Looks like this is the only viable option.
        payload.type = Network.CharacterOwnership.REJECTED;
        if(requester.pipe != null) mailman.out.add(new SendRequest(requester.pipe, ProtoBufferEnum.CHARACTER_OWNERSHIP, payload));
    }

    private void sendAvailability(int type, int charIndex, MessageChannel excluding, int request) {
        for (PlayingDevice peer : peers) {
            if(peer.isAnonymous()) continue;
            if(peer.pipe == excluding) continue; // got it already asyncronously
            final Network.CharacterOwnership notification = new Network.CharacterOwnership();
            notification.ticket = request;
            notification.character = charIndex;
            notification.type = type;
            if(peer.pipe != null) mailman.out.add(new SendRequest(peer.pipe, ProtoBufferEnum.CHARACTER_OWNERSHIP, notification));
        }
    }


    public interface AuthDeviceHolderFactoryBinder<VH extends RecyclerView.ViewHolder> {
        VH createUnbound(ViewGroup parent, int viewType);
        /**
         * Bind some data maintained by me to the holder. I take care of the mappings, you do the
         * actual layout! Data passed here can be retained, it won't be reused, they are all locals.
         * @param deviceName Resolved device name. Maybe I should be smarter about that.
         * @param characters Each value is the index to use in PartyOwnerData.Group.Definition.party
         *                   notably, those are NOT peer ids, as there's no such thing there.
         */
        void bind(@NonNull VH target, @NonNull String deviceName, @Nullable ArrayList<Integer> characters);
    }

    public static class AuthDeviceAdapter<VH extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<VH> {
        private final AuthDeviceHolderFactoryBinder<VH> factory;
        private final PcAssignmentHelper owner;

        AuthDeviceAdapter(@NonNull AuthDeviceHolderFactoryBinder<VH> factory, @NonNull PcAssignmentHelper owner) {
            this.factory = factory;
            this.owner = owner;
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            return factory.createUnbound(parent, viewType);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            PlayingDevice dev = null;
            int index = 0;
            for (PlayingDevice match : owner.peers) {
                if(match.isAnonymous()) continue;
                if(index == position) {
                    dev = match;
                    break;
                }
                index++;
            }
            if(null == dev) return; // impossible
            ArrayList<Integer> charList = null;
            for (int loop = 0; loop < owner.assignment.size(); loop++) {
                Integer match = owner.assignment.get(loop);
                if(null == match || match == LOCAL_BINDING) continue;
                if(dev == owner.getDeviceByKeyIndex(match)) {
                    if(charList == null) charList = new ArrayList<>();
                    charList.add(loop);
                }
            }
            factory.bind(holder, owner.party.devices[dev.keyIndex].name, charList);
        }

        @Override
        public int getItemCount() {
            int count = 0;
            for (PlayingDevice match : owner.peers) {
                if (match.isAnonymous()) continue;
                count++;
            }
            return count;
        }
    }
    public AuthDeviceAdapter authDeviceAdapter;


    public interface UnassignedPcHolderFactoryBinder<VH extends RecyclerView.ViewHolder> {
        VH createUnbound(ViewGroup parent, int viewType);

        /**
         * @param index Character index in party owner PC usual array, get your info there.
         */
        void bind(@NonNull VH target, int index);
    }
    public static class UnassignedPcsAdapter<VH extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<VH> {
        private final UnassignedPcHolderFactoryBinder<VH> factory;
        private final PcAssignmentHelper owner;

        public UnassignedPcsAdapter(@NonNull UnassignedPcHolderFactoryBinder<VH> factory, @NonNull PcAssignmentHelper owner) {
            this.factory = factory;
            this.owner = owner;
            setHasStableIds(true);
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            return factory.createUnbound(parent, viewType);
        }

        @Override
        public long getItemId(int position) {
            for(int scan = 0; scan < owner.assignment.size(); scan++) {
                if(null != owner.assignment.get(scan)) continue;
                if(0 == position) return scan;
                position--;
            }
            return RecyclerView.NO_ID;
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            int slot;
            for(slot = 0; slot < owner.assignment.size(); slot++) {
                if(null != owner.assignment.get(slot)) continue;
                if(0 == position) break;
                position--;
            }
            factory.bind(holder, slot);
        }

        @Override
        public int getItemCount() {
            int count = 0;
            for (Integer dev : owner.assignment) {
                if(null == dev) count++;
            }
            return count;
        }
    }
    public UnassignedPcsAdapter unboundPcAdapter;
    public final Mailman mailman = new Mailman();
}
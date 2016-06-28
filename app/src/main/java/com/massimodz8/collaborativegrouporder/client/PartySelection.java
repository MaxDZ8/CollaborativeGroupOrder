package com.massimodz8.collaborativegrouporder.client;

import android.net.nsd.NsdManager;
import android.os.Handler;
import android.os.Message;

import com.massimodz8.collaborativegrouporder.AccumulatingDiscoveryListener;
import com.massimodz8.collaborativegrouporder.Mailman;
import com.massimodz8.collaborativegrouporder.MainMenuActivity;
import com.massimodz8.collaborativegrouporder.PartyInfo;
import com.massimodz8.collaborativegrouporder.PseudoStack;
import com.massimodz8.collaborativegrouporder.SendRequest;
import com.massimodz8.collaborativegrouporder.master.PartyCreator;
import com.massimodz8.collaborativegrouporder.networkio.Events;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.networkio.ProtoBufferEnum;
import com.massimodz8.collaborativegrouporder.networkio.PumpTarget;
import com.massimodz8.collaborativegrouporder.networkio.Pumper;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * Created by Massimo on 14/06/2016.
 * Model for SelectFormingGroupActivity. We are enumerating parties we find on the network or
 * by connecting directly to them.
 */
public class PartySelection implements AccumulatingDiscoveryListener.OnTick {
    final AccumulatingDiscoveryListener explorer = new AccumulatingDiscoveryListener();
    final public Mailman sender = new Mailman();
    final ArrayList<GroupState> candidates = new ArrayList<>();
    final MyHandler handler = new MyHandler(this);


    public GroupState resParty;
    public Pumper.MessagePumpingThread resWorker;
    public int resAdvancement;

    public void shutdown() {
        explorer.stopDiscovery();
        sender.out.add(new SendRequest());
        sender.interrupt();
        new Thread() {
            @Override
            public void run() {
                for (Pumper.MessagePumpingThread goner : netPump.move()) {
                    goner.interrupt();
                    try {
                        goner.getSource().socket.close();
                    } catch (IOException e) {
                        // bye!
                    }
                }

            }
        }.start();
    }

    interface Listener {
        void onDisconnected(GroupState gs);
        void onPartyFoundOrLost();
        void onPartyInfoChanged(GroupState gs);
        void onPartyCharBudgetChanged(GroupState gs);
        void onKeyReceived(GroupState got);

        void onDone();
    }

    PseudoStack<Listener> onEvent = new PseudoStack<>();

    static class MyHandler extends Handler {
        public MyHandler(PartySelection target) { this.target = new WeakReference<>(target); }

        final WeakReference<PartySelection> target;

        @Override
        public void handleMessage(Message msg) {
            PartySelection target = this.target.get();
            final Listener dispatch = target.onEvent.get();
            switch(msg.what) {
                case MSG_CHECK_NETWORK_SERVICES: {
                    if(target.checkNetwork() && dispatch != null) dispatch.onPartyFoundOrLost();
                } break;
                case MSG_SOCKET_DISCONNECTED: {
                    final Events.SocketDisconnected real = (Events.SocketDisconnected) msg.obj;
                    final GroupState gs = target.getParty(real.which);
                    if(gs == null) break;
                    gs.disconnected = real.reason;
                    if(dispatch != null) dispatch.onDisconnected(gs);
                } break;
                case MSG_GROUP_INFO: {
                    final Events.GroupInfo real = (Events.GroupInfo) msg.obj;
                    GroupState gs = target.getParty(real.which);
                    if(gs == null) return;
                    if(real.payload.name.isEmpty()) return; // I consider those malicious.
                    PartyInfo keep = new PartyInfo(real.payload.version, real.payload.name, real.payload.advancementPace);
                    keep.options = real.payload.options; /// TODO: those should be localized
                    gs.group = keep;
                    if(dispatch != null) dispatch.onPartyInfoChanged(gs);
                } break;
                case MSG_CHAR_BUDGET: {
                    final Events.CharBudget real = (Events.CharBudget) msg.obj;
                    GroupState gs = target.getParty(real.which);
                    if(gs == null) return; // This might happen if we're shutting down or if lost connection to discovered services and already removed
                    if(real.payload.charSpecific != 0) return; // no playing charactes defined there!
                    gs.charBudget = real.payload.total;
                    gs.nextMsgDelay_ms = real.payload.period;
                    if(dispatch != null) dispatch.onPartyCharBudgetChanged(gs);
                } break;
                case MSG_PUMPER_DETACHED: {
                    final MessageChannel real = (MessageChannel)msg.obj;
                    GroupState got = target.getParty(real);
                    if(got == null) return; // impossible
                    // Also get the rid of everything that isn't you. Farewell.
                    target.resParty = got;
                    target.resWorker = target.netPump.move(got.channel);
                    target.resAdvancement = got.group.advancementPace;
                    if(dispatch != null) dispatch.onDone();
                    break;
                }
                case MSG_GROUP_FORMED: {
                    final Events.GroupKey real = (Events.GroupKey) msg.obj;
                    GroupState got = target.getParty(real.origin);
                    if(got == null) return; // impossible
                    got.salt = real.key;
                    if(dispatch != null) dispatch.onKeyReceived(got);
                }
            }
        }
    }

    final Pumper netPump = new Pumper(handler, MSG_SOCKET_DISCONNECTED, MSG_PUMPER_DETACHED)
            .add(ProtoBufferEnum.GROUP_INFO, new PumpTarget.Callbacks<Network.GroupInfo>() {
                @Override
                public Network.GroupInfo make() {
                    return new Network.GroupInfo();
                }

                @Override
                public boolean mangle(MessageChannel from, Network.GroupInfo msg) throws IOException {
                    handler.sendMessage(handler.obtainMessage(MSG_GROUP_INFO, new Events.GroupInfo(from, msg)));
                    return false;
                }
            }).add(ProtoBufferEnum.CHAR_BUDGET, new PumpTarget.Callbacks<Network.CharBudget>() {
                @Override
                public Network.CharBudget make() {
                    return new Network.CharBudget();
                }

                @Override
                public boolean mangle(MessageChannel from, Network.CharBudget msg) throws IOException {
                    handler.sendMessage(handler.obtainMessage(MSG_CHAR_BUDGET, new Events.CharBudget(from, msg)));
                    return false;
                }
            }).add(ProtoBufferEnum.GROUP_FORMED, new PumpTarget.Callbacks<Network.GroupFormed>() {
                @Override
                public Network.GroupFormed make() { return new Network.GroupFormed(); }

                @Override
                public boolean mangle(MessageChannel from, Network.GroupFormed msg) throws IOException {
                    handler.sendMessage(handler.obtainMessage(MSG_GROUP_FORMED, new Events.GroupKey(from, msg.salt)));
                    return true;
                }
            });

    public PartySelection(NsdManager nsd) {
        sender.start();
        explorer.beginDiscovery(PartyCreator.PARTY_FORMING_SERVICE_TYPE, nsd, this);
    }


    static final int MSG_CHECK_NETWORK_SERVICES = 1;
    static final int MSG_SOCKET_DISCONNECTED = 2;
    static final int MSG_GROUP_INFO = 3;
    static final int MSG_CHAR_BUDGET = 4;
    static final int MSG_PUMPER_DETACHED = 5;
    static final int MSG_GROUP_FORMED = 6;

    private int prevDiscoveryStatus = AccumulatingDiscoveryListener.IDLE;

    boolean checkNetwork() {
        int status = explorer.getDiscoveryStatus();
        if(status == AccumulatingDiscoveryListener.IDLE || status == AccumulatingDiscoveryListener.STARTING) return false;

        int diffs = prevDiscoveryStatus != status? 1 : 0;
        prevDiscoveryStatus = status;
        synchronized (explorer.foundServices) {
            if (explorer.foundServices.size() == 0 && candidates.size() == 0) return diffs != 0;
            for (AccumulatingDiscoveryListener.FoundService el : explorer.foundServices) {
                int match = el.socket == null ? candidates.size() : 0;
                for (; match < candidates.size(); match++) {
                    if (candidates.get(match).channel.socket == el.socket) break;
                }
                if (match == candidates.size()) {
                    final GroupState ngs = new GroupState(new MessageChannel(el.socket));
                    candidates.add(ngs);
                    netPump.pump(ngs.channel);
                    Network.Hello payload = new Network.Hello();
                    payload.version = MainMenuActivity.NETWORK_VERSION;
                    sender.out.add(new SendRequest(ngs.channel, ProtoBufferEnum.HELLO, payload, null));
                    diffs++;
                }
            }
            for (int loop = 0; loop < candidates.size(); loop++) {
                final GroupState gs = candidates.get(loop);
                if (!gs.discovered) continue;
                AccumulatingDiscoveryListener.FoundService match = null;
                for (AccumulatingDiscoveryListener.FoundService el : explorer.foundServices) {
                    if (el.socket == gs.channel.socket) {
                        match = el;
                        break;
                    }
                }
                if (match == null) {
                    candidates.remove(loop--);
                    diffs++;
                }
            }
        }
        return diffs != 0;
    }

    GroupState getParty(MessageChannel c) {
        for(GroupState gs : candidates) {
            if(gs.channel == c) return gs;
        }
        return null;
    }

    // AccumulatingDiscoveryListener.OnTick vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv
    @Override
    public void tick(int old, int current) {
        handler.sendMessage(handler.obtainMessage(MSG_CHECK_NETWORK_SERVICES));
    }
    // AccumulatingDiscoveryListener.OnTick ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
}

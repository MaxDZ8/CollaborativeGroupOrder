package com.massimodz8.collaborativegrouporder.client;

import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;

import com.massimodz8.collaborativegrouporder.AccumulatingDiscoveryListener;
import com.massimodz8.collaborativegrouporder.JoinVerificator;
import com.massimodz8.collaborativegrouporder.Mailman;
import com.massimodz8.collaborativegrouporder.MainMenuActivity;
import com.massimodz8.collaborativegrouporder.MaxUtils;
import com.massimodz8.collaborativegrouporder.PartyInfo;
import com.massimodz8.collaborativegrouporder.PseudoStack;
import com.massimodz8.collaborativegrouporder.SendRequest;
import com.massimodz8.collaborativegrouporder.master.PartyJoinOrder;
import com.massimodz8.collaborativegrouporder.networkio.Events;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.networkio.ProtoBufferEnum;
import com.massimodz8.collaborativegrouporder.networkio.PumpTarget;
import com.massimodz8.collaborativegrouporder.networkio.Pumper;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;
import com.massimodz8.collaborativegrouporder.protocol.nano.StartData;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.Socket;
import java.util.ArrayList;

/**
 * Created by Massimo on 15/06/2016.
 * Model for JoinSessionActivity, this is what the client does to join a game.
 * This part focuses on initial handshaking with the server (if not already done) and pulling
 * the list of available characters.
 */
public class JoinGame implements AccumulatingDiscoveryListener.OnTick {
    /// This must be initially populated by whoever spans this activity.
    final public StartData.PartyClientData.Group party;
    public final Pumper.MessagePumpingThread serverConn;

    public void shutdown() {
        sender.out.add(new SendRequest());
        sender.interrupt();
        new Thread() {
            @Override
            public void run() {
                for (Pumper.MessagePumpingThread goner : pumper.move()) {
                    goner.interrupt();
                    try {
                        goner.getSource().socket.close();
                    } catch (IOException e) {
                        // suppress
                    }
                }
            }
        }.start();
    }

    public static class Result {
        public final Pumper.MessagePumpingThread worker; // if serverConn was not null then references the same object
        public final Network.PlayingCharacterDefinition first; // if I get one of those it's because I have been identified.

        protected Result(Pumper.MessagePumpingThread worker, Network.PlayingCharacterDefinition first) {
            this.worker = worker;
            this.first = first;
        }
    }
    public Result result;

    final AccumulatingDiscoveryListener explorer;
    ArrayList<PartyAttempt> attempts = new ArrayList<>();
    final Mailman sender = new Mailman();

    final PseudoStack<Runnable> onEvent = new PseudoStack<>();

    public JoinGame(StartData.PartyClientData.Group party, @Nullable Pumper.MessagePumpingThread serverConn, NsdManager nsd) {
        this.party = party;
        this.serverConn = serverConn;
        sender.start();
        if(serverConn == null) { // explore mode
            explorer = new AccumulatingDiscoveryListener();
            explorer.beginDiscovery(PartyJoinOrder.PARTY_GOING_ADVENTURING_SERVICE_TYPE, nsd, this);
        }
        else { // connect to specified mode
            explorer = null;
            PartyAttempt dummy = new PartyAttempt(null);
            dummy.pipe = serverConn.getSource();
            pumper.pump(serverConn);
            attempts.add(dummy);
            dummy.refresh(); // kick in our pretty sequence of events.
        }
    }

    class PartyAttempt {
        final NsdServiceInfo source; /// set first
        MessageChannel pipe;
        volatile PartyInfo party; /// We get this from successful handshake
        volatile Network.PlayingCharacterDefinition charDef; // we get this from successful authorize

        private int lastSend = SENT_NOTHING;

        static final int SENT_NOTHING = 0;
        static final int SENT_DOORMAT_REQUEST = 1;
        static final int SENT_KEY = 2;
        boolean waitServerReply;
        Exception error;

        byte[] doormat;
        public boolean discarded;

        private PartyAttempt(NsdServiceInfo source) {
            this.source = source;
        }

        /// Start an handshaking process which will produce this.party with the info we care.
        public void connect() {
            if(null != source) {
                new AsyncTask<Void, Void, MessageChannel>() {
                    Exception err;

                    @Override
                    protected MessageChannel doInBackground(Void... params) {
                        Socket s;
                        try {
                            s = new Socket(source.getHost(), source.getPort());
                        } catch (IOException e) {
                            err = e;
                            return null; // do nothing, I might have to signal that but I don't care for now, I'm late.
                        }
                        return new MessageChannel(s);
                    }

                    @Override
                    protected void onPostExecute(MessageChannel messageChannel) {
                        pipe = messageChannel;
                        error = err;
                        final Runnable runnable = onEvent.get();
                        if(runnable != null) runnable.run();
                    }
                }.execute();
            }
        }

        // Pipe is ready to transfer something.
        public void refresh() {
            if(waitServerReply) return;
            if(error != null) return;
            if(pipe == null) return; // not connected yet
            switch(lastSend) {
                case SENT_NOTHING: {
                    final Network.Hello keyed = new Network.Hello();
                    keyed.version = MainMenuActivity.NETWORK_VERSION;
                    sender.out.add(new SendRequest(pipe, ProtoBufferEnum.HELLO, keyed, null));
                    lastSend = SENT_DOORMAT_REQUEST;
                } break;
                case SENT_DOORMAT_REQUEST: {
                    JoinVerificator helper = new JoinVerificator(JoinGame.this.party, MaxUtils.hasher);
                    final Network.Hello auth = new Network.Hello();
                    auth.authorize = helper.mangle(doormat);
                    auth.version = MainMenuActivity.NETWORK_VERSION;
                    sender.out.add(new SendRequest(pipe, ProtoBufferEnum.HELLO, auth, null));
                    lastSend = SENT_KEY;
                }
            }
            waitServerReply = true;
        }
    }
    private MyHandler handler = new MyHandler(this);
    private final Pumper pumper = new Pumper(handler, MSG_DISCONNECTED, MSG_DETACHED)
            .add(ProtoBufferEnum.GROUP_INFO, new PumpTarget.Callbacks<Network.GroupInfo>() {
                @Override
                public Network.GroupInfo make() { return new Network.GroupInfo(); }

                @Override
                public boolean mangle(MessageChannel from, Network.GroupInfo msg) throws IOException {
                    handler.sendMessage(handler.obtainMessage(MSG_PARTY_INFO, new Events.GroupInfo(from, msg)));
                    return false;
                }
            }).add(ProtoBufferEnum.PLAYING_CHARACTER_DEFINITION, new PumpTarget.Callbacks<Network.PlayingCharacterDefinition>() {
                @Override
                public Network.PlayingCharacterDefinition make() { return new Network.PlayingCharacterDefinition(); }

                @Override
                public boolean mangle(MessageChannel from, Network.PlayingCharacterDefinition msg) throws IOException {
                    handler.sendMessage(handler.obtainMessage(MSG_CHAR_DEFINITION, new Events.CharacterDefinition(from, msg)));
                    return true;
                }
            });


    private static class MyHandler extends Handler {
        final WeakReference<JoinGame> me;

        private MyHandler(JoinGame me) {
            this.me = new WeakReference<>(me);
        }

        @Override
        public void handleMessage(Message msg) {
            final JoinGame me = this.me.get();
            final Runnable callback = me.onEvent.get();
            switch(msg.what) {
                case MSG_TICK_EXPLORER:
                    if(me.explorer == null) break; // impossible but static analyzer cannot figure
                    synchronized(me.explorer.foundServices) {
                        me.connectNewDiscoveries();
                        for (PartyAttempt party : me.attempts) party.refresh();
                    }
                    break;
                case MSG_DISCONNECTED: { // uhm... I cannot get to process this for anything I care, so...
                    MessageChannel real = (MessageChannel)msg.obj;
                    for (PartyAttempt check : me.attempts) {
                        if(real != check.pipe) continue;
                        check.pipe = null; // not a valid group, restart handshake from scratch if needed.
                    }
                } break;
                case MSG_DETACHED: { // that's what happens when we get the party info. It's ok.
                    MessageChannel real = (MessageChannel)msg.obj;
                    for (PartyAttempt check : me.attempts) {
                        if(real == check.pipe) {
                            me.result = new Result(me.pumper.move(real), check.charDef);
                            if(callback != null) callback.run();
                            return;
                        }
                    }
                } break;
                case MSG_PARTY_INFO: {
                    Events.GroupInfo real = (Events.GroupInfo)msg.obj;
                    PartyAttempt match = null;
                    for (PartyAttempt test : me.attempts) {
                        if(test.pipe == real.which) {
                            match = test;
                            break;
                        }
                    }
                    if(null == match) break; // impossible but make static tools happy
                    if(real.payload.forming || 0 == real.payload.doormat.length || !real.payload.name.equals(me.party.name)) { // this server is uninteresting, uncollaborative or simply another thing.
                        final Pumper.MessagePumpingThread goner = me.pumper.move(match.pipe);
                        match.discarded = true;
                        match.pipe = null;
                        new Thread() {
                            @Override
                            public void run() {
                                goner.interrupt();
                                try {
                                    goner.getSource().socket.close();
                                } catch (IOException e) {
                                    // it's a goner anyway
                                }
                            }
                        }.start();
                        break;
                    }
                    match.party = new PartyInfo(real.payload.version, real.payload.name);
                    match.party.options = real.payload.options;
                    match.doormat = real.payload.doormat;
                    match.waitServerReply = false;
                    match.refresh();

                } break;
                case MSG_CHAR_DEFINITION: {
                    Events.CharacterDefinition real = (Events.CharacterDefinition)msg.obj;
                    PartyAttempt match = null;
                    for (PartyAttempt test : me.attempts) {
                        if(test.pipe == real.origin) {
                            match = test;
                            break;
                        }
                    }
                    if(null == match) return; // impossible but make static tools happy
                    match.charDef = real.character; // wait for detach to populate my pumper, then go to PC selection.
                } break;
            }
            if(callback != null) callback.run();
        }
    }
    private static final int MSG_TICK_EXPLORER = 1;
    private static final int MSG_DISCONNECTED = 2;
    private static final int MSG_DETACHED = 3;
    private static final int MSG_PARTY_INFO = 4;
    private static final int MSG_CHAR_DEFINITION = 5;

    private void connectNewDiscoveries() {
        for (AccumulatingDiscoveryListener.FoundService serv : explorer.foundServices) {
            PartyAttempt match = null;
            for (PartyAttempt check : attempts) {
                if(check.source.equals(serv.info)) {
                    match = check;
                    break;
                }
            }
            if(null != match) return;
            match = new PartyAttempt(serv.info);
            attempts.add(match);
            match.connect();
        }
    }

    void join(Network.GroupInfo ginfo, Pumper.MessagePumpingThread worker) {
        PartyAttempt dummy = new PartyAttempt(null);
        dummy.pipe = worker.getSource();
        dummy.lastSend = PartyAttempt.SENT_DOORMAT_REQUEST;
        dummy.party = new PartyInfo(ginfo.version, ginfo.name);
        dummy.party.options = ginfo.options;
        dummy.doormat = ginfo.doormat;
        attempts.add(dummy);
        pumper.pump(worker);
    }

    // AccumulatingDiscoveryListener.OnTick vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv
    @Override
    public void tick(int old, int current) {
        int[] ugly = new int[] { old, current };
        handler.sendMessage(handler.obtainMessage(MSG_TICK_EXPLORER, ugly));
    }
    // AccumulatingDiscoveryListener.OnTick ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
}

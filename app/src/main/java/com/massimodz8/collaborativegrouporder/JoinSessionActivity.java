package com.massimodz8.collaborativegrouporder;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.transition.TransitionManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.networkio.Events;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.networkio.ProtoBufferEnum;
import com.massimodz8.collaborativegrouporder.networkio.PumpTarget;
import com.massimodz8.collaborativegrouporder.networkio.Pumper;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;
import com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

public class JoinSessionActivity extends AppCompatActivity implements AccumulatingDiscoveryListener.OnTick {
    public static class State {
        /// This must be initially populated by whoever spans this activity.
        final PersistentStorage.PartyClientData.Group party;

        AccumulatingDiscoveryListener explorer;
        ArrayList<PartyAttempt> attempts = new ArrayList<>();
        Pumper.MessagePumpingThread[] workers;

        public State(PersistentStorage.PartyClientData.Group party) {
            this.party = party;
        }
    }
    public static class Result {
        final Pumper.MessagePumpingThread worker;
        final PersistentStorage.PartyClientData.Group party;
        final PartyInfo info;
        final Network.PlayingCharacterList pcList;

        public Result(Pumper.MessagePumpingThread worker, PersistentStorage.PartyClientData.Group party, PartyInfo info, Network.PlayingCharacterList pcList) {
            this.worker = worker;
            this.party = party;
            this.info = info;
            this.pcList = pcList;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join_session);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "TODO: explicit connection", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
        final ActionBar sab = getSupportActionBar();
        if(null != sab) sab.setDisplayHomeAsUpEnabled(true);

        final CrossActivityShare share = (CrossActivityShare) getApplicationContext();
        myState = share.jsaState;
        share.jsaState = null;

        ((TextView)findViewById(R.id.jsa_partyName)).setText(myState.party.name);

        handler = new MyHandler(this);
        pumper = new Pumper(handler, MSG_DISCONNECTED, MSG_DETACHED)
                .add(ProtoBufferEnum.GROUP_INFO, new PumpTarget.Callbacks<Network.GroupInfo>() {
                    @Override
                    public Network.GroupInfo make() { return new Network.GroupInfo(); }

                    @Override
                    public boolean mangle(MessageChannel from, Network.GroupInfo msg) throws IOException {
                        handler.sendMessage(handler.obtainMessage(MSG_PARTY_INFO, new Events.GroupInfo(from, msg)));
                        return false;
                    }
                }).add(ProtoBufferEnum.CHARACTER_LIST, new PumpTarget.Callbacks<Network.PlayingCharacterList>() {
                    @Override
                    public Network.PlayingCharacterList make() { return new Network.PlayingCharacterList(); }

                    @Override
                    public boolean mangle(MessageChannel from, Network.PlayingCharacterList msg) throws IOException {
                        handler.sendMessage(handler.obtainMessage(MSG_CHAR_LIST, new Events.CharList(from, msg)));
                        return true;
                    }
                });

        if(null == share.pumpers) { // initialize network scan mode
            if (null != myState.explorer) myState.explorer.setCallback(this);
            else {
                final NsdManager nsd = (NsdManager) getSystemService(Context.NSD_SERVICE);
                myState.explorer = new AccumulatingDiscoveryListener();
                myState.explorer.beginDiscovery(MainMenuActivity.PARTY_GOING_ADVENTURING_SERVICE_TYPE, nsd, this);
            }
            if (null != myState.workers) {
                for (Pumper.MessagePumpingThread w : myState.workers) pumper.pump(w);
                myState.workers = null;
            }
        }
        else { // just shake to the specified peer and be done with it.
            PartyAttempt dummy = new PartyAttempt(null);
            dummy.pipe = share.pumpers[0].getSource();
            pumper.pump(share.pumpers[0]);
            myState.attempts.add(dummy);
            share.pumpers = null;
            dummy.refresh(); // kick in our pretty sequence of events.

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                ViewGroup contentRoot = (ViewGroup) findViewById(R.id.jsa_contentRoot);
                TransitionManager.beginDelayedTransition(contentRoot);
            }
            TextView state = (TextView) findViewById(R.id.jsa_state);
            state.setText(R.string.jsa_pullingFromExistingConnection);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        final CrossActivityShare share = (CrossActivityShare) getApplicationContext();
        if(null != myState.explorer) myState.explorer.unregisterCallback(); // perhaps we continued using an existing connection
        myState.workers = pumper.move();
        share.jsaState = myState;
        myState = null;
    }

    @Override
    protected void onDestroy() {
        if(null != myState) {
            pumper.shutdown();
            myState.explorer.stopDiscovery();
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    for (PartyAttempt el : myState.attempts) {
                        if(null == el.pipe) continue; // what if we're still handshaking this? I leak it and the runtime will go boom as I haven't closed the socket. I don't care.
                        try {
                            el.pipe.socket.close();
                        } catch (IOException e) {
                            // suppress this, they're just going away.
                        }
                    }
                    return null;
                }
            }.execute();

        }
        super.onDestroy();
    }

    // AccumulatingDiscoveryListener.OnTick vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv
    @Override
    public void tick(int old, int current) {
        int[] ugly = new int[] { old, current };
        handler.sendMessage(handler.obtainMessage(MSG_TICK_EXPLORER, ugly));
    }
    // AccumulatingDiscoveryListener.OnTick ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

    private static final int MSG_TICK_EXPLORER = 1;
    private static final int MSG_DISCONNECTED = 2;
    private static final int MSG_DETACHED = 3;
    private static final int MSG_PARTY_INFO = 4;
    private static final int MSG_CHAR_LIST = 5;

    State myState;
    Handler handler;
    Pumper pumper;

    private class PartyAttempt {
        final NsdServiceInfo source; /// set first
        volatile MessageChannel pipe;
        volatile PartyInfo party; /// We get this from successful handshake
        volatile Network.PlayingCharacterList charList; // we get this from successful authorize

        int lastSend = SENT_NOTHING;

        static final int SENT_NOTHING = 0;
        static final int SENT_DOORMAT_REQUEST = 1;
        static final int SENT_KEY = 2;
        boolean waitServerReply;
        volatile Exception error;

        byte[] doormat;

        private PartyAttempt(NsdServiceInfo source) {
            this.source = source;
        }

        /// Start an handshaking process which will produce this.party with the info we care.
        public void connect() {
            if(null != source) {
                // TODO: Cannot I just use the NsdServiceInfo ???
                //final NsdManager nsd = (NsdManager) getSystemService(Context.NSD_SERVICE);
                //nsd.resolveService(source, this);
                new AsyncTask<Void, Void, Void>() {
                    @Override
                    protected Void doInBackground(Void... params) {
                        Socket s;
                        try {
                            s = new Socket(source.getHost(), source.getPort());
                        } catch (IOException e) {
                            return null; // do nothing, I might have to signal that but I don't care for now, I'm late.
                        }
                        pipe = new MessageChannel(s);
                        return null;
                    }
                }.execute();
            }
        }

        // Pipe is ready to transfer something.
        public void refresh() {
            if(waitServerReply) return;
            switch(lastSend) {
                case SENT_NOTHING: {
                    final Network.Hello keyed = new Network.Hello();
                    keyed.verifyMe = true;
                    keyed.version = MainMenuActivity.NETWORK_VERSION;
                    new AsyncTask<Void, Void, Void>() {
                        @Override
                        protected Void doInBackground(Void... params) {
                            try {
                                pipe.writeSync(ProtoBufferEnum.HELLO, keyed);
                            } catch (IOException e) {
                                error = e;
                            }
                            return null;
                        }
                    }.execute();
                    lastSend = SENT_DOORMAT_REQUEST;
                    waitServerReply = true;
                } break;
                case SENT_DOORMAT_REQUEST: {
                    JoinVerificator helper;
                    try {
                        helper = new JoinVerificator();
                    } catch (NoSuchAlgorithmException e) {
                        error = e;
                        return;
                    }
                    final Network.Hello auth = new Network.Hello();
                    auth.authorize = helper.mangle(doormat, myState.party.key);
                    auth.version = MainMenuActivity.NETWORK_VERSION;
                    new AsyncTask<Void, Void, Void>() {
                        @Override
                        protected Void doInBackground(Void... params) {
                            try {
                                pipe.writeSync(ProtoBufferEnum.HELLO, auth);
                            } catch (IOException e) {
                                error = e;
                            }
                            return null;
                        }
                    }.execute();
                    lastSend = SENT_KEY;
                    waitServerReply = true;
                }
            }
            waitServerReply = true;
        }
    }

    private static class MyHandler extends Handler {
        final WeakReference<JoinSessionActivity> me;

        private MyHandler(JoinSessionActivity me) {
            this.me = new WeakReference<>(me);
        }

        @Override
        public void handleMessage(Message msg) {
            final JoinSessionActivity me = this.me.get();
            switch(msg.what) {
                case MSG_TICK_EXPLORER:
                    int[] ugly = (int[])msg.obj;
                    me.refreshStatus(ugly[0], ugly[1]);
                    synchronized(me.myState.explorer.foundServices) {
                        me.connectNewDiscoveries();
                        int index = 0;
                        for (PartyAttempt party : me.myState.attempts) {
                            index++;
                            if (null == party.pipe) continue; // not connected yet
                            if (null != party.party) {
                                if (null == party.party.name || party.party.name.isEmpty())
                                    continue; // to be ignored, for a reason or the other, likely handshake failed or no good key
                            }
                            if (null != party.error) {
                                me.error(index, party);
                                continue;
                            }
                            party.refresh();
                        }
                    }
                    break;
                case MSG_DISCONNECTED: { // uhm... I cannot get to process this for anything I care, so...
                    MessageChannel real = (MessageChannel)msg.obj;
                    for (PartyAttempt check : me.myState.attempts) {
                        if(real != check.pipe) continue;
                        check.party.name = ""; // not a valid group, restart handshake from scratch if needed.
                    }
                } break;
                case MSG_DETACHED: { // that's what happens when we get the party info. It's ok.
                    MessageChannel real = (MessageChannel)msg.obj;
                    for (PartyAttempt check : me.myState.attempts) {
                        if(real == check.pipe) {
                            final Pumper.MessagePumpingThread move = me.pumper.move(real);
                            me.myState.attempts.remove(check);
                            me.gotcha(move, check);
                            return;
                        }
                    }
                } break;
                case MSG_PARTY_INFO: {
                    Events.GroupInfo real = (Events.GroupInfo)msg.obj;
                    me.partyInfo(real.which, real.payload);
                } break;
                case MSG_CHAR_LIST: {
                    Events.CharList real = (Events.CharList)msg.obj;
                    me.charList(real.origin, real.payload);
                } break;
            }
        }
    }

    private void refreshStatus(int was, int now) {
        if(was == now) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            ViewGroup contentRoot = (ViewGroup) findViewById(R.id.jsa_contentRoot);
            TransitionManager.beginDelayedTransition(contentRoot);
        }
        TextView status = (TextView) findViewById(R.id.jsa_state);
        switch(now) {
            case AccumulatingDiscoveryListener.START_FAILED:
                status.setText(R.string.jsa_failedNetworkExploreStart);
                findViewById(R.id.jsa_progressBar).setEnabled(false);
                break;
            case AccumulatingDiscoveryListener.EXPLORING:
                status.setText(R.string.jsa_searching);
                findViewById(R.id.jsa_progressBar).setEnabled(false);
                break;
                /*
                unused, we stop only when going away
            case STOPPING = 4 ;
            case STOPPED = 5 ;
            case STOP_FAILED = 6 ;
            */
        }
    }

    private void gotcha(Pumper.MessagePumpingThread move, PartyAttempt check) {
        myState.attempts.remove(check);
        pumper.move(move.getSource());
        final CrossActivityShare share = (CrossActivityShare) getApplicationContext();
        share.jsaResult = new Result(move, myState.party, check.party, check.charList);
        setResult(RESULT_OK);
        finish();
    }

    private void error(int index, PartyAttempt party) {
        String gname = String.valueOf(index);
        if(null != party.party && null != party.party.name && !party.party.name.isEmpty()) gname = party.party.name;
        new AlertDialog.Builder(this).setMessage(String.format(getString(R.string.jsa_errorWhileJoining), gname, party.error.getLocalizedMessage())).show();
        party.error = null;
        final Socket goner = party.pipe.socket;
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                try {
                    goner.close();
                } catch (IOException e) {
                    // just suppress. It's gone anyway.
                }
                return null;
            }
        }.execute();
        party.pipe = null;
    }

    private void connectNewDiscoveries() {
        for (AccumulatingDiscoveryListener.FoundService serv : myState.explorer.foundServices) {
            PartyAttempt match = null;
            for (PartyAttempt check : myState.attempts) {
                if(check.source.equals(serv.info)) {
                    match = check;
                    break;
                }
            }
            if(null != match) return;
            match = new PartyAttempt(serv.info);
            myState.attempts.add(match);
            match.connect();
        }

    }

    private void charList(MessageChannel origin, Network.PlayingCharacterList payload) {
        PartyAttempt match = null;
        for (PartyAttempt test : myState.attempts) {
            if(test.pipe == origin) {
                match = test;
                break;
            }
        }
        if(null == match) return; // impossible but make static tools happy
        match.charList = payload; // wait for detach to populate my pumper, then go to PC selection.
    }

    private void partyInfo(MessageChannel which, Network.GroupInfo payload) {
        PartyAttempt match = null;
        for (PartyAttempt test : myState.attempts) {
            if(test.pipe == which) {
                match = test;
                break;
            }
        }
        if(null == match) return; // impossible but make static tools happy

        if(payload.forming || 0 == payload.doormat.length || !payload.name.equals(myState.party.name)) { // this server is uninteresting, uncollaborative or simply another thing.
            final Socket goner = match.pipe.socket;
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    try {
                        goner.close();
                    } catch (IOException e) {
                        // it's a goner anyway
                    }
                    return null;
                }
            }.execute();
            match.pipe = null;
            return;
        }

        match.party = new PartyInfo(payload.version, payload.name);
        match.party.options = payload.options;
        match.doormat = payload.doormat;
        match.waitServerReply = false;
    }
}

package com.massimodz8.collaborativegrouporder;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.transition.TransitionManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.master.PartyJoinOrderService;
import com.massimodz8.collaborativegrouporder.networkio.Events;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.networkio.ProtoBufferEnum;
import com.massimodz8.collaborativegrouporder.networkio.PumpTarget;
import com.massimodz8.collaborativegrouporder.networkio.Pumper;
import com.massimodz8.collaborativegrouporder.protocol.nano.StartData;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.Socket;
import java.util.ArrayList;

public class JoinSessionActivity extends AppCompatActivity implements AccumulatingDiscoveryListener.OnTick {
    public static class State {
        /// This must be initially populated by whoever spans this activity.
        final StartData.PartyClientData.Group party;

        AccumulatingDiscoveryListener explorer;
        ArrayList<PartyAttempt> attempts = new ArrayList<>();
        Pumper.MessagePumpingThread[] workers;

        public State(StartData.PartyClientData.Group party) {
            this.party = party;
        }
    }
    public static class Result {
        final Pumper.MessagePumpingThread worker;
        final StartData.PartyClientData.Group party;
        final Network.PlayingCharacterDefinition first; // if I get one of those it's because I have been identified.

        public Result(Pumper.MessagePumpingThread worker, StartData.PartyClientData.Group party, Network.PlayingCharacterDefinition first) {
            this.worker = worker;
            this.party = party;
            this.first = first;
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
                startActivityForResult(new Intent(JoinSessionActivity.this, ExplicitConnectionActivity.class), REQUEST_EXPLICIT_CONNECTION);
            }
        });
        final ActionBar sab = getSupportActionBar();
        if(null != sab) sab.setDisplayHomeAsUpEnabled(true);
    }

    @Override
    protected void onStart() {
        super.onStart();
        willBeRestored = false; // we don't really know, but we have being restored now so let's try again.

        final CrossActivityShare share = ((CrossActivityShare) getApplicationContext());
        if(null == myState) {
            myState = share.jsaState;
            share.jsaState = null;
        }

        ((TextView)findViewById(R.id.jsa_partyName)).setText(myState.party.name);

        if(null == handler) handler = new MyHandler(this);
        if(null == pumper) {
            pumper = new Pumper(handler, MSG_DISCONNECTED, MSG_DETACHED)
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
        }

        if(null == share.pumpers) { // initialize network scan mode
            if (null != myState.explorer) myState.explorer.setCallback(this);
            else {
                final NsdManager nsd = (NsdManager) getSystemService(Context.NSD_SERVICE);
                myState.explorer = new AccumulatingDiscoveryListener();
                myState.explorer.beginDiscovery(PartyJoinOrderService.PARTY_GOING_ADVENTURING_SERVICE_TYPE, nsd, this);
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
        willBeRestored = true;
    }

    @Override
    protected void onDestroy() {
        if(!willBeRestored) {
            pumper.shutdown();
            if(myState.explorer != null) myState.explorer.stopDiscovery(); // only created if not reusing an existing connection
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
    private static final int MSG_CHAR_DEFINITION = 5;

    State myState;
    Handler handler;
    Pumper pumper;

    private class PartyAttempt {
        final NsdServiceInfo source; /// set first
        volatile MessageChannel pipe;
        volatile PartyInfo party; /// We get this from successful handshake
        volatile Network.PlayingCharacterDefinition charDef; // we get this from successful authorize

        private int lastSend = SENT_NOTHING;

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
                } break;
                case SENT_DOORMAT_REQUEST: {
                    JoinVerificator helper = new JoinVerificator(myState.party, MaxUtils.hasher);
                    final Network.Hello auth = new Network.Hello();
                    auth.authorize = helper.mangle(doormat);
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
                case MSG_CHAR_DEFINITION: {
                    Events.CharacterDefinition real = (Events.CharacterDefinition)msg.obj;
                    me.charDef(real.origin, real.character);
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
        final CrossActivityShare share = (CrossActivityShare) getApplicationContext();
        share.jsaResult = new Result(move, myState.party, check.charDef);
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

    private void charDef(MessageChannel origin, Network.PlayingCharacterDefinition payload) {
        PartyAttempt match = null;
        for (PartyAttempt test : myState.attempts) {
            if(test.pipe == origin) {
                match = test;
                break;
            }
        }
        if(null == match) return; // impossible but make static tools happy
        match.charDef = payload; // wait for detach to populate my pumper, then go to PC selection.
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
        match.refresh();
    }

    private static final int REQUEST_EXPLICIT_CONNECTION = 1;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode != REQUEST_EXPLICIT_CONNECTION) return;
        if(resultCode != RESULT_OK) return;
        final Pumper.MessagePumpingThread worker = ExplicitConnectionActivity.masterDevice;
        final Network.GroupInfo ginfo = ExplicitConnectionActivity.probedParty;
        ExplicitConnectionActivity.masterDevice = null;
        ExplicitConnectionActivity.probedParty = null;
        if(ginfo.forming) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.jsa_connectedToForming)
                    .show();
            worker.interrupt();
            return;
        }
        if(!ginfo.name.equals(myState.party.name)) {
            new AlertDialog.Builder(this)
                    .setMessage(String.format(getString(R.string.jsa_connectedDifferentName), ginfo.name))
                    .setPositiveButton(R.string.jsa_connectedAttemptJoin, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            join(ginfo, worker);
                        }
                    })
                    .setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            worker.interrupt();
                        }
                    })
                    .show();
            return;
        }
        join(ginfo, worker);
    }

    private void join(Network.GroupInfo ginfo, Pumper.MessagePumpingThread worker) {
        PartyAttempt dummy = new PartyAttempt(null);
        dummy.pipe = worker.getSource();
        dummy.lastSend = PartyAttempt.SENT_DOORMAT_REQUEST;
        dummy.party = new PartyInfo(ginfo.version, ginfo.name);
        dummy.party.options = ginfo.options;
        dummy.doormat = ginfo.doormat;
        myState.attempts.add(dummy);
        pumper.pump(worker);
    }

    /// Set by onSaveInstanceState to understand when to clean resources/connections for real.
    private boolean willBeRestored;
}

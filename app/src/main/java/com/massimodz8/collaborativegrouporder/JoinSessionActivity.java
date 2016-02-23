package com.massimodz8.collaborativegrouporder;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;

import com.google.protobuf.nano.MessageNano;
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
import java.util.ArrayList;

public class JoinSessionActivity extends AppCompatActivity implements AccumulatingDiscoveryListener.OnStatusChanged {
    public static class State {
        final PersistentStorage.PartyClientData.Group party;

        AccumulatingDiscoveryListener explorer;
        ArrayList<PartyAttempt> attempts = new ArrayList<>();

        public State(PersistentStorage.PartyClientData.Group party) {
            this.party = party;
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

        if(null != myState.explorer) myState.explorer.setCallback(this);
        else {
            final NsdManager nsd = (NsdManager) getSystemService(Context.NSD_SERVICE);
            myState.explorer = new AccumulatingDiscoveryListener();
            myState.explorer.beginDiscovery(MainMenuActivity.PARTY_GOING_ADVENTURING_SERVICE_TYPE, nsd, this);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        final CrossActivityShare share = (CrossActivityShare) getApplicationContext();
        myState.explorer.unregisterCallback();
        share.jsaState = myState;
        myState = null;
    }

    @Override
    protected void onDestroy() {
        if(null != myState) {
            myState.explorer.stopDiscovery();
        }
        super.onDestroy();
    }

    // AccumulatingDiscoveryListener.OnStatusChanged vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv
    @Override
    public void newStatus(int old, int current) {
        handler.sendMessage(handler.obtainMessage(MSG_TICK_EXPLORER));
    }
    // AccumulatingDiscoveryListener.OnStatusChanged ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

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
        public Pumper.MessagePumpingThread detached;

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
                        MessageChannel channel = new MessageChannel(s);
                        Network.Hello keyed = new Network.Hello();
                        keyed.verifyMe = true;
                        keyed.version = MainMenuActivity.NETWORK_VERSION;
                        try {
                            channel.writeSync(ProtoBufferEnum.HELLO, keyed);
                        } catch (IOException e) {
                            return null; // uhm... not really... this is big. But I am late.
                        }
                        pipe = channel;
                        return null;
                    }
                }.execute();

            }
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
                case MSG_TICK_EXPLORER: {
                    // Add new discoveries to my list.
                    for (AccumulatingDiscoveryListener.FoundService serv : me.myState.explorer.foundServices) {
                        PartyAttempt match = null;
                        for (PartyAttempt check : me.myState.attempts) {
                            if(check.source.equals(serv.info)) {
                                match = check;
                                break;
                            }
                        }
                        if(null != match) return;
                        match = me.newPartyAttempt(serv.info);
                        me.myState.attempts.add(match);
                        match.connect();
                    }
                    // If something is not connected yet but it already has a message channel then
                    // we should be waiting for server reply.
                    for (PartyAttempt check : me.myState.attempts) {
                        if(null == check.pipe) continue;
                        if(null != check.party) continue; // already handshaked and already detached
                        if(me.pumper.yours(check.pipe)) continue;
                        me.pumper.pump(check.pipe);
                    }
                } break;
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
                        if(real != check.pipe) continue;
                        check.detached = me.pumper.move(real);
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

    private void charList(MessageChannel origin, Network.PlayingCharacterList payload) {
        new AlertDialog.Builder(this).setMessage("go char selection! " + "TODO").show();
    }

    private void partyInfo(MessageChannel which, Network.GroupInfo payload) {
        new AlertDialog.Builder(this).setMessage("party info " + payload.name + "TODO").show();
    }

    private void selectCharacters(PartyAttempt check) {
        new AlertDialog.Builder(this).setMessage("matched " + check.party.name + "TODO").show();
    }

    private PartyAttempt newPartyAttempt(NsdServiceInfo info) {
        return new PartyAttempt(info);
    }
}

package com.massimodz8.collaborativegrouporder;

import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;

import com.massimodz8.collaborativegrouporder.networkio.Events;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.networkio.ProtoBufferEnum;
import com.massimodz8.collaborativegrouporder.networkio.PumpTarget;
import com.massimodz8.collaborativegrouporder.networkio.Pumper;
import com.massimodz8.collaborativegrouporder.protocol.nano.StartData;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.Vector;

public class NewCharactersProposalActivity extends AppCompatActivity implements PlayingCharacterListAdapter.DataPuller {
    GroupState party;
    Vector<BuildingPlayingCharacter> characters = new Vector<>();
    RecyclerView.Adapter list = new PlayingCharacterListAdapter(this, PlayingCharacterListAdapter.MODE_CLIENT_INPUT);
    Handler handler = new MyHandler(this);
    Pumper netWorker = new Pumper(handler, MSG_SOCKET_DISCONNECTED, MSG_PUMPER_DETACHED)
            .add(ProtoBufferEnum.GROUP_FORMED, new PumpTarget.Callbacks<Network.GroupFormed>() {
                @Override
                public Network.GroupFormed make() { return new Network.GroupFormed(); }

                @Override
                public boolean mangle(MessageChannel from, Network.GroupFormed msg) throws IOException {
                    if(0 != msg.salt.length) handler.sendMessage(handler.obtainMessage(MSG_NEW_KEY, msg.salt));
                    else handler.sendMessage(handler.obtainMessage(MSG_PC_APPROVAL, new Events.CharacterAcceptStatus(from, msg.peerKey, msg.accepted)));
                    return false;
                }
            }).add(ProtoBufferEnum.GROUP_READY, new PumpTarget.Callbacks<Network.GroupReady>() {
                @Override
                public Network.GroupReady make() { return new Network.GroupReady(); }

                @Override
                public boolean mangle(MessageChannel from, Network.GroupReady msg) throws IOException {
                    handler.sendMessage(handler.obtainMessage(MSG_DONE, msg.goAdventuring));
                    return true;
                }
            });

    static final int MSG_SOCKET_DISCONNECTED = 1;
    static final int MSG_PUMPER_DETACHED = 2;
    static final int MSG_NEW_KEY = 3;
    static final int MSG_PC_APPROVAL = 4;
    static final int MSG_DONE = 5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_characters_proposal);

        CrossActivityShare state = (CrossActivityShare) getApplicationContext();
        party = state.candidates.elementAt(0);
        state.candidates = null;
        netWorker.pump(state.pumpers[0]);
        state.pumpers = null;

        characters.add(new BuildingPlayingCharacter());

        RecyclerView listWidget = (RecyclerView)findViewById(R.id.ncpa_list);
        listWidget.setLayoutManager(new LinearLayoutManager(this));
        listWidget.setAdapter(list);
        refreshGUI();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        CrossActivityShare state = (CrossActivityShare) getApplicationContext();
        state.candidates = new Vector<>();
        state.candidates.add(party);
        state.pumpers = new Pumper.MessagePumpingThread[] { netWorker.move(party.channel) };
        party = null;
    }

    @Override
    protected void onDestroy() {
        final Pumper.MessagePumpingThread[] pumps = netWorker.move();
        for (Pumper.MessagePumpingThread w : pumps) w.interrupt(); // there should be only one anyway
        if(null != party) {
            try {
                party.channel.socket.close();
            } catch (IOException e) {
                // nothing, we're going away.
            }
        }
        super.onDestroy();
    }

    void refreshGUI() {
        boolean status = true;
        for(BuildingPlayingCharacter c : characters) {
            if (BuildingPlayingCharacter.STATUS_BUILDING == c.status) {
                status = false;
                break;
            }
        }
        findViewById(R.id.ncpa_action).setEnabled(status);
        list.notifyDataSetChanged();
    }


    static class MyHandler extends Handler {
        public MyHandler(NewCharactersProposalActivity target) { this.target = new WeakReference<>(target); }

        final WeakReference<NewCharactersProposalActivity> target;

        @Override
        public void handleMessage(Message msg) {
            final NewCharactersProposalActivity target = this.target.get();
            switch(msg.what) {
                case MSG_SOCKET_DISCONNECTED: {
                    target.makeDialog().setMessage(R.string.ncpa_lostConnection)
                            .setPositiveButton(R.string.ncpa_lostConnection_backToMain, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) { target.finish(); }
                            }).setCancelable(false).show();
                } break;
                case MSG_PUMPER_DETACHED: break; // impossible, cannot happen
                case MSG_NEW_KEY: {
                    target.party.salt = (byte[]) msg.obj;
                } break;
                case MSG_PC_APPROVAL: target.confirmationStatus((Events.CharacterAcceptStatus)msg.obj); break;
                case MSG_DONE: target.saveData((Boolean)msg.obj); break;
            }
            target.refreshGUI();
        }
    }

    private void confirmationStatus(Events.CharacterAcceptStatus obj) {
        BuildingPlayingCharacter match = null;
        for(BuildingPlayingCharacter test : characters) {
            if(test.unique == obj.key) {
                match = test;
                break;
            }
        }
        if(null == match) return;
        match.status = obj.accepted? BuildingPlayingCharacter.STATUS_ACCEPTED : BuildingPlayingCharacter.STATUS_BUILDING;
        if(!obj.accepted) {
            new AlertDialog.Builder(this)
                    .setMessage(String.format(getString(R.string.ncpa_characterRejectedRetryMessage), match.name))
                    .show();
        }
        refreshGUI();
    }

    private void saveData(final boolean goAdventuring) {
        final NewCharactersProposalActivity self = this;
        new AsyncActivityLoadUpdateTask<StartData.PartyClientData>(PersistentDataUtils.DEFAULT_KEY_FILE_NAME, "keyList-", self, new AsyncActivityLoadUpdateTask.ActivityCallbacks(this) {
            @Override
            public void onCompletedSuccessfully() {
                String extra = ' ' + getString(R.string.ncpa_goingAdventuring);
                String msg = String.format(getString(R.string.ncpa_creationCompleted), goAdventuring ? extra : "");
                int label = goAdventuring? R.string.ncpa_goAdventuring : R.string.ncpa_newDataSaved_done;
                new AlertDialog.Builder(self)
                        .setTitle(R.string.dataLoadUpdate_newGroupSaved_title)
                        .setMessage(msg)
                        .setCancelable(false)
                        .setPositiveButton(label, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finishingTouches(goAdventuring);
                            }
                        })
                        .show();
            }
        }) {
            @Override
            protected void appendNewEntry(StartData.PartyClientData loaded) {
                StartData.PartyClientData.Group[] longer = new StartData.PartyClientData.Group[loaded.everything.length + 1];
                System.arraycopy(loaded.everything, 0, longer, 0, loaded.everything.length);
                StartData.PartyClientData.Group gen = new StartData.PartyClientData.Group();
                gen.key = party.salt;
                gen.name = party.group.name;
                gen.received = new com.google.protobuf.nano.Timestamp();
                gen.received.seconds = System.currentTimeMillis() / 1000;
                gen.sessionFile = PersistentDataUtils.makeInitialSession(new Date(), gen.name);
                longer[loaded.everything.length] = gen;
                loaded.everything =  longer;
                newKey = gen;
            }
            @Override
            protected void setVersion(StartData.PartyClientData result) { result.version = PersistentDataUtils.CLIENT_DATA_WRITE_VERSION; }
            @Override
            protected void upgrade(PersistentDataUtils helper, StartData.PartyClientData result) { helper.upgrade(result); }
            @Override
            protected ArrayList<String> validateLoadedDefinitions(PersistentDataUtils helper, StartData.PartyClientData result) { return helper.validateLoadedDefinitions(result); }
            @Override
            protected StartData.PartyClientData allocate() { return new StartData.PartyClientData(); }
        }.execute();
    }

    private StartData.PartyClientData.Group newKey;

    private void finishingTouches(boolean goAdventuring) {
        CrossActivityShare state = (CrossActivityShare) getApplicationContext();
        state.newKey = newKey;
        if(goAdventuring) {
            state.pumpers = netWorker.move();
            party = null; // party != null --> onDestroy will close socket!
        }
        setResult(RESULT_OK);
        finish();
    }

    public void addCharCandidate(View v) {
        characters.add(new BuildingPlayingCharacter());
        refreshGUI();
    }

    // PlayingCharacterListAdapter.DataPuller vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv
    @Override
    public int getVisibleCount() { return characters.size(); }

    @Override
    public void action(final BuildingPlayingCharacter who, int what) {
        // PlayingCharacterListAdapter.SEND: {
        final MessageChannel channel = party.channel;
        new AsyncTask<Void, Void, Exception>() {
            @Override
            protected Exception doInBackground(Void... params) {
                Network.PlayingCharacterDefinition wire = new Network.PlayingCharacterDefinition();
                wire.name = who.name;
                wire.initiativeBonus = who.initiativeBonus;
                wire.healthPoints = who.fullHealth;
                wire.experience = who.experience;
                wire.peerKey = who.unique;
                wire.level = who.level;
                try {
                    channel.writeSync(ProtoBufferEnum.PLAYING_CHARACTER_DEFINITION, wire);
                } catch (IOException e) {
                    return e;
                }
                return null;
            }

            @Override
            protected void onPostExecute(Exception e) {
                if(e != null) {
                    makeDialog().setMessage(getString(R.string.ncpa_failedSend) + e.getLocalizedMessage());
                    return;
                }
                who.status = BuildingPlayingCharacter.STATUS_SENT;
                refreshGUI();
            }
        }.execute();
    }

    @Override
    public AlertDialog.Builder makeDialog() { return new AlertDialog.Builder(this); }

    @Override
    public View inflate(int resource, ViewGroup root, boolean attachToRoot) { return getLayoutInflater().inflate(resource, root, attachToRoot); }

    @Override
    public BuildingPlayingCharacter get(int position) { return characters.elementAt(position); }

    @Override
    public long getStableId(int position) { return get(position).unique; }
    // PlayingCharacterListAdapter.DataPuller ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
}

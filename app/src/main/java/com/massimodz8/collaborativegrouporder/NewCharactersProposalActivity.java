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
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;
import com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
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
                    if(null != msg.salt) handler.sendMessage(handler.obtainMessage(MSG_NEW_KEY, msg.salt));
                    else handler.sendMessage(handler.obtainMessage(MSG_PC_APPROVAL, new Events.CharacterAcceptStatus(from, msg.peerKey, msg.accepted)));
                    return false;
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
        netWorker.shutdown();
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
                    Events.GroupKey real = (Events.GroupKey) msg.obj;
                    target.party.salt = real.key;
                } break;
                case MSG_PC_APPROVAL: target.confirmationStatus((Events.CharacterAcceptStatus)msg.obj);
                case MSG_DONE: target.saveData(); break;
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

    private void saveData() {
        final NewCharactersProposalActivity self = this;
        new AsyncActivityLoadUpdateTask<PersistentStorage.PartyClientData>(PersistentDataUtils.DEFAULT_KEY_FILE_NAME, "keyList-", self) {
            @Override
            protected void onCompletedSuccessfully() {
                new AlertDialog.Builder(self)
                        .setTitle(R.string.dataLoadUpdate_newGroupSaved_title)
                        .setMessage(R.string.dataLoadUpdate_newGroupSaved_msg)
                        .setCancelable(false)
                        .setPositiveButton(R.string.ncpa_newDataSaved_goAdventuring, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finishingTouches(party.group.name, party.salt, true);
                            }
                        })
                        .setNegativeButton(R.string.dataLoadUpdate_finished_newDataSaved_mainMenu, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finishingTouches(party.group.name, party.salt, false);
                            }
                        })
                        .show();
            }
            @Override
            protected void appendNewEntry(PersistentStorage.PartyClientData loaded) {
                PersistentStorage.PartyClientData.Group[] longer = new PersistentStorage.PartyClientData.Group[loaded.everything.length + 1];
                System.arraycopy(loaded.everything, 0, longer, 0, loaded.everything.length);
                PersistentStorage.PartyClientData.Group gen = new PersistentStorage.PartyClientData.Group();
                gen.key = party.salt;
                gen.name = party.group.name;
                longer[loaded.everything.length] = gen;
                loaded.everything =  longer;
            }
            @Override
            protected void setVersion(PersistentStorage.PartyClientData result) { result.version = PersistentDataUtils.CLIENT_DATA_WRITE_VERSION; }
            @Override
            protected void upgrade(PersistentDataUtils helper, PersistentStorage.PartyClientData result) { helper.upgrade(result); }
            @Override
            protected ArrayList<String> validateLoadedDefinitions(PersistentDataUtils helper, PersistentStorage.PartyClientData result) { return helper.validateLoadedDefinitions(result); }
            @Override
            protected PersistentStorage.PartyClientData allocate() { return new PersistentStorage.PartyClientData(); }
        }.execute();
    }

    private void finishingTouches(String name, byte[] groupKey, boolean goAdventuring) {
        CrossActivityShare state = (CrossActivityShare) getApplicationContext();
        state.goAdventuring = goAdventuring;
        state.newGroupName = name;
        state.newGroupKey = groupKey;
        finish();
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

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
import android.widget.Button;

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

public class NewCharactersApprovalActivity extends AppCompatActivity {

    private static final int MSG_SOCKET_LOST = 1;
    private static final int MSG_PUMPER_DETACHED = 2;
    private static final int MSG_CHARACTER_DEFINITION = 3;
    private static final int MSG_PEER_MESSAGE = 4;

    private static final int PEER_MESSAGE_INTERVAL = 5000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_characters_approval);

        final CrossActivityShare state = (CrossActivityShare) getApplicationContext();
        building.name = state.newGroupName;
        building.salt = state.newGroupKey;
        building.clients = state.clients;
        for(Pumper.MessagePumpingThread p : state.pumpers) netWorkers.pump(p);
        state.clients = null;
        state.newGroupName = null;
        state.newGroupKey = null;
        state.pumpers = null;

        RecyclerView groupList = (RecyclerView) findViewById(R.id.ncaa_list);
        groupList.setLayoutManager(new LinearLayoutManager(this));
        groupList.setAdapter(listAdapter);
        refreshGUI();
    }

    boolean saving;
    BuildingCharacters building = new BuildingCharacters();
    Handler guiHandler = new MyHandler(this);
    private RecyclerView.Adapter listAdapter = new PlayingCharacterListAdapter(new PlayingCharacterListAdapter.DataPuller() {
        @Override
        public int getVisibleCount() {
            int count = 0;
            for(DeviceStatus dev : building.clients) {
                if(dev.kicked || !dev.groupMember) continue;
                for(BuildingPlayingCharacter pc : dev.chars) {
                    if(BuildingPlayingCharacter.STATUS_REJECTED == pc.status) continue;
                    count++;
                }
            }
            return count;
        }

        @Override
        public void action(final BuildingPlayingCharacter who, int what) {
            final int previously = who.status;
            who.status = what == PlayingCharacterListAdapter.ACCEPT? BuildingPlayingCharacter.STATUS_ACCEPTED : BuildingPlayingCharacter.STATUS_REJECTED;

            DeviceStatus owner = null;
            for(DeviceStatus d : building.clients) { // if it's rejected it isn't visualized but keys are keys and objects are objects so...
                for(BuildingPlayingCharacter pc : d.chars)
                    if(pc == who) {
                        owner = d;
                        break;
                    }
            }
            if(owner == null) return; // Impossible (for the time being)
            final MessageChannel pipe = owner.source;
            new AsyncTask<Void, Void, Exception>() {
                @Override
                protected Exception doInBackground(Void... params) {
                    Network.GroupFormed msg = new Network.GroupFormed();
                    msg.peerKey = who.peerKey;
                    msg.accepted = BuildingPlayingCharacter.STATUS_ACCEPTED == who.status;
                    try {
                        pipe.writeSync(ProtoBufferEnum.GROUP_FORMED, msg);
                    } catch (IOException e) {
                        return e;
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(Exception e) {
                    if(e != null) {
                        makeDialog()
                                .setMessage(String.format(getString(R.string.ncaa_failedAcceptRejectReplySend), who.name, e.getLocalizedMessage()))
                                .show();
                        who.status = previously;
                    }
                    refreshGUI();
                }
            }.execute();
        }

        @Override
        public AlertDialog.Builder makeDialog() { return new AlertDialog.Builder(NewCharactersApprovalActivity.this); }

        @Override
        public String getString(int r) { return NewCharactersApprovalActivity.this.getString(r); }

        @Override
        public View inflate(int resource, ViewGroup root, boolean attachToRoot) { return getLayoutInflater().inflate(resource, root, attachToRoot); }

        @Override
        public BuildingPlayingCharacter get(int position) {
            int count = 0;
            for(DeviceStatus dev : building.clients) {
                if(dev.kicked || !dev.groupMember) continue;
                for(BuildingPlayingCharacter pc : dev.chars) {
                    if(BuildingPlayingCharacter.STATUS_REJECTED == pc.status) continue;
                    if(position == count) return pc;
                    count++;
                }
            }
            return null;
        }

        @Override
        public long getStableId(int position) {
            BuildingPlayingCharacter bpc = get(position);
            if(null == bpc) return RecyclerView.NO_ID;
            return bpc.unique;
        }
    }, PlayingCharacterListAdapter.MODE_SERVER_ACCEPTANCE);

    static class MyHandler extends Handler {
        final WeakReference<NewCharactersApprovalActivity> target;

        public MyHandler(NewCharactersApprovalActivity target) {
            this.target = new WeakReference<>(target);
        }

        @Override
        public void handleMessage(Message msg) {
            final NewCharactersApprovalActivity target = this.target.get();
            switch (msg.what) {
                case MSG_CHARACTER_DEFINITION: target.building.definePlayingCharacter((Events.CharacterDefinition) msg.obj); break;
                case MSG_PEER_MESSAGE: target.building.setMessage((Events.PeerMessage) msg.obj, PEER_MESSAGE_INTERVAL); break;
                case MSG_SOCKET_LOST: target.remove((MessageChannel) msg.obj); break;
                case MSG_PUMPER_DETACHED: break; // this never triggers for us
            }
            target.refreshGUI();
        }
    }

    Pumper netWorkers = new Pumper(guiHandler, MSG_SOCKET_LOST, MSG_PUMPER_DETACHED)
            .add(ProtoBufferEnum.PEER_MESSAGE, new PumpTarget.Callbacks<Network.PeerMessage>() {
                @Override
                public Network.PeerMessage make() {
                    return new Network.PeerMessage();
                }

                @Override
                public boolean mangle(MessageChannel from, Network.PeerMessage msg) throws IOException {
                    guiHandler.sendMessage(guiHandler.obtainMessage(MSG_PEER_MESSAGE, new Events.PeerMessage(from, msg)));
                    return false;
                }
            }).add(ProtoBufferEnum.PLAYING_CHARACTER_DEFINITION, new PumpTarget.Callbacks<Network.PlayingCharacterDefinition>() {
                @Override
                public Network.PlayingCharacterDefinition make() { return new Network.PlayingCharacterDefinition(); }

                @Override
                public boolean mangle(MessageChannel from, Network.PlayingCharacterDefinition msg) throws IOException {
                    guiHandler.sendMessage(guiHandler.obtainMessage(MSG_CHARACTER_DEFINITION, new Events.CharacterDefinition(from, msg)));
                    return false;
                }
            });

    void refreshGUI() {
        if(saving) {
            MaxUtils.setVisibility(this, View.GONE,
                    R.id.ncaa_list,
                    R.id.ncaa_action);
            findViewById(R.id.ncaa_saveFeedback).setVisibility(View.VISIBLE);
            return;
        }
        int accepted = 0;
        for(DeviceStatus dev : building.clients) {
            for(BuildingPlayingCharacter pc : dev.chars) {
                if(BuildingPlayingCharacter.STATUS_ACCEPTED == pc.status) accepted++;
            }
        }
        Button btn = (Button) findViewById(R.id.ncaa_action);
        btn.setText(0 == accepted ? R.string.ncaa_definingPCs : R.string.ncaa_makeGroup);
        btn.setEnabled(0 != accepted);
        listAdapter.notifyDataSetChanged();
    }


    private void remove(MessageChannel gone) {
        DeviceStatus owner = building.get(gone);
        if(null == owner) return; // impossible, but ok.
        if(null == owner.lastMessage) return; // impossible, but ok.
        String singular = getString(R.string.ncaa_lostConn_singularCharacter);
        String plural = String.format(getString(R.string.ncaa_lostConn_pluralCharacter), owner.chars.size());
        String chars = owner.chars.size() == 1? singular : plural;
        String string = String.format(getString(R.string.ncaa_lostConn_msg), owner.lastMessage, chars);
        // what if the device also defined characters? I don't care! They're not even shown for the time being!
        new AlertDialog.Builder(this).setMessage(string).show();
        netWorkers.forget(gone);
        //building.clients.remove(owner); we keep it around anyway
        try {
            gone.socket.close(); // will fail but we do that anyway
        } catch (IOException e) {
            // do nothing. It's gone anyway.
        }
        gone.socket = null;
    }

    public void action_callback(View btn) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.ncaa_save_title)
                .setMessage(R.string.ncaa_save_msg)
                .setPositiveButton(R.string.ncaa_done, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        saving = true;
                        refreshGUI();
                        startSaving();
                    }
                })
                .show();
    }

    private void startSaving() {
        new AsyncActivityLoadUpdateTask<PersistentStorage.PartyOwnerData>(PersistentDataUtils.DEFAULT_GROUP_DATA_FILE_NAME, "groupList-", this) {
            @Override
            protected void onCompletedSuccessfully() {
                new AlertDialog.Builder(NewCharactersApprovalActivity.this)
                        .setTitle(R.string.dataLoadUpdate_newGroupSaved_title)
                        .setMessage(R.string.dataLoadUpdate_newGroupSaved_msg)
                        .setCancelable(false)
                        .setPositiveButton(R.string.ncaa_newDataSaved_done, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finishingTouches(true);
                            }
                        })
                        .setNegativeButton(R.string.dataLoadUpdate_finished_newDataSaved_mainMenu, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finishingTouches(false);
                            }
                        })
                        .show();

            }

            @Override
            protected void appendNewEntry(PersistentStorage.PartyOwnerData loaded) {
                PersistentStorage.PartyOwnerData.Group[] longer = new PersistentStorage.PartyOwnerData.Group[loaded.everything.length + 1];
                System.arraycopy(loaded.everything, 0, longer, 0, loaded.everything.length);
                longer[loaded.everything.length] = makeGroup();
                loaded.everything = longer;
            }

            @Override
            protected void setVersion(PersistentStorage.PartyOwnerData result) {
                result.version = PersistentDataUtils.OWNER_DATA_VERSION;
            }

            @Override
            protected void upgrade(PersistentDataUtils helper, PersistentStorage.PartyOwnerData result) {
                helper.upgrade(result);
            }

            @Override
            protected ArrayList<String> validateLoadedDefinitions(PersistentDataUtils helper, PersistentStorage.PartyOwnerData result) {
                return helper.validateLoadedDefinitions(result);
            }

            @Override
            protected PersistentStorage.PartyOwnerData allocate() {
                return new PersistentStorage.PartyOwnerData();
            }
        }.execute();
    }

    private PersistentStorage.PartyOwnerData.Group makeGroup() {
        PersistentStorage.PartyOwnerData.Group ret = new PersistentStorage.PartyOwnerData.Group();
        ret.name = building.name;
        ret.salt = building.salt;
        int count = 0;
        for(DeviceStatus dev : building.clients) {
            if(dev.kicked || !dev.groupMember) continue;
            for(BuildingPlayingCharacter pc : dev.chars) {
                if(BuildingPlayingCharacter.STATUS_ACCEPTED == pc.status) count++;
            }
        }
        ret.usually = new PersistentStorage.PartyOwnerData.Group.Definition();
        ret.usually.party = new PersistentStorage.Actor[count];
        count = 0;
        for(DeviceStatus dev : building.clients) {
            if(dev.kicked || !dev.groupMember) continue;
            for(BuildingPlayingCharacter pc : dev.chars) {
                if(BuildingPlayingCharacter.STATUS_ACCEPTED == pc.status) {
                    PersistentStorage.Actor built = new PersistentStorage.Actor();
                    built.name = pc.name;
                    built.level = pc.level;
                    built.stats =  new PersistentStorage.ActorStatistics[] {
                            new PersistentStorage.ActorStatistics()
                    };
                    built.stats[0].initBonus = pc.initiativeBonus;
                    built.stats[0].experience = pc.experience;
                    built.stats[0].healthPoints = pc.fullHealth;
                    ret.usually.party[count++] = built;
                }
            }
        }
        return ret;
    }

    void finishingTouches(final boolean goAdventuring) {
        // This is mostly irrelevant. Mostly. Whole point is sending GroupReady message but that's just curtesy,
        // the clients will decide what to do anyway when the connections go down.
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                final int sillyDelayMS = 250; // make sure the messages go through. Yeah, I should display a progress w/e
                try {
                    Network.GroupReady byebye = new Network.GroupReady();
                    byebye.goAdventuring = goAdventuring;
                    for(DeviceStatus dev : building.clients) {
                        try {
                            dev.source.writeSync(ProtoBufferEnum.GROUP_READY, byebye);
                            dev.source.socket.getOutputStream().flush();
                        } catch (IOException e) {
                            // we try.
                        }
                    }
                    Thread.sleep(sillyDelayMS);
                    for(DeviceStatus dev : building.clients) {
                        try {
                            dev.source.socket.close();
                        } catch (IOException e ) {
                            // anyhow...
                        }
                    }
                } catch (InterruptedException e) {
                    // Sorry dudes, we're going down anyway.
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                final CrossActivityShare state = (CrossActivityShare) getApplicationContext();
                state.newGroupName = building.name;
                state.newGroupKey = building.salt;
                if(goAdventuring) state.pumpers = netWorkers.move();
                netWorkers.shutdown();
                setResult(RESULT_OK);
                finish();
            }
        }.execute();
    }
}

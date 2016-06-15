package com.massimodz8.collaborativegrouporder.client;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.StringRes;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;

import com.massimodz8.collaborativegrouporder.AsyncActivityLoadUpdateTask;
import com.massimodz8.collaborativegrouporder.BuildingPlayingCharacter;
import com.massimodz8.collaborativegrouporder.PCViewHolder;
import com.massimodz8.collaborativegrouporder.PersistentDataUtils;
import com.massimodz8.collaborativegrouporder.R;
import com.massimodz8.collaborativegrouporder.RunningServiceHandles;
import com.massimodz8.collaborativegrouporder.SendRequest;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.networkio.ProtoBufferEnum;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;
import com.massimodz8.collaborativegrouporder.protocol.nano.StartData;

import java.util.ArrayList;
import java.util.Date;

public class NewCharactersProposalActivity extends AppCompatActivity {
    final MyLister list = new MyLister();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_characters_proposal);

        RecyclerView listWidget = (RecyclerView)findViewById(R.id.ncpa_list);
        listWidget.setLayoutManager(new LinearLayoutManager(this));
        listWidget.setAdapter(list);
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        eventid = RunningServiceHandles.getInstance().newChars.onEvent.put(new Runnable() {
            @Override
            public void run() {
                refresh();
            }
        });
    }

    int eventid;

    @Override
    protected void onPause() {
        super.onPause();
        RunningServiceHandles.getInstance().newChars.onEvent.remove(eventid);
    }

    @Override
    public boolean onSupportNavigateUp() {
        if(RunningServiceHandles.getInstance().newChars.saving == null) return true;
        // We do nothing. I'll be out very soon automatically.
        return false;
    }

    @Override
    public void onBackPressed() {
        if(RunningServiceHandles.getInstance().newChars.saving == null) {
            super.onBackPressed();
            return;
        }
    }

    void refresh() {
        final CharacterProposals state = RunningServiceHandles.getInstance().newChars;
        boolean status = true;
        for(BuildingPlayingCharacter c : state.characters) {
            if (BuildingPlayingCharacter.STATUS_BUILDING == c.status) {
                status = false;
                break;
            }
        }
        findViewById(R.id.ncpa_action).setEnabled(status);
        list.notifyDataSetChanged();
        // And we're done with UI update. Now the real deal is evolving the finite state machine or perhaps not.
        final NewCharactersProposalActivity self = NewCharactersProposalActivity.this;
        if(state.rejected.size() > 0) { // easiest: just notify and retry
            if(dialog != null) return;
            dialog = new AlertDialog.Builder(this, R.style.AppDialogStyle)
                    .setMessage(String.format(getString(R.string.ncpa_characterRejectedRetryMessage), state.rejected.get(state.rejected.size() - 1).name))
                    .setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            self.dialog = null;
                            refresh();
                        }
                    })
                    .show();
            state.rejected.remove(state.rejected.size() - 1);
            refresh();
            return;
        }
        if(state.disconnected) {
            if(dialog != null) return; // already signaling
            dialog = new AlertDialog.Builder(this, R.style.AppDialogStyle)
                    .setMessage(R.string.ncpa_lostConnection)
                    .setPositiveButton(R.string.ncpa_lostConnection_backToMain, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) { finish(); }
                    })
                    .setCancelable(false)
                    .show();
        }
        if(!state.done || !state.detached || state.party.salt == null) return; // wait some more. A bit of a waste but that's milliseconds, not relevant for UI
        if(state.saving == null) {
            state.saving = new AsyncActivityLoadUpdateTask<StartData.PartyClientData>(PersistentDataUtils.MAIN_DATA_SUBDIR, PersistentDataUtils.DEFAULT_KEY_FILE_NAME, "keyList-", self, new AsyncActivityLoadUpdateTask.ActivityCallbacks(this) {
                @Override
                public void onCompletedSuccessfully() {
                    final boolean goAdventuring = state.master != null;
                    String extra = ' ' + getString(R.string.ncpa_goingAdventuring);
                    String msg = String.format(getString(R.string.ncpa_creationCompleted), goAdventuring ? extra : "");
                    int label = goAdventuring? R.string.ncpa_goAdventuring : R.string.ncpa_newDataSaved_done;
                    new AlertDialog.Builder(self, R.style.AppDialogStyle)
                            .setTitle(R.string.dataLoadUpdate_newGroupSaved_title)
                            .setMessage(msg)
                            .setCancelable(false)
                            .setPositiveButton(label, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    setResult(RESULT_OK);
                                    finish();
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
                    gen.key = state.party.salt;
                    gen.name = state.party.group.name;
                    gen.received = new com.google.protobuf.nano.Timestamp();
                    gen.received.seconds = System.currentTimeMillis() / 1000;
                    gen.sessionFile = PersistentDataUtils.makeInitialSession(new Date(), self.getFilesDir(), gen.name);
                    longer[loaded.everything.length] = gen;
                    loaded.everything =  longer;
                }
                @Override
                protected void setVersion(StartData.PartyClientData result) { result.version = PersistentDataUtils.CLIENT_DATA_WRITE_VERSION; }
                @Override
                protected void upgrade(PersistentDataUtils helper, StartData.PartyClientData result) { helper.upgrade(result); }
                @Override
                protected ArrayList<String> validateLoadedDefinitions(PersistentDataUtils helper, StartData.PartyClientData result) { return helper.validateLoadedDefinitions(result); }
                @Override
                protected StartData.PartyClientData allocate() { return new StartData.PartyClientData(); }
            };
            state.saving.execute();
        }
    }

    public void addCharCandidate_callback(View v) {
        RunningServiceHandles.getInstance().newChars.characters.add(new BuildingPlayingCharacter());
        refresh();
    }

    private class MyLister extends RecyclerView.Adapter<PCViewHolder> {
        public MyLister() { setHasStableIds(true); }

        @Override
        public long getItemId(int position) { return state.characters.get(position).unique; }

        @Override
        public PCViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new PCViewHolder(getLayoutInflater().inflate(R.layout.vh_playing_character_definition_input, parent, false)) {
                @Override
                protected String getString(@StringRes int resid) { return NewCharactersProposalActivity.this.getString(resid); }
                @Override
                public void action() {
                    // PlayingCharacterListAdapter.SEND: {
                    final MessageChannel channel = state.party.channel;
                    Network.PlayingCharacterDefinition wire = new Network.PlayingCharacterDefinition();
                    wire.name = who.name;
                    wire.initiativeBonus = who.initiativeBonus;
                    wire.healthPoints = who.fullHealth;
                    wire.experience = who.experience;
                    wire.peerKey = who.unique;
                    wire.level = who.level;
                    state.sender.out.add(new SendRequest(channel, ProtoBufferEnum.PLAYING_CHARACTER_DEFINITION, wire, null));
                    refresh();
                }
            };
        }

        @Override
        public void onBindViewHolder(PCViewHolder holder, int position) { holder.bind(state.characters.get(position)); }

        @Override
        public int getItemCount() { return state.characters.size(); }

        private final CharacterProposals state = RunningServiceHandles.getInstance().newChars;
    }
    private AlertDialog dialog;
}

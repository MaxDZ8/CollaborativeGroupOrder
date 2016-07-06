package com.massimodz8.collaborativegrouporder.client;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.StringRes;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;

import com.massimodz8.collaborativegrouporder.AsyncRenamingStore;
import com.massimodz8.collaborativegrouporder.BuildingPlayingCharacter;
import com.massimodz8.collaborativegrouporder.MaxUtils;
import com.massimodz8.collaborativegrouporder.PCViewHolder;
import com.massimodz8.collaborativegrouporder.PersistentDataUtils;
import com.massimodz8.collaborativegrouporder.R;
import com.massimodz8.collaborativegrouporder.RunningServiceHandles;
import com.massimodz8.collaborativegrouporder.SendRequest;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.networkio.ProtoBufferEnum;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;
import com.massimodz8.collaborativegrouporder.protocol.nano.StartData;
import com.massimodz8.collaborativegrouporder.protocol.nano.UserOf;

import java.util.ArrayList;
import java.util.Date;

public class NewCharactersProposalActivity extends AppCompatActivity {
    private @UserOf CharacterProposals state;

    final MyLister list = new MyLister();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_characters_proposal);

        RecyclerView listWidget = (RecyclerView)findViewById(R.id.ncpa_list);
        listWidget.setLayoutManager(new LinearLayoutManager(this));
        listWidget.setAdapter(list);
        state = RunningServiceHandles.getInstance().newChars;
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        eventid = state.onEvent.put(new Runnable() {
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
        state.onEvent.remove(eventid);
    }

    @Override
    public boolean onSupportNavigateUp() {
        if(state.saving == null) return true;
        // We do nothing. I'll be out very soon automatically.
        return false;
    }

    @Override
    public void onBackPressed() {
        if(state.saving == null) super.onBackPressed();
    }

    void refresh() {
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
            ArrayList<StartData.PartyClientData.Group> longer = new ArrayList<>(RunningServiceHandles.getInstance().state.data.groupKeys);
            final StartData.PartyClientData.Group gen = new StartData.PartyClientData.Group();
            gen.key = state.party.salt;
            gen.name = state.party.group.name;
            gen.received = new com.google.protobuf.nano.Timestamp();
            gen.received.seconds = System.currentTimeMillis() / 1000;
            //gen.sessionFile = PersistentDataUtils.makeInitialSession(new Date(), self.getFilesDir(), gen.name); // nope! Working thread!
            longer.add(gen);
            state.saving = new AsyncRenamingStore<StartData.PartyClientData>(getFilesDir(), PersistentDataUtils.MAIN_DATA_SUBDIR, PersistentDataUtils.DEFAULT_KEY_FILE_NAME, PersistentDataUtils.makePartyClientData(longer)) {
                @Override
                protected String getString(@StringRes int res) {
                    return NewCharactersProposalActivity.this.getString(res);
                }

                @Override
                protected Exception doInBackground(Void... params) {
                    gen.sessionFile = PersistentDataUtils.makeInitialSession(new Date(), getFilesDir(), gen.name); // nope! Working thread!
                    return super.doInBackground(params);
                }

                @Override
                protected void onPostExecute(Exception e) {
                    state.saving = null;
                    if(e != null) {
                        new AlertDialog.Builder(NewCharactersProposalActivity.this, R.style.AppDialogStyle)
                                .setTitle(R.string.generic_IOError)
                                .setMessage(e.getLocalizedMessage())
                                .show();
                        return;
                    }
                    final RunningServiceHandles handles = RunningServiceHandles.getInstance();
                    handles.state.data.groupKeys.add(gen);
                    handles.newChars.resParty = gen;
                    final boolean goAdventuring = handles.newChars.master != null;
                    String extra = ' ' + getString(R.string.ncpa_goingAdventuring);
                    String msg = String.format(getString(R.string.ncpa_creationCompleted), goAdventuring ? extra : "");
                    int label = goAdventuring ? R.string.ncpa_goAdventuring : R.string.ncpa_newDataSaved_done;
                    new AlertDialog.Builder(NewCharactersProposalActivity.this, R.style.AppDialogStyle)
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
            };
        }
    }

    public void addCharCandidate_callback(View v) {
        state.characters.add(new BuildingPlayingCharacter());
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
                public void action() {
                    // PlayingCharacterListAdapter.SEND: {
                    final MessageChannel channel = state.party.channel;
                    Network.PlayingCharacterDefinition wire = MaxUtils.makePlayingCharacterDefinition(who);
                    state.sender.out.add(new SendRequest(channel, ProtoBufferEnum.PLAYING_CHARACTER_DEFINITION, wire, null));
                    who.status = BuildingPlayingCharacter.STATUS_SENT;
                    refresh();
                }
            };
        }

        @Override
        public void onBindViewHolder(PCViewHolder holder, int position) {
            BuildingPlayingCharacter pc = state.characters.get(position);
            holder.bind(pc, pc.status != BuildingPlayingCharacter.STATUS_ACCEPTED && pc.status != BuildingPlayingCharacter.STATUS_SENT); }

        @Override
        public int getItemCount() { return state.characters.size(); }

    }
    private AlertDialog dialog;
}

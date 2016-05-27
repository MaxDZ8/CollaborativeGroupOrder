package com.massimodz8.collaborativegrouporder.master;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.AsyncActivityLoadUpdateTask;
import com.massimodz8.collaborativegrouporder.BuildingPlayingCharacter;
import com.massimodz8.collaborativegrouporder.HoriSwipeOnlyTouchCallback;
import com.massimodz8.collaborativegrouporder.MaxUtils;
import com.massimodz8.collaborativegrouporder.PCViewHolder;
import com.massimodz8.collaborativegrouporder.PreSeparatorDecorator;
import com.massimodz8.collaborativegrouporder.R;
import com.massimodz8.collaborativegrouporder.RunningServiceHandles;
import com.massimodz8.collaborativegrouporder.protocol.nano.Session;
import com.massimodz8.collaborativegrouporder.protocol.nano.StartData;

import java.util.ArrayList;

public class NewCharactersApprovalActivity extends AppCompatActivity {
    public static final String RESULT_ACTION = "com.massimodz8.collaborativegrouporder.master.NewCharactersApprovalActivity.RESULT";
    public static final String RESULT_EXTRA_GO_ADVENTURING = "com.massimodz8.collaborativegrouporder.master.NewCharactersApprovalActivity.RESULT_EXTRA_GO_ADVENTURING";
    private MenuItem storeGroup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_characters_approval);

        final PartyCreationService room = RunningServiceHandles.getInstance().create;

        RecyclerView groupList = (RecyclerView) findViewById(R.id.ncaa_list);
        groupList.setLayoutManager(new LinearLayoutManager(this));
        groupList.setAdapter(room.building.setNewCharsApprovalAdapter(new PartyDefinitionHelper.CharsApprovalHolderFactoryBinder<PcApprovalVh>() {
            @Override
            public PcApprovalVh createUnbound(ViewGroup parent, int viewType) {
                return new PcApprovalVh(getLayoutInflater().inflate(R.layout.vh_character_approval, parent, false));
            }

            @Override
            public void bind(@NonNull PcApprovalVh vh, @NonNull BuildingPlayingCharacter proposal) {
                vh.name.setText(proposal.name);
                vh.hp.setText(String.valueOf(proposal.fullHealth));
                vh.initBonus.setText(String.valueOf(proposal.initiativeBonus));
                vh.xp.setText(String.valueOf(proposal.experience));
                vh.level.setText(String.valueOf(proposal.level));
                vh.unique = proposal.unique;
                vh.accepted.setVisibility(proposal.status == BuildingPlayingCharacter.STATUS_ACCEPTED? View.VISIBLE : View.GONE);
            }
        }));
        groupList.addItemDecoration(new PreSeparatorDecorator(groupList, this) {
            @Override
            protected boolean isEligible(int position) {
                return position != 0;
            }
        });
        new HoriSwipeOnlyTouchCallback(groupList) {
            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                if(viewHolder instanceof PcApprovalVh) {
                    PcApprovalVh real = (PcApprovalVh) viewHolder;
                    room.reject(real.unique);
                    int count = 0;
                    for (PartyDefinitionHelper.DeviceStatus dev : room.building.clients) {
                        if(dev.kicked || !dev.groupMember) continue;
                        for (BuildingPlayingCharacter pc : dev.chars) {
                            if(pc.status == BuildingPlayingCharacter.STATUS_ACCEPTED) count++;
                        }
                    }
                    storeGroup.setEnabled(count != 0);
                }
            }

            @Override
            protected boolean disable() {
                return saving != null || sending != null;
            }

            @Override
            protected boolean canSwipe(RecyclerView rv, RecyclerView.ViewHolder vh) {
                if(vh instanceof PcApprovalVh) {
                    PcApprovalVh real = (PcApprovalVh) vh;
                    return !room.isApproved(real.unique);
                }
                return true;
            }
        };
        MaxUtils.beginDelayedTransition(this);
        TextView status = (TextView) findViewById(R.id.ncaa_status);
        status.setText(R.string.ncaa_definingPCs);
    }

    @Override
    protected void onDestroy() {
        final PartyCreationService room = RunningServiceHandles.getInstance().create;
        if(room != null) {
            if(room.building != null) room.building.setNewCharsApprovalAdapter(null);
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.ncaa_menu, menu);
        storeGroup = menu.findItem(R.id.ncaa_menu_save);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final PartyCreationService room = RunningServiceHandles.getInstance().create;
        switch(item.getItemId()) {
            case R.id.ncaa_menu_save: {
                new AlertDialog.Builder(this, R.style.AppDialogStyle)
                        .setTitle(R.string.ncaa_save_title)
                        .setMessage(R.string.ncaa_save_msg)
                        .setPositiveButton(R.string.ncaa_done, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                MaxUtils.beginDelayedTransition(NewCharactersApprovalActivity.this);
                                final TextView status = (TextView) findViewById(R.id.ncaa_status);
                                status.setText(R.string.ncaa_savingPleaseWait);
                                findViewById(R.id.ncaa_list).setEnabled(false);
                                storeGroup.setEnabled(false);
                                final AsyncActivityLoadUpdateTask<StartData.PartyOwnerData> temp = room.saveParty(NewCharactersApprovalActivity.this, new StoreDoneCallbacks());
                                temp.execute();
                                saving = temp;
                            }
                        }).show();
                break;
            }
            case R.id.ncaa_menu_genChar: {
                final AlertDialog dlg = new AlertDialog.Builder(this, R.style.AppDialogStyle)
                        .setTitle(R.string.ncaa_genCharTitle)
                        .setView(R.layout.vh_playing_character_definition_input)
                        .show();
                final BuildingPlayingCharacter pc = new BuildingPlayingCharacter();
                final PCViewHolder helper = new PCViewHolder(dlg.findViewById(R.id.vhRoot)) {
                    @Override
                    protected String getString(@StringRes int resid) {
                        return NewCharactersApprovalActivity.this.getString(resid);
                    }

                    @Override
                    protected void action() {
                        dlg.dismiss();
                        pc.status = BuildingPlayingCharacter.STATUS_ACCEPTED;
                        final PartyCreationService create = RunningServiceHandles.getInstance().create;
                        create.building.defineLocalCharacter(pc);
                    }
                };
                helper.bind(pc);
                break;
            }
        }
        return false;
    }

    @Override
    public boolean onSupportNavigateUp() {
        if(saving != null || sending != null) {
            MaxUtils.askExitConfirmation(this, R.string.ncaa_exitConfirmMsg);
            return true;
        }
        return super.onSupportNavigateUp();
    }

    @Override
    public void onBackPressed() {
        if(saving != null || sending != null) MaxUtils.askExitConfirmation(this, R.string.ncaa_exitConfirmMsg);
        else super.onBackPressed();
    }

    AsyncTask saving, sending;

    private class StoreDoneCallbacks extends AsyncActivityLoadUpdateTask.ActivityCallbacks {
        public StoreDoneCallbacks() {
            super(NewCharactersApprovalActivity.this);
        }

        @Override
        public void onFailedExistingLoad(@NonNull ArrayList<String> errors) {
            saving = null;
            super.onFailedExistingLoad(errors);
        }

        @Override
        public void onFailedSave(@NonNull Exception wrong) {
            saving = null;
            super.onFailedSave(wrong);
        }

        @Override
        public void onCompletedSuccessfully() {
            final PartyCreationService room = RunningServiceHandles.getInstance().create;
            room.defs.add(room.generatedParty);
            room.generatedStat = new Session.Suspended();
            saving = null;
            new AlertDialog.Builder(NewCharactersApprovalActivity.this, R.style.AppDialogStyle)
                    .setTitle(R.string.dataLoadUpdate_newGroupSaved_title)
                    .setMessage(R.string.dataLoadUpdate_newGroupSaved_msg)
                    .setCancelable(false)
                    .setPositiveButton(R.string.ncaa_newDataSaved_done, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            final AsyncTask<Void, Void, Void> temp = room.sendPartyCompleteMessages(true, new SendCompleteCallback(true));
                            sending = temp;
                            temp.execute();
                        }
                    })
                    .setNegativeButton(R.string.dataLoadUpdate_finished_newDataSaved_mainMenu, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            final AsyncTask<Void, Void, Void> temp = room.sendPartyCompleteMessages(false, new SendCompleteCallback(false));
                            sending = temp;
                            temp.execute();
                        }
                    })
                    .show();
        }
    }

    private class SendCompleteCallback implements Runnable {
        private final boolean goAdventuring;

        public SendCompleteCallback(boolean goAdventuring) {
            this.goAdventuring = goAdventuring;
        }

        @Override
        public void run() {
            sending = null;
            Intent back = new Intent(RESULT_ACTION);
            back.putExtra(RESULT_EXTRA_GO_ADVENTURING, goAdventuring);
            setResult(RESULT_OK, back);
            finish();
        }
    }

    private class PcApprovalVh extends RecyclerView.ViewHolder implements View.OnClickListener {
        final TextView name, level, xp, hp, initBonus;
        final View accepted;
        int unique;

        public PcApprovalVh(View v) {
            super(v);
            name = (TextView) v.findViewById(R.id.vhCA_name);
            level = (TextView) v.findViewById(R.id.vhCA_level);
            xp = (TextView) v.findViewById(R.id.vhCA_xp);
            hp = (TextView) v.findViewById(R.id.vhCA_hp);
            initBonus = (TextView) v.findViewById(R.id.vhCA_initBonus);
            accepted = v.findViewById(R.id.vhCA_accepted);
            v.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            final PartyCreationService room = RunningServiceHandles.getInstance().create;
            room.approve(unique);
            MaxUtils.beginDelayedTransition(NewCharactersApprovalActivity.this);
            accepted.setVisibility(View.VISIBLE);
            storeGroup.setEnabled(true);
        }
    }
}

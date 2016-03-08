package com.massimodz8.collaborativegrouporder;

import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.transition.TransitionManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.master.PartyCreationService;
import com.massimodz8.collaborativegrouporder.master.PartyDefinitionHelper;

import java.util.ArrayList;

public class NewCharactersApprovalActivity extends AppCompatActivity implements ServiceConnection {
    public static final String RESULT_ACTION = "com.massimodz8.collaborativegrouporder.NewCharactersApprovalActivity.RESULT";
    public static final String RESULT_EXTRA_GO_ADVENTURING = "com.massimodz8.collaborativegrouporder.NewCharactersApprovalActivity.RESULT_EXTRA_GO_ADVENTURING";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_characters_approval);

        Intent temp = new Intent(this, PartyCreationService.class);
        if(!bindService(temp, this, 0)) {
            failedServiceBind();
        }
    }

    @Override
    protected void onDestroy() {
        if(room != null) {
            room.setNewCharsApprovalAdapter(null);
            unbindService(this);
        }
        super.onDestroy();
    }

    AsyncTask saving, sending;

    public void action_callback(View btn) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.ncaa_save_title)
                .setMessage(R.string.ncaa_save_msg)
                .setPositiveButton(R.string.ncaa_done, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        beginDelayedTransition();
                        final TextView status = (TextView) findViewById(R.id.ncaa_status);
                        status.setText(R.string.ncaa_savingPleaseWait);
                        saving = room.saveParty(NewCharactersApprovalActivity.this, new StoreDoneCallbacks());
                    }
                })
                .show();
    }

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
            saving = null;
            new AlertDialog.Builder(NewCharactersApprovalActivity.this)
                    .setTitle(R.string.dataLoadUpdate_newGroupSaved_title)
                    .setMessage(R.string.dataLoadUpdate_newGroupSaved_msg)
                    .setCancelable(false)
                    .setPositiveButton(R.string.ncaa_newDataSaved_done, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            sending = room.sendPartyCompleteMessages(true, new SendCompleteCallback(true));
                        }
                    })
                    .setNegativeButton(R.string.dataLoadUpdate_finished_newDataSaved_mainMenu, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            sending = room.sendPartyCompleteMessages(false, new SendCompleteCallback(false));
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



    // ServiceConnection vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        final PartyCreationService.LocalBinder binder = (PartyCreationService.LocalBinder) service;
        room = binder.getConcreteService();

        RecyclerView groupList = (RecyclerView) findViewById(R.id.ncaa_list);
        groupList.setLayoutManager(new LinearLayoutManager(this));
        groupList.setAdapter(room.setNewCharsApprovalAdapter(new PartyDefinitionHelper.CharsApprovalHolderFactoryBinder<PcApprovalVh>() {
            @Override
            public PcApprovalVh createUnbound(ViewGroup parent, int viewType) {
                return new PcApprovalVh(getLayoutInflater().inflate(R.layout.card_joining_character_server_output, parent, false));
            }

            @Override
            public void bind(@NonNull PcApprovalVh vh, @NonNull BuildingPlayingCharacter proposal) {
                vh.name.setText(proposal.name);
                vh.hp.setText(String.valueOf(proposal.fullHealth));
                vh.initBonus.setText(String.valueOf(proposal.initiativeBonus));
                vh.xp.setText(String.valueOf(proposal.experience));
                vh.level.setText(String.valueOf(proposal.level));
                vh.unique = proposal.unique;
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
                }
            }

            @Override
            protected boolean disable() {
                return saving == null && sending == null;
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
        beginDelayedTransition();
        TextView status = (TextView) findViewById(R.id.ncaa_status);
        status.setText(R.string.ncaa_definingPCs);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        // TODO onServiceDisconnected
        room = null;
    }
    // ServiceConnection ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^


    private void beginDelayedTransition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            TransitionManager.beginDelayedTransition((ViewGroup) findViewById(R.id.activityRoot));
        }
    }

    private void failedServiceBind() {
        beginDelayedTransition();
        final TextView status = (TextView) findViewById(R.id.ncaa_status);
        status.setText(R.string.master_cannotBindPartyService);
        MaxUtils.setVisibility(this, View.GONE,
                R.id.npdsa_partyName,
                R.id.npdsa_activate);
    }

    private PartyCreationService room;

    private class PcApprovalVh extends RecyclerView.ViewHolder implements View.OnClickListener {
        final TextView name, level, xp, hp, initBonus;
        int unique;

        public PcApprovalVh(View v) {
            super(v);
            name = (TextView) v.findViewById(R.id.vhCA_name);
            level = (TextView) v.findViewById(R.id.vhCA_level);
            xp = (TextView) v.findViewById(R.id.vhCA_xp);
            hp = (TextView) v.findViewById(R.id.vhCA_hp);
            initBonus = (TextView) v.findViewById(R.id.vhCA_initBonus);
            v.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            room.approve(unique);

        }
    }
}

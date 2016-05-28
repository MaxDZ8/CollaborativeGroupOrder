package com.massimodz8.collaborativegrouporder.master;

import android.app.Notification;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.nsd.NsdManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.NotificationCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.ConnectionInfoDialog;
import com.massimodz8.collaborativegrouporder.HoriSwipeOnlyTouchCallback;
import com.massimodz8.collaborativegrouporder.MaxUtils;
import com.massimodz8.collaborativegrouporder.PreSeparatorDecorator;
import com.massimodz8.collaborativegrouporder.R;
import com.massimodz8.collaborativegrouporder.RunningServiceHandles;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.protocol.nano.StartData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

public class NewPartyDeviceSelectionActivity extends AppCompatActivity implements TextView.OnEditorActionListener {
    @Override
    protected void onDestroy() {
        final PartyCreationService room = RunningServiceHandles.getInstance().create;
        if(room != null) { // in many cases, parent_activity.onActivityResult has already cleaned up when this is destroyed so...
            room.onNewPublishStatus = null;
            room.setNewClientDevicesAdapter(null);
            room.onTalkingDeviceCountChanged = null;
            // Shutting down is not necessary at all... the parent activity shuts down service anyway.
        }
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_party_device_selection);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        final ActionBar sab = getSupportActionBar();
        if(null != sab) {
            sab.setDisplayHomeAsUpEnabled(true);
            sab.setTitle(R.string.npdsa_title);
        }

        final TextInputLayout namein = (TextInputLayout) findViewById(R.id.npdsa_partyName);
        EditText sure = namein.getEditText();
        if(null == sure) { // impossible, but the static analyzer does not know
            finish();
            return;
        }
        sure.setOnEditorActionListener(this);

        final PartyCreationService room = RunningServiceHandles.getInstance().create;
        if(room.mode == PartyCreationService.MODE_ADD_NEW_DEVICES_TO_EXISTING) {
            sure.setText(room.generatedParty.name);
            sure.setEnabled(false);
        }
        final FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final int devCount = room.getMemberCount();
                String use;
                switch(devCount) {
                    case 0: {
                        if(room.mode == PartyCreationService.MODE_ADD_NEW_DEVICES_TO_EXISTING) {
                            finish();
                            return; // seriously, what are you doing man?
                        }
                        use = getString(R.string.npdsa_emptyParty);
                    } break;
                    case 1: {
                        use = getString(R.string.npdsa_singleDeviceParty);
                    } break;
                    default:
                        use = getString(R.string.npdsa_closing_pluralDevices);
                        use = String.format(Locale.getDefault(), use, devCount);
                }
                if(devCount != 1) use = String.format(use, devCount);
                PartySealer sealer = new PartySealer();
                if(room.mode == PartyCreationService.MODE_ADD_NEW_DEVICES_TO_EXISTING) sealer.onClick(null, -1);
                else {
                    new AlertDialog.Builder(NewPartyDeviceSelectionActivity.this, R.style.AppDialogStyle)
                            .setTitle(R.string.npdsa_sealing_title)
                            .setMessage(use)
                            .setPositiveButton(R.string.npdsa_goDefinePC, sealer)
                            .show();

                }
            }
        });
        final TextView status = (TextView) findViewById(R.id.npdsa_status);
        room.onNewPublishStatus = new PublishAcceptService.NewPublishStatusCallback() {
            @Override
            public void onNewPublishStatus(int now) {
                switch(now) {
                    case PublishAcceptService.PUBLISHER_PUBLISHING: {
                        MaxUtils.beginDelayedTransition(NewPartyDeviceSelectionActivity.this);
                        status.setText(R.string.master_publishing);
                        findViewById(R.id.npdsa_partyName).setEnabled(true);
                    } break;
                    case PublishAcceptService.PUBLISHER_START_FAILED: {
                        MaxUtils.beginDelayedTransition(NewPartyDeviceSelectionActivity.this);
                        status.setText(R.string.master_failedPublish);
                        findViewById(R.id.npdsa_partyName).setEnabled(true);
                    } break;
                }

            }
        };

        MaxUtils.beginDelayedTransition(this);
        devList = (RecyclerView) findViewById(R.id.npdsa_deviceList);
        devList.setLayoutManager(new LinearLayoutManager(this));
        devList.setAdapter(room.setNewClientDevicesAdapter(new PartyCreationService.ClientDeviceHolderFactoryBinder<DeviceViewHolder>() {
            @Override
            public DeviceViewHolder createUnbound(ViewGroup parent, int viewType) {
                View layout = getLayoutInflater().inflate(R.layout.card_joining_device, parent, false);
                return new DeviceViewHolder(layout);
            }

            @Override
            public void bind(@NonNull DeviceViewHolder holder, @NonNull PartyDefinitionHelper.DeviceStatus dev) {
                holder.bind(dev);
            }
        }));
        devList.addItemDecoration(new PreSeparatorDecorator(devList, this) {
            @Override
            protected boolean isEligible(int position) {
                return position != 0;
            }
        });
        new HoriSwipeOnlyTouchCallback(devList) {
            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                final DeviceViewHolder real = (DeviceViewHolder) viewHolder;
                room.kick(real.key, true);
                hiddenManagement.setEnabled(true);
                String msg = String.format(getString(R.string.npdsa_deviceHidden), room.getDeviceNameByKey(real.key));
                Snackbar.make(findViewById(R.id.activityRoot), msg, Snackbar.LENGTH_LONG)
                        .setAction(R.string.generic_undo, new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                room.setVisible(real.key);
                                hiddenManagement.setEnabled(room.getDeviceCount(true) != 0);
                            }
                        })
                        .show();
            }

            @Override
            protected boolean disable() { return false; }

            @Override
            protected boolean canSwipe(RecyclerView rv, RecyclerView.ViewHolder vh) { return true; }
        };
        if(room.getBuildingPartyName() != null) { // restoring, so pull it in foreground! Otherwise defer until group name entered.
            elevateServicePriority();
        }
        else {
            status.setText(R.string.npdsa_waitingPartyName);
        }
        final int[] previously = new int[] { 0, 0 };
        room.onTalkingDeviceCountChanged = new PartyCreationService.OnTalkingDeviceCountListener() {
            @Override
            public void currentlyTalking(int count) {
                if(room.getMemberCount() != 0) return; // already transitioned to another state
                if(previously[0] == count) return; // nothing to do
                previously[0] = count;
                if(previously[1] == 0) {
                    final View root = findViewById(R.id.activityRoot);
                    Snackbar.make(root, R.string.npdsa_tapHint, Snackbar.LENGTH_SHORT)
                            .setCallback(new Snackbar.Callback() {
                                @Override
                                public void onDismissed(Snackbar snackbar, int event) {
                                    Snackbar.make(root, R.string.npdsa_longTapHint, Snackbar.LENGTH_SHORT).show();
                                }
                            })
                            .show();
                    previously[1] = 1;
                }
            }
        };
    }

    @Override
    protected void onStart() {
        super.onStart();
        final PartyCreationService room = RunningServiceHandles.getInstance().create;
        room.accept();
        if(room.getBuildingPartyName() != null) {
            NsdManager nsd = (NsdManager) getSystemService(Context.NSD_SERVICE);
            if (nsd == null) {
                new AlertDialog.Builder(this, R.style.AppDialogStyle)
                        .setMessage(R.string.both_noDiscoveryManager)
                        .show();
                return;
            }
            if(room.mode == PartyCreationService.MODE_ADD_NEW_DEVICES_TO_EXISTING) publishGroup();
            else room.beginPublishing(nsd, room.getBuildingPartyName(), PartyCreationService.PARTY_FORMING_SERVICE_TYPE);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.new_party_device_selection_activity, menu);
        hiddenManagement = menu.findItem(R.id.npdsa_menu_hiddenDevices);
        hiddenManagement.setEnabled(false);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final PartyCreationService room = RunningServiceHandles.getInstance().create;
        switch(item.getItemId()) {
            case R.id.npdsa_menu_explicitConnInfo: {
                int serverPort = room == null? 0 : room.getServerPort();
                new ConnectionInfoDialog(this, serverPort).show();
                break;
            }
            case R.id.npdsa_menu_hiddenDevices: {
                final ArrayList<PartyDefinitionHelper.DeviceStatus> silent = room.getDevices(true);
                new HiddenDeviceManagementDialog(this, silent) {
                    @Override
                    protected void requestDisconnect(PartyDefinitionHelper.DeviceStatus data) {
                        room.kick(data.source, false);
                    }

                    @Override
                    protected void requestRestore(PartyDefinitionHelper.DeviceStatus data) {
                        room.setVisible(data.source);
                    }
                };
                break;
            }
        }
        return false;
    }


    @Override
    public boolean onSupportNavigateUp() {
        final PartyCreationService room = RunningServiceHandles.getInstance().create;
        if(room.getBuildingPartyName() != null && room.getMemberCount() != 0) {
            MaxUtils.askExitConfirmation(this);
            return true;
        }
        return super.onSupportNavigateUp();
    }

    @Override
    public void onBackPressed() {
        final PartyCreationService room = RunningServiceHandles.getInstance().create;
        if(room.getBuildingPartyName() != null && room.getMemberCount() != 0) MaxUtils.askExitConfirmation(this);
        else super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(resultCode != RESULT_OK) return;
        setResult(RESULT_OK, data);
        finish();
    }

    private class DeviceViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener, ActionMode.Callback {
        TextView msg, name;
        View memberIcon, memberMsg;
        MessageChannel key;
        final Typeface original;

        public DeviceViewHolder(View itemView) {
            super(itemView);
            msg = (TextView) itemView.findViewById(R.id.card_joiningDevice_msg);
            itemView.setOnClickListener(this);
            original = msg.getTypeface();
            memberIcon = itemView.findViewById(R.id.card_joiningDevice_groupMemberIcon);
            memberMsg = itemView.findViewById(R.id.card_joiningDevice_groupMember);
            name = (TextView) itemView.findViewById(R.id.card_joiningDevice_name);
            itemView.setOnLongClickListener(this);
        }

        @Override
        public void onClick(View v) {
            RunningServiceHandles.getInstance().create.toggleMembership(key);
        }

        public void bind(PartyDefinitionHelper.DeviceStatus dev) {
            key = dev.source;
            msg.setText(dev.lastMessage);
            final int weight = dev.groupMember ? Typeface.BOLD : Typeface.NORMAL;
            MaxUtils.setVisibility(dev.groupMember ? View.VISIBLE : View.GONE, memberIcon, memberMsg);
            msg.setTypeface(original, weight);
            name.setText(dev.name);
        }

        @Override
        public boolean onLongClick(View v) {
            if(actionMode != null) actionMode.finish();
            startActionMode(this);
            return true;
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            actionMode = mode;
            mode.getMenuInflater().inflate(R.menu.npdsa_action_mode, menu);
            mode.setTitle(name.getText());
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) { return false; }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            final PartyCreationService room = RunningServiceHandles.getInstance().create;
            switch(item.getItemId()) {
                case R.id.npdsaAM_setName:
                    new SetNameDialog(NewPartyDeviceSelectionActivity.this, mode, room.building.get(key), new Runnable() {
                        @Override
                        public void run() {
                            devList.getAdapter().notifyDataSetChanged();
                        }
                    });
                    return true;
                case R.id.npdsaAM_setCharBudget:
                    new SetCharBudgetDialog(NewPartyDeviceSelectionActivity.this, mode, room.building.get(key)) {
                        @Override
                        protected void requestBudgetChange(PartyDefinitionHelper.DeviceStatus devStat, int newValue) {
                            room.building.setCharBudget(devStat, newValue);
                        }
                    };
                    return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            actionMode = null;
        }
    }

    private class PartySealer implements AlertDialog.OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            final Intent intent = new Intent(NewPartyDeviceSelectionActivity.this, NewCharactersApprovalActivity.class);
            final PartyCreationService room = RunningServiceHandles.getInstance().create;
            room.closeGroup(new PartyCreationService.OnKeysSentListener() {
                @Override
                public void onKeysSent(int bad) {
                    if (0 == bad) {
                        startActivityForResult(intent, REQUEST_APPROVE_PLAYING_CHARACTERS);
                        return;
                    }
                    new AlertDialog.Builder(NewPartyDeviceSelectionActivity.this, R.style.AppDialogStyle)
                            .setMessage(R.string.npdsa_failedKeySendDlgMsg)
                            .setPositiveButton(R.string.npdsa_carryOnDlgAction, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    startActivityForResult(intent, REQUEST_APPROVE_PLAYING_CHARACTERS);
                                }
                            }).setNegativeButton(R.string.master_giveUpAndGoBack, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    }).show();
                }
            });
        }
    }

    private void publishGroup() {
        final PartyCreationService room = RunningServiceHandles.getInstance().create;
        if(room.getPublishStatus() != PartyCreationService.PUBLISHER_IDLE) return; // unlikely, as I disable trigger
        final TextInputLayout til = (TextInputLayout) findViewById(R.id.npdsa_partyName);
        final EditText view = til.getEditText();
        if(view == null) return; // impossible
        final String groupName = view.getText().toString().trim();
        ArrayList<StartData.PartyOwnerData.Group> collisions = room.beginBuilding(groupName, getString(R.string.npdsa_unknownDeviceName));
        if (groupName.isEmpty() || null != collisions) {
            int msg = groupName.isEmpty() ? R.string.npdsa_badParty_msg_emptyName : R.string.npdsa_badParty_msg_alreadyThere;
            new AlertDialog.Builder(this, R.style.AppDialogStyle)
                    .setTitle(R.string.npdsa_badParty_title)
                    .setCancelable(false)
                    .setMessage(msg)
                    .setPositiveButton(R.string.npdsa_badParty_retry, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            view.requestFocus();
                        }
                    })
                    .show();
            return;
        }
        NsdManager nsd = (NsdManager) getSystemService(Context.NSD_SERVICE);
        if (nsd == null) {
            new AlertDialog.Builder(this, R.style.AppDialogStyle)
                    .setMessage(R.string.both_noDiscoveryManager)
                    .show();
            return;
        }
        try {
            room.startListening();
        } catch (IOException e) {
            new AlertDialog.Builder(this, R.style.AppDialogStyle)
                    .setMessage(R.string.master_badServerSocket)
                    .setPositiveButton(R.string.master_giveUpAndGoBack, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .show();
            return;
        }
        room.beginPublishing(nsd, groupName, PartyCreationService.PARTY_FORMING_SERVICE_TYPE);
        MaxUtils.beginDelayedTransition(this);
        view.setEnabled(false);
        elevateServicePriority();
        MaxUtils.setVisibility(this, View.VISIBLE,
                R.id.npdsa_deviceList,
                R.id.npdsa_publishing,
                R.id.fab);
        findViewById(R.id.npdsa_deviceList).setVisibility(View.VISIBLE);
        Snackbar.make(findViewById(R.id.activityRoot), R.string.npdsa_waitingToTalk, Snackbar.LENGTH_SHORT).show();
        view.setEnabled(false);
    }

    // TextView.OnEditorActionListener vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv
    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        publishGroup();
        return true;
    }
    // TextView.OnEditorActionListener ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    private void elevateServicePriority() {
        final PartyCreationService room = RunningServiceHandles.getInstance().create;
        final int resid = room.mode == PartyCreationService.MODE_ADD_NEW_DEVICES_TO_EXISTING?
                R.string.npdsa_notifyContentAdd :
                R.string.npdsa_notifyContentNew;
        final android.support.v4.app.NotificationCompat.Builder help = new NotificationCompat.Builder(this)
                .setOngoing(true)
                .setContentTitle(room.getBuildingPartyName())
                .setContentText(getText(resid))
                .setSmallIcon(R.drawable.ic_notify_icon)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            help.setCategory(Notification.CATEGORY_SERVICE);
        }
        room.startForeground(NOTIFICATION_ID, help.build());
    }

    private static final int NOTIFICATION_ID = 1;
    private RecyclerView devList;
    private ActionMode actionMode;
    private MenuItem hiddenManagement;
    private static final int REQUEST_APPROVE_PLAYING_CHARACTERS = 1;
}

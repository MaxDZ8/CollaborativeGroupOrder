package com.massimodz8.collaborativegrouporder.master;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.nsd.NsdManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.ConnectionInfoDialog;
import com.massimodz8.collaborativegrouporder.HoriSwipeOnlyTouchCallback;
import com.massimodz8.collaborativegrouporder.InternalStateService;
import com.massimodz8.collaborativegrouporder.MaxUtils;
import com.massimodz8.collaborativegrouporder.PreSeparatorDecorator;
import com.massimodz8.collaborativegrouporder.ProtobufSupport;
import com.massimodz8.collaborativegrouporder.R;
import com.massimodz8.collaborativegrouporder.RunningServiceHandles;
import com.massimodz8.collaborativegrouporder.SuccessiveSnackbars;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.protocol.nano.LevelAdvancement;
import com.massimodz8.collaborativegrouporder.protocol.nano.StartData;
import com.massimodz8.collaborativegrouporder.protocol.nano.UserOf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

public class NewPartyDeviceSelectionActivity extends AppCompatActivity implements TextView.OnEditorActionListener {
    private @UserOf PartyCreator room;

    @Override
    protected void onPause() {
        super.onPause();
        if(room != null) { // in many cases, parent_activity.onActivityResult has already cleaned up when this is destroyed so...
            room.onNewPublishStatus = null;
            room.setNewClientDevicesAdapter(null);
            room.onTalkingDeviceCountChanged = null;
            // Shutting down is not necessary at all... the parent activity shuts down service anyway.
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_party_device_selection);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        final ActionBar sab = getSupportActionBar();
        if (null != sab) {
            sab.setDisplayHomeAsUpEnabled(true);
            sab.setTitle(R.string.npdsa_title);
        }
        room = RunningServiceHandles.getInstance().create;
        final FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final int devCount = room.getMemberCount();
                String use;
                switch(devCount) {
                    case 0: {
                        if(room.mode == PartyCreator.MODE_ADD_NEW_DEVICES_TO_EXISTING) {
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
                if(room.mode == PartyCreator.MODE_ADD_NEW_DEVICES_TO_EXISTING) sealer.onClick(null, -1);
                else {
                    new AlertDialog.Builder(NewPartyDeviceSelectionActivity.this, R.style.AppDialogStyle)
                            .setTitle(R.string.npdsa_sealing_title)
                            .setMessage(use)
                            .setPositiveButton(R.string.npdsa_goDefinePC, sealer)
                            .setIcon(R.drawable.ic_info_white_24dp)
                            .show();

                }
            }
        });
        devList = (RecyclerView) findViewById(R.id.npdsa_deviceList);
        devList.setLayoutManager(new LinearLayoutManager(this));
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
    }

    @Override
    protected void onResume() {
        super.onResume();

        final TextInputLayout namein = (TextInputLayout) findViewById(R.id.npdsa_partyName);
        EditText sure = namein.getEditText();
        if(null == sure) { // impossible, but the static analyzer does not know
            finish();
            return;
        }
        sure.setOnEditorActionListener(this);

        if(room.mode == PartyCreator.MODE_ADD_NEW_DEVICES_TO_EXISTING) {
            sure.setText(room.generatedParty.name);
            sure.setEnabled(false);
            sure.clearFocus();
            advancementButtonLabel();
        }
        final TextView status = (TextView) findViewById(R.id.npdsa_status);
        room.onNewPublishStatus = new PublishAcceptHelper.NewPublishStatusCallback() {
            @Override
            public void onNewPublishStatus(int now) {
                switch(now) {
                    case PublishAcceptHelper.PUBLISHER_PUBLISHING: {
                        MaxUtils.beginDelayedTransition(NewPartyDeviceSelectionActivity.this);
                        WifiManager wifi = (WifiManager) getSystemService(WIFI_SERVICE);  // guaranteed non-null here, but let's be more flexible for future wi-fi direct/bluetooth stuff
                        WifiInfo cinfo = null == wifi? null : wifi.getConnectionInfo();
                        String netName = null == cinfo? getString(R.string.npdsa_noCurrentNetwork_placeholderName) : cinfo.getSSID();
                        status.setText(String.format(getString(R.string.master_publishing), netName));
                        findViewById(R.id.npdsa_partyName).setEnabled(true);
                    } break;
                    case PublishAcceptHelper.PUBLISHER_START_FAILED: {
                        MaxUtils.beginDelayedTransition(NewPartyDeviceSelectionActivity.this);
                        status.setText(R.string.master_failedPublish);
                        findViewById(R.id.npdsa_partyName).setEnabled(true);
                    } break;
                }

            }
        };

        MaxUtils.beginDelayedTransition(this);
        devList.setAdapter(room.setNewClientDevicesAdapter(new PartyCreator.ClientDeviceHolderFactoryBinder<DeviceViewHolder>() {
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
        if(room.getBuildingPartyName() == null) { // restoring, so pull it in foreground! Otherwise defer until group name entered.
            status.setText(R.string.npdsa_waitingPartyName);
        }
        final int[] previously = new int[] { 0, 0 };
        room.onTalkingDeviceCountChanged = new PartyCreator.OnTalkingDeviceCountListener() {
            @Override
            public void currentlyTalking(int count) {
                if(room.getMemberCount() != 0) return; // already transitioned to another state
                if(previously[0] == count) return; // nothing to do
                previously[0] = count;
                if(previously[1] == 0) {
                    final View root = findViewById(R.id.activityRoot);
                    new SuccessiveSnackbars(root, Snackbar.LENGTH_SHORT, NewPartyDeviceSelectionActivity.this,
                            R.string.npdsa_tapHint,
                            R.string.npdsa_longTapHint)
                            .show();
                    previously[1] = 1;
                }
            }
        };
        if(room.newPartyName != null && room.advancementPace != LevelAdvancement.LA_UNSPECIFIED) publishGroup();
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
        if(room.getBuildingPartyName() != null && room.getMemberCount() != 0) {
            MaxUtils.askExitConfirmation(this);
            return true;
        }
        return super.onSupportNavigateUp();
    }

    @Override
    public void onBackPressed() {
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
            room.toggleMembership(key);
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
            room.closeGroup(new PartyCreator.OnKeysSentListener() {
                @Override
                public void onKeysSent(int bad) {
                    if (0 == bad) {
                        startActivityForResult(intent, REQUEST_APPROVE_PLAYING_CHARACTERS);
                        return;
                    }
                    new AlertDialog.Builder(NewPartyDeviceSelectionActivity.this, R.style.AppDialogStyle)
                            .setIcon(R.drawable.ic_warning_white_24px)
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
        if(room.building != null || room.getPublishStatus() != PartyCreator.PUBLISHER_IDLE) return; // unlikely, as I disable trigger
        final TextInputLayout til = (TextInputLayout) findViewById(R.id.npdsa_partyName);
        final EditText view = til.getEditText();
        if(view == null) return; // impossible
        room.newPartyName = view.getText().toString().trim();
        ArrayList<StartData.PartyOwnerData.Group> collisions = room.beginBuilding(getString(R.string.npdsa_unknownDeviceName));
        if (room.newPartyName.isEmpty() || null != collisions) {
            int msg = room.newPartyName.isEmpty() ? R.string.npdsa_badParty_msg_emptyName : R.string.npdsa_badParty_msg_alreadyThere;
            new AlertDialog.Builder(this, R.style.AppDialogStyle)
                    .setIcon(R.drawable.ic_error_white_24dp)
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
        WifiManager wifi = (WifiManager) getSystemService(WIFI_SERVICE);
        if(null == wifi || null == wifi.getConnectionInfo()) {
            new AlertDialog.Builder(this, R.style.AppDialogStyle)
                    .setMessage(R.string.npdsa_noWifiManager)
                    .setIcon(R.drawable.ic_warning_white_24px)
                    .show();
        }
        NsdManager nsd = (NsdManager) getSystemService(Context.NSD_SERVICE);
        if (nsd == null) {
            new AlertDialog.Builder(this, R.style.AppDialogStyle)
                    .setMessage(R.string.both_noDiscoveryManager)
                    .setIcon(R.drawable.ic_error_white_24dp)
                    .show();
            return;
        }
        try {
            room.startListening(null);
        } catch (IOException e) {
            new AlertDialog.Builder(this, R.style.AppDialogStyle)
                    .setIcon(R.drawable.ic_error_white_24dp)
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
        room.beginPublishing(nsd, room.newPartyName, PartyCreator.PARTY_FORMING_SERVICE_TYPE);
        MaxUtils.beginDelayedTransition(this);
        view.setEnabled(false);

        InternalStateService state = RunningServiceHandles.getInstance().state;
        final int resid = room.mode == PartyCreator.MODE_ADD_NEW_DEVICES_TO_EXISTING?
                R.string.npdsa_notifyContentAdd :
                R.string.npdsa_notifyContentNew;
        Notification notification = state.buildNotification(room.newPartyName, getString(resid));
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(InternalStateService.INTERNAL_STATE_NOTIFICATION_ID, notification);
        state.notification = notification;

        MaxUtils.setVisibility(this, View.VISIBLE,
                R.id.npdsa_deviceList,
                R.id.npdsa_publishing,
                R.id.fab);
        findViewById(R.id.npdsa_deviceList).setVisibility(View.VISIBLE);
        Snackbar.make(findViewById(R.id.activityRoot), R.string.npdsa_waitingToTalk, Snackbar.LENGTH_SHORT).show();
        view.setEnabled(false);
        findViewById(R.id.npdsa_levelAdvBtn).setEnabled(false);
    }


    public void setLevelAdv_callback(View unused) {
        final int[] la = {
                LevelAdvancement.LA_PF_FAST,
                LevelAdvancement.LA_PF_MEDIUM,
                LevelAdvancement.LA_PF_SLOW
        };
        final String[] name = {
                getString(R.string.levelAdv_fast),
                getString(R.string.levelAdv_medium),
                getString(R.string.levelAdv_slow)
        };
        new AlertDialog.Builder(this)
                .setIcon(R.drawable.ic_info_white_24dp)
                .setTitle(R.string.npdsa_levelAdv_dlgTitle)
                .setSingleChoiceItems(name, -1, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        room.advancementPace = la[which];
                        advancementButtonLabel();
                        dialog.dismiss();
                        publishGroup();
                    }
                })
                .show();
    }

    private void advancementButtonLabel() {
        String adv = ProtobufSupport.levelAdvToString(room.advancementPace, this);
        MaxUtils.beginDelayedTransition(NewPartyDeviceSelectionActivity.this);
        ((Button)findViewById(R.id.npdsa_levelAdvBtn)).setText(String.format(getString(R.string.npdsa_levelAdvBtn_setFormat), adv));
    }

    // TextView.OnEditorActionListener vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv
    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if(room.advancementPace != LevelAdvancement.LA_UNSPECIFIED) publishGroup();
        else setLevelAdv_callback(null);
        return true;
    }
    // TextView.OnEditorActionListener ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

    private RecyclerView devList;
    private ActionMode actionMode;
    private MenuItem hiddenManagement;
    private static final int REQUEST_APPROVE_PLAYING_CHARACTERS = 1;
}

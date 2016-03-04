package com.massimodz8.collaborativegrouporder.master;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.nsd.NsdManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.NotificationCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.transition.TransitionManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.HoriSwipeOnlyTouchCallback;
import com.massimodz8.collaborativegrouporder.MaxUtils;
import com.massimodz8.collaborativegrouporder.PreSeparatorDecorator;
import com.massimodz8.collaborativegrouporder.R;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.networkio.ProtoBufferEnum;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;
import com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Date;

public class NewPartyDeviceSelectionActivity extends AppCompatActivity implements TextWatcher, ServiceConnection {
    public static MessageDigest hasher;

    @Override
    protected void onDestroy() {
        if(null != room) {
            if (!isChangingConfigurations()) {
                room.shutdown();
                room.stopForeground(true);
                unbindService(this);
            }
            room.setNewClientDevicesAdapter(null);
        }
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_party_device_selection);

        action = (Button) findViewById(R.id.npdsa_activate);
        final TextInputLayout namein = (TextInputLayout) findViewById(R.id.npdsa_partyName);
        EditText sure = namein.getEditText();
        if(null != sure) sure.addTextChangedListener(this);

        Intent temp = new Intent(this, PartyCreationService.class);
        if(!bindService(temp, this, 0)) {
            failedServiceBind();
        }
    }


    private String building;
    private PartyCreationService room;
    private Button action;


    protected class DeviceViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        TextView msg;
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
        }

        @Override
        public void onClick(View v) {
            if(room == null) return;
            room.toggleMembership(key);
        }

        public void bind(PartyDefinitionHelper.DeviceStatus dev) {
            key = dev.source;
            msg.setText(dev.lastMessage);
            final int weight = dev.groupMember ? Typeface.BOLD : Typeface.NORMAL;
            MaxUtils.setVisibility(dev.groupMember ? View.VISIBLE : View.GONE, memberIcon, memberMsg);
            msg.setTypeface(original, weight);
            findViewById(R.id.npdsa_activate).setEnabled(room.getMemberCount() > 0);
        }
    }

    public void action_callback(View btn) {
        if (room.getPublishStatus() == PartyCreationService.PUBLISHER_IDLE) {
            publishGroup();
            btn.setEnabled(false);
            return;
        }
        final int devCount = room.getDeviceCount();
        String use = devCount == 1 ? getString(R.string.npdsa_closing_oneDevice) : String.format(getString(R.string.npdsa_closing_pluralDevices), devCount);
        new AlertDialog.Builder(this)
                .setTitle(R.string.npdsa_sealing_title)
                .setMessage(String.format(getString(R.string.npdsa_sealing_msg), use))
                .setPositiveButton(R.string.npdsa_goDefinePC, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        closeGroup();
                    }
                })
                .show();
    }

    private void publishGroup() {
        final TextInputLayout til = (TextInputLayout) findViewById(R.id.npdsa_partyName);
        final EditText view = til.getEditText();
        if(view == null) return; // impossible
        final String groupName = view.getText().toString().trim();
        ArrayList<PersistentStorage.PartyOwnerData.Group> collisions = room.beginBuilding(groupName);
        if (groupName.isEmpty() || null != collisions) {
            int msg = groupName.isEmpty() ? R.string.npdsa_badParty_msg_emptyName : R.string.npdsa_badParty_msg_alreadyThere;
            new AlertDialog.Builder(this)
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
            new AlertDialog.Builder(this)
                    .setMessage(R.string.newPartyDeviceSelectionActivity_noDiscoveryManager)
                    .show();
            return;
        }
        try {
            room.startListening();
        } catch (IOException e) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.badServerSocket)
                    .setPositiveButton(R.string.giveUpAndGoBack, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .show();
            return;
        }
        room.beginPublishing(nsd, groupName);
        view.setEnabled(false);
        building = groupName;
        elevateServicePriority();
    }

    void closeGroup() {
        if(room == null) return; // happens if the connection to service has gone down
        final Network.GroupFormed form = new Network.GroupFormed();
        room.stopPublishing();
        room.stopListening(true);
        room.kickNonMembers();
        final ArrayList<PartyDefinitionHelper.DeviceStatus> clients = room.getDevices();
        int keyCount = 0;
        for (PartyDefinitionHelper.DeviceStatus dev : clients) {
            final String message = String.format("keyIndex=%1$d, name=\"%2$s\" created=%3$s", keyCount++, building, new Date().toString());
            hasher.reset();
            dev.salt = hasher.digest(message.getBytes());
        }
        new AsyncTask<Void, Void, Void>() {
            Exception[] errors = new Exception[clients.size()];
            int bad;

            @Override
            protected Void doInBackground(Void... params) {
                int slot = 0;
                for (PartyDefinitionHelper.DeviceStatus dev : clients) {
                    form.salt = dev.salt;
                    try {
                        dev.source.writeSync(ProtoBufferEnum.GROUP_FORMED, form);
                    } catch (IOException e) {
                        errors[slot] = e;
                        bad++;
                    }
                    slot++;
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                if(0 != bad) {
                    new AlertDialog.Builder(NewPartyDeviceSelectionActivity.this)
                            .setMessage(R.string.npdsa_failedKeySendDlgMsg)
                            .setPositiveButton(R.string.npdsa_carryOnDlgAction, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    setResult(RESULT_OK);
                                    finish();
                                }
                            }).setNegativeButton(R.string.npdsa_discardDlgAction, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    finish();
                                }
                            }).show();
                    return;
                }
                setResult(RESULT_OK);
                finish();
            }
        }.execute();
    }

    private void beginDelayedTransition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            TransitionManager.beginDelayedTransition((ViewGroup) findViewById(R.id.ga_activityRoot));
        }
    }

    private void failedServiceBind() {
        beginDelayedTransition();
        final TextView status = (TextView) findViewById(R.id.ga_state);
        status.setText(R.string.npdsa_cannotBindPartyService);
        MaxUtils.setVisibility(this, View.GONE,
                R.id.npdsa_partyName,
                R.id.npdsa_activate);
    }

    // TextWatcher vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv
    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) { }
    @Override
    public void afterTextChanged(Editable s) {
        action.setEnabled(s.toString().trim().length() > 0);
    }
    // TextWatcher ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    // ServiceConnection vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        PartyCreationService.LocalBinder binder = (PartyCreationService.LocalBinder) service;
        this.room = binder.getConcreteService();
        beginDelayedTransition();
        TextView status = (TextView) findViewById(R.id.npdsa_status);
        status.setText(R.string.generic_publishing);
        findViewById(R.id.npdsa_partyName).setEnabled(true);

        RecyclerView groupList = (RecyclerView) findViewById(R.id.npdsa_deviceList);
        groupList.setLayoutManager(new LinearLayoutManager(this));
        groupList.setAdapter(room.setNewClientDevicesAdapter(new PartyCreationService.ClientDeviceHolderFactoryBinder<DeviceViewHolder>() {
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
        groupList.addItemDecoration(new PreSeparatorDecorator(groupList, this) {
            @Override
            protected boolean isEligible(int position) {
                return position != 0;
            }
        });
        new HoriSwipeOnlyTouchCallback(groupList) {
            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                if (room == null) return;
                final DeviceViewHolder real = (DeviceViewHolder) viewHolder;
                room.kick(real.key, true);
                new AlertDialog.Builder(NewPartyDeviceSelectionActivity.this)
                        .setTitle(R.string.kickDevice_title)
                        .setMessage(R.string.kickDevice_msg)
                        .setPositiveButton(R.string.kickDevice_positive, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if(room != null) room.kick(real.key, true);
                            }
                        }).show();
            }

            @Override
            protected boolean disable() { return false; }

            @Override
            protected boolean canSwipe(RecyclerView rv, RecyclerView.ViewHolder vh) { return true; }
        };
        if(room.getBuildingPartyName() != null) { // restoring, so pull it in foreground! Otherwise defer until group name entered.
            elevateServicePriority();
        }
    }

    private void elevateServicePriority() {
        final android.support.v4.app.NotificationCompat.Builder help = new NotificationCompat.Builder(this)
                .setOngoing(true)

                .setContentTitle(room.getBuildingPartyName())
                .setContentText(getString(R.string.npdsa_notifyContent))
                .setSmallIcon(R.drawable.ic_notify_icon)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            help.setCategory(Notification.CATEGORY_SERVICE);
        }
        room.startForeground(NOTIFICATION_ID, help.build());
    }

    private static final int NOTIFICATION_ID = 1;


    @Override
    public void onServiceDisconnected(ComponentName name) {
        unbindService(this);
        new AlertDialog.Builder(this)
                .setMessage(R.string.generic_lostServiceConn)
                .show();
        room = null;
    }
    // ServiceConnection ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
}

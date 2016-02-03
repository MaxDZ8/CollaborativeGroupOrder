package com.massimodz8.collaborativegrouporder;

import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Typeface;
import android.net.nsd.NsdManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.networkio.Events;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

public class NewPartyDeviceSelectionActivity extends AppCompatActivity implements ServiceConnection, TextWatcher {

    public static final int PUBLISHER_CHECK_PERIOD = 250;
    public static final int PUBLISHER_CHECK_DELAY = 1000;
    private static final int MSG_SERVICE_REGISTRATION_FAILED = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_party_device_selection);
        final ActionBar bar = getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
        }

        if (savedInstanceState != null) {
            deviceKey = savedInstanceState.getLong(EXTRA_SERVICED_DEVICE_STATUS_KEY, 0);
            serviceKey = savedInstanceState.getLong(EXTRA_SERVICED_PUBLISHED_SERVICE_KEY, 0);
        }

        action = (Button) findViewById(R.id.newPartyDeviceSelectionActivity_activate);
        final EditText namein = (EditText) findViewById(R.id.newPartyDeviceSelectionActivity_groupName);
        namein.addTextChangedListener(this);

        RecyclerView groupList = (RecyclerView) findViewById(R.id.newPartyDeviceSelectionActivity_deviceList);
        groupList.setLayoutManager(new LinearLayoutManager(this));
        groupList.setAdapter(listAdapter);

        Intent sharing = new Intent(this, CrossActivityService.class);
        if (!bindService(sharing, this, 0)) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.couldNotBindInternalService)
                    .setCancelable(false)
                    .setPositiveButton(R.string.giveUpAndGoBack, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .show();
        }
    }

    static final String EXTRA_SERVICED_DEVICE_STATUS_KEY = "com.massimodz8.collaborativegrouporder.NewPartyDeviceSelectionActivity.servicedDeviceStatusKey";
    static final String EXTRA_SERVICED_PUBLISHED_SERVICE_KEY = "com.massimodz8.collaborativegrouporder.NewPartyDeviceSelectionActivity.publishedServiceKey";

    CrossActivityService.ProxyBinder binder;
    long deviceKey, serviceKey;
    Handler guiHandler = new MyHandler(this);
    Vector<DeviceStatus> clients = new Vector<>();
    PublishedService publisher;
    ServerSocket landing;
    Button action;
    Timer ticker;
    RecyclerView.Adapter<DeviceViewHolder> listAdapter = new RecyclerView.Adapter<DeviceViewHolder>() {
        @Override
        public long getItemId(int position) {
            int good = 0;
            for (DeviceStatus d : clients) {
                if (d.lastMessage == null) continue;
                if (good == position) return d.source.unique;
                good++;
            }
            return RecyclerView.NO_ID;
        }

        @Override
        public DeviceViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View layout = getLayoutInflater().inflate(R.layout.card_joining_device, parent, false);
            return new DeviceViewHolder(layout);
        }

        @Override
        public void onBindViewHolder(DeviceViewHolder holder, int position) {
            int count = 0;
            DeviceStatus dev = null;
            for (DeviceStatus d : clients) {
                if (d.lastMessage != null && !d.kicked) {
                    if (count == position) {
                        dev = d;
                        break;
                    }
                    count++;
                }
            }
            if (dev == null) return; // impossible but make lint happy
            holder.key = dev.source;
            holder.msg.setText(dev.lastMessage);
            holder.accepted.setChecked(dev.groupMember);
        }

        @Override
        public int getItemCount() {
            int count = 0;
            for (DeviceStatus d : clients) {
                if (d.lastMessage != null && !d.kicked) count++;
            }
            return count;
        }
    };

    protected class DeviceViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {
        public TextView msg;
        public CheckBox accepted;
        MessageChannel key;
        final Typeface original;

        public DeviceViewHolder(View itemView) {
            super(itemView);
            msg = (TextView) itemView.findViewById(R.id.card_joiningDevice_msg);
            accepted = (CheckBox) itemView.findViewById(R.id.card_joiningDevice_accepted);
            itemView.findViewById(R.id.card_joiningDevice_kick).setOnClickListener(this);
            accepted.setOnCheckedChangeListener(this);
            original = accepted.getTypeface();
        }

        @Override
        public void onClick(View v) {
            new AlertDialog.Builder(NewPartyDeviceSelectionActivity.this)
                    .setTitle(R.string.kickDevice_title)
                    .setMessage(R.string.kickDevice_msg)
                    .setPositiveButton(R.string.kickDevice_positive, new KickListener(key))
                    .show();
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            final int res = isChecked ? R.string.card_joining_device_groupMemberCheck : R.string.joiningDeviceHello_acceptedCheckbox_nope;
            final int weight = isChecked ? Typeface.BOLD : Typeface.NORMAL;
            accepted.setText(res);
            accepted.setTypeface(original, weight);
            int count = 0;
            for (DeviceStatus dev : clients) {
                if (dev.source == key) {
                    dev.groupMember = isChecked;
                }
                if (dev.groupMember) count++;
            }
            listAdapter.notifyDataSetChanged();
            findViewById(R.id.newPartyDeviceSelectionActivity_activate).setEnabled(count > 0);
        }
    }

    class KickListener implements DialogInterface.OnClickListener {
        public KickListener(MessageChannel key) {
            this.key = key;
        }

        final MessageChannel key;

        @Override
        public void onClick(DialogInterface dialog, int which) {
            if (which == AlertDialog.BUTTON_POSITIVE) {
                for (DeviceStatus dev : clients) {
                    if (dev.source == key) {
                        dev.kicked = true;
                        dev.groupMember = false;
                        break;
                    }
                }
            }
        }
    }

    static class MyHandler extends Handler {
        final WeakReference<NewPartyDeviceSelectionActivity> target;

        public MyHandler(NewPartyDeviceSelectionActivity target) {
            this.target = new WeakReference<>(target);
        }

        @Override
        public void handleMessage(Message msg) {
            final NewPartyDeviceSelectionActivity target = this.target.get();
            switch (msg.what) {
                case MSG_SERVICE_REGISTRATION_FAILED: {
                    String readable = nsdErrorString(target.publisher.getErrorCode());
                    new AlertDialog.Builder(target)
                            .setMessage(String.format(target.getString(R.string.serviceRegFailed_msg), readable))
                            .show();
                    target.ticker.cancel();
                    target.ticker = null;
                    target.publisher = null;
                    try {
                        target.landing.close();
                    } catch (IOException e) {
                        // No idea what to do with that...
                    }
                    target.landing = null;
                }
                //case MSG_SOCKET_DEAD: target.onSocketDead((Events.SocketDisconnected) msg.obj); break;
                //case MSG_PEER_MESSAGE_UPDATED: {
                //    Events.PeerMessage real = (Events.PeerMessage)msg.obj;
                //    target.gathering.updateDeviceMessage(real.which, real.msg);
                //    target.onTalkingCountChanged();
                //    break;
                //}
                //case MSG_CHARACTER_DEFINITION: target.characterUpdate((Events.CharacterDefinition)msg.obj); break;
            }
        }
    }

    public void action_callback(View btn) {
        btn.setEnabled(false);
        if (landing == null) publishGroup();
        else closeGroup();
    }

    private void publishGroup() {
        final TextView view = (TextView) findViewById(R.id.newPartyDeviceSelectionActivity_groupName);
        final String groupName = view.getText().toString().trim();
        if (groupName.isEmpty() || binder.getGroupByName(groupName) != null) {
            int msg = groupName.isEmpty() ? R.string.newPartyDeviceSelection_badParty_msg_emptyName : R.string.newPartyDeviceSelection_badParty_msg_alreadyThere;
            new AlertDialog.Builder(this)
                    .setTitle(R.string.newPartyDeviceSelectionActivity_badParty_title)
                    .setMessage(msg)
                    .setPositiveButton(R.string.newPartyDeviceSelection_badParty_retry, new DialogInterface.OnClickListener() {
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
        ServerSocket listener;
        try {
            listener = new ServerSocket(0);
        } catch (IOException e) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.newPartyDeviceSelectionActivity_badLanding)
                    .setPositiveButton(R.string.giveUpAndGoBack, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .show();
            return;
        }
        publisher = new PublishedService(nsd);
        publisher.beginPublishing(listener, groupName, MainMenuActivity.GROUP_FORMING_SERVICE_TYPE);
        view.setEnabled(false);
        landing = listener;
        ticker = new Timer();
        ticker.schedule(new TimerTask() {
            @Override
            public void run() {
                switch(publisher.getStatus()) {
                    //case STATUS_IDLE = 0; // just created, doing nothing.
                    case PublishedService.STATUS_STARTING: break;
                    case PublishedService.STATUS_PUBLISHING: break;
                    case PublishedService.STATUS_STOPPED: break;
                    case PublishedService.STATUS_STOP_FAILED:
                        // I don't think there's anything worth doing there.
                        break;
                    case PublishedService.STATUS_START_FAILED:
                        guiHandler.sendMessage(guiHandler.obtainMessage(MSG_SERVICE_REGISTRATION_FAILED));
                }

            }
        }, PUBLISHER_CHECK_DELAY, PUBLISHER_CHECK_PERIOD);
    }

    void closeGroup() {
        /// TODO
    }

    private static String nsdErrorString(int error) {
        switch(error) {
            case NsdManager.FAILURE_ALREADY_ACTIVE: return "FAILURE_ALREADY_ACTIVE";
            case NsdManager.FAILURE_INTERNAL_ERROR: return "FAILURE_INTERNAL_ERROR";
            case NsdManager.FAILURE_MAX_LIMIT: return "FAILURE_MAX_LIMIT";
        }
        return String.format("%1$d", error);
    }

    static class DeviceStatus {
        final MessageChannel source;
        String lastMessage; // if null still not talking
        int charBudget;
        boolean groupMember;
        boolean kicked;
        Vector<BuildingPlayingCharacter> chars = new Vector<>(); // if contains something we have been promoted

        public DeviceStatus(MessageChannel source) {
            this.source = source;
        }
    }


    DeviceStatus get(MessageChannel c) {
        for (DeviceStatus d : clients) {
            if (d.source == c) return d;
        }
        return null; // impossible most of the time
    }

    String listAddresses() {
        Enumeration<NetworkInterface> nics;
        try {
            nics = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            return getString(R.string.cannotEnumerateNICs);
        }
        String hostInfo = "";
        if (nics != null) {
            while (nics.hasMoreElements()) {
                NetworkInterface n = nics.nextElement();
                Enumeration<InetAddress> addrs = n.getInetAddresses();
                Inet4Address ipFour = null;
                Inet6Address ipSix = null;
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a.isAnyLocalAddress()) continue; // ~0.0.0.0 or ::, sure not useful
                    if (a.isLoopbackAddress()) continue; // ~127.0.0.1 or ::1, not useful
                    if (ipFour == null && a instanceof Inet4Address) ipFour = (Inet4Address) a;
                    if (ipSix == null && a instanceof Inet6Address) ipSix = (Inet6Address) a;
                }
                if (ipFour != null)
                    hostInfo += String.format(getString(R.string.explicit_address), stripUselessChars(ipFour.toString()));
                if (ipSix != null)
                    hostInfo += String.format(getString(R.string.explicit_address), stripUselessChars(ipSix.toString()));
            }
        }
        return hostInfo;
    }

    private static String stripUselessChars(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '%') {
                s = s.substring(0, i);
                break;
            }
        }
        return s.charAt(0) == '/' ? s.substring(1) : s;
    }

    /// GUI state can be inferred by the state of our data.
    private void refreshGUI() {
        if (landing == null)
            return; // initial state as in editor is "fine", button is enabled by listener on the input field
        action.setText(R.string.newPartyDeviceSelectionActivity_goDefiningPCs);
        int count = 0;
        for (DeviceStatus dev : clients) {
            if (dev.groupMember) count++;
        }
        action.setEnabled(count != 0);
        TextView info = (TextView) findViewById(R.id.newPartyDeviceSelectionActivity_explicitConnectionInfos);
        info.setText(String.format(getString(R.string.newPartyDeviceSelectionActivity_explicitConnectionInfos), landing.getLocalPort(), listAddresses()));

        ViewUtils.setVisibility(this, View.VISIBLE,
                R.id.newPartyDeviceSelectionActivity_publishFeedback,
                R.id.newPartyDeviceSelectionActivity_deviceList,
                R.id.newPartyDeviceSelectionActivity_explicitConnectionInfos,
                R.id.newPartyDeviceSelectionActivity_publishing);
        findViewById(R.id.newPartyDeviceSelectionActivity_inputNameInstructions).setVisibility(View.GONE);
        findViewById(R.id.newPartyDeviceSelectionActivity_groupName).setEnabled(false);
    }

    // ServiceConnection vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        binder = (CrossActivityService.ProxyBinder) service;
        if (deviceKey != 0) {
            clients = (Vector<DeviceStatus>) binder.get(deviceKey);
        }
        if (serviceKey != 0) {
            publisher = (PublishedService) binder.get(serviceKey);
            landing = publisher.getSocket();
        }
        refreshGUI();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.lostCrossActivitySharingService))
                .setCancelable(false)
                .setPositiveButton(R.string.giveUpAndGoBack, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .show();
    }
    // ServiceConnection ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    // TextWatcher vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv
    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) { }
    @Override
    public void afterTextChanged(Editable s) {
        action.setEnabled(s.toString().trim().length() > 0);
    }
    // TextVWatcher ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
}

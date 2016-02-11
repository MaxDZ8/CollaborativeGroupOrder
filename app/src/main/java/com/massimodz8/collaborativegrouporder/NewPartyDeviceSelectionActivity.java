package com.massimodz8.collaborativegrouporder;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.nsd.NsdManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
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
import com.massimodz8.collaborativegrouporder.networkio.LandingServer;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.networkio.ProtoBufferEnum;
import com.massimodz8.collaborativegrouporder.networkio.PumpTarget;
import com.massimodz8.collaborativegrouporder.networkio.Pumper;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;
import com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.SocketException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Enumeration;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

public class NewPartyDeviceSelectionActivity extends AppCompatActivity implements TextWatcher {
    static final int PUBLISHER_CHECK_PERIOD = 250;
    static final int PUBLISHER_CHECK_DELAY = 1000;
    static final int PEER_MESSAGE_INTERVAL_MS = 2000;
    static final int INITIAL_MESSAGE_CHAR_BUDGET = 30;

    static final int MSG_SERVICE_REGISTRATION_FAILED = 1;
    static final int MSG_FAILED_ACCEPT = 2;
    static final int MSG_CONNECTED = 3;
    static final int MSG_SOCKET_LOST = 4;
    static final int MSG_PEER_MESSAGE = 5;
    static final int MSG_CHARACTER_DEFINITION = 6;
    static final int MSG_REFRESH_GUI = 7;
    static final int MSG_PUMPER_DETACHED = 8;

    @Override
    protected void onDestroy() {
        if(null != ticker) ticker.cancel();
        if(null != publisher) publisher.stopPublishing();
        if(null != acceptor) acceptor.shutdown();
        if(null != landing) {
            try {
                landing.close();
            } catch (IOException e) {
                // just suppress
            }
        }
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // no need to, regen GUI from data
        //super.onSaveInstanceState(outState);

        CrossActivityShare state = (CrossActivityShare) getApplicationContext();
        if(null != netWorkers && 0 != netWorkers.getClientCount()) state.pumpers = netWorkers.move();
        state.landing = landing;
        state.clients = building.clients;
        state.publisher = publisher;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_party_device_selection);

        action = (Button) findViewById(R.id.npdsa_activate);
        final EditText namein = (EditText) findViewById(R.id.npdsa_groupName);
        namein.addTextChangedListener(this);

        RecyclerView groupList = (RecyclerView) findViewById(R.id.npdsa_deviceList);
        groupList.setLayoutManager(new LinearLayoutManager(this));
        groupList.setAdapter(listAdapter);
    }

    @Override
    protected void onStart() {
        super.onStart();

        CrossActivityShare state = (CrossActivityShare) getApplicationContext();
        if(null != state.pumpers) {
            for(Pumper.MessagePumpingThread fella : state.pumpers) netWorkers.pump(fella);
            state.pumpers = null;
        }
        if (null == state.clients) {
            building.clients = new Vector<>();
        } else {
            building.clients = state.clients;
            state.clients = null;
        }
        if(null != state.landing) {
            landing = state.landing;
            state.landing = null;
        }
        if(null != state.publisher) {
            publisher = state.publisher;
            state.publisher = null;
        }
        if(null != landing) {
            acceptor = new MyLandingServer(landing);
        }
        refreshGUI();
    }

    Button action;
    Timer ticker;
    Handler guiHandler = new MyHandler(this);
    LandingServer acceptor;
    RecyclerView.Adapter<DeviceViewHolder> listAdapter = new RecyclerView.Adapter<DeviceViewHolder>() {
        @Override
        public long getItemId(int position) {
            int good = 0;
            for (DeviceStatus d : building.clients) {
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
            for (DeviceStatus d : building.clients) {
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
            for (DeviceStatus d : building.clients) {
                if (d.lastMessage != null && !d.kicked) count++;
            }
            return count;
        }
    };

    // Stuff going to CrossActivityShare vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv
    public BuildingCharacters building = new BuildingCharacters();
    volatile PublishedService publisher;
    ServerSocket landing;
    Pumper netWorkers = new Pumper(guiHandler, MSG_SOCKET_LOST, MSG_PUMPER_DETACHED)
            .add(ProtoBufferEnum.HELLO, new PumpTarget.Callbacks<Network.Hello>() {
                @Override
                public Network.Hello make() { return new Network.Hello(); }

                @Override
                public boolean mangle(MessageChannel from, Network.Hello msg) throws IOException {
                    Network.GroupInfo send = new Network.GroupInfo();
                    send.forming = true;
                    send.name = publisher.name;
                    send.version = MainMenuActivity.NETWORK_VERSION;
                    from.writeSync(ProtoBufferEnum.GROUP_INFO, send);
                    Network.CharBudget bud = new Network.CharBudget();
                    bud.total = INITIAL_MESSAGE_CHAR_BUDGET;
                    bud.period = PEER_MESSAGE_INTERVAL_MS;
                    from.write(ProtoBufferEnum.CHAR_BUDGET, bud);
                    return false;
                }
            }).add(ProtoBufferEnum.PEER_MESSAGE, new PumpTarget.Callbacks<Network.PeerMessage>() {
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
            }); // the pumper itself does not go. We save the pumping threads instead.
    // Stuff going to CrossActivityShare ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    


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
            for (DeviceStatus dev : building.clients) {
                if (dev.source == key) {
                    dev.groupMember = isChecked;
                }
                if (dev.groupMember) count++;
            }
            listAdapter.notifyDataSetChanged();
            findViewById(R.id.npdsa_activate).setEnabled(count > 0);
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
                for (DeviceStatus dev : building.clients) {
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
                    target.acceptor.shutdown();
                    target.acceptor = null;
                } break;
                case MSG_CONNECTED: {
                    DeviceStatus got = new DeviceStatus((MessageChannel) msg.obj);
                    got.charBudget = INITIAL_MESSAGE_CHAR_BUDGET;
                    target.building.clients.add(got);
                    target.netWorkers.pump(got.source);
                } break;
                case MSG_FAILED_ACCEPT: {
                    new AlertDialog.Builder(target)
                            .setMessage(R.string.npdsa_failedAccept)
                            .show();
                } break;
                case MSG_CHARACTER_DEFINITION: target.building.definePlayingCharacter((Events.CharacterDefinition) msg.obj); break;
                case MSG_PEER_MESSAGE: target.building.setMessage((Events.PeerMessage) msg.obj, PEER_MESSAGE_INTERVAL_MS); break;
                case MSG_SOCKET_LOST: target.remove((MessageChannel) msg.obj); break;
                case MSG_REFRESH_GUI: break; // we call refresh anyway...
                case MSG_PUMPER_DETACHED: break; // this never triggers for us
            }
            target.refreshGUI();
        }
    }

    private void remove(MessageChannel gone) {
        DeviceStatus owner = building.get(gone);
        if(null == owner) return; // impossible, but ok.
        if(null == owner.lastMessage) return; // a silent going away is normal
        String string = String.format(getString(R.string.npdsa_lostTalkingDevice), owner.lastMessage);
        // what if the device also defined characters? I don't care! They're not even shown for the time being!
        new AlertDialog.Builder(this).setMessage(string).show();
        netWorkers.forget(gone);
        building.clients.remove(owner);
        try {
            gone.socket.close();
        } catch (IOException e) {
            // do nothing. It's gone anyway.
        }
    }

    public void action_callback(View btn) {
        if (landing == null) {
            publishGroup();
            btn.setEnabled(false);
        }
        else {
            int devCount = 0;
            for(DeviceStatus dev : building.clients) {
                if(dev.groupMember) devCount++;
            }
            String singular = getString(R.string.npdsa_closing_oneDevice);
            String plural = String.format(getString(R.string.npdsa_closing_pluralDevices), devCount);
            String use = devCount == 1? singular : plural;
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
    }

    private void publishGroup() {
        final TextView view = (TextView) findViewById(R.id.npdsa_groupName);
        final String groupName = view.getText().toString().trim();
        int collisions = 0;
        CrossActivityShare state = (CrossActivityShare) getApplicationContext();
        for(PersistentStorage.PartyOwnerData.Group test : state.groupDefs) {
            if(test.name.equals(groupName)) {
                collisions++;
                break;
            }
        }
        for(PersistentStorage.PartyClientData.Group test : state.groupKeys) {
            if(test.name.equals(groupName)) {
                collisions++;
                break;
            }
        }
        if (groupName.isEmpty() || 0 != collisions) {
            int msg = groupName.isEmpty() ? R.string.npdsa_badParty_msg_emptyName : R.string.npdsa_badParty_msg_alreadyThere;
            new AlertDialog.Builder(this)
                    .setTitle(R.string.npdsa_badParty_title)
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
        ServerSocket listener;
        try {
            listener = new ServerSocket(0);
        } catch (IOException e) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.npdsa_badLanding)
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
        acceptor = new MyLandingServer(landing);
        ticker = new Timer();
        class StooPid {
            int value;
        }
        final StooPid previously = new StooPid();
        previously.value = publisher.getStatus();
        ticker.schedule(new TimerTask() {
            @Override
            public void run() {
                if(null == publisher) return; // happens if we got cancelled a moment ago
                int now = publisher.getStatus();
                switch (now) {
                    //case STATUS_IDLE = 0; // just created, doing nothing.
                    case PublishedService.STATUS_STARTING:
                        break;
                    case PublishedService.STATUS_PUBLISHING:
                        break;
                    case PublishedService.STATUS_STOPPED:
                        break;
                    case PublishedService.STATUS_STOP_FAILED:
                        // I don't think there's anything worth doing there.
                        break;
                    case PublishedService.STATUS_START_FAILED:
                        guiHandler.sendMessage(guiHandler.obtainMessage(MSG_SERVICE_REGISTRATION_FAILED));
                }
                if (previously.value != now) {
                    guiHandler.sendMessage(guiHandler.obtainMessage(MSG_REFRESH_GUI));
                    previously.value = now;
                }
            }
        }, PUBLISHER_CHECK_DELAY, PUBLISHER_CHECK_PERIOD);
        building.name = groupName;
    }

    void closeGroup() {
        try {
            building.salt = buildKey();
        } catch (NoSuchAlgorithmException e) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.npdsa_failedSalt)
                    .show();
        }

        if(null != publisher) {
            publisher.stopPublishing();
            publisher = null;
        }
        if(null != landing) {
            acceptor.shutdown();
            try {
                landing.close();
            } catch (IOException e) {
                // suppress
            }
            landing = null;
            acceptor = null;
        }

        final Network.GroupFormed form = new Network.GroupFormed();
        form.salt = building.salt;
        new AsyncTask<Void, Void, Void>() {
            Exception[] errors = new Exception[building.clients.size()];
            int bad;

            @Override
            protected Void doInBackground(Void... params) {
                int slot = 0;
                for (DeviceStatus dev : building.clients) {
                    if (!dev.kicked && dev.groupMember) {
                        try {
                            dev.source.writeSync(ProtoBufferEnum.GROUP_FORMED, form);
                        } catch (IOException e) {
                            errors[slot] = e;
                            bad++;
                        }
                    }
                    slot++;
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                if(0 != bad) {
                    new AlertDialog.Builder(NewPartyDeviceSelectionActivity.this)
                            .setMessage("At least one connected device couldn't get in the party.")
                            .setPositiveButton("Continue anyway", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) { goDefinePCs(); }
                            }).setNegativeButton("Restart", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    finish();
                                }
                            }).show();
                    return;
                }
                goDefinePCs();
            }
        }.execute();
    }

    private void goDefinePCs() {
        CrossActivityShare state = (CrossActivityShare) getApplicationContext();
        if(null != netWorkers && 0 != netWorkers.getClientCount()) {
            // Before continuing, get the rid of everything that's not a member.
            Vector<DeviceStatus> members = new Vector<>();
            for(DeviceStatus dev : building.clients) {
                if(dev.groupMember) members.add(dev);
                else {
                    netWorkers.forget(dev.source);
                    try {
                        dev.source.socket.close();
                    } catch (IOException e) {
                        // I don't care about those guys.
                    }
                }
            }
            building.clients = members;
            state.pumpers = netWorkers.move();
        }
        state.clients = building.clients;
        state.newGroupKey = building.salt;
        state.newGroupName = building.name;
        building.clients = null;

        setResult(RESULT_OK);
        finish();
    }

    public byte[] buildKey() throws NoSuchAlgorithmException {
        int count = 0;
        for(DeviceStatus dev : building.clients) {
            if(dev.groupMember) count++;
        }
        final String message = String.format("counting=%1$d name=\"%2$s\" created=%3$s", count, building.name, new Date().toString());
        MessageDigest hasher = MessageDigest.getInstance("SHA-256");
        return hasher.digest(message.getBytes());
    }

    private static String nsdErrorString(int error) {
        switch(error) {
            case NsdManager.FAILURE_ALREADY_ACTIVE: return "FAILURE_ALREADY_ACTIVE";
            case NsdManager.FAILURE_INTERNAL_ERROR: return "FAILURE_INTERNAL_ERROR";
            case NsdManager.FAILURE_MAX_LIMIT: return "FAILURE_MAX_LIMIT";
        }
        return String.format("%1$d", error);
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
        action.setText(R.string.npdsa_goDefiningPCs);
        int count = 0;
        for (DeviceStatus dev : building.clients) {
            if (dev.groupMember) count++;
        }
        action.setEnabled(count != 0);
        TextView info = (TextView) findViewById(R.id.npdsa_explicitConnectionInfos);
        info.setText(String.format(getString(R.string.npdsa_explicitConnectionInfos), landing.getLocalPort(), listAddresses()));

        ViewUtils.setVisibility(this, View.VISIBLE,
                R.id.npdsa_publishFeedback,
                R.id.npdsa_deviceList,
                R.id.npdsa_explicitConnectionInfos,
                R.id.npdsa_publishing);
        findViewById(R.id.npdsa_groupName).setEnabled(false);
        listAdapter.notifyDataSetChanged();
    }

    class MyLandingServer extends LandingServer {
        public MyLandingServer(ServerSocket source) {
            super(source);
        }
        @Override
        public void failedAccept() { guiHandler.sendMessage(guiHandler.obtainMessage(MSG_FAILED_ACCEPT)); }
        @Override
        public void connected(MessageChannel newComer) { guiHandler.sendMessage(guiHandler.obtainMessage(MSG_CONNECTED, newComer)); }
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
    // TextVWatcher ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
}
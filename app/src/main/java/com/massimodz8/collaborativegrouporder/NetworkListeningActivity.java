package com.massimodz8.collaborativegrouporder;

import android.content.Context;
import android.content.DialogInterface;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.networkMessage.PeerMessage;
import com.massimodz8.collaborativegrouporder.networkMessage.ServerInfoRequest;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Vector;

/* This activity is started by the MainMenuActivity when the user wants to assemble a new party.
We open a Wi-Fi direct bubble and a proper service and listen to network to find users joining.
 */
/** todo: this is an excellent moment to provide some ads: after the GM started scanning
 * he has to wait for users to join and I can push to him whatever I want.  */
public class NetworkListeningActivity extends AppCompatActivity implements NsdManager.RegistrationListener {
    public static final int PROTOCOL_VERSION = 1;
    private ServerSocket landing;
    private NsdManager nsdService;
    private Handler guiThreadHandler;
    private Thread acceptor;
    private RecyclerView.Adapter groupListAdapter;
    private SocketChecker socketWatcher;
    private IdentityHashMap<Class, Integer> messageFilters;

    static class HandShakedDevice {
        OOSocket pipe;
        public ThreadedSocketPump cancelMe; // if nonnull it's most likely sleeping on this socket waiting for a message to update

        HandShakedDevice(OOSocket s) { pipe = s; }

        @Override
        protected void finalize() throws Throwable { stopPumping();    super.finalize(); }
        public void stopPumping() {
            if(cancelMe != null) {
                cancelMe.interrupt();
                cancelMe = null;
            }
        }
    }
    private Vector<HandShakedDevice> anonymousDevices = new Vector<>(); /// Devices connected but no hello message sent yet.

    static class TalkingDevice {
        public final HandShakedDevice initial;

        public boolean accepted = false;
        public String lastMsg = "";
        public final int unique = created++;

        private static int created = 0;

        TalkingDevice(HandShakedDevice dev) { initial = dev; }
    }
    Vector<TalkingDevice> candidates = new Vector<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_network_listening);
    }
    protected void onDestroy() {
        if(acceptor != null) acceptor.interrupt();
        if(socketWatcher != null) socketWatcher.interrupt();
        if(nsdService != null) nsdService.unregisterService(this);
        super.onDestroy();
    }

    @Override
    public void onResume() {
        // regen service if I decide to tear it down.
        super.onResume();
    }

    @Override
    public void onPause() {
        // nsdService.unregisterService(this);
        // I don't do this. I really want the service to stay on.
        super.onPause();
    }

    public void initiatePartyHandshake(View view) {
        final EditText groupNameView = (EditText)findViewById(R.id.in_partyName);
        final String gname = groupNameView.getText().toString();
        if(gname.isEmpty()) {
            AlertDialog.Builder build = new AlertDialog.Builder(this);
            build.setTitle(R.string.groupNameIsEmpty_title)
                    .setMessage(R.string.groupNameIsEmpty_msg)
                    .setPositiveButton(R.string.groupName_retry, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            groupNameView.requestFocus();
                        }
                    });
            build.show();
            return;
        }
        view.setVisibility(View.GONE);
        findViewById(R.id.txt_privacyWarning).setVisibility(View.GONE);


        try {
            landing = new ServerSocket(0);
        } catch (IOException e) {
            AlertDialog.Builder build = new AlertDialog.Builder(this);
            build.setTitle(R.string.serverSocketFailed_title)
                    .setMessage(R.string.serverSocketFailed_msg);
            build.show();
            return;
        }

        nsdService = (NsdManager)getSystemService(Context.NSD_SERVICE);
        if(nsdService == null) {
            AlertDialog.Builder build = new AlertDialog.Builder(this);
            build.setTitle(R.string.nullNSDService_title)
                    .setMessage(R.string.nullNSDService_msg);
            build.show();
        }

        groupListAdapter = new DeviceListAdapter();
        RecyclerView groupList = (RecyclerView) findViewById(R.id.groupList);
        groupList.setLayoutManager(new LinearLayoutManager(this));
        groupList.setAdapter(groupListAdapter);
        final NetworkListeningActivity self = this;
        guiThreadHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                switch(msg.what) {
                    case MSG_SERVICE_REGISTRATION_COMPLETE:
                        ServiceRegistrationResult res = (ServiceRegistrationResult)msg.obj;
                        if(res == null) res = new ServiceRegistrationResult(-1); // impossible by construction... for now.
                        if(res.successful) {
                            if(!res.netName.equals(gname)) {
                                TextView note = (TextView)findViewById(R.id.txt_renamedService);
                                String text = getString(R.string.renamedGroup);
                                note.setText(String.format(text, res.netName));
                                note.setVisibility(View.VISIBLE);
                            }
                            TextView port = (TextView)findViewById(R.id.txt_FYI_port);
                            String hostInfo = getString(R.string.FYI_explicitConnectInfo);
                            hostInfo += String.format(getString(R.string.explicit_portInfo), landing.getLocalPort());

                            Enumeration<NetworkInterface> nics = null;
                            try {
                                nics = NetworkInterface.getNetworkInterfaces();
                            } catch (SocketException e) {
                                AlertDialog.Builder build = new AlertDialog.Builder(self);
                                build.setTitle("Network error")
                                        .setMessage("Failed to get network information. This isn't really a problem but you'll have to find this device network address in some other way in case your friends need explicit connection data.");
                                build.show();
                            }
                            if(nics != null) {
                                while(nics.hasMoreElements()) {
                                    NetworkInterface n = nics.nextElement();
                                    Enumeration<InetAddress> addrs = n.getInetAddresses();
                                    Inet4Address ipFour = null;
                                    Inet6Address ipSix = null;
                                    while(addrs.hasMoreElements()) {
                                        InetAddress a = addrs.nextElement();
                                        if(a.isAnyLocalAddress()) continue; // ~0.0.0.0 or ::, sure not useful
                                        if(a.isLoopbackAddress()) continue; // ~127.0.0.1 or ::1, not useful
                                        if(ipFour == null && a instanceof Inet4Address) ipFour = (Inet4Address)a;
                                        if(ipSix == null && a instanceof Inet6Address) ipSix = (Inet6Address)a;
                                    }
                                    if(ipFour != null) hostInfo += String.format(getString(R.string.explicit_address), stripUselessChars(ipFour.toString()));
                                    if(ipSix != null) hostInfo += String.format(getString(R.string.explicit_address), stripUselessChars(ipSix.toString()));
                                }
                            }

                            port.setText(hostInfo);
                            port.setVisibility(View.VISIBLE);

                            findViewById(R.id.txt_scanning).setVisibility(View.VISIBLE);
                            findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
                            findViewById(R.id.groupList).setVisibility(View.VISIBLE);
                            View closeGroupBtn = findViewById(R.id.btn_closeGroup);
                            closeGroupBtn.setVisibility(View.VISIBLE);
                            closeGroupBtn.setEnabled(false);
                        }
                        else {
                            AlertDialog.Builder build = new AlertDialog.Builder(self);
                            String readable = nsdErrorString(res.error);
                            build.setTitle(R.string.serviceRegFailed_title)
                                    .setMessage(String.format(getString(R.string.serviceRegFailed_msg), readable));
                            build.show();
                        }
                        return true;
                    case MSG_PLAYER_WELCOME: {
                        OOSocket client = (OOSocket)msg.obj;
                        if(socketWatcher == null) {
                            socketWatcher = new SocketChecker(guiThreadHandler, MSG_SOCKET_DEAD);
                            socketWatcher.start();
                        }
                        final HandShakedDevice silent = new HandShakedDevice(client);
                        anonymousDevices.add(silent);
                        socketWatcher.add(client.s);
                        refreshDeviceCounts();
                        silent.cancelMe = new ThreadedSocketPump(client, guiThreadHandler, getProtocolFilters(), MSG_SOCKET_DEAD, MSG_UNMATCHED_OBJECT_FROM_PEER);
                        silent.cancelMe.start();
                        break;
                    }
                    case MSG_SOCKET_DEAD:
                        forgetSocket(((OOSocket)msg.obj).s);
                        break;
                    case MSG_PEER_MESSAGE_EXPECTED: {
                        final OOSocket real = (OOSocket)msg.obj;
                        forgetSocket(real.s);
                    } break;
                    case MSG_PEER_MESSAGE_UPDATED: {
                        final ThreadedSocketPump.Message pumped = (ThreadedSocketPump.Message)msg.obj;
                        update(pumped.origin, (PeerMessage)pumped.msg);
                        break;
                    }
                    case MSG_GROUP_INFO_REQUESTED: {
                        final ThreadedSocketPump.Message pumped = (ThreadedSocketPump.Message)msg.obj;
                        new AsyncTask<Void, Void, Void>() {
                            @Override
                            protected Void doInBackground(Void... params) {
                                try {
                                    pumped.origin.writer.writeObject(new ConnectedGroup(PROTOCOL_VERSION, gname));
                                } catch (IOException e) {
                                    forgetSocket(pumped.origin.s); // protocol is stop-n-wait anyway. If I cannot reply I'm busted.
                                }
                                return null;
                            }
                        }.execute();
                    }
                }
                return false;
            }
        });
        NsdServiceInfo servInfo  = new NsdServiceInfo();
        servInfo.setServiceName(gname);
        servInfo.setServiceType("_groupInitiative._tcp");
        servInfo.setPort(landing.getLocalPort());
        nsdService.registerService(servInfo, NsdManager.PROTOCOL_DNS_SD, this);

        view.setEnabled(false);
        groupNameView.setEnabled(false);
        groupNameView.setVisibility(View.GONE);
        findViewById(R.id.txt_getGroupNameDesc).setVisibility(View.GONE);
        final ActionBar actionBar = getSupportActionBar();
        if(actionBar != null) actionBar.setTitle(String.format(getString(R.string.networkListening_groupNameTitle), gname));


    }

    private Map<Class, Integer> getProtocolFilters() {
        if(messageFilters == null) {
            IdentityHashMap<Class, Integer> hey = new IdentityHashMap<>();
            hey.put(PeerMessage.class, MSG_PEER_MESSAGE_UPDATED);
            hey.put(ServerInfoRequest.class, MSG_GROUP_INFO_REQUESTED);
            messageFilters = hey;
        }
        return messageFilters;
    }

    private void update(OOSocket from, PeerMessage msg) {
        // First case: maybe this is something we have to upgrade to TalkingDevice!
        boolean anon = false;
        for(int i = 0; i < anonymousDevices.size(); i++) {
            final HandShakedDevice dev = anonymousDevices.elementAt(i);
            if(dev.pipe == from) {
                candidates.add(new TalkingDevice(dev));
                anonymousDevices.remove(i);
                anon = true;
                break;
            }
        }
        // Now look and move string for talkers.
        boolean talkers = false;
        for(int i = 0; i < candidates.size(); i++) {
            final TalkingDevice dev = candidates.elementAt(i);
            if(dev.initial.pipe == from) {
                dev.lastMsg = msg.text;
                talkers = true;
                break;
            }
        }
        if(anon || talkers) refreshDeviceCounts();
        if(talkers) groupListAdapter.notifyDataSetChanged();
    }

    private void forgetSocket(Socket s) {
        final int anon = anonymousDevices.size();
        final int cand = candidates.size();
        for(int i = 0; i < anonymousDevices.size(); i++) {
            final HandShakedDevice dev = anonymousDevices.elementAt(i);
            if(dev.pipe.s == s) {
                dev.stopPumping();
                anonymousDevices.remove(i);
                socketWatcher.remove(s);
                break;
            }
        }
        for(int i = 0; i < candidates.size(); i++) {
            final TalkingDevice dev = candidates.elementAt(i);
            if(dev.initial.pipe.s == s) {
                dev.initial.stopPumping();
                candidates.remove(i);
                socketWatcher.remove(s);
                break;
            }
        }
        if(anon != anonymousDevices.size() || cand != candidates.size()) refreshDeviceCounts();
        if(cand != candidates.size()) groupListAdapter.notifyDataSetChanged();
    }

    private void refreshDeviceCounts() {
        final TextView connected = (TextView)findViewById(R.id.txt_connectedDeviceCounts);
        if(anonymousDevices.size() > 0) {
            String show = getString(R.string.anonDeviceStats);
            final String wut = getString(anonymousDevices.size() < 2? R.string.word_device_singular : R.string.word_device_plural);
            connected.setText(String.format(show, anonymousDevices.size(), wut));
            connected.setVisibility(View.VISIBLE);
        }
        else connected.setVisibility(View.INVISIBLE);

        final TextView talking = (TextView)findViewById(R.id.txt_talkingDeviceCounts);
        if(candidates.size() > 0) {
            String show = getString(R.string.talkingDeviceStats);
            final String wut = getString(candidates.size() < 2? R.string.word_device_singular : R.string.word_device_plural);
            talking.setText(String.format(show, candidates.size(), wut));
            talking.setVisibility(View.VISIBLE);
        }
        else talking.setVisibility(View.INVISIBLE);
    }

    private static String stripUselessChars(String s) {
        for(int i = 0; i < s.length(); i++) {
            if(s.charAt(i) == '%') {
                s = s.substring(0, i);
                break;
            }
        }
        return s.charAt(0) == '/'? s.substring(1) : s;
    }

    private static String nsdErrorString(int error) {
        switch(error) {
            case NsdManager.FAILURE_ALREADY_ACTIVE: return "FAILURE_ALREADY_ACTIVE";
            case NsdManager.FAILURE_INTERNAL_ERROR: return "FAILURE_INTERNAL_ERROR";
            case NsdManager.FAILURE_MAX_LIMIT: return "FAILURE_MAX_LIMIT";
        }
        return String.format("%1$d", error);
    }


    // NsdManager.RegistrationListener() is async AND on a different thread so I cannot just
    // modify the various controls from there. Instead, wait for success/fail and then
    // pass a notification to the UI thread.
    public static final int MSG_SERVICE_REGISTRATION_COMPLETE = 1;
    public static final int MSG_PLAYER_WELCOME = 3;
    public static final int MSG_SOCKET_DEAD = 4;
    public static final int MSG_PEER_MESSAGE_EXPECTED = 5;
    public static final int MSG_PEER_MESSAGE_UPDATED = 6;
    public static final int MSG_UNMATCHED_OBJECT_FROM_PEER = 7;
    public static final int MSG_GROUP_INFO_REQUESTED = 8;

    static class ServiceRegistrationResult {
        public ServiceRegistrationResult(String netName) {
            this.netName = netName;
            successful = true;
        }
        public ServiceRegistrationResult(int err) {
            error = err;
            successful = false;
        }

        public int error;
        public boolean successful;
        public String netName;
    }

    /** Compared to JoinGroupActivity.GroupListAdapter this is a bit different. Why?
     * The main issue is I use different types of items, the first line is an easygoing TextView
     * showing count of how many devices have connected but not identified yet.
     * Then, for every device there's a message. Messages come and go, devices come and go.
     * Every device can be kicked (action button) or accepted in the group (checkbox).
     */
    private class DeviceListAdapter extends RecyclerView.Adapter<DeviceListAdapter.DeviceViewHolder> {
        protected class DeviceViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {
            public TextView msg;
            public CheckBox accepted;
            TextView details;
            int index = -1;
            public DeviceViewHolder(View itemView) {
                super(itemView);
                msg = (TextView)itemView.findViewById(R.id.card_joiningDevice_msg);
                details = (TextView)itemView.findViewById(R.id.card_joiningDevice_details);
                accepted = (CheckBox)itemView.findViewById(R.id.card_joiningDevice_accepted);
                itemView.findViewById(R.id.card_joiningDevice_kick).setOnClickListener(this);
                accepted.setOnCheckedChangeListener(this);
            }

            @Override
            public void onClick(View v) {
                kickDevice(index);
            }

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                candidates.elementAt(index).accepted = isChecked;
            }
        }

        @Override
        public DeviceViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inf = getLayoutInflater();
            View layout = inf.inflate(R.layout.card_joining_device, parent, false);
            return new DeviceViewHolder(layout);
        }

        @Override
        public void onBindViewHolder(DeviceViewHolder holder, int position) {
            holder.index = position;
            final TalkingDevice dev = candidates.elementAt(position);
            holder.msg.setText(dev.lastMsg);
            holder.accepted.setChecked(dev.accepted);
            String format = getString(R.string.joiningDeviceWithHello_details);
            format = String.format(format, dev.unique, dev.initial.pipe.s.getInetAddress().toString());
            holder.details.setText(format);
        }

        @Override
        public int getItemCount() {
            return candidates.size();
        }
    }

    private void kickDevice(int index) {
        AlertDialog.Builder build = new AlertDialog.Builder(this);
        build.setTitle("TODO: STUB")
                .setMessage("Kicking device" + Integer.toString(index) + " is not currently implemented.");
        build.show();
    }

    //
    // NsdManager.RegistrationListener() ___________________________________________________________
    @Override
    public void onServiceRegistered(NsdServiceInfo info) {
        final String netName = info.getServiceName();
        Message msg = Message.obtain(guiThreadHandler, MSG_SERVICE_REGISTRATION_COMPLETE, new ServiceRegistrationResult(netName));
        guiThreadHandler.sendMessage(msg);
    }

    @Override
    public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
        Message msg = Message.obtain(guiThreadHandler, MSG_SERVICE_REGISTRATION_COMPLETE, new ServiceRegistrationResult(errorCode));
        guiThreadHandler.sendMessage(msg);
    }

    @Override
    public void onServiceUnregistered(NsdServiceInfo arg0) { }

    @Override
    public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) { }
}

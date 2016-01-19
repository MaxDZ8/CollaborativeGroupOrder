package com.massimodz8.collaborativegrouporder;

import android.content.Context;
import android.content.DialogInterface;
import android.net.nsd.NsdManager;
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

import com.massimodz8.collaborativegrouporder.networkio.Events;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.networkio.formingServer.GroupForming;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Vector;

/* This activity is started by the MainMenuActivity when the user wants to assemble a new party.
We publish a service and listen to network to find users joining.
 */
/** todo: this is an excellent moment to provide some ads: after the GM started scanning
 * he has to wait for users to join and I can push to him whatever I want.  */
public class CreatePartyActivity extends AppCompatActivity {
    private GroupForming gathering;
    private RecyclerView.Adapter groupListAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_network_listening);
    }
    protected void onDestroy() {
        if(gathering != null) try {
            gathering.shutdown();
        } catch (IOException e) {
            /// ehrm... hope n pray the OS will just kill us
        }
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

        NsdManager nsd = (NsdManager)getSystemService(Context.NSD_SERVICE);
        if(nsd == null) {
            AlertDialog.Builder build = new AlertDialog.Builder(this);
            build.setTitle(R.string.nullNSDService_title)
                    .setMessage(R.string.nullNSDService_msg);
            build.show();
            return;
        }

        final CreatePartyActivity self = this;
        try {
            gathering = new GroupForming(gname, nsd, new MyHandler(), MSG_SERVICE_REGISTRATION_COMPLETE) {
                @Override
                public void onFailedAccept() {
                    AlertDialog.Builder build = new AlertDialog.Builder(self);
                    build.setMessage(R.string.cannotAccept);
                    build.show();
                }

                @Override
                public void refreshSilentCount(int currently) {
                    handler.sendMessage(handler.obtainMessage(MSG_SILENT_DEVICE_COUNT, currently));
                }
            };
        } catch (IOException e) {
            AlertDialog.Builder build = new AlertDialog.Builder(this);
            build.setTitle(R.string.serverSocketFailed_title)
                    .setMessage(R.string.serverSocketFailed_msg);
            build.show();
        }
    }

    void onNSRegistrationComplete(GroupForming.ServiceRegistrationResult res) {
        if(res == null) res = new GroupForming.ServiceRegistrationResult(-1); // impossible by construction... for now.
        if(!res.successful) {
            AlertDialog.Builder build = new AlertDialog.Builder(this);
            String readable = nsdErrorString(res.error);
            build.setTitle(R.string.serviceRegFailed_title)
                    .setMessage(String.format(getString(R.string.serviceRegFailed_msg), readable));
            build.show();
            return;
        }
        if(!res.netName.equals(gathering.userName)) {
            TextView note = (TextView)findViewById(R.id.txt_renamedService);
            String text = getString(R.string.renamedGroup);
            note.setText(String.format(text, res.netName));
            note.setVisibility(View.VISIBLE);
        }
        TextView port = (TextView)findViewById(R.id.txt_FYI_port);
        String hostInfo = getString(R.string.FYI_explicitConnectInfo);
        hostInfo += String.format(getString(R.string.explicit_portInfo), gathering.getLocalPort());

        groupListAdapter = new DeviceListAdapter();
        RecyclerView groupList = (RecyclerView) findViewById(R.id.groupList);
        groupList.setLayoutManager(new LinearLayoutManager(this));
        groupList.setAdapter(groupListAdapter);

        final EditText groupNameView = (EditText)findViewById(R.id.in_partyName);
        groupNameView.setEnabled(false);
        groupNameView.setVisibility(View.GONE);
        findViewById(R.id.txt_getGroupNameDesc).setVisibility(View.GONE);
        final ActionBar actionBar = getSupportActionBar();
        if(actionBar != null) actionBar.setTitle(String.format(getString(R.string.networkListening_groupNameTitle), gathering.userName));

        Enumeration<NetworkInterface> nics;
        try {
            nics = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            AlertDialog.Builder build = new AlertDialog.Builder(this);
            build.setMessage(R.string.cannotEnumerateNICs);
            build.show();
            return;
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

        try {
            gathering.begin(MSG_SOCKET_DEAD, MSG_PEER_MESSAGE_UPDATED);
        } catch (IOException e) {
            AlertDialog.Builder build = new AlertDialog.Builder(this);
            build.setMessage(R.string.cannotStartSilentForming);
            build.show();
        }
    }

    class MyHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case MSG_SERVICE_REGISTRATION_COMPLETE: onNSRegistrationComplete((GroupForming.ServiceRegistrationResult) msg.obj); break;
                case MSG_SILENT_DEVICE_COUNT: onSilentCountChanged(); break;
                case MSG_SOCKET_DEAD: onSocketDead((Events.SocketDisconnected)msg.obj); break;
            }
        }
    }

    static class PlayingCharacter {
        String name;
        int initiativeBonus;
    }

    static class DeviceStatus {
        public final MessageChannel source;
        public String lastMessage; // if null still not talking
        public boolean groupMember;
        Vector<PlayingCharacter> chars = new Vector<>(); // if contains something we have been promoted
        String names() {
            String names = "";
            for(PlayingCharacter pc : chars) names += (names.length() != 0? ", " : "") + pc.name;
            return names;
        }

        public DeviceStatus(MessageChannel source) {
            this.source = source;
        }
    }

    /// This must be a vector, not a map because we need the ids to be in a predictable relationship
    /// with what's being displayed by the talking device list, which indices this by index. MOFO
    Vector<DeviceStatus> group = new Vector<>();

    private DeviceStatus get(MessageChannel c) {
        for(DeviceStatus d : group) {
            if(d.source == c) return d;
        }
        return null; // impossible most of the time
    }

    private void onSocketDead(Events.SocketDisconnected obj) {
        DeviceStatus dev = get(obj.which);
        if(dev == null) return; // impossible by construction...almost but might happen in some rare case if we parse a socket dead before a connect... uhm
        if(dev.lastMessage != null) {
            dev.lastMessage = String.format(getString(R.string.ohNo_talkingIsGone), dev.lastMessage);
            AlertDialog.Builder build = new AlertDialog.Builder(this);
            build.setMessage(dev.lastMessage);
            build.show();
        }
        else if(dev.chars.size() != 0) {
            String show = String.format(getString(R.string.ohNo_pgsAreGone), dev.names());

            AlertDialog.Builder build = new AlertDialog.Builder(this);
            build.setMessage(show);
            build.show();
        }
        // Else an unidentified has gone away. Not much of a problem.

        group.remove(obj.which);
        if(dev.lastMessage == null) onSilentCountChanged();
        else if(dev.chars.isEmpty()) {
            groupListAdapter.notifyDataSetChanged();
            onTalkingCountChanged();
        }
        else {
            /// TODO
            throw new UnsupportedOperationException("This is TODO!");
        }
    }

    private void onTalkingCountChanged() {
        updateCount(R.id.txt_talkingDeviceCounts, gathering.getTalkingCount());
    }

    private void onSilentCountChanged() {
        updateCount(R.id.txt_connectedDeviceCounts, gathering.getSilentCount());
    }

    private void updateCount(int targetID, int count) {
        TextView target = (TextView)findViewById(R.id.txt_connectedDeviceCounts);
        if(gathering == null) return; // impossible
        if(count == 0) target.setVisibility(View.INVISIBLE);
        else {
            String show = getString(count < 2? R.string.word_device_singular : R.string.word_device_plural);
            target.setText(String.format(getString(R.string.anonDeviceStats), count, show));
            target.setVisibility(View.VISIBLE);
        }
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
    public static final int MSG_SILENT_DEVICE_COUNT = 2;
    public static final int MSG_SOCKET_DEAD = 3;
    public static final int MSG_PEER_MESSAGE_UPDATED = 4;


    /** Compared to JoinGroupActivity.GroupListAdapter this is a bit different. Why?
     * The main issue is I use different types of items, the first line is an easygoing TextView
     * showing count of how many devices have connected but not identified yet.
     * Then, for every device there's a message. Messages come and go, devices come and go.
     * Every device can be kicked (action button) or accepted in the group (checkbox).
     */
    private class DeviceListAdapter extends RecyclerView.Adapter<DeviceListAdapter.DeviceViewHolder> {
        public DeviceListAdapter() {
            setHasStableIds(true);
        }

        protected class DeviceViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {
            public TextView msg;
            public CheckBox accepted;
            TextView details;
            MessageChannel key;

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
                kickDevice(key);
            }

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setGroupMember(key, isChecked);
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
            int count = 0;
            DeviceStatus dev = null;
            for(DeviceStatus d : group) {
                if(d.lastMessage != null) {
                    if(count == position) {
                        dev = d;
                        break;
                    }
                    count++;
                }
            }
            //if(dev == null) // crash
            holder.key = dev.source;
            holder.msg.setText(dev.lastMessage);
            holder.accepted.setChecked(dev.groupMember);
            String format = getString(R.string.joiningDeviceWithHello_details);
            format = String.format(format, dev.source.unique, dev.source.socket.getInetAddress().toString());
            holder.details.setText(format);
        }

        @Override
        public int getItemCount() {
            int count = 0;
            for(DeviceStatus d : group) {
                if(d.lastMessage != null) count++;
            }
            return count;
        }
    }

    private void setGroupMember(MessageChannel key, boolean isChecked) {

    }

    private void kickDevice(MessageChannel device) {
        AlertDialog.Builder build = new AlertDialog.Builder(this);
        build.setTitle("TODO: STUB")
                .setMessage("Kicking device is not currently implemented.");
        build.show();
    }
}

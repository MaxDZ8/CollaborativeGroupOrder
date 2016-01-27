package com.massimodz8.collaborativegrouporder;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.net.nsd.NsdManager;
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

import com.massimodz8.collaborativegrouporder.networkio.Events;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.networkio.ProtoBufferEnum;
import com.massimodz8.collaborativegrouporder.networkio.formingServer.GroupForming;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Enumeration;
import java.util.Vector;

/* This activity is started by the MainMenuActivity when the user wants to assemble a new party.
We publish a service and listen to network to find users joining.
 */
/** todo: this is an excellent moment to provide some ads: after the GM started scanning
 * he has to wait for users to join and I can push to him whatever I want.  */
public class CreatePartyActivity extends AppCompatActivity implements PlayingCharacterListAdapter.DataPuller {
    private GroupForming gathering;
    private RecyclerView.Adapter groupListAdapter, characterListAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_party);

        characterListAdapter = new PlayingCharacterListAdapter(this, PlayingCharacterListAdapter.MODE_SERVER_ACCEPTANCE);
        RecyclerView target = (RecyclerView) findViewById(R.id.pcList);
        target.setLayoutManager(new LinearLayoutManager(this));
        target.setAdapter(characterListAdapter);
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
        building = new Forming(gname);

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
            gathering = new GroupForming(gname, nsd, new MyHandler(this), MSG_SERVICE_REGISTRATION_COMPLETE) {
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

    static class MyHandler extends Handler {
        final WeakReference<CreatePartyActivity> target;

        public MyHandler(CreatePartyActivity target) {
            this.target = new WeakReference<>(target);
        }

        @Override
        public void handleMessage(Message msg) {
            final CreatePartyActivity target = this.target.get();
            switch(msg.what) {
                case MSG_SERVICE_REGISTRATION_COMPLETE: target.onNSRegistrationComplete((GroupForming.ServiceRegistrationResult) msg.obj); break;
                case MSG_SILENT_DEVICE_COUNT: target.onSilentCountChanged(); break;
                case MSG_SOCKET_DEAD: target.onSocketDead((Events.SocketDisconnected) msg.obj); break;
                case MSG_PEER_MESSAGE_UPDATED: target.messageUpdate((Events.PeerMessage) msg.obj); break;
                case MSG_CHARACTER_DEFINITION: target.characterUpdate((Events.CharacterDefinition)msg.obj); break;
            }
        }
    }

    private void characterUpdate(final Events.CharacterDefinition obj) {
        final String badThing = good(obj.character);
        if(badThing != null) {
            final CreatePartyActivity self = this;
            new AsyncTask<Void, Void, Exception>() {
                @Override
                protected Exception doInBackground(Void... params) {
                    Network.GroupFormed reject = new Network.GroupFormed();
                    reject.peerKey = obj.character.peerKey;
                    try {
                        obj.origin.writeSync(ProtoBufferEnum.GROUP_FORMED, reject);
                    } catch (IOException e) {
                        return e;
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(Exception e) {
                    if(e == null) return;
                    new AlertDialog.Builder(self)
                            .setMessage(e.getLocalizedMessage())
                            .show();
                }
            }.execute();
            return;
        }
        DeviceStatus owner = building.get(obj.origin);
        if(owner == null) return; // must have been pushed out of group and trying to make something odd.
        // Can this even happen? It's highly unlikely (a message must be sent by client before we disconnect it
        // but after it received a group forming.
        // That's not very likely to happen as disconnects are sent before we even promote message channels
        // but due to the async nature of things I am defensive.
        BuildingPlayingCharacter character = owner.getCharByKey(obj.character.peerKey);
        if(character == null) {
            character = new BuildingPlayingCharacter(obj.character.peerKey);
            character.experience = obj.character.experience;
            character.initiativeBonus = obj.character.initiativeBonus;
            character.name = obj.character.name;
            character.fullHealth = obj.character.healthPoints;
            owner.chars.add(character);
            characterListAdapter.notifyDataSetChanged();
            return;
        }
        if(character.status == BuildingPlayingCharacter.STATUS_ACCEPTED) {
            /*
            Cannot happen in current client implementation
            What do we do here? We must restart validation... but the protocol does not support anything like this at the time,
            especially because a confirm/reject message could already be flying... So what do I do?
            I consider the client malicious and ignore the thing. He will have a surprise when the group goes adventure later.
            */
            return;
        }
        /*
        If I am here then STATUS_RECEIVED, so I just update the thing.
        OFC there's the chance a player sends two messages in rapid succession (impossible in current client) resulting in the second spec
        being accepted instead of the first if the latter is received while the DM is hitting the accept button.
        This is borderline malicious, the players will have to sort it out when going adventure.
        However, for the sake of protocol flexibility, I currently allow that.
        */
        character.experience = obj.character.experience;
        character.initiativeBonus = obj.character.initiativeBonus;
        character.name = obj.character.name;
        owner.chars.add(character);
        characterListAdapter.notifyDataSetChanged();
    }

    private static String good(Network.PlayingCharacterDefinition def) {
        if(def.experience < 0) return "Bad character: experience must be positive.";
        if(def.healthPoints < 0) return "Bad character: health points must be positive.";
        if(def.name.isEmpty()) return "Bad character: must have a name.";
        return null;
    }

    private void messageUpdate(Events.PeerMessage msg) {
        DeviceStatus dst = building.get(msg.which);
        if(dst == null) {
            dst = new DeviceStatus(msg.which);
            dst.charBudget = GroupForming.INITIAL_CHAR_BUDGET;
            building.group.add(dst);
        }
        gathering.promoteSilent(dst.source, MSG_SOCKET_DEAD, MSG_PEER_MESSAGE_UPDATED);
        if(dst.charBudget < 1) return; // ignore
        final int len = msg.msg.length();
        dst.lastMessage = msg.msg.substring(0, len < dst.charBudget? len : dst.charBudget);
        dst.charBudget = dst.groupMember? CHAR_BUDGET_GROUP_MEMBER : CHAR_BUDGET_TALKING;
        groupListAdapter.notifyDataSetChanged();
        final Network.CharBudget credits = new Network.CharBudget();
        credits.total = dst.charBudget;
        credits.period = dst.groupMember? CHAR_BUDGET_DELAY_MS_GROUP_MEMBER : CHAR_BUDGET_DELAY_MS_TALKING;
        try {
            msg.which.writeSync(ProtoBufferEnum.CHAR_BUDGET, credits);
        } catch (IOException e) {
            /// TODO: figure out what to do in this case.
        }
        groupListAdapter.notifyDataSetChanged();
    }

    static final int CHAR_BUDGET_GROUP_MEMBER = 20;
    static final int CHAR_BUDGET_TALKING = 10;
    static final int CHAR_BUDGET_DELAY_MS_TALKING = 2000;
    static final int CHAR_BUDGET_DELAY_MS_GROUP_MEMBER = 500;

    static class DeviceStatus {
        public final MessageChannel source;
        public String lastMessage; // if null still not talking
        public int charBudget;
        public boolean groupMember;
        Vector<BuildingPlayingCharacter> chars = new Vector<>(); // if contains something we have been promoted
        String names() {
            String names = "";
            for(BuildingPlayingCharacter pc : chars) names += (names.length() != 0? ", " : "") + pc.name;
            return names;
        }

        public DeviceStatus(MessageChannel source) {
            this.source = source;
        }

        public BuildingPlayingCharacter getCharByKey(int key) {
            for(BuildingPlayingCharacter pc : chars) {
                if(pc.status == BuildingPlayingCharacter.STATUS_REJECTED) continue;
                if(pc.id == key) return pc;
            }
            return null;
        }
    }


    static class Forming {
        public Forming(String presentationName) {
            this.presentationName = presentationName;
        }

        public final String presentationName;
        public volatile byte[] unique;
        /// This must be a vector, not a map because we need the ids to be in a predictable relationship
        /// with what's being displayed by the talking device list, which indices this by index. MOFO
        Vector<DeviceStatus> group = new Vector<>();

        public DeviceStatus get(MessageChannel c) {
            for(DeviceStatus d : group) {
                if(d.source == c) return d;
            }
            return null; // impossible most of the time
        }
    }
    Forming building;



    private void onSocketDead(Events.SocketDisconnected obj) {
        DeviceStatus dev = building.get(obj.which);
        if(dev == null) {
            /* devices are added to list processed by get(MessageChannel) only after they produced a message as well so this is silent.
            Unfortunately, because of the way our stuff is mangled in a modular fashion we won't get a MSG_SILENT_DEVICE_COUNT (because the Pumper
            does not know about the extended semantics) so we trigger manually. */
            onSilentCountChanged();
            return;
        }
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

        building.group.remove(dev);
        if(dev.chars.isEmpty()) {
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
    public static final int MSG_CHARACTER_DEFINITION = 5;


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
            MessageChannel key;
            final Typeface original;

            public DeviceViewHolder(View itemView) {
                super(itemView);
                msg = (TextView)itemView.findViewById(R.id.card_joiningDevice_msg);
                accepted = (CheckBox)itemView.findViewById(R.id.card_joiningDevice_accepted);
                itemView.findViewById(R.id.card_joiningDevice_kick).setOnClickListener(this);
                accepted.setOnCheckedChangeListener(this);
                original = accepted.getTypeface();
            }

            @Override
            public void onClick(View v) {
                kickDevice(key);
            }

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                final int res = isChecked? R.string.card_joining_device_groupMemberCheck : R.string.joiningDeviceHello_acceptedCheckbox_nope;
                final int weight = isChecked? Typeface.BOLD : Typeface.NORMAL;
                accepted.setText(res);
                accepted.setTypeface(original, weight);
                setGroupMember(key, isChecked);
            }
        }

        @Override
        public long getItemId(int position) {
            int good = 0;
            for(DeviceStatus d : building.group) {
                if(d.lastMessage == null) continue;
                if(good == position) return d.source.unique;
                good++;
            }
            return RecyclerView.NO_ID;
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
            for(DeviceStatus d : building.group) {
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
        }

        @Override
        public int getItemCount() {
            int count = 0;
            for(DeviceStatus d : building.group) {
                if(d.lastMessage != null) count++;
            }
            return count;
        }
    }

    private void setGroupMember(MessageChannel key, boolean isChecked) {
        DeviceStatus dev = building.get(key);
        if(dev == null) return; // impossible
        dev.groupMember = isChecked;
        int count = 0;
        for(DeviceStatus d : building.group) {
            if(d.groupMember) count++;
        }
        findViewById(R.id.btn_closeGroup).setEnabled(count != 0);
    }

    private void kickDevice(final MessageChannel device) {
        AlertDialog.Builder build = new AlertDialog.Builder(this);
        build.setTitle(R.string.kickDevice_title)
                .setMessage(R.string.kickDevice_msg)
                .setPositiveButton(R.string.kickDevice_positive, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == AlertDialog.BUTTON_POSITIVE) {
                            gathering.kick(device);
                            building.group.remove(building.get(device));
                            groupListAdapter.notifyDataSetChanged();
                        }
                    }
                }).show();
    }

    public void createGroup(View button) {
        for(int clear = 0; clear < building.group.size(); clear++) {
            if(!building.group.elementAt(clear).groupMember) {
                building.group.remove(clear);
                clear--;
            }
        }
        MessageChannel[] good = new MessageChannel[building.group.size()];
        for(int cp = 0; cp < building.group.size(); cp++) good[cp] = building.group.elementAt(cp).source;
        try {
            gathering.promoteTalking(good, MSG_SOCKET_DEAD, MSG_CHARACTER_DEFINITION);
        } catch (IOException e) {
            // Sockets couldn't be released. It doesn't matter: I'm not following those guys.
        }

        // Now boring stuff, all the UI so far must go.
        int[] gone = new int[] {
                R.id.progressBar, R.id.txt_connectedDeviceCounts, R.id.txt_talkingDeviceCounts,
                R.id.groupList, R.id.btn_closeGroup, R.id.txt_FYI_port, R.id.txt_scanning
        };
        for(int id : gone) findViewById(id).setVisibility(View.GONE);

        final CreatePartyActivity self = this;
        new AsyncTask<Void, Void, Exception>() {
            @Override
            protected Exception doInBackground(Void... params) {
                final int groupCount = 0;
                /// TODO: Let's load the group list and count how many groups we have.
                final Date creation = new Date();
                final String message = String.format("[%1$d] %2$s", groupCount, creation.toString());
                MessageDigest hasher = null;
                try {
                    hasher = MessageDigest.getInstance("SHA-256");
                } catch (NoSuchAlgorithmException e) {
                    return e;
                }

                Network.GroupFormed forming = new Network.GroupFormed();
                forming.salt = hasher.digest(message.getBytes());
                building.unique = forming.salt;
                int failures = 0;
                for(DeviceStatus dev : building.group) {
                    try {
                        dev.source.writeSync(ProtoBufferEnum.GROUP_FORMED, forming);
                    } catch (IOException e) {
                        failures++;
                    }
                }
                if(failures != 0) return new Exception(String.format(getString(R.string.atLeastOneFailedWriteSync)));
                return null;
            }

            @Override
            protected void onPostExecute(Exception e) {
                if(e != null) {
                    new AlertDialog.Builder(self)
                            .setMessage(String.format(getString(R.string.createPartyActivity_failedPCDefinitionSwitch), e.toString()))
                            .show();
                    return;
                }
                findViewById(R.id.pcList).setVisibility(View.VISIBLE);
                findViewById(R.id.createPartyActivity_definingCharactersFeedback).setVisibility(View.VISIBLE);
                final String localized = getString(R.string.phaseDefiningCharacters);
                final ActionBar actionBar = self.getSupportActionBar();
                if(actionBar != null) actionBar.setTitle(String.format("%1$s - %2$s", building.presentationName, localized));
            }
        }.execute();
    }

    //
    // PlayingCharacterListAdapter.DataPuller ______________________________________________________
    @Override
    public int getVisibleCount() {
        if(building == null) return 0;
        int count = 0;
        final int filter = BuildingPlayingCharacter.STATUS_REJECTED;
        for(DeviceStatus dev : building.group) {
            for(BuildingPlayingCharacter pc : dev.chars) {
                if(filter != pc.status) count++;
            }
        }
        return count;
    }

    @Override
    public void action(BuildingPlayingCharacter who, int what) {
        who.status = what == PlayingCharacterListAdapter.ACCEPT? BuildingPlayingCharacter.STATUS_ACCEPTED : BuildingPlayingCharacter.STATUS_REJECTED;
        characterListAdapter.notifyDataSetChanged();
    }

    @Override
    public AlertDialog.Builder makeDialog() {
        return new AlertDialog.Builder(this);
    }

    @Override
    public View inflate(int resource, ViewGroup root, boolean attachToRoot) {
        return getLayoutInflater().inflate(resource, root, attachToRoot);
    }

    @Override
    public BuildingPlayingCharacter get(int position) {
        if(building == null) return null;
        int count = 0;
        final int filter = BuildingPlayingCharacter.STATUS_REJECTED;
        for(DeviceStatus dev : building.group) {
            for(BuildingPlayingCharacter pc : dev.chars) {
                if(filter == pc.status) continue;
                if(position == count) return pc;
                count++;
            }
        }
        return null;
    }

    @Override
    public long getStableId(int position) {
        if(building == null) return RecyclerView.NO_ID;
        long stable = 0;
        for(DeviceStatus dev : building.group) {
            for(BuildingPlayingCharacter pc : dev.chars) stable++;
        }
        return stable;
    }
}

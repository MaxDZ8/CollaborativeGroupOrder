package com.massimodz8.collaborativegrouporder.networkio.formingServer;

import android.content.DialogInterface;
import android.graphics.Typeface;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.BuildingPlayingCharacter;
import com.massimodz8.collaborativegrouporder.PlayingCharacterListAdapter;
import com.massimodz8.collaborativegrouporder.R;
import com.massimodz8.collaborativegrouporder.networkio.LandingServer;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.networkio.ProtoBufferEnum;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;

import java.io.IOException;
import java.net.ServerSocket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Vector;

/**
 * Created by Massimo on 14/01/2016.
 * Help CreatePartyActivity by shuffling connections around, maintaining state and providing
 * higher level GUI hooks with no thread hazards.
 */
public abstract class GroupForming extends RecyclerView.Adapter<GroupForming.DeviceViewHolder> implements NsdManager.RegistrationListener, PlayingCharacterListAdapter.DataPuller {
    protected abstract void onFailedAccept();
    protected abstract void refreshSilentCount(int currently);
    protected abstract void refreshCharacterData();
    protected abstract void onGroupMemberChange(int currentCount);

    public String getUserName() {
        return userName;
    }

    private String userName;
    public final Handler handler;
    public int serviceRegistrationCompleteCode;

    public void shutdown() throws IOException {
        if(forming != null) forming.shutdown();
        if(talking != null) talking.shutdown();
        if(silent != null) silent.shutdown();
        if(nsdUnregister) nsd.unregisterService(this);
        if(acceptor != null) acceptor.shutdown();
        new Thread() {
            @Override
            public void run() {
                try {
                    landing.close();
                } catch (IOException e) {
                    // If it dies, we just give up and crash.
                }
            }
        }.start();
    }

    public void kick(MessageChannel device) {
        if(forming != null) forming.silentShutdown(device);
        if(talking != null) talking.silentShutdown(device);
        if(silent != null) silent.silentShutdown(device);
        final DeviceStatus status = get(device);
        status.kicked = true;
        notifyDataSetChanged();
    }

    public void update(MessageChannel origin, Network.PlayingCharacterDefinition definition) {
        DeviceStatus owner = get(origin);
        if(owner == null) return; // must have been pushed out of group and trying to make something odd.
        // Can this even happen? It's highly unlikely (a message must be sent by client before we disconnect it
        // but after it received a group forming.
        // That's not very likely to happen as disconnects are sent before we even promote message channels
        // but due to the async nature of things I am defensive.
        // Remember the client reuses keys and characters whatever possible, we reuse them as well.
        BuildingPlayingCharacter character = null;
        for(BuildingPlayingCharacter pc : owner.chars) {
            if(pc.id == definition.peerKey) {
                character = pc;
                break;
            }
        }
        boolean add = character == null;
        if(character == null) character = new BuildingPlayingCharacter(definition.peerKey);
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
        If I am here then STATUS_BUILDING, so I just update the thing.
        OFC there's the chance a player sends two messages in rapid succession (impossible in current client) resulting in the second spec
        being accepted instead of the first if the latter is received while the DM is hitting the accept button.
        This is borderline malicious, the players will have to sort it out when going adventure.
        However, for the sake of protocol flexibility, I currently allow that.
        */
        character.status = BuildingPlayingCharacter.STATUS_BUILDING; // not really, it could be previously rejected.
        character.experience = definition.experience;
        character.initiativeBonus = definition.initiativeBonus;
        character.name = definition.name;
        character.fullHealth = definition.healthPoints;
        if(add) owner.chars.add(character);
        refreshCharacterData();
    }


    public void updateDeviceMessage(MessageChannel which, String msg) {
        DeviceStatus dst = get(which);
        if(dst == null) {
            dst = new DeviceStatus(which);
            dst.charBudget = INITIAL_CHAR_BUDGET;
            clients.add(dst);
        }
        promoteSilent(dst.source);
        if(dst.charBudget < 1) return; // ignore
        final int len = msg.length();
        dst.lastMessage = msg.substring(0, len < dst.charBudget? len : dst.charBudget);
        dst.charBudget = dst.groupMember? CHAR_BUDGET_GROUP_MEMBER : CHAR_BUDGET_TALKING;
        final Network.CharBudget credits = new Network.CharBudget();
        credits.total = dst.charBudget;
        credits.period = dst.groupMember? CHAR_BUDGET_DELAY_MS_GROUP_MEMBER : CHAR_BUDGET_DELAY_MS_TALKING;
        try {
            which.writeSync(ProtoBufferEnum.CHAR_BUDGET, credits);
        } catch (IOException e) {
            /// TODO: figure out what to do in this case.
        }
        notifyDataSetChanged();
    }

    public int countAcceptedCharacters() {
        int count = 0;
        for(DeviceStatus dev : clients) {
            if(dev.kicked) continue;
            for(BuildingPlayingCharacter pc : dev.chars) {
                if(BuildingPlayingCharacter.STATUS_ACCEPTED == pc.status) count++;
            }
        }
        return count;
    }

    public int countGroupDevices() {
        int count = 0;
        for(DeviceStatus dev : clients) {
            if(dev.groupMember && !dev.kicked) count++;
        }
        return count;
    }

    public int countTalkingDevices() {
        int count = 0;
        for(DeviceStatus dev : clients) {
            if(!dev.kicked) count++;
        }
        return count;
    }

    public void setGroupMember(MessageChannel key, boolean isChecked) {
        DeviceStatus dev = get(key);
        if(dev == null) return; // impossible
        dev.groupMember = isChecked;
    }

    public byte[] buildKey() throws NoSuchAlgorithmException {
        final String message = String.format("counting=%1$d name=\"%2$s\" created=%3$s", countGroupDevices(), userName, new Date().toString());
        MessageDigest hasher = MessageDigest.getInstance("SHA-256");
        return hasher.digest(message.getBytes());
    }

    public int broadcast(int typeCode, MessageNano payload) {
        int fail = 0;
        for(DeviceStatus dev : clients) {
            try {
                dev.source.writeSync(typeCode, payload);
            } catch (IOException e) {
                fail++;
            }
        }
        return fail;
    }

    public String getMessage(MessageChannel which) {
        for(DeviceStatus stat : clients) {
            if(which == stat.source) return stat.lastMessage;
        }
        return null;
    }

    public String getCharNames(MessageChannel which) {
        String names = null;
        for(DeviceStatus stat : clients) {
            if(which == stat.source) {
                if(stat.chars.isEmpty()) break;
                names = "";
                for(BuildingPlayingCharacter pc : stat.chars) names += (names.length() != 0? ", " : "") + pc.name;
                break;
            }
        }
        return names;
    }


    public static class ServiceRegistrationResult {
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

    public GroupForming(Handler handler) {
        setHasStableIds(true);
        this.handler = handler;
    }

    public boolean publish(String userName, NsdManager nsd, int serviceRegistrationCompleteCode) {
        try {
            landing = new ServerSocket(0);
        } catch (IOException e) {
            return false;
        }
        this.userName = userName;
        this.nsd = nsd;
        this.serviceRegistrationCompleteCode = serviceRegistrationCompleteCode;

        NsdServiceInfo servInfo  = new NsdServiceInfo();
        servInfo.setServiceName(userName);
        servInfo.setServiceType(SERVICE_TYPE);
        servInfo.setPort(getLocalPort());
        nsd.registerService(servInfo, NsdManager.PROTOCOL_DNS_SD, this);
        return true;
    }

    public int getLocalPort() { return landing.getLocalPort(); }


    /// Start forming a group by starting a listen server and accepting connections.
    public void begin(int disconnect, int peerMessage) throws IOException {
        disconnectCode = disconnect;
        peerMessageCode = peerMessage;
        acceptor = new LandingServer(landing, new LandingServer.Callbacks() {
            @Override
            public void failedAccept() { onFailedAccept(); }

            @Override
            public void connected(MessageChannel newComer) {
                if(silent != null) {
                    silent.pump(newComer);
                    refreshSilentCount(silent.getClientCount());
                }
                else {
                    try {
                        newComer.socket.close();
                    } catch (IOException e) {
                        // nothing to do here really, IDK. The dude was late to the party.
                        // Very unlikely to happen.
                    }
                }
            }
        });
        silent = new SilentDevices(handler, disconnect, peerMessage, userName, INITIAL_CHAR_BUDGET, INITIAL_CHAR_DELAY);
    }

    /// Listen for the various events, do your mangling and promote peers to a different stage.
    /// When not matched the function call is NOP, so you can just call this blindly.
    public boolean promoteSilent(MessageChannel peer) {
        if(talking == null) talking = new TalkingDevices(handler, disconnectCode, peerMessageCode);
        if(silent.yours(peer)) {
            talking.pump(peer, silent.move(peer));
            return true;
        }
        return false;
    }

    /// Brings a talking devices to group forming phase and terminates connections to others.
    public void makeDeviceGroup(int definedCharacter, byte[] key) {
        uniqueKey = key;
        for(int clear = 0; clear < clients.size(); clear++) {
            final DeviceStatus el = clients.elementAt(clear);
            if(!el.groupMember || el.kicked) {
                if(silent != null) silent.silentShutdown(el.source);
                if(talking != null) talking.silentShutdown(el.source);
                clients.remove(clear);
                clear--;
            }
        }
        if(forming == null) forming = new PCDefiningDevices(handler, disconnectCode, definedCharacter);
        acceptor.shutdown();
        acceptor = null;
        final ServerSocket capture = landing;
        new Thread() {
            @Override
            public void run() {
                try {
                    capture.close();
                } catch (IOException e) {
                    // well, nothing. Just leak it. Maybe we can at least finish defining group.
                }
            }
        }.start();

        for(DeviceStatus c : clients) forming.pump(c.source, talking.move(c.source));
        try {
            silent.shutdown();
        } catch (IOException e) {
            // no problem, the thread pumps are gone anyway so the sockets are dead.
        }
        silent = null;
        try {
            talking.shutdown();
        } catch (IOException e) {
            // no problem, the thread pumps are gone anyway so the sockets are dead.
        }
        talking = null;
        nsd.unregisterService(this);
        nsdUnregister = false;
    }

    public int getSilentCount() {
        if(silent == null) return 0;
        return silent.getClientCount();
    }

    public int getTalkingCount() {
        if(talking == null) return 0;
        return talking.getClientCount();
    }

    //
    // NsdManager.RegistrationListener() ___________________________________________________________
    @Override
    public void onServiceRegistered(NsdServiceInfo info) {
        final String netName = info.getServiceName();
        Message msg = Message.obtain(handler, serviceRegistrationCompleteCode, new ServiceRegistrationResult(netName));
        handler.sendMessage(msg);
    }

    @Override
    public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
        Message msg = Message.obtain(handler, serviceRegistrationCompleteCode, new ServiceRegistrationResult(errorCode));
        handler.sendMessage(msg);
    }

    @Override
    public void onServiceUnregistered(NsdServiceInfo arg0) { }

    @Override
    public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) { }

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
        for(GroupForming.DeviceStatus d : clients) {
            if(d.source == c) return d;
        }
        return null; // impossible most of the time
    }

    static final int INITIAL_CHAR_BUDGET = 20;
    static final int INITIAL_CHAR_DELAY = 2000;
    static final int CHAR_BUDGET_GROUP_MEMBER = 20;
    static final int CHAR_BUDGET_TALKING = 10;
    static final int CHAR_BUDGET_DELAY_MS_TALKING = 2000;
    static final int CHAR_BUDGET_DELAY_MS_GROUP_MEMBER = 500;
    public static final String SERVICE_TYPE = "_formingGroupInitiative._tcp";

    SilentDevices silent;
    TalkingDevices talking;
    PCDefiningDevices forming;
    int disconnectCode, peerMessageCode;

    ServerSocket landing;
    NsdManager nsd;
    boolean nsdUnregister = true;
    LandingServer acceptor;

    Vector<DeviceStatus> clients = new Vector<>();
    byte[] uniqueKey;


    //
    // RecyclerView.Adapter<DeviceListAdapter.DeviceViewHolder> ____________________________________
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
            makeDialog()
                    .setTitle(R.string.kickDevice_title)
                    .setMessage(R.string.kickDevice_msg)
                    .setPositiveButton(R.string.kickDevice_positive, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (which == AlertDialog.BUTTON_POSITIVE) kick(key);
                        }
                    }).show();
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            final int res = isChecked? R.string.card_joining_device_groupMemberCheck : R.string.joiningDeviceHello_acceptedCheckbox_nope;
            final int weight = isChecked? Typeface.BOLD : Typeface.NORMAL;
            accepted.setText(res);
            accepted.setTypeface(original, weight);
            setGroupMember(key, isChecked);
            notifyDataSetChanged();
            onGroupMemberChange(countGroupDevices());
        }
    }


    @Override
    public long getItemId(int position) {
        int good = 0;
        for(DeviceStatus d : clients) {
            if(d.lastMessage == null) continue;
            if(good == position) return d.source.unique;
            good++;
        }
        return RecyclerView.NO_ID;
    }

    @Override
    public DeviceViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View layout = inflate(R.layout.card_joining_device, parent, false);
        return new DeviceViewHolder(layout);
    }

    @Override
    public void onBindViewHolder(DeviceViewHolder holder, int position) {
        int count = 0;
        DeviceStatus dev = null;
        for(DeviceStatus d : clients) {
            if(d.lastMessage != null) {
                if(count == position) {
                    dev = d;
                    break;
                }
                count++;
            }
        }
        if(dev == null) return; // impossible but make lint happy
        holder.key = dev.source;
        holder.msg.setText(dev.lastMessage);
        holder.accepted.setChecked(dev.groupMember);
    }

    @Override
    public int getItemCount() {
        return countTalkingDevices();
    }

    //
    // PlayingCharacterListAdapter.DataPuller ______________________________________________________
    @Override
    public int getVisibleCount() {
        int count = 0;
        final int filter = BuildingPlayingCharacter.STATUS_REJECTED;
        for(DeviceStatus dev : clients) {
            if(dev.kicked) continue;
            for(BuildingPlayingCharacter pc : dev.chars) {
                if(filter != pc.status) count++;
            }
        }
        return count;
    }

    @Override
    public void action(final BuildingPlayingCharacter who, int what) {
        final int previously = who.status;
        who.status = what == PlayingCharacterListAdapter.ACCEPT? BuildingPlayingCharacter.STATUS_ACCEPTED : BuildingPlayingCharacter.STATUS_REJECTED;

        DeviceStatus owner = null;
        for(DeviceStatus d : clients) { // if it's rejected it isn't visualized but keys are keys and objects are objects so...
            for(BuildingPlayingCharacter pc : d.chars)
                if(pc == who) {
                    owner = d;
                    break;
                }
        }
        if(owner == null) return; // Impossible (for the time being)
        final DeviceStatus capture = owner;
        final GroupForming self = this;
        new AsyncTask<Void, Void, Exception>() {
            @Override
            protected Exception doInBackground(Void... params) {
                Network.GroupFormed msg = new Network.GroupFormed();
                msg.peerKey = who.id;
                msg.accepted = BuildingPlayingCharacter.STATUS_ACCEPTED == who.status;
                try {
                    capture.source.writeSync(ProtoBufferEnum.GROUP_FORMED, msg);
                } catch (IOException e) {
                    return e;
                }
                return null;
            }

            @Override
            protected void onPostExecute(Exception e) {
                if(e != null) {
                    self.makeDialog()
                            .setMessage(String.format(getString(R.string.createPartyActivity_failedAcceptRejectReplySend), who.name, e.getLocalizedMessage()))
                            .show();
                    who.status = previously;
                }
                self.refreshCharacterData();
            }
        }.execute();
    }

    @Override
    public BuildingPlayingCharacter get(int position) {
        int count = 0;
        for(DeviceStatus dev : clients) {
            if(dev.kicked) continue;
            for(BuildingPlayingCharacter pc : dev.chars) {
                if(BuildingPlayingCharacter.STATUS_REJECTED == pc.status) continue;
                if(position == count) return pc;
                count++;
            }
        }
        return null;
    }

    @Override
    public long getStableId(int position) {
        long stable = 0;
        for(DeviceStatus dev : clients) {
            if(dev.kicked) stable += dev.chars.size();
            else {
                for(BuildingPlayingCharacter pc : dev.chars) {
                    if(BuildingPlayingCharacter.STATUS_REJECTED != pc.status) stable++;
                }
            }
        }
        return stable;
    }
}

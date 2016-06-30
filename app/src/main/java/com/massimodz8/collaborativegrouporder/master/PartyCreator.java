package com.massimodz8.collaborativegrouporder.master;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.ViewGroup;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.protobuf.nano.CodedInputByteBufferNano;
import com.google.protobuf.nano.CodedOutputByteBufferNano;
import com.massimodz8.collaborativegrouporder.AsyncActivityLoadUpdateTask;
import com.massimodz8.collaborativegrouporder.AsyncLoadUpdateTask;
import com.massimodz8.collaborativegrouporder.BuildingPlayingCharacter;
import com.massimodz8.collaborativegrouporder.MaxUtils;
import com.massimodz8.collaborativegrouporder.PersistentDataUtils;
import com.massimodz8.collaborativegrouporder.RunningServiceHandles;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.networkio.ProtoBufferEnum;
import com.massimodz8.collaborativegrouporder.networkio.Pumper;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;
import com.massimodz8.collaborativegrouporder.protocol.nano.RPGClass;
import com.massimodz8.collaborativegrouporder.protocol.nano.Session;
import com.massimodz8.collaborativegrouporder.protocol.nano.StartData;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

public class PartyCreator extends PublishAcceptHelper {
    public static final String PARTY_FORMING_SERVICE_TYPE = "_formingGroupInitiative._tcp";
    public static final int MODE_MAKE_NEW_PARTY = 0;
    public static final int MODE_ADD_NEW_DEVICES_TO_EXISTING = 1;

    /*
    generated by calling saveParty(), to be used after saving completed to add it to the internal
    representation so you can sync with ease and no storage IO. You most likely just want do append
    to this.defs.
    When saveParty() is called, it will generate this by calling makeGroup() unless a group is
    already there, in which case it will use the existing group.
    */
    public StartData.PartyOwnerData.Group generatedParty;
    public Session.Suspended generatedStat;
    public int mode = MODE_MAKE_NEW_PARTY;

    public String newPartyName;
    public int advancementPace;

    public interface OnTalkingDeviceCountListener {
        void currentlyTalking(int count);
    }
    public OnTalkingDeviceCountListener onTalkingDeviceCountChanged;

    public PartyCreator(Context context) {
        this.context = context;
    }

    /**
     * Initializes construction of a new party with the given name. Construction starts if
     * no collisions are detected. Only to be called once.
     * @return null, or a list containing at least 1 element.
     */
    public @Nullable ArrayList<StartData.PartyOwnerData.Group> beginBuilding(String unknownDeviceName) {
        ArrayList<StartData.PartyOwnerData.Group> collisions = null;
        if(mode != MODE_ADD_NEW_DEVICES_TO_EXISTING) { // if == already validated.
            ArrayList<StartData.PartyOwnerData.Group> defs = RunningServiceHandles.getInstance().state.data.groupDefs;
            for (StartData.PartyOwnerData.Group match : defs) {
                if (match.name.equals(newPartyName)) {
                    if (null == collisions) collisions = new ArrayList<>();
                    collisions.add(match);
                }
            }
        }
        this.unknownDeviceName = unknownDeviceName;
        if(null == collisions) building = new PartyDefinitionHelper(newPartyName, advancementPace) {
            @Override
            protected void onMessageChanged(DeviceStatus owner) {
                if(null != clientDeviceAdapter) clientDeviceAdapter.notifyDataSetChanged();
            }

            @Override
            protected void onGone(MessageChannel which, Exception reason) {
                if(reason == null) return;
                // For the time being, I remove it. Maybe in the future I'll do it differently.
                building.kick(which, false);
                building.netPump.move(which);

            }

            @Override
            protected void onDetached(MessageChannel which) { /* never happens */ }

            @Override
            protected void onTalkingDeviceCountChanged(int currently) {
                if(null != onTalkingDeviceCountChanged) onTalkingDeviceCountChanged.currentlyTalking(currently);
            }
        };
        return collisions;
    }

    public @Nullable String getBuildingPartyName() {
        if(mode == MODE_ADD_NEW_DEVICES_TO_EXISTING) return generatedParty.name;
        return building == null? null : building.name;
    }

    /**
     * Kicking a device by dropping its TCP isn't a very smart thing to do as the device will be
     * free to reconnect and I don't want to check its IP, which is not reliable anyway!
     * Most of the time, a soft kick is best kick: just silence the dude and discard input.
     * Disconnection is better left to after the listening service has been shut down, maybe using
     * kickNonMembers.
     */
    public void kick(MessageChannel pipe, boolean soft) {
        building.kick(pipe, soft);
    }

    public void setVisible(MessageChannel pipe) {
        if(building.setVisible(pipe) && clientDeviceAdapter != null) clientDeviceAdapter.notifyDataSetChanged();
    }

    @NonNull
    public ArrayList<PartyDefinitionHelper.DeviceStatus> getDevices(boolean kicked) {
        ArrayList<PartyDefinitionHelper.DeviceStatus> dev = new ArrayList<>(building.clients.size());
        for (PartyDefinitionHelper.DeviceStatus check : building.clients) {
            if(check.kicked == kicked) dev.add(check);
        }
        return dev;
    }

    public void kickNonMembers() { building.kickNonMembers(); }

    public interface OnKeysSentListener {
        void onKeysSent(int errors);
    }


    public void closeGroup(@NonNull final OnKeysSentListener onComplete) {
        stopPublishing();
        stopListening(false);
        kickNonMembers();
        final Network.GroupFormed form = new Network.GroupFormed();
        final ArrayList<PartyDefinitionHelper.DeviceStatus> clients = getDevices(false);
        int keyCount = mode == MODE_ADD_NEW_DEVICES_TO_EXISTING? generatedParty.devices.length : 0;
        for (PartyDefinitionHelper.DeviceStatus dev : clients) {
            final String message = String.format(Locale.ENGLISH, "keyIndex=%1$d, name=\"%2$s\" created=%3$s", keyCount++, building, new Date().toString());
            MaxUtils.hasher.reset();
            dev.salt = MaxUtils.hasher.digest(message.getBytes());
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
                onComplete.onKeysSent(bad);
            }
        }.execute();
    }

    public interface ClientDeviceHolderFactoryBinder<VH extends RecyclerView.ViewHolder> {
        VH createUnbound(ViewGroup parent, int viewType);
        void bind(@NonNull VH target, @NonNull PartyDefinitionHelper.DeviceStatus deviceName);
    }

    public <VH extends RecyclerView.ViewHolder> RecyclerView.Adapter<VH> setNewClientDevicesAdapter(@Nullable ClientDeviceHolderFactoryBinder<VH> factory) {
        ClientDeviceAdapter<VH> gen = null;
        if(factory != null) gen = new ClientDeviceAdapter<>(factory);
        clientDeviceAdapter = gen;
        return gen;
    }

    /**
     * @return New membership status. True is party member.
     */
    public boolean toggleMembership(MessageChannel key) {
        for (PartyDefinitionHelper.DeviceStatus dev : building.clients) {
            if(dev.source == key) {
                dev.groupMember = !dev.groupMember;
                if(clientDeviceAdapter != null) clientDeviceAdapter.notifyDataSetChanged();
                return dev.groupMember;
            }
        }
        return false;
    }

    public int getMemberCount() {
        int count = 0;
        for (PartyDefinitionHelper.DeviceStatus dev : building.clients) {
            if(dev.groupMember && !dev.kicked) count++;
        }
        return count;
    }

    public int getDeviceCount(boolean kicked) {
        int count = 0;
        for (PartyDefinitionHelper.DeviceStatus dev : building.clients) {
            if(dev.kicked == kicked) count++;
        }
        return count;
    }

    public void shutdown() {
        stopListening(true);
        stopPublishing();
        if(building != null) {
            final Pumper.MessagePumpingThread[] away = building.netPump.move();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    for (Pumper.MessagePumpingThread worker : away) {
                        worker.interrupt();
                        try {
                            worker.getSource().socket.close();
                        } catch (IOException e) {
                            // suppress
                        }
                    }
                }
            });
        }
    }

    public AsyncActivityLoadUpdateTask<StartData.PartyOwnerData> saveParty(final @NonNull Activity activity, @NonNull AsyncLoadUpdateTask.Callbacks cb) {
        return new AsyncActivityLoadUpdateTask<StartData.PartyOwnerData>(PersistentDataUtils.MAIN_DATA_SUBDIR, PersistentDataUtils.DEFAULT_GROUP_DATA_FILE_NAME, "groupList-", activity, cb) {
            @Override
            protected void appendNewEntry(StartData.PartyOwnerData loaded) {
                if(mode == MODE_ADD_NEW_DEVICES_TO_EXISTING) { // everything is already there but I must generate the new keys.
                    int devCount = 0, pcCount = 0;
                    for(PartyDefinitionHelper.DeviceStatus dev : building.clients) {
                        if(dev.kicked || !dev.groupMember) continue;
                        devCount++; // save all devices, even if they don't have proposed a pg
                        for (BuildingPlayingCharacter pc : dev.chars) {
                            if(pc.status != BuildingPlayingCharacter.STATUS_ACCEPTED) continue;
                            pcCount++;
                        }
                    }
                    {
                        int previously = generatedParty.devices.length;
                        StartData.PartyOwnerData.DeviceInfo[] longer = Arrays.copyOf(generatedParty.devices, previously + devCount);
                        System.arraycopy(generatedParty.devices, 0, longer, 0, previously);
                        devCount = previously;
                        for (PartyDefinitionHelper.DeviceStatus dev : building.clients) {
                            longer[devCount++] = from(dev);
                        }
                        generatedParty.devices = longer;
                    }
                    {
                        int previously = generatedParty.devices.length;
                        StartData.ActorDefinition[] longer = Arrays.copyOf(generatedParty.party, previously + pcCount);
                        pcCount = previously;
                        for(PartyDefinitionHelper.DeviceStatus dev : building.clients) {
                            if(dev.kicked || !dev.groupMember) continue;
                            devCount++; // save all devices, even if they don't have proposed a pg
                            for (BuildingPlayingCharacter pc : dev.chars) {
                                if(pc.status != BuildingPlayingCharacter.STATUS_ACCEPTED) continue;
                                longer[pcCount++] = from(pc);
                            }
                        }
                        generatedParty.party = longer;
                    }
                    return;
                }
                StartData.PartyOwnerData.Group[] longer = new StartData.PartyOwnerData.Group[loaded.everything.length + 1];
                System.arraycopy(loaded.everything, 0, longer, 0, loaded.everything.length);
                if(null == generatedParty) generatedParty = makeGroup(activity.getFilesDir());
                longer[loaded.everything.length] = generatedParty;
                loaded.everything = longer;
            }

            @Override
            protected void setVersion(StartData.PartyOwnerData result) {
                result.version = PersistentDataUtils.OWNER_DATA_VERSION;
            }

            @Override
            protected void upgrade(PersistentDataUtils helper, StartData.PartyOwnerData result) {
                helper.upgrade(result);
            }

            @Override
            protected ArrayList<String> validateLoadedDefinitions(PersistentDataUtils helper, StartData.PartyOwnerData result) {
                return helper.validateLoadedDefinitions(result);
            }

            @Override
            protected StartData.PartyOwnerData allocate() {
                return new StartData.PartyOwnerData();
            }
        };
    }

    public void approve(int unique) {
        building.approve(unique);
    }

    public void reject(int unique) {
        building.reject(unique);
    }

    public boolean isApproved(int unique) {
        return building.isApproved(unique);
    }

    public AsyncTask<Void, Void, Void> sendPartyCompleteMessages(final boolean goAdventuring, final @NonNull Runnable onComplete) {
        final int sillyDelayMS = 250; // make sure the messages go through. Yeah, I should display a progress w/e
        return new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                try {
                    Network.PhaseControl byebye = new Network.PhaseControl();
                    byebye.type = Network.PhaseControl.T_NO_MORE_DEFINITIONS;
                    byebye.terminated = !goAdventuring;
                    for (PartyDefinitionHelper.DeviceStatus dev : building.clients) {
                        try {
                            dev.source.writeSync(ProtoBufferEnum.PHASE_CONTROL, byebye);
                            dev.source.socket.getOutputStream().flush();
                        } catch (IOException e) {
                            // we try.
                        }
                    }
                    Thread.sleep(sillyDelayMS);
                } catch (InterruptedException e) {
                    // Sorry dudes, we're going down anyway.
                }

                int count = 0;
                for (PartyDefinitionHelper.DeviceStatus dev : building.clients) {
                    if(!dev.groupMember || dev.kicked) continue;
                    for (BuildingPlayingCharacter pc : dev.chars) {
                        if(pc.status == BuildingPlayingCharacter.STATUS_ACCEPTED) count++;
                    }
                }
                for (BuildingPlayingCharacter pc : building.localChars) {
                    if(pc.status == BuildingPlayingCharacter.STATUS_ACCEPTED) count++;
                }
                Bundle bundle = new Bundle();
                bundle.putBoolean(MaxUtils.FA_PARAM_GOING_ADVENTURE, goAdventuring);
                bundle.putInt(MaxUtils.FA_PARAM_KNOWN_PC_COUNT, count);
                FirebaseAnalytics.getInstance(context).logEvent(MaxUtils.FA_EVENT_PARTY_COMPLETED, bundle);
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                onComplete.run();
            }
        };
    }

    /**
     * Note this returns everything that is 'connected', you probably want to purge
     * @return non-null means we 'go adventuring', eventually with no connected peers.
     */
    public @Nullable Pumper.MessagePumpingThread[] moveClients() {
        final ArrayList<PartyDefinitionHelper.DeviceStatus> validish = getDevices(false);
        Pumper.MessagePumpingThread[] result = new Pumper.MessagePumpingThread[validish.size()];
        for(int loop = 0; loop < result.length; loop++) {
            result[loop] = building.netPump.move(validish.get(loop).source);
        }
        return result;
    }


    PartyDefinitionHelper building;

    private RecyclerView.Adapter clientDeviceAdapter;


    private class ClientDeviceAdapter<VH extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<VH> {
        private final ClientDeviceHolderFactoryBinder<VH> factory;

        ClientDeviceAdapter(@NonNull ClientDeviceHolderFactoryBinder<VH> factory) {
            this.factory = factory;
        }

        @Override
        public long getItemId(int position) {
            int good = 0;
            for (PartyDefinitionHelper.DeviceStatus d : building.clients) {
                if (d.lastMessage == null) continue;
                if (good == position) return d.source.unique;
                good++;
            }
            return RecyclerView.NO_ID;
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            return factory.createUnbound(parent, viewType);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            int count = 0;
            PartyDefinitionHelper.DeviceStatus dev = null;
            for (PartyDefinitionHelper.DeviceStatus d : building.clients) {
                if (d.lastMessage != null && !d.kicked) {
                    if (count == position) {
                        dev = d;
                        break;
                    }
                    count++;
                }
            }
            if (dev == null) return; // impossible but make lint happy
            factory.bind(holder, dev);
        }

        @Override
        public int getItemCount() {
            int count = 0;
            for (PartyDefinitionHelper.DeviceStatus d : building.clients) {
                if (d.lastMessage != null && !d.kicked) count++;
            }
            return count;
        }
    }

    public String getDeviceNameByKey(MessageChannel pipe) {
        for (PartyDefinitionHelper.DeviceStatus dev : building.clients) {
            if(dev.source == pipe) return dev.name;
        }
        return String.format(unknownDeviceName, -1);
    }


    private StartData.PartyOwnerData.Group makeGroup(File filesDir) {
        StartData.PartyOwnerData.Group ret = new StartData.PartyOwnerData.Group();
        ret.created = new com.google.protobuf.nano.Timestamp();
        ret.created.seconds = System.currentTimeMillis() / 1000;
        ret.sessionFile = PersistentDataUtils.makeInitialSession(new Date(ret.created.seconds * 1000), filesDir, building.name);
        if(ret.sessionFile == null) throw new RuntimeException();
        ret.name = building.name;
        ret.advancementPace = building.advancementPace;
        {
            int devCount = 0;
            for(PartyDefinitionHelper.DeviceStatus dev : building.clients) {
                if(dev.kicked || !dev.groupMember) continue;
                devCount++; // save all devices, even if they don't have proposed a pg
            }
            ret.devices = new StartData.PartyOwnerData.DeviceInfo[devCount];
            devCount = 0;
            for(PartyDefinitionHelper.DeviceStatus dev : building.clients) {
                if(dev.kicked || !dev.groupMember) continue;
                ret.devices[devCount++] = from(dev);
            }
        }
        int count = 0;
        for(PartyDefinitionHelper.DeviceStatus dev : building.clients) {
            if(dev.kicked || !dev.groupMember) continue;
            for(BuildingPlayingCharacter pc : dev.chars) {
                if(BuildingPlayingCharacter.STATUS_ACCEPTED == pc.status) count++;
            }
        }
        ret.party = new StartData.ActorDefinition[count + building.localChars.size()];
        count = 0;
        for(PartyDefinitionHelper.DeviceStatus dev : building.clients) {
            if(dev.kicked || !dev.groupMember) continue;
            for(BuildingPlayingCharacter pc : dev.chars) {
                if(BuildingPlayingCharacter.STATUS_ACCEPTED == pc.status) {
                    ret.party[count++] = from(pc);
                }
            }
        }
        for (BuildingPlayingCharacter loc : building.localChars) ret.party[count++] = from(loc);
        return ret;
    }

    private static StartData.PartyOwnerData.DeviceInfo from(PartyDefinitionHelper.DeviceStatus dev) {
        StartData.PartyOwnerData.DeviceInfo gen = new StartData.PartyOwnerData.DeviceInfo();
        gen.salt = dev.salt;
        gen.name = dev.name;
        return gen;
    }

    public static StartData.ActorDefinition from(BuildingPlayingCharacter pc) {
        StartData.ActorDefinition built = new StartData.ActorDefinition();
        built.name = pc.name;
        built.experience = pc.experience;
        built.stats =  new StartData.ActorStatistics[] {
                new StartData.ActorStatistics()
        };
        built.stats[0].initBonus = pc.initiativeBonus;
        built.stats[0].healthPoints = pc.fullHealth;
        final byte[] buff = new byte[pc.lastLevelClass.getSerializedSize()];
        built.stats[0].career = new RPGClass.LevelClass();
        try {
            pc.lastLevelClass.writeTo(CodedOutputByteBufferNano.newInstance(buff));
            built.stats[0].career.mergeFrom(CodedInputByteBufferNano.newInstance(buff));
        } catch (IOException e) {
            // impossible in this context
        }
        return built;
    }

    private Context context;

    // PublishAcceptService vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv
    private String unknownDeviceName;
    @Override
    protected void onNewClient(@NonNull MessageChannel fresh) {
        final PartyDefinitionHelper.DeviceStatus newComer = new PartyDefinitionHelper.DeviceStatus(fresh);
        newComer.name = String.format(unknownDeviceName, building.clients.size() + 1);
        building.clients.add(newComer);
        building.netPump.pump(fresh);
    }

    // PublishAcceptService ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
}

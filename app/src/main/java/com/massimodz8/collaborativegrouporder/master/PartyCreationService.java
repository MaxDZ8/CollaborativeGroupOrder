package com.massimodz8.collaborativegrouporder.master;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.ViewGroup;

import com.massimodz8.collaborativegrouporder.AsyncActivityLoadUpdateTask;
import com.massimodz8.collaborativegrouporder.AsyncLoadUpdateTask;
import com.massimodz8.collaborativegrouporder.BuildingPlayingCharacter;
import com.massimodz8.collaborativegrouporder.MaxUtils;
import com.massimodz8.collaborativegrouporder.PersistentDataUtils;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.networkio.ProtoBufferEnum;
import com.massimodz8.collaborativegrouporder.networkio.Pumper;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;
import com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;

public class PartyCreationService extends PublishAcceptService {
    public static final String PARTY_FORMING_SERVICE_TYPE = "_formingGroupInitiative._tcp";

    public interface OnTalkingDeviceCountListener {
        void currentlyTalking(int count);
    }
    public OnTalkingDeviceCountListener onTalkingDeviceCountChanged;

    /**
     * This is to be set immediately after binding to the service. Used as an 'input' to check if
     * adding a new group collides with an existing one as owned groups must have unique names!
     * Also, output of party creation already saved and synched to storage.
     */
    public ArrayList<PersistentStorage.PartyOwnerData.Group> defs;

    public PartyCreationService() {
    }

    /**
     * Initializes construction of a new party with the given name. Construction starts if
     * no collisions are detected. Only to be called once.
     * @param name Name to match against existing groups. Group names must be unique across owned
     *             groups but can be non-unique across keys or keys and owned. This is because
     *             clients have no control over group name.
     * @return null, or a list containing at least 1 element.
     */
    public @Nullable ArrayList<PersistentStorage.PartyOwnerData.Group> beginBuilding(String name, String unknownDeviceName) {
        ArrayList<PersistentStorage.PartyOwnerData.Group> collisions = null;
        for (PersistentStorage.PartyOwnerData.Group match : defs) {
            if(match.name.equals(name)) {
                if(null == collisions) collisions = new ArrayList<>();
                collisions.add(match);
            }
        }
        this.unknownDeviceName = unknownDeviceName;
        if(null == collisions) building = new PartyDefinitionHelper(name) {
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

    public @Nullable String getBuildingPartyName() { return building == null? null : building.name; }

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
        int keyCount = 0;
        for (PartyDefinitionHelper.DeviceStatus dev : clients) {
            final String message = String.format("keyIndex=%1$d, name=\"%2$s\" created=%3$s", keyCount++, building, new Date().toString());
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

    public AsyncActivityLoadUpdateTask<PersistentStorage.PartyOwnerData> saveParty(final @NonNull Activity stringResolver, @NonNull AsyncLoadUpdateTask.Callbacks cb) {
        return new AsyncActivityLoadUpdateTask<PersistentStorage.PartyOwnerData>(PersistentDataUtils.DEFAULT_GROUP_DATA_FILE_NAME, "groupList-", stringResolver, cb) {
            @Override
            protected void appendNewEntry(PersistentStorage.PartyOwnerData loaded) {
                PersistentStorage.PartyOwnerData.Group[] longer = new PersistentStorage.PartyOwnerData.Group[loaded.everything.length + 1];
                System.arraycopy(loaded.everything, 0, longer, 0, loaded.everything.length);
                longer[loaded.everything.length] = makeGroup();
                loaded.everything = longer;
            }

            @Override
            protected void setVersion(PersistentStorage.PartyOwnerData result) {
                result.version = PersistentDataUtils.OWNER_DATA_VERSION;
            }

            @Override
            protected void upgrade(PersistentDataUtils helper, PersistentStorage.PartyOwnerData result) {
                helper.upgrade(result);
            }

            @Override
            protected ArrayList<String> validateLoadedDefinitions(PersistentDataUtils helper, PersistentStorage.PartyOwnerData result) {
                return helper.validateLoadedDefinitions(result);
            }

            @Override
            protected PersistentStorage.PartyOwnerData allocate() {
                return new PersistentStorage.PartyOwnerData();
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

    public AsyncTask sendPartyCompleteMessages(final boolean goAdventuring, final @NonNull Runnable onComplete) {
        final int sillyDelayMS = 250; // make sure the messages go through. Yeah, I should display a progress w/e
        return new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                try {
                    Network.GroupReady byebye = new Network.GroupReady();
                    byebye.goAdventuring = goAdventuring;
                    for (PartyDefinitionHelper.DeviceStatus dev : building.clients) {
                        try {
                            dev.source.writeSync(ProtoBufferEnum.GROUP_READY, byebye);
                            dev.source.socket.getOutputStream().flush();
                        } catch (IOException e) {
                            // we try.
                        }
                    }
                    Thread.sleep(sillyDelayMS);
                } catch (InterruptedException e) {
                    // Sorry dudes, we're going down anyway.
                }
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

    public void shutdown() {
        stopListening(true);
        stopPublishing();
        if(building == null) return;
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


    public class LocalBinder extends Binder {
        public PartyCreationService getConcreteService() {
            return PartyCreationService.this;
        }
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


    private PersistentStorage.PartyOwnerData.Group makeGroup() {
        PersistentStorage.PartyOwnerData.Group ret = new PersistentStorage.PartyOwnerData.Group();
        ret.name = building.name;
        int count = 0;
        for(PartyDefinitionHelper.DeviceStatus dev : building.clients) {
            if(dev.kicked || !dev.groupMember) continue;
            for(BuildingPlayingCharacter pc : dev.chars) {
                if(BuildingPlayingCharacter.STATUS_ACCEPTED == pc.status) count++;
            }
        }
        ret.party = new PersistentStorage.ActorDefinition[count];
        count = 0;
        for(PartyDefinitionHelper.DeviceStatus dev : building.clients) {
            if(dev.kicked || !dev.groupMember) continue;
            for(BuildingPlayingCharacter pc : dev.chars) {
                if(BuildingPlayingCharacter.STATUS_ACCEPTED == pc.status) {
                    PersistentStorage.ActorDefinition built = new PersistentStorage.ActorDefinition();
                    built.name = pc.name;
                    built.level = pc.level;
                    built.experience = pc.experience;
                    built.stats =  new PersistentStorage.ActorStatistics[] {
                            new PersistentStorage.ActorStatistics()
                    };
                    built.stats[0].initBonus = pc.initiativeBonus;
                    built.stats[0].healthPoints = pc.fullHealth;
                    ret.party[count++] = built;
                }
            }
        }
        return ret;
    }

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
    // Service vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv
    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }
    // Service ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
}

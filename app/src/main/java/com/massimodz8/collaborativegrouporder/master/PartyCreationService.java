package com.massimodz8.collaborativegrouporder.master;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.ViewGroup;

import com.massimodz8.collaborativegrouporder.BuildingPlayingCharacter;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.networkio.Pumper;
import com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage;

import java.io.IOException;
import java.util.ArrayList;

public abstract class PartyCreationService extends PublishAcceptService {
    public static final String PARTY_FORMING_SERVICE_TYPE = "_formingGroupInitiative._tcp";

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
    public @Nullable ArrayList<PersistentStorage.PartyOwnerData.Group> beginBuilding(String name) {
        ArrayList<PersistentStorage.PartyOwnerData.Group> collisions = null;
        for (PersistentStorage.PartyOwnerData.Group match : defs) {
            if(match.name.equals(name)) {
                if(null == collisions) collisions = new ArrayList<>();
                collisions.add(match);
            }
        }
        if(null == collisions) building = new PartyDefinitionHelper(name) {
            @Override
            protected void onCharacterDefined(BuildingPlayingCharacter pc) {
                // TODO PartyDefinitionHelper.onCharacterDefined
            }

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

    @NonNull
    public ArrayList<PartyDefinitionHelper.DeviceStatus> getDevices() {
        ArrayList<PartyDefinitionHelper.DeviceStatus> dev = new ArrayList<>(building.clients.size());
        for (PartyDefinitionHelper.DeviceStatus check : building.clients) {
            if(!check.kicked) dev.add(check);
        }
        return dev;
    }

    public void kickNonMembers() { building.kickNonMembers(); }

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

    public int getDeviceCount() {
        int count = 0;
        for (PartyDefinitionHelper.DeviceStatus dev : building.clients) {
            if(!dev.kicked) count++;
        }
        return count;
    }

    /**
     * Note this returns everything that is 'connected', you probably want to purge
     * @return non-null means we 'go adventuring', eventually with no connected peers.
     */
    public @Nullable Pumper.MessagePumpingThread[] moveClients() {
        final ArrayList<PartyDefinitionHelper.DeviceStatus> validish = getDevices();
        Pumper.MessagePumpingThread[] result = new Pumper.MessagePumpingThread[validish.size()];
        for(int loop = 0; loop < result.length; loop++) {
            result[loop] = building.netPump.move(validish.get(loop).source);
        }
        return result;
    }

    public void shutdown() {
        stopListening(true);
        stopPublishing();
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


    private PartyDefinitionHelper building;
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

    // PublishAcceptService vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv
    @Override
    protected void onNewClient(@NonNull MessageChannel fresh) {
        building.clients.add(new PartyDefinitionHelper.DeviceStatus(fresh));
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

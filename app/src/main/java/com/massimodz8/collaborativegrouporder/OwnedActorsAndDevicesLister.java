package com.massimodz8.collaborativegrouporder;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.massimodz8.collaborativegrouporder.protocol.nano.Network;
import com.massimodz8.collaborativegrouporder.protocol.nano.StartData;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Created by Massimo on 26/05/2016.
 * Given a persistent PartyOwnerData.Group object, list all PCs, NPCs and party member devices.
 * Converts definitions into 'live actors' so they are coherent with the usual representation in battle.
 */
public class OwnedActorsAndDevicesLister extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public OwnedActorsAndDevicesLister(@NonNull StartData.PartyOwnerData.Group owned, @NonNull LayoutInflater inflater) {
        setHasStableIds(true);
        this.owned = owned;
        this.li = inflater;
        for (StartData.ActorDefinition def : owned.party) {
            real.put(def, MaxUtils.makeActorState(def, next++, Network.ActorState.T_PLAYING_CHARACTER));
        }
        for (StartData.ActorDefinition def : owned.npcs) {
            real.put(def, MaxUtils.makeActorState(def, next++, Network.ActorState.T_NPC));
        }
        if(owned.devices.length != 0) {
            deviceSectionSeparatorId = next++;
        }
        for (StartData.PartyOwnerData.DeviceInfo dev : owned.devices) {
            deviceid.put(dev, next++);
        }

    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        switch(viewType) {
            case T_ACTOR: return new AdventuringActorDataVH(li.inflate(R.layout.vh_adventuring_actor_data, parent, false)) {
                @Override
                public void onClick(View v) { }
            };
            case T_DEVICES_SECTION_HEADER: return new DeviceSeparatorVH(li.inflate(R.layout.card_party_devices_separator, parent, false));
            case T_DEVICE: return new PartyMemberDeviceVH(li, parent);
        }
        return null;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        if(holder instanceof AdventuringActorDataVH) {
            final AdventuringActorDataVH real = (AdventuringActorDataVH) holder;
            StartData.ActorDefinition def;
            int type;
            if (position < owned.party.length) {
                def = owned.party[position];
                type = Network.ActorState.T_PLAYING_CHARACTER;
            } else {
                position -= owned.party.length;
                if (position < owned.npcs.length) {
                    def = owned.npcs[position];
                    type = Network.ActorState.T_NPC;
                } else return;
            }
            Network.ActorState as = this.real.get(def);
            if (as == null) { // maybe I added new stuff. super.notifyDataSetChanged is final so I must do this on the fly
                as = MaxUtils.makeActorState(def, next++, type);
                this.real.put(def, as);
            }
            real.bindData(as);
        }
        if(holder instanceof PartyMemberDeviceVH) {
            final PartyMemberDeviceVH real = (PartyMemberDeviceVH) holder;
            position -= owned.party.length;
            position -= owned.npcs.length;
            position--; // section separator bruh!
            real.bindData(owned.devices[position]);
        }
    }

    @Override
    public int getItemCount() {
        int count = owned.party.length + owned.npcs.length;
        if(owned.devices.length != 0) {
            count++; // section header
            count += owned.devices.length;
        }
        return count;
    }

    @Override
    public long getItemId(int position) {
        StartData.ActorDefinition def;
        int type;
        if(position < owned.party.length) {
            def = owned.party[position];
            type = Network.ActorState.T_PLAYING_CHARACTER;
        }
        else {
            position -= owned.party.length;
            if(position < owned.npcs.length) {
                def = owned.npcs[position];
                type = Network.ActorState.T_NPC;
            }
            else {
                position -= owned.npcs.length;
                if(position == 0) return deviceSectionSeparatorId;
                position--;
                Integer id = deviceid.get(owned.devices[position]);
                if(id == null) {
                    if(deviceid.isEmpty()) deviceSectionSeparatorId = next++;
                    id = next;
                    deviceid.put(owned.devices[position], next++);
                }
                return id;
            }
        }
        Network.ActorState as = real.get(def);
        if(as == null) { // maybe I added new stuff. super.notifyDataSetChanged is final so I must do this on the fly
            as = MaxUtils.makeActorState(def, next++, type);
            real.put(def, as);
        }
        return as.peerKey;
    }

    @Override
    public int getItemViewType(int position) {
        if(position < owned.party.length) return T_ACTOR;
        position -= owned.party.length;
        if(position < owned.npcs.length) return T_ACTOR;
        position -= owned.npcs.length;
        if(position == 0) return T_DEVICES_SECTION_HEADER;
        return T_DEVICE;
    }

    public StartData.ActorDefinition[] getContainerArray(StartData.ActorDefinition def) {
        for (StartData.ActorDefinition el : owned.party) {
            if(el == def) return owned.party;
        }
        for (StartData.ActorDefinition el : owned.npcs) {
            if(el == def) return owned.npcs;
        }
        return null;
    }

    public StartData.ActorDefinition getOriginalDefinition(Network.ActorState actor) {
        StartData.ActorDefinition def = null;
        for (Map.Entry<StartData.ActorDefinition, Network.ActorState> el : real.entrySet()) {
            if(el.getValue() == actor) {
                def = el.getKey();
                break;
            }
        }
        return def;
    }

    private final StartData.PartyOwnerData.Group owned;
    private final LayoutInflater li;
    private int next, deviceSectionSeparatorId;
    private final IdentityHashMap<StartData.ActorDefinition, Network.ActorState> real = new IdentityHashMap<>();
    private final IdentityHashMap<StartData.PartyOwnerData.DeviceInfo, Integer> deviceid = new IdentityHashMap<>();
    private static final int T_ACTOR = 1, T_DEVICES_SECTION_HEADER = 2, T_DEVICE = 3;

    private static class DeviceSeparatorVH extends RecyclerView.ViewHolder {
        public DeviceSeparatorVH(View itemView) {
            super(itemView);
        }
    }
}

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
 * Given a persistent PartyOwnerData.Group object, list all PCs and NPCs converting definitions
 * into 'live actors' so they are coherent with the usual representation in battle.
 */
public class OwnedActorsLister extends RecyclerView.Adapter<AdventuringActorDataVH> {
    public OwnedActorsLister(@NonNull StartData.PartyOwnerData.Group owned, @NonNull LayoutInflater inflater) {
        setHasStableIds(true);
        this.owned = owned;
        this.li = inflater;
        for (StartData.ActorDefinition def : owned.party) {
            real.put(def, MaxUtils.makeActorState(def, next++, Network.ActorState.T_PLAYING_CHARACTER));
        }
        for (StartData.ActorDefinition def : owned.npcs) {
            real.put(def, MaxUtils.makeActorState(def, next++, Network.ActorState.T_NPC));
        }
    }

    @Override
    public AdventuringActorDataVH onCreateViewHolder(ViewGroup parent, int viewType) {
        return new AdventuringActorDataVH(li.inflate(R.layout.vh_adventuring_actor_data, parent, false)) {
            @Override
            public void onClick(View v) { }
        };
    }

    @Override
    public void onBindViewHolder(AdventuringActorDataVH holder, int position) {
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
            else return;
        }
        Network.ActorState as = real.get(def);
        if(as == null) { // maybe I added new stuff. super.notifyDataSetChanged is final so I must do this on the fly
            as = MaxUtils.makeActorState(def, next++, type);
            real.put(def, as);
        }
        holder.bindData(as);
    }

    @Override
    public int getItemCount() { return owned.party.length + owned.npcs.length; }

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
            else return RecyclerView.NO_ID;
        }
        Network.ActorState as = real.get(def);
        if(as == null) { // maybe I added new stuff. super.notifyDataSetChanged is final so I must do this on the fly
            as = MaxUtils.makeActorState(def, next++, type);
            real.put(def, as);
        }
        return as.peerKey;
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
    private int next;
    private final IdentityHashMap<StartData.ActorDefinition, Network.ActorState> real = new IdentityHashMap<>();
}

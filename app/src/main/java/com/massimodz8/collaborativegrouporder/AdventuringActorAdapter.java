package com.massimodz8.collaborativegrouporder;

import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;

import com.massimodz8.collaborativegrouporder.protocol.nano.Network;

import java.util.IdentityHashMap;

/**
 * Created by Massimo on 18/04/2016.
 * Adventuring actors are supposed to be stable so the RecyclerView can shuffle them around
 * and be cool! Somebody will therefore have to maintain persistent IDs.
 * The way integers are mapped is fixed here: it's a map lookup.
 */
public abstract class AdventuringActorAdapter<AAVH extends AdventuringActorDataVH> extends RecyclerView.Adapter<AAVH> {
    public AdventuringActorAdapter() {
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        Network.ActorState actor = getActorByPos(position);
        return actor != null ? actor.peerKey : RecyclerView.NO_ID;
    }

    protected abstract boolean isCurrent(Network.ActorState actor);
    public abstract Network.ActorState getActorByPos(int position);

    /// Used to inflate new VHs.
    protected abstract LayoutInflater getLayoutInflater();
}


package com.massimodz8.collaborativegrouporder;

import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;

import java.util.IdentityHashMap;

/**
 * Created by Massimo on 18/04/2016.
 * Adventuring actors are supposed to be stable so the RecyclerView can shuffle them around
 * and be cool! Somebody will therefore have to maintain persistent IDs.
 * The way integers are mapped is fixed here: it's a map lookup.
 */
public abstract class AdventuringActorAdapter<AAVH extends AdventuringActorDataVH> extends RecyclerView.Adapter<AAVH> {
    public IdentityHashMap<AbsLiveActor, Integer> actorId;

    public AdventuringActorAdapter() {
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        Integer index = actorId == null? null : actorId.get(getActorByPos(position));
        return index != null ? index : RecyclerView.NO_ID;
    }

    protected abstract boolean isCurrent(AbsLiveActor actor);
    protected abstract AbsLiveActor getActorByPos(int position);

    /**
     * So, I want to modify... or get a value, the setting is driven by VH clicking while the get
     * happens when rebinding data.
     * @param actor What we're talking about. Objects are unique so easy!
     * @param newValue New value to set, if provided.
     * @return Current value. Will be newValue if it was non-null.
     */
    protected abstract boolean enabledSetOrGet(AbsLiveActor actor, @Nullable Boolean newValue);

    /// Used to inflate new VHs.
    protected abstract LayoutInflater getLayoutInflater();
}


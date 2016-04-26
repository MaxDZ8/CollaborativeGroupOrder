package com.massimodz8.collaborativegrouporder;

import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import java.util.IdentityHashMap;

/**
 * Created by Massimo on 18/04/2016.
 * Adventuring actors are supposed to be stable so the RecyclerView can shuffle them around
 * and be cool! Somebody will therefore have to maintain persistent IDs.
 * The way integers are mapped is fixed here: it's a map lookup.
 */
public abstract class AdventuringActorAdapter extends RecyclerView.Adapter<AdventuringActorVH> {
    final IdentityHashMap<AbsLiveActor, Integer> actorId;
    AdventuringActorVH.ClickCallback clickCallback;
    private final boolean hideCheckbox;

    public AdventuringActorAdapter(IdentityHashMap<AbsLiveActor, Integer> actorId, AdventuringActorVH.ClickCallback clickCallback, boolean hideCheckbox) {
        this.actorId = actorId;
        this.clickCallback = clickCallback;
        this.hideCheckbox = hideCheckbox;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        Integer index = actorId.get(getActorByPos(position));
        return index != null ? index : RecyclerView.NO_ID;
    }

    @Override
    public AdventuringActorVH onCreateViewHolder(ViewGroup parent, int viewType) {
        return new AdventuringActorVH(getLayoutInflater().inflate(R.layout.vh_adventuring_actor, parent, false), clickCallback, hideCheckbox) {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isCurrent(actor)) return;
                enabledSetOrGet(actor, isChecked);
            }
        };
    }

    @Override
    public void onBindViewHolder(AdventuringActorVH holder, int position) {
        AbsLiveActor actor = getActorByPos(position);
        int flags = 0;
        if(isCurrent(actor)) flags |= AdventuringActorVH.SHOW_HIGHLIGHT;
        if(enabledSetOrGet(actor, null)) flags |= AdventuringActorVH.CHECKED;
        holder.bindData(flags, actor);
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


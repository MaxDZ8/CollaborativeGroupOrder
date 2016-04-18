package com.massimodz8.collaborativegrouporder.master;

import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.text.Layout;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import com.massimodz8.collaborativegrouporder.R;

import java.util.IdentityHashMap;

/**
 * Created by Massimo on 18/04/2016.
 * Adventuring actors are supposed to be stable so the RecyclerView can shuffle them around
 * and be cool! Somebody will therefore have to maintain persistent IDs.
 * The way integers are mapped is fixed here: it's a map lookup.
 */
abstract class AdventuringActorAdapter extends RecyclerView.Adapter<AdventuringActorVH> {
    final IdentityHashMap<AbsLiveActor, Integer> actorId;

    AdventuringActorAdapter(IdentityHashMap<AbsLiveActor, Integer> actorId) {
        this.actorId = actorId;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        Integer index = actorId.get(getActorByPos(position));
        return index != null ? index : RecyclerView.NO_ID;
    }

    @Override
    public AdventuringActorVH onCreateViewHolder(ViewGroup parent, int viewType) {
        return new AdventuringActorVH(getLayoutInflater().inflate(R.layout.vh_adventuring_actor, parent, false)) {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                enabledSetOrGet(actor, isChecked);
            }
        };
    }

    @Override
    public void onBindViewHolder(AdventuringActorVH holder, int position) {
        AbsLiveActor actor = getActorByPos(position);
        holder.actor = actor;
        holder.selected.setChecked(enabledSetOrGet(actor, null));
        // TODO holder.avatar
        int res;
        switch(actor.type) {
            case AbsLiveActor.TYPE_PLAYING_CHARACTER: res = R.string.fra_actorType_playingCharacter; break;
            case AbsLiveActor.TYPE_MONSTER: res = R.string.fra_actorType_monster; break;
            case AbsLiveActor.TYPE_NPC: res = R.string.fra_actorType_npc; break;
            default: res = R.string.fra_actorType_unmatched;
        }
        holder.actorShortType.setText(res);
        holder.name.setText(actor.displayName);
        int[] hp = actor.getHealth();
        holder.hbar.currentHp = hp[0];
        holder.hbar.maxHp = hp[1];
        holder.hbar.invalidate();
    }


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
    abstract LayoutInflater getLayoutInflater();
}


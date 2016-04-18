package com.massimodz8.collaborativegrouporder.master;

import android.support.v7.widget.RecyclerView;
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
    final LayoutInflater inflater;
    final IdentityHashMap<AbsLiveActor, Integer> actorId;

    AdventuringActorAdapter(LayoutInflater inflater, IdentityHashMap<AbsLiveActor, Integer> actorId) {
        this.inflater = inflater;
        this.actorId = actorId;
        setHasStableIds(true);
    }

    @Override
    public int getItemCount() { return getGame() != null? getGame().getPlaySession().getNumActors() : 0; }

    @Override
    public long getItemId(int position) {
        Integer index = actorId.get(getGame().getPlaySession().getActor(position));
        return index != null ? index : RecyclerView.NO_ID;
    }

    @Override
    public AdventuringActorVH onCreateViewHolder(ViewGroup parent, int viewType) {
        return new AdventuringActorVH(inflater.inflate(R.layout.vh_adventuring_actor, parent, false)) {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                getGame().getPlaySession().willFight(actor, isChecked);
            }
        };
    }

    @Override
    public void onBindViewHolder(AdventuringActorVH holder, int position) {
        final SessionHelper.PlayState session = getGame().getPlaySession();
        AbsLiveActor actor = session.getActor(position);
        holder.actor = actor;
        holder.selected.setChecked(session.willFight(actor, null));
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

    abstract PartyJoinOrderService getGame();
}


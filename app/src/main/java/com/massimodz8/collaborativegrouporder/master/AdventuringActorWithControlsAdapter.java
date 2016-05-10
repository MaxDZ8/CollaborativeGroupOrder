package com.massimodz8.collaborativegrouporder.master;

import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import com.massimodz8.collaborativegrouporder.AdventuringActorAdapter;
import com.massimodz8.collaborativegrouporder.R;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;

/**
 * Created by Massimo on 27/04/2016.
 * Lots of stuff in common by adapter based on battle state. Used by free roaming and battle mode.
 */
public abstract class AdventuringActorWithControlsAdapter extends AdventuringActorAdapter<AdventuringActorControlsVH> {
    SessionHelper playState;

    @Override
    public Network.ActorState getActorByPos(int position) {
        if(playState == null) return null;
        return playState.getActor(position);
    }

    @Override
    public AdventuringActorControlsVH onCreateViewHolder(ViewGroup parent, int viewType) {
        return new AdventuringActorControlsVH(getLayoutInflater().inflate(R.layout.vh_adventuring_actor_controls, parent, false)) {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(actor == null) return;
                playState.willFight(actor, isChecked);
            }

            @Override
            public void onClick(View v) {
                if(selected.isEnabled()) selected.setChecked(!selected.isChecked());
            }
        };
    }

    @Override
    public void onBindViewHolder(AdventuringActorControlsVH holder, int position) {
        Network.ActorState actor = getActorByPos(position);
        holder.showHilight = isCurrent(actor);
        holder.checked = isChecked(actor);
        holder.bindData(actor);
    }

    protected boolean isChecked(Network.ActorState actor) {
        return playState.willFight(actor, null);
    }

    @Override
    public int getItemCount() { return playState != null? playState.getNumActors() : 0; }
}

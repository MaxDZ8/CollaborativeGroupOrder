package com.massimodz8.collaborativegrouporder.master;

import android.support.annotation.Nullable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import com.massimodz8.collaborativegrouporder.AbsLiveActor;
import com.massimodz8.collaborativegrouporder.AdventuringActorAdapter;
import com.massimodz8.collaborativegrouporder.R;

import java.util.IdentityHashMap;

/**
 * Created by Massimo on 27/04/2016.
 * Lots of stuff in common by adapter based on battle state. Used by free roaming and battle mode.
 */
public abstract class AdventuringActorWithControlsAdapter extends AdventuringActorAdapter<AdventuringActorControlsVH> {
    SessionHelper.PlayState playState;

    @Override
    protected AbsLiveActor getActorByPos(int position) {
        if(playState == null) return null;
        return playState.getActor(position);
    }

    @Override
    protected boolean enabledSetOrGet(AbsLiveActor actor, @Nullable Boolean newValue) {
        return playState.willFight(actor, newValue);
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
        AbsLiveActor actor = getActorByPos(position);
        holder.showHilight = isCurrent(actor);
        holder.checked = enabledSetOrGet(actor, null);
        holder.bindData(actor);
    }

    @Override
    public int getItemCount() { return playState != null? playState.getNumActors() : 0; }
}

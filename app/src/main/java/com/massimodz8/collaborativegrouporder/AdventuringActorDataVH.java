package com.massimodz8.collaborativegrouporder;

import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Created by Massimo on 18/04/2016.
 * Adventuring actors are drawn in a consistent way across various activities, at least
 * 'free roaming' and 'battle'. They are sorta the same but managed differently. I want them to be
 * consistent so always present them using this thing.
 */
public abstract class AdventuringActorDataVH extends RecyclerView.ViewHolder implements View.OnClickListener {
    final ImageView avatar;
    final TextView actorShortType, name;
    final HealthBar hbar;
    final public Button prepared;
    public AbsLiveActor actor;

    public AdventuringActorDataVH(View iv) {
        super(iv);
        avatar = (ImageView) iv.findViewById(R.id.vhAAD_avatar);
        actorShortType = (TextView) iv.findViewById(R.id.vhAAD_actorTypeShort);
        name = (TextView) iv.findViewById(R.id.vhAAD_name);
        hbar = (HealthBar) iv.findViewById(R.id.vhAAD_health);
        prepared = (Button) iv.findViewById(R.id.vhAAD_preparedAction);

        iv.setOnClickListener(this);
        prepared.setOnClickListener(this);
    }

    public void bindData(AbsLiveActor actor) {
        this.actor = actor;
        // TODO holder.avatar
        int res;
        switch (actor.type) {
            case AbsLiveActor.TYPE_PLAYING_CHARACTER:
                res = R.string.fra_actorType_playingCharacter;
                break;
            case AbsLiveActor.TYPE_MONSTER:
                res = R.string.fra_actorType_monster;
                break;
            case AbsLiveActor.TYPE_NPC:
                res = R.string.fra_actorType_npc;
                break;
            default:
                res = R.string.fra_actorType_unmatched;
        }
        actorShortType.setText(res);
        name.setText(actor.displayName);
        int[] hp = actor.getHealth();
        hbar.currentHp = hp[0];
        hbar.maxHp = hp[1];
        hbar.invalidate();
        if(actor.actionCondition == null) {
            prepared.setVisibility(View.GONE);
            prepared.setEnabled(true);
        }
        else {
            prepared.setVisibility(View.VISIBLE);
            if(actor.actionCondition.length() == 0) prepared.setText(R.string.vhAA_preparedActionNoNoteGiven);
            else prepared.setText(actor.actionCondition);
            prepared.setEnabled(!actor.conditionTriggered);

        }
    }
}

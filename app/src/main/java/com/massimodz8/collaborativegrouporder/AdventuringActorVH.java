package com.massimodz8.collaborativegrouporder;

import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.HealthBar;
import com.massimodz8.collaborativegrouporder.R;
import com.massimodz8.collaborativegrouporder.master.AbsLiveActor;

/**
 * Created by Massimo on 18/04/2016.
 * Adventuring actors are drawn in a consistent way across various activities, at least
 * 'free roaming' and 'battle'. They are sorta the same but managed differently. I want them to be
 * consistent so always present them using this thing.
 */
public abstract class AdventuringActorVH extends RecyclerView.ViewHolder implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {
    final CheckBox selected;
    final ImageView avatar;
    final TextView actorShortType, name;
    final HealthBar hbar;
    final View hilite;
    final public ClickCallback onClickCallback;
    public AbsLiveActor actor;

    public interface ClickCallback {
        void onClick(AdventuringActorVH self, View view);
    }

    public static class ClickSelected implements ClickCallback {
        @Override
        public void onClick(AdventuringActorVH self, View view) {
            self.selected.performClick();
        }
    }

    public AdventuringActorVH(View iv, @Nullable ClickCallback onClick, boolean hideSelectCheck) {
        super(iv);
        selected = (CheckBox) iv.findViewById(R.id.vhAA_selected);
        avatar = (ImageView) iv.findViewById(R.id.vhAA_avatar);
        actorShortType = (TextView) iv.findViewById(R.id.vhAA_actorTypeShort);
        name = (TextView) iv.findViewById(R.id.vhAA_name);
        hbar = (HealthBar) iv.findViewById(R.id.vhAA_health);
        selected.setOnCheckedChangeListener(this);
        hilite = iv.findViewById(R.id.vhAA_currentPlayerHighlight);

        if(null != onClick) iv.setOnClickListener(this);
        onClickCallback = onClick;

        if(hideSelectCheck) {
            selected.setVisibility(View.INVISIBLE);
            // I cannot just make this GONE because I need some thickness for current player highlight.
            selected.setScaleX(.125f);
            selected.setScaleY(.125f);
            // But I can make it 1/8 of the size and it'll still be nice.
        }
    }

    @Override
    public void onClick(View v) {
        if(null != onClickCallback) onClickCallback.onClick(this, v);
    }

    static final int SHOW_HIGHLIGHT = 1;
    static final int CHECKED = 2;

    public void bindData(int stat, AbsLiveActor actor) {
        final boolean showHilight = (stat & SHOW_HIGHLIGHT) != 0;
        final boolean checked = (stat & CHECKED) != 0;
        this.actor = actor;
        selected.setChecked(checked);
        selected.setEnabled(!showHilight);
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
        hilite.setVisibility(showHilight ? View.VISIBLE : View.GONE);
    }
}
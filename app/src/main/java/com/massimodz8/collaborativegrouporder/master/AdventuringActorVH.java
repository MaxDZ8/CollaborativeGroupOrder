package com.massimodz8.collaborativegrouporder.master;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.HealthBar;
import com.massimodz8.collaborativegrouporder.R;

/**
 * Created by Massimo on 18/04/2016.
 * Adventuring actors are drawn in a consistent way across various activities, at least
 * 'free roaming' and 'battle'. They are sorta the same but managed differently. I want them to be
 * consistent so always present them using this thing.
 */
abstract class AdventuringActorVH extends RecyclerView.ViewHolder implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {
    final CheckBox selected;
    final ImageView avatar;
    final TextView actorShortType, name;
    final HealthBar hbar;
    public AbsLiveActor actor;

    public AdventuringActorVH(View iv) {
        super(iv);
        selected = (CheckBox) iv.findViewById(R.id.vhAA_selected);
        avatar = (ImageView) iv.findViewById(R.id.vhAA_avatar);
        actorShortType = (TextView) iv.findViewById(R.id.vhAA_actorTypeShort);
        name = (TextView) iv.findViewById(R.id.vhAA_name);
        hbar = (HealthBar) iv.findViewById(R.id.vhAA_health);
        iv.setOnClickListener(this);
        selected.setOnCheckedChangeListener(this);
    }

    @Override
    public void onClick(View v) {
        selected.performClick();
    }
}
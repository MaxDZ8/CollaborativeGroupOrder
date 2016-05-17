package com.massimodz8.collaborativegrouporder.master;

import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.MaxUtils;
import com.massimodz8.collaborativegrouporder.R;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;

import java.util.IdentityHashMap;
import java.util.Locale;

/**
 * Created by Massimo on 17/05/2016.
 * Refactoring SpawnMonsterActivity made me realize monster viewholders are seriously messed up.
 * Taking it easier.
 */
public abstract class SpawnableAdventuringActorVH extends RecyclerView.ViewHolder implements View.OnClickListener{
    public static String intCrFormat, ratioCrFormat;

    protected abstract String selectedText();

    public SpawnableAdventuringActorVH(LayoutInflater inf, ViewGroup root) {
        super(inf.inflate(R.layout.vh_spawnable_adventuring_actor, root, false));
        battleCount = (TextView) itemView.findViewById(R.id.vhSAA_spawnCount);
        cr = (TextView) itemView.findViewById(R.id.vhSAA_cr);
        name = (TextView) itemView.findViewById(R.id.vhSAA_name);
        itemView.setOnClickListener(this);
    }

    public void bindData(Network.ActorState actor) {
        this.actor = actor;
        MaxUtils.setTextUnlessNull(battleCount, selectedText(), View.GONE);
        if(actor.cr.denominator == 1) cr.setText(String.format(Locale.getDefault(), intCrFormat, actor.cr.numerator));
        else cr.setText(String.format(Locale.getDefault(), ratioCrFormat, actor.cr.numerator, actor.cr.denominator));
        name.setText(actor.name);
    }
    private final TextView battleCount, cr, name;
    protected Network.ActorState actor;
}

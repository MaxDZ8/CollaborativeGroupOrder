package com.massimodz8.collaborativegrouporder.master;

import android.support.v7.widget.RecyclerView;
import android.text.Layout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.AdventuringActorDataVH;
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
    public static String countFormat, intCrFormat, ratioCrFormat;

    public SpawnableAdventuringActorVH(LayoutInflater inf, ViewGroup root, IdentityHashMap<Network.ActorState, Integer> spawnCount) {
        super(inf.inflate(R.layout.vh_spawnable_adventuring_actor, root, false));
        battleCount = (TextView) itemView.findViewById(R.id.vhSAA_spawnCount);
        cr = (TextView) itemView.findViewById(R.id.vhSAA_cr);
        name = (TextView) itemView.findViewById(R.id.vhSAA_name);
        this.spawnCount = spawnCount;
        itemView.setOnClickListener(this);
    }

    public void bindData(Network.ActorState actor) {
        this.actor = actor;
        final Integer count = spawnCount.get(actor);
        if(count == null || count < 1) battleCount.setVisibility(View.GONE);
        else {
            battleCount.setText(String.format(countFormat, count));
            battleCount.setVisibility(View.VISIBLE);
        }
        if(actor.cr.denominator == 1) cr.setText(String.format(Locale.getDefault(), intCrFormat, actor.cr.numerator));
        else cr.setText(String.format(Locale.getDefault(), ratioCrFormat, actor.cr.numerator, actor.cr.denominator));
        name.setText(actor.name);
    }
    private final TextView battleCount, cr, name;
    private final IdentityHashMap<Network.ActorState, Integer> spawnCount; // null or <1 -> not shown
    protected Network.ActorState actor;
}

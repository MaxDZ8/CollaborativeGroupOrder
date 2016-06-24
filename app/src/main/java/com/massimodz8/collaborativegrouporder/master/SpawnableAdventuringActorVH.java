package com.massimodz8.collaborativegrouporder.master;

import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.MaxUtils;
import com.massimodz8.collaborativegrouporder.R;
import com.massimodz8.collaborativegrouporder.protocol.nano.MonsterData;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;

import java.util.IdentityHashMap;
import java.util.Locale;

/**
 * Created by Massimo on 17/05/2016.
 * Refactoring SpawnMonsterActivity made me realize monster viewholders are seriously messed up.
 * Taking it easier.
 */
public abstract class SpawnableAdventuringActorVH extends RecyclerView.ViewHolder implements View.OnClickListener{
    public static String intCrFormat, ratioCrFormat, akaFormat;

    protected abstract String selectedText();

    public SpawnableAdventuringActorVH(LayoutInflater inf, ViewGroup root) {
        super(inf.inflate(R.layout.vh_spawnable_adventuring_actor, root, false));
        battleCount = (TextView) itemView.findViewById(R.id.vhSAA_spawnCount);
        cr = (TextView) itemView.findViewById(R.id.vhSAA_cr);
        name = (TextView) itemView.findViewById(R.id.vhSAA_name);
        alternates = (TextView) itemView.findViewById(R.id.vhSAA_alternateNames);
        itemView.setOnClickListener(this);
    }

    public void bindData(MonsterData.Monster definition, String[] mainNames) {
        this.mob = definition;
        MaxUtils.setTextUnlessNull(battleCount, selectedText(), View.GONE);
        if(mob.header.cr.denominator == 1) cr.setText(String.format(Locale.getDefault(), intCrFormat, mob.header.cr.numerator));
        else cr.setText(String.format(Locale.getDefault(), ratioCrFormat, mob.header.cr.numerator, mob.header.cr.denominator));
        name.setText(mainNames != null? mainNames[0] : definition.header.name[0]);
        String others = null;
        if(mainNames != null) {
            others = "";
            for(int loop = 1; loop < mainNames.length; loop++) others += String.format(akaFormat, mainNames[loop]);
        }
        if(definition.header.name.length > 1) {
            if(others == null) others = "";
            for(int loop = 1; loop < definition.header.name.length; loop++) others += String.format(akaFormat, definition.header.name[loop]);
        }
        if(others != null) others = others.trim();
        MaxUtils.setTextUnlessNull(alternates, others, View.GONE);
    }
    public final TextView battleCount, cr, name, alternates;
    protected MonsterData.Monster mob;
    protected Network.ActorState actor;
}

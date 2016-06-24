package com.massimodz8.collaborativegrouporder.master;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.MaxUtils;
import com.massimodz8.collaborativegrouporder.R;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;
import com.massimodz8.collaborativegrouporder.protocol.nano.PreparedEncounters;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.Locale;

/**
 * Created by Massimo on 17/05/2016.
 * This is ideally the same as SpawnableAdventuringActorVH with the only difference it contains
 * a list of SpawnableAdventuringActorVH objects in a RecyclerView which are grouped and will
 * redirect clicks here.
 */
public abstract class PreparedBattleVH extends RecyclerView.ViewHolder implements View.OnClickListener {
    public PreparedBattleVH(LayoutInflater inf, ViewGroup root) {
        super(inf.inflate(R.layout.vh_prepared_battle, root, false));
        inflater = inf;
        willBattle = (TextView) itemView.findViewById(R.id.vhPB_spawnCount);
        desc = (TextView) itemView.findViewById(R.id.vhPB_desc);
        created = (TextView) itemView.findViewById(R.id.vhPB_creationDate);
        container = (LinearLayout) itemView.findViewById(R.id.vhPB_innerLinearLayout);
        itemView.setOnClickListener(this);
    }

    protected abstract String selectedText();

    public void bindData(PreparedEncounters.Battle battle) {
        this.battle = battle;
        MaxUtils.setTextUnlessNull(willBattle, selectedText(), View.GONE);
        desc.setText(battle.desc);
        created.setText(DateFormat.getInstance().format(new Date(battle.created.seconds * 1000)));
        for (SpawnableAdventuringActorVH goner : actors) container.removeView(goner.itemView);
        actors.clear();
        for (Network.ActorState actor : battle.actors) {
            final SpawnableAdventuringActorVH built = new SpawnableAdventuringActorVH(inflater, container) {
                @Override
                protected String selectedText() {
                    return null;
                }

                @Override
                public void onClick(View v) {
                    PreparedBattleVH.this.onClick(v);
                }
            };
            if(actor.cr.denominator == 1) built.cr.setText(String.format(Locale.getDefault(), SpawnableAdventuringActorVH.intCrFormat, actor.cr.numerator));
            else built.cr.setText(String.format(Locale.getDefault(), SpawnableAdventuringActorVH.ratioCrFormat, actor.cr.numerator, actor.cr.denominator));
            built.name.setText(actor.name);
            actors.add(built);
            container.addView(built.itemView);
        }
    }

    private final TextView willBattle, desc, created;
    private final IdentityHashMap<Network.ActorState, Integer> localIds = new IdentityHashMap<>();
    private final LinearLayout container;
    protected PreparedEncounters.Battle battle;
    private final LayoutInflater inflater;
    private final ArrayList<SpawnableAdventuringActorVH> actors = new ArrayList<>();
}

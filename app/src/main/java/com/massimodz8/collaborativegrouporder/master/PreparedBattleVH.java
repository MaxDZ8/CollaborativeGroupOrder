package com.massimodz8.collaborativegrouporder.master;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.MaxUtils;
import com.massimodz8.collaborativegrouporder.R;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;
import com.massimodz8.collaborativegrouporder.protocol.nano.PreparedEncounters;

import java.text.DateFormat;
import java.util.Date;
import java.util.IdentityHashMap;

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
        list = (RecyclerView) itemView.findViewById(R.id.vhPB_list);
        list.setAdapter(new BattleLister());
        itemView.setOnClickListener(this);
    }

    protected abstract String selectedText();

    public void bindData(PreparedEncounters.Battle battle) {
        this.battle = battle;
        MaxUtils.setTextUnlessNull(willBattle, selectedText(), View.GONE);
        desc.setText(battle.desc);
        created.setText(DateFormat.getInstance().format(new Date(battle.created.seconds * 1000)));
    }

    private final TextView willBattle, desc, created;
    private final IdentityHashMap<Network.ActorState, Integer> localIds = new IdentityHashMap<>();
    private final RecyclerView list;
    protected PreparedEncounters.Battle battle;
    private final LayoutInflater inflater;

    private class BattleLister extends RecyclerView.Adapter<SpawnableAdventuringActorVH> {
        @Override
        public SpawnableAdventuringActorVH onCreateViewHolder(ViewGroup parent, int viewType) {
            return new SpawnableAdventuringActorVH(inflater, parent) {
                @Override
                protected String selectedText() { return null;}

                @Override
                public void onClick(View v) {
                    PreparedBattleVH.this.onClick(v);
                }
            };
        }

        @Override
        public void onBindViewHolder(SpawnableAdventuringActorVH holder, int position) {
            holder.bindData(battle.actors[position]);
        }

        @Override
        public int getItemCount() { return battle.actors.length; }
    }
}

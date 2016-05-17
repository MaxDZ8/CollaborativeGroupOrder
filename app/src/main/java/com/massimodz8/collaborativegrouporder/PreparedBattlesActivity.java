package com.massimodz8.collaborativegrouporder;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.protocol.nano.Network;
import com.massimodz8.collaborativegrouporder.protocol.nano.PreparedEncounters;

import java.util.IdentityHashMap;

public class PreparedBattlesActivity extends AppCompatActivity {
    public static PreparedEncounters.Collection custom;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_prepared_battles);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        final android.support.v7.app.ActionBar sab = getSupportActionBar();
        if(sab != null) sab.setDisplayHomeAsUpEnabled(true);

        PreparedBattleHeaderVH.createFormat = getString(R.string.pba_battleCreatedFormat);

        TextView status = (TextView) findViewById(R.id.pba_status);
        if(custom.battles.length == 0) status.setText(R.string.pba_nothingFound);
        else status.setVisibility(View.GONE);

        ids = new IdentityHashMap<>();
        int id = 0;
        for (PreparedEncounters.Battle el : custom.battles) {
            ids.put(el, id++);
            for (Network.ActorState actor : el.actors) ids.put(actor, id++);
        }
        RecyclerView rv = (RecyclerView) findViewById(R.id.pba_list);
        rv.setAdapter(new MyDualHolderAdapter());
        View fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(new Intent(PreparedBattlesActivity.this, NewPreparedBattleActivity.class), REQUEST_PREPARE_NEW);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode != REQUEST_PREPARE_NEW) return;
        if(resultCode != RESULT_OK) return;
        RecyclerView rv = (RecyclerView) findViewById(R.id.pba_list);
        rv.getAdapter().notifyItemInserted(custom.battles.length - 1);
    }

    class MyDualHolderAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        public static final int BATTLE = 1;
        public static final int ACTOR = 2;

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            if(viewType == BATTLE) return new PreparedBattleHeaderVH(getLayoutInflater().inflate(R.layout.vh_prepared_battle, parent, false));
            return new AdventuringActorDataVH(getLayoutInflater().inflate(R.layout.vh_adventuring_actor_data, parent, false)) {
                @Override
                public void onClick(View v) { }
            };
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if(holder instanceof PreparedBattleHeaderVH) {
                PreparedBattleHeaderVH real = (PreparedBattleHeaderVH)holder;
                PreparedEncounters.Battle battle = null;
                int index = 0;
                for (PreparedEncounters.Battle cand : custom.battles) {
                    if(index == position) {
                        battle = cand;
                        break;
                    }
                    index++;
                    index += cand.actors.length;
                }
                if(battle != null) real.bindData(battle);
            }
            if(holder instanceof AdventuringActorDataVH) {
                AdventuringActorDataVH real = (AdventuringActorDataVH)holder;
                Network.ActorState actor = null;
                for (PreparedEncounters.Battle cand : custom.battles) {
                    position--;
                    if(position < cand.actors.length) {
                        actor = cand.actors[position];
                        break;
                    }
                    position -= cand.actors.length;
                }
                if(actor != null) real.bindData(actor);
            }
        }

        @Override
        public int getItemCount() {
            int count = custom.battles.length;
            for (PreparedEncounters.Battle el : custom.battles) count += el.actors.length;
            return count;
        }

        @Override
        public int getItemViewType(int position) {
            for (PreparedEncounters.Battle el : custom.battles) {
                if(position == 0) return BATTLE;
                position--;
                if(position < el.actors.length) return ACTOR;
                position -= el.actors.length;
            }
            return ACTOR;
        }

        @Override
        public long getItemId(int position) {
            for (PreparedEncounters.Battle cand : custom.battles) {
                if(position == 0) return ids.get(cand);
                position--;
                if(position < cand.actors.length) return ids.get(cand.actors[position]);
                position -= cand.actors.length;
            }
            return RecyclerView.NO_ID;
        }
    }

    private IdentityHashMap<Object, Integer> ids; // we only add to this, eventually we flush everything when this is destroyed.

    private static final int REQUEST_PREPARE_NEW = 1;
}

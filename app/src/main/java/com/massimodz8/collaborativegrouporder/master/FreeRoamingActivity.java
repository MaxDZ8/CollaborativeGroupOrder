package com.massimodz8.collaborativegrouporder.master;


import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.support.v7.widget.Toolbar;

import com.massimodz8.collaborativegrouporder.HealthBar;
import com.massimodz8.collaborativegrouporder.MaxUtils;
import com.massimodz8.collaborativegrouporder.PreSeparatorDecorator;
import com.massimodz8.collaborativegrouporder.R;

import java.util.IdentityHashMap;

public class FreeRoamingActivity extends AppCompatActivity implements ServiceConnection {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_free_roaming);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int count = 0;
                int pgCount = 0;
                for(int loop = 0; loop < game.getNumActors(); loop++) {
                    AbsLiveActor actor = game.getActor(loop);
                    int add = game.willFight(actor, null)? 1 : 0;
                    if(actor.type == AbsLiveActor.TYPE_PLAYING_CHARACTER) pgCount += add;
                    else count += add;
                }
                if(pgCount == 0) Snackbar.make(findViewById(R.id.activityRoot), R.string.fra_noBattle_zeroPcs, Snackbar.LENGTH_LONG).show();
                else if(count == 0)Snackbar.make(findViewById(R.id.activityRoot), R.string.fra_noBattle_nobody, Snackbar.LENGTH_LONG).show();
                else {
                    new AlertDialog.Builder(FreeRoamingActivity.this).setMessage("TODO").show();
                }
            }
        });
        final ActionBar sab = getSupportActionBar();
        if(null != sab) sab.setDisplayHomeAsUpEnabled(true);

        RecyclerView rv = (RecyclerView) findViewById(R.id.fra_list);
        rv.setAdapter(lister);
        rv.addItemDecoration(new PreSeparatorDecorator(rv, this) {
            @Override
            protected boolean isEligible(int position) {
                return position != 0;
            }
        });

        if(!bindService(new Intent(this, PartyJoinOrderService.class), this, 0)) {
            MaxUtils.beginDelayedTransition(this);
            TextView ohno = (TextView) findViewById(R.id.fra_instructions);
            ohno.setText(R.string.fra_failedBind);
            return;
        }
        mustUnbind = true;
    }

    @Override
    protected void onDestroy() {
        if(game != null) game.end();
        if(mustUnbind) unbindService(this);
        super.onDestroy();
    }

    private boolean mustUnbind;
    private SessionHelper.PlayState game;
    private AdventuringActorAdapter lister = new AdventuringActorAdapter();
    private IdentityHashMap<AbsLiveActor, Integer> actorId = new IdentityHashMap<>();
    private int numDefinedActors; // those won't get expunged, no matter what

    private class AdventuringActorVH extends RecyclerView.ViewHolder implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {
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

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            game.willFight(actor, isChecked);
        }
    }

    private class AdventuringActorAdapter extends RecyclerView.Adapter<AdventuringActorVH> {
        AdventuringActorAdapter() {
            setHasStableIds(true);
        }

        @Override
        public int getItemCount() { return game != null? game.getNumActors() : 0; }

        @Override
        public long getItemId(int position) {
            Integer index = actorId.get(game.getActor(position));
            return index != null ? index : RecyclerView.NO_ID;
        }

        @Override
        public AdventuringActorVH onCreateViewHolder(ViewGroup parent, int viewType) {
            return new AdventuringActorVH(getLayoutInflater().inflate(R.layout.vh_adventuring_actor, parent, false));
        }

        @Override
        public void onBindViewHolder(AdventuringActorVH holder, int position) {
            AbsLiveActor actor = game.getActor(position);
            holder.actor = actor;
            holder.selected.setChecked(game.willFight(actor, null));
            // TODO holder.avatar
            int res;
            switch(actor.type) {
                case AbsLiveActor.TYPE_PLAYING_CHARACTER: res = R.string.fra_actorType_playingCharacter; break;
                case AbsLiveActor.TYPE_MONSTER: res = R.string.fra_actorType_monster; break;
                case AbsLiveActor.TYPE_NPC: res = R.string.fra_actorType_npc; break;
                default: res = R.string.fra_actorType_unmatched;
            }
            holder.actorShortType.setText(res);
            holder.name.setText(actor.displayName);
            int[] hp = actor.getHealth();
            holder.hbar.currentHp = hp[0];
            holder.hbar.maxHp = hp[1];
            holder.hbar.invalidate();
        }
    }

    // ServiceConnection vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        PartyJoinOrderService.LocalBinder real = (PartyJoinOrderService.LocalBinder)service;
        PartyJoinOrderService serv = real.getConcreteService();
        game = serv.getPlaySession();
        game.begin(new Runnable() {
            @Override
            public void run() {
                numDefinedActors = game.getNumActors();
                for(int loop = 0; loop < numDefinedActors; loop++) actorId.put(game.getActor(loop), loop);
                lister.notifyDataSetChanged();
                Snackbar.make(findViewById(R.id.activityRoot), R.string.fra_dataLoadedFeedback, Snackbar.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }
    // ServiceConnection ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
}

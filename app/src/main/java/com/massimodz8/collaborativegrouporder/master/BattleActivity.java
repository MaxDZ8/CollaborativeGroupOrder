package com.massimodz8.collaborativegrouporder.master;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.MaxUtils;
import com.massimodz8.collaborativegrouporder.PreSeparatorDecorator;
import com.massimodz8.collaborativegrouporder.R;

import java.util.IdentityHashMap;

public class BattleActivity extends AppCompatActivity implements ServiceConnection {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_battle);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        final ActionBar sab = getSupportActionBar();
        if(null != sab) sab.setDisplayHomeAsUpEnabled(true);

        if(!bindService(new Intent(this, PartyJoinOrderService.class), this, 0)) {
            MaxUtils.beginDelayedTransition(this);
            TextView ohno = (TextView) findViewById(R.id.fra_instructions);
            ohno.setText(R.string.master_cannotBindAdventuringService);
            return;
        }
        mustUnbind = true;

        final FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                fab.setVisibility(View.GONE);
                new AlertDialog.Builder(BattleActivity.this).setMessage("gotta do something").show();
            }
        });
    }

    boolean mustUnbind;
    private PartyJoinOrderService game;
    private IdentityHashMap<AbsLiveActor, Integer> actorId = new IdentityHashMap<>();
    private AdventuringActorAdapter lister = new AdventuringActorAdapter(actorId) {
        @Override
        public int getItemCount() {
            return game.getPlaySession().battleState.battlers.length;
        }

        @Override
        protected AbsLiveActor getActorByPos(int position) {
            return game.getPlaySession().battleState.battlers[position];
        }

        @Override
        protected boolean enabledSetOrGet(AbsLiveActor actor, @Nullable Boolean newValue) {
            int index = 0;
            final BattleHelper battle = game.getPlaySession().battleState;
            for (AbsLiveActor test : battle.battlers) {
                if(actor == test) {
                    if(newValue != null) battle.enabled[index] = newValue;
                    return battle.enabled[index];
                }
                index++;
            }

            return false;
        }

        @Override
        LayoutInflater getLayoutInflater() {
            return BattleActivity.this.getLayoutInflater();
        }
    };
    private int numDefinedActors; // those won't get expunged, no matter what

    @Override
    protected void onDestroy() {
        if(game != null) game.getPlaySession().battleState = null;
        if(mustUnbind) unbindService(this);
        super.onDestroy();
    }


    // ServiceConnection vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        PartyJoinOrderService.LocalBinder real = (PartyJoinOrderService.LocalBinder)service;
        game = real.getConcreteService();
        final BattleHelper battle = game.getPlaySession().battleState;
        for (AbsLiveActor actor : battle.battlers) actorId.put(actor, numDefinedActors++);
        lister.notifyDataSetChanged();
        final RecyclerView rv = (RecyclerView) findViewById(R.id.ba_orderedList);
        rv.setAdapter(lister);
        rv.addItemDecoration(new PreSeparatorDecorator(rv, this) {
            @Override
            protected boolean isEligible(int position) {
                return true;
            }
        });
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
    }
    // ServiceConnection ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
}

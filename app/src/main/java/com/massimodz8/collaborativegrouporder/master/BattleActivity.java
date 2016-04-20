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

import com.massimodz8.collaborativegrouporder.AdventuringActorAdapter;
import com.massimodz8.collaborativegrouporder.AdventuringActorVH;
import com.massimodz8.collaborativegrouporder.MaxUtils;
import com.massimodz8.collaborativegrouporder.MyActorRoundActivity;
import com.massimodz8.collaborativegrouporder.PreSeparatorDecorator;
import com.massimodz8.collaborativegrouporder.R;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;

import java.util.IdentityHashMap;
import java.util.Locale;

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
                MaxUtils.beginDelayedTransition(BattleActivity.this);
                beginRound(1);
                nextEnabledActor(0);
            }
        });
    }

    boolean mustUnbind;
    private PartyJoinOrderService game;
    private IdentityHashMap<AbsLiveActor, Integer> actorId = new IdentityHashMap<>();
    private AdventuringActorAdapter lister = new AdventuringActorAdapter(actorId, new DifferentClickCallback()) {
        @Override
        public int getItemCount() {
            return game.getPlaySession().battleState.battlers.length;
        }

        @Override
        protected boolean isCurrent(AbsLiveActor actor) {
            if (game == null) return false;
            final BattleHelper battle = game.getPlaySession().battleState;
            int matched = 0;
            for (AbsLiveActor check : battle.battlers) {
                if (check == actor) break;
                matched++;
            }
            return matched == battle.currentActor;
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
        protected LayoutInflater getLayoutInflater() {
            return BattleActivity.this.getLayoutInflater();
        }
    };
    private class DifferentClickCallback extends AdventuringActorVH.ClickSelected {
        @Override
        public void onClick(AdventuringActorVH self, View view) {
            if(game == null || game.getPlaySession() == null || game.getPlaySession().battleState == null) super.onClick(self, view); // impossible
            final BattleHelper state = game.getPlaySession().battleState;
            int myIndex = 0;
            for (AbsLiveActor test : state.battlers) {
                if(test == self.actor) break;
                myIndex++;
            }
            if(myIndex != state.currentActor) super.onClick(self, view); // toggle 'will act next round'
            else startActivityForResult(new Intent(BattleActivity.this, MyActorRoundActivity.class), REQUEST_MONSTER_TURN);
        }
    }
    private int numDefinedActors; // those won't get expunged, no matter what

    @Override
    protected void onDestroy() {
        if(mustUnbind) unbindService(this);
        super.onDestroy();
    }

    /// It's just an helper function to set the round string.
    /// TODO: those two could easily be a single call with nullable Integer params.
    private void beginRound(int round) {
        if(game == null || game.getPlaySession() == null) return; // impossible by construction
        final BattleHelper battle = game.getPlaySession().battleState;
        if(battle == null) return; // impossible by construction
        final TextView status = (TextView) findViewById(R.id.ba_status);
        battle.round = round;
        status.setText(String.format(Locale.ROOT, getString(R.string.ba_roundNumber), battle.round));
    }

    private void nextEnabledActor(int startIndex) {
        if(game == null || game.getPlaySession() == null) return; // impossible by construction
        final BattleHelper battle = game.getPlaySession().battleState;
        if(battle == null) return; // impossible by construction
        int prev = battle.currentActor;
        startIndex %= battle.battlers.length;
        while(!battle.enabled[startIndex]) startIndex++;
        startIndex %= battle.battlers.length; // not necessary but just for extra safety.
        battle.currentActor = startIndex;
        if(prev >= 0) lister.notifyItemChanged(prev);
        lister.notifyItemChanged(startIndex);
        // If played here open detail screen. Otherwise, send your-turn message.
        final AbsLiveActor active = battle.battlers[battle.currentActor];
        final MessageChannel pipe;
        if(active instanceof CharacterActor) {
            pipe = game.getMessageChannel(((CharacterActor) active).character);
        }
        else pipe = null;
        if(pipe != null) {
            new AlertDialog.Builder(this).setMessage("TODO: real networking.").show();
            return;
        }
        startActivityForResult(new Intent(this, MyActorRoundActivity.class), REQUEST_MONSTER_TURN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode != REQUEST_MONSTER_TURN) return;
        if(resultCode != RESULT_OK) return;
        final BattleHelper battle = game.getPlaySession().battleState;
        MaxUtils.beginDelayedTransition(this);
        if(battle.currentActor + 1 >= battle.battlers.length) beginRound(battle.round + 1);
        nextEnabledActor(battle.currentActor + 1);
    }

    private static final int REQUEST_MONSTER_TURN = 1;

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

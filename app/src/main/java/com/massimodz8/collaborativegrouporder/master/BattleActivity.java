package com.massimodz8.collaborativegrouporder.master;

import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.AbsLiveActor;
import com.massimodz8.collaborativegrouporder.CharacterActor;
import com.massimodz8.collaborativegrouporder.InitiativeScore;
import com.massimodz8.collaborativegrouporder.MaxUtils;
import com.massimodz8.collaborativegrouporder.MyActorRoundActivity;
import com.massimodz8.collaborativegrouporder.PreSeparatorDecorator;
import com.massimodz8.collaborativegrouporder.R;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;
import com.massimodz8.collaborativegrouporder.protocol.nano.StartData;

import java.util.ArrayList;
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
                final BattleHelper battle = game.sessionHelper.session.battleState;
                battle.tickRound();
                final TextView status = (TextView) findViewById(R.id.ba_roundCount);
                status.setText(String.format(Locale.ROOT, getString(R.string.ba_roundNumber), battle.round));
                lister.notifyItemChanged(battle.currentActor);
                activateNewActor();
            }
        });
    }

    private int numDefinedActors; // those won't get expunged, no matter what
    boolean mustUnbind;
    private PartyJoinOrderService game;
    private AdventuringActorWithControlsAdapter lister = new AdventuringActorWithControlsAdapter() {
        @Override
        public AdventuringActorControlsVH onCreateViewHolder(ViewGroup parent, int viewType) {
            final AdventuringActorControlsVH result = new AdventuringActorControlsVH(BattleActivity.this.getLayoutInflater().inflate(R.layout.vh_adventuring_actor_controls, parent, false)) {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (actor == null) return;
                    for (InitiativeScore el : game.sessionHelper.session.battleState.ordered) {
                        if(el.actor == actor) {
                            el.enabled = isChecked;
                            break;
                        }
                    }
                }

                @Override
                public void onClick(View v) {
                    if (game == null || game.sessionHelper.session == null || game.sessionHelper.session.battleState == null) {
                        if (selected.isEnabled())
                            selected.setChecked(!selected.isChecked()); // toggle 'will act next round'
                        return;
                    }
                    final BattleHelper state = game.sessionHelper.session.battleState;
                    int myIndex = 0;
                    for (InitiativeScore test : state.ordered) {
                        if (test.actor == actor) break;
                        myIndex++;
                    }
                    if (myIndex != state.currentActor && selected.isEnabled()) {
                        selected.setChecked(!selected.isChecked()); // toggle 'will act next round'
                    } else {
                        activateNewActor();
                    }
                }
            };
            result.prepared.setOnClickListener(new TriggerPreparedActionListener(result));
            return result;
        }

        @Override
        protected boolean isCurrent(AbsLiveActor actor) {
            if (game == null) return false;
            final BattleHelper battle = game.sessionHelper.session.battleState;
            int matched = 0;
            for (InitiativeScore check : battle.ordered) {
                if (check.actor == actor) break;
                matched++;
            }
            if(battle.triggered != null) {
                final AbsLiveActor interruptor = battle.triggered.get(battle.triggered.size() - 1);
                if(interruptor.conditionTriggered && actor == interruptor) return true;
            }
            return matched == battle.currentActor;
        }

        @Override
        protected LayoutInflater getLayoutInflater() {
            return BattleActivity.this.getLayoutInflater();
        }
        @Override
        public int getItemCount() {
            return game.sessionHelper.session.battleState.ordered.length;
        }

        @Override
        protected AbsLiveActor getActorByPos(int position) {
            return game.sessionHelper.session.battleState.ordered[position].actor;
        }

        @Override
        protected boolean enabledSetOrGet(AbsLiveActor actor, @Nullable Boolean newValue) {
            int index = 0;
            final BattleHelper battle = game.sessionHelper.session.battleState;
            for (InitiativeScore test : battle.ordered) {
                if(actor == test.actor) {
                    if(newValue != null) battle.ordered[index].enabled = newValue;
                    return battle.ordered[index].enabled;
                }
                index++;
            }

            return false;
        }
    };


    private class TriggerPreparedActionListener implements View.OnClickListener {
        final AdventuringActorControlsVH target;

        public TriggerPreparedActionListener(AdventuringActorControlsVH target) {
            this.target = target;
        }

        @Override
        public void onClick(View v) {
            if (target.actor == null || target.actor.actionCondition == null)
                return; // impossible by context
            final BattleHelper battle = game.sessionHelper.session.battleState;
            if (battle.triggered == null) battle.triggered = new ArrayList<>();
            battle.triggered.add(target.actor);
            target.actor.conditionTriggered = true;
            final int interrupted = battle.currentActor;
            final InitiativeScore prev = battle.ordered[interrupted];
            battle.currentActor = 0;
            for (InitiativeScore el : battle.ordered) {
                if (el.actor == target.actor) break;
                battle.currentActor++;
            }
            battle.shuffleCurrent(interrupted - (interrupted != 0 ? 1 : 0));
            battle.currentActor = 0;
            for (InitiativeScore el : battle.ordered) {
                if (el == prev) break;
                battle.currentActor++;
            }
            activateNewActor();
        }
    }

    @Override
    protected void onDestroy() {
        if(mustUnbind) unbindService(this);
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode != REQUEST_MONSTER_TURN) return;
        if(resultCode != RESULT_OK) {
            MaxUtils.beginDelayedTransition(this);
            // Even if the dude is still doing his round we regen everything. Why?
            // Typical case: he did an attack and made damage. It's not like I want to track those things.
            lister.notifyDataSetChanged();
            return;
        }
        actionCompleted();
    }

    private void actionCompleted() {
        final BattleHelper battle = game.sessionHelper.session.battleState;
        final int prev, currently;
        boolean fromReadiedStack = false;
        if(battle.triggered == null) {
            prev = battle.currentActor;
            battle.tickRound();
            currently = battle.currentActor;
        }
        else {
            int match = 0;
            int active = battle.triggered.size() - 1;
            for (InitiativeScore test : battle.ordered) {
                if(test.actor == battle.triggered.get(active)) break;
                match++;
            }
            prev = match;
            battle.ordered[match].actor.actionCondition = null;
            battle.ordered[match].actor.conditionTriggered = false;
            battle.triggered.remove(active);
            active--;
            if(battle.triggered.isEmpty()) battle.triggered = null;
            if(battle.triggered == null) currently = battle.currentActor;
            else {
                match = 0;
                for (InitiativeScore test : battle.ordered) {
                    if(test.actor == battle.triggered.get(active)) break;
                    match++;
                }
                currently = match;
                fromReadiedStack = true;
            }
        }
        lister.notifyItemChanged(prev);
        lister.notifyItemChanged(currently);
        MaxUtils.beginDelayedTransition(this);
        final TextView status = (TextView) findViewById(R.id.ba_roundCount);
        status.setText(String.format(Locale.ROOT, getString(R.string.ba_roundNumber), battle.round));
        final InitiativeScore init = battle.ordered[currently];
        if(init.actor.actionCondition == null || fromReadiedStack) {
            activateNewActor();
            return;
        }
        new AlertDialog.Builder(this)
                .setMessage(String.format(getString(R.string.ba_dlg_gotPreparedAction), init.actor.displayName))
                .setPositiveButton(R.string.ba_dlg_gotPreparedAction_renew, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Renewing is super cool - the dude will just not act and we're nice with it.
                        actionCompleted();
                    }
                }).setNegativeButton(getString(R.string.ba_dlg_gotPreparedAction_discard), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Getting the rid of an action is nontrivial, we might have to signal it. It's just a curtesy anyway.
                StartData.ActorDefinition def = init.actor instanceof CharacterActor ? ((CharacterActor)init.actor).character : null;
                MessageChannel pipe = def != null? game.getMessageChannel(def) : null;
                if(pipe == null) { // we mangle it there. That's nice.
                    init.actor.actionCondition = null;
                    MaxUtils.beginDelayedTransition(BattleActivity.this);
                    lister.notifyItemChanged(currently);
                    activateNewActor();
                    return;
                }
                final PcAssignmentHelper.PlayingDevice dev = game.assignmentHelper.getDevice(pipe);
                game.assignmentHelper.activateRemote(dev, game.actorId.get(init.actor), Network.TurnControl.T_PREPARED_CANCELLED);
                game.assignmentHelper.activateRemote(dev, game.actorId.get(init.actor), Network.TurnControl.T_REGULAR);
            }
        }).setCancelable(false)
                .show();
    }

    private void activateNewActor() {        // If played here open detail screen. Otherwise, send your-turn message.
        if(game.sessionHelper.session.activateNewActor() != null) {
            Snackbar.make(findViewById(R.id.activityRoot), R.string.ba_actorByPlayerSnack, Snackbar.LENGTH_SHORT).show();
            return;
        }
        final Intent intent = new Intent(this, MyActorRoundActivity.class)
                .putExtra(MyActorRoundActivity.EXTRA_SUPPRESS_VIBRATION, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivityForResult(intent, REQUEST_MONSTER_TURN);
    }

    private static final int REQUEST_MONSTER_TURN = 1;

    // ServiceConnection vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        PartyJoinOrderService.LocalBinder real = (PartyJoinOrderService.LocalBinder)service;
        game = real.getConcreteService();
        lister.playState = game.sessionHelper.session;
        lister.actorId = game.actorId;
        final BattleHelper battle = game.sessionHelper.session.battleState;
        for (InitiativeScore el : battle.ordered) game.actorId.put(el.actor, numDefinedActors++);
        lister.notifyDataSetChanged();
        final RecyclerView rv = (RecyclerView) findViewById(R.id.ba_orderedList);
        rv.setAdapter(lister);
        rv.addItemDecoration(new PreSeparatorDecorator(rv, this) {
            @Override
            protected boolean isEligible(int position) {
                return true;
            }
        });
        if(battle.round > 0) { // battle already started...
            findViewById(R.id.fab).setVisibility(View.GONE);
            final TextView status = (TextView) findViewById(R.id.ba_roundCount);
            status.setText(String.format(Locale.ROOT, getString(R.string.ba_roundNumber), battle.round));
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
    }
    // ServiceConnection ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
}

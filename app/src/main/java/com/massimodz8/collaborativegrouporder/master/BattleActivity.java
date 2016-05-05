package com.massimodz8.collaborativegrouporder.master;

import android.content.ComponentName;
import android.content.DialogInterface;
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
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.InitiativeScore;
import com.massimodz8.collaborativegrouporder.MaxUtils;
import com.massimodz8.collaborativegrouporder.MyActorRoundActivity;
import com.massimodz8.collaborativegrouporder.PreSeparatorDecorator;
import com.massimodz8.collaborativegrouporder.R;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;

import java.util.ArrayDeque;
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
                final BattleHelper battle = game.sessionHelper.session.battleState;
                battle.round = 1;
                battle.currentActor = battle.ordered[0].actorID;

                fab.setVisibility(View.GONE);
                MaxUtils.beginDelayedTransition(BattleActivity.this);
                final TextView status = (TextView) findViewById(R.id.ba_roundCount);
                status.setText(String.format(Locale.ROOT, getString(R.string.ba_roundNumber), battle.round));
                lister.notifyItemChanged(0);
                activateNewActor();
            }
        });
    }

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
                        if(el.actorID == actor.peerKey) {
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
                    if (actor.peerKey != state.currentActor) {
                        if(selected.isEnabled()) selected.setChecked(!selected.isChecked()); // toggle 'will act next round'
                    } else {
                        activateNewActor();
                    }
                }
            };
            result.prepared.setOnClickListener(new TriggerPreparedActionListener(result));
            return result;
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
        protected boolean isCurrent(Network.ActorState actor) {
            if (game == null) return false;
            final BattleHelper battle = game.sessionHelper.session.battleState;
            if(battle.triggered != null) {
                final Network.ActorState interruptor = game.sessionHelper.session.getActorById(battle.triggered.getLast());
                if(interruptor.preparedTriggered && actor.peerKey == interruptor.peerKey) return true;
            }
            return actor.peerKey == battle.currentActor;
        }

        @Override
        public Network.ActorState getActorByPos(int position) {
            final SessionHelper.PlayState session = game.sessionHelper.session;
            return session.getActorById(session.battleState.ordered[position].actorID);
        }

        @Override
        protected boolean isChecked(Network.ActorState actor) {
            int index = 0;
            final BattleHelper battle = game.sessionHelper.session.battleState;
            for (InitiativeScore test : battle.ordered) {
                if(actor.peerKey == test.actorID) return battle.ordered[index].enabled;
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
            if (target.actor == null || target.actor.prepareCondition == null)
                return; // impossible by context
            final BattleHelper battle = game.sessionHelper.session.battleState;
            if (battle.triggered == null) battle.triggered = new ArrayDeque<>();
            battle.triggered.push(target.actor.peerKey);
            target.actor.preparedTriggered = true;
            int interrupted = battle.actorCompleted(false);
            battle.currentActor = interrupted;
            int newPos = 0;
            for (InitiativeScore el : battle.ordered) {
                if(el.actorID == interrupted) break;
                newPos++;
            }
            if(battle.moveCurrentToSlot(newPos)) {
                game.pushBattleOrder();
                battle.actorCompleted(true);
                activateNewActor();
            }
        }
    }

    @Override
    protected void onDestroy() {
        if(game != null) game.onRemoteTurnCompleted.pop();
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
        battle.actorCompleted(true);
        final int currid = battle.currentActor;
        int currSlot = 0;
        for(int loop = 0; loop < lister.getItemCount(); loop++) {
            final Network.ActorState actor = lister.getActorByPos(loop);
            if(actor.peerKey == currid) currSlot = loop;
        }
        lister.notifyDataSetChanged(); // check everything, player might have healed or damaged others
        MaxUtils.beginDelayedTransition(this);
        final TextView status = (TextView) findViewById(R.id.ba_roundCount);
        status.setText(String.format(Locale.ROOT, getString(R.string.ba_roundNumber), battle.round));
        final Network.ActorState actor = game.sessionHelper.session.getActorById(currid);
        if(actor.prepareCondition.isEmpty() || battle.fromReadiedStack) {
            activateNewActor();
            return;
        }
        final int currentSlot = currSlot;
        new AlertDialog.Builder(this)
                .setMessage(String.format(getString(R.string.ba_dlg_gotPreparedAction), actor.name))
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
                MessageChannel pipe = game.assignmentHelper.getMessageChannelByPeerKey(actor.peerKey);
                if(pipe == null) { // we mangle it there. That's nice.
                    actor.prepareCondition = null;
                    MaxUtils.beginDelayedTransition(BattleActivity.this);
                    lister.notifyItemChanged(currentSlot);
                    activateNewActor();
                    return;
                }
                final PcAssignmentHelper.PlayingDevice dev = game.assignmentHelper.getDevice(pipe);
                game.assignmentHelper.activateRemote(dev, actor.peerKey, Network.TurnControl.T_PREPARED_CANCELLED, battle.round);
                game.assignmentHelper.activateRemote(dev, actor.peerKey, Network.TurnControl.T_REGULAR, battle.round);
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
        final BattleHelper battle = game.sessionHelper.session.battleState;
        lister.notifyDataSetChanged();
        final RecyclerView rv = (RecyclerView) findViewById(R.id.ba_orderedList);
        rv.setAdapter(lister);
        rv.addItemDecoration(new PreSeparatorDecorator(rv, this) {
            @Override
            protected boolean isEligible(int position) {
                return true;
            }
        });
        rv.setVisibility(View.VISIBLE);
        if(battle.round > 0) { // battle already started...
            findViewById(R.id.fab).setVisibility(View.GONE);
            final TextView status = (TextView) findViewById(R.id.ba_roundCount);
            status.setText(String.format(Locale.ROOT, getString(R.string.ba_roundNumber), battle.round));
        }
        game.onRemoteTurnCompleted.push(new Runnable() {
            @Override
            public void run() { actionCompleted(); }
        });
        game.onRemoteActorShuffled.push(new Runnable() {
            @Override
            public void run() {
                actionCompleted();
            }
        });
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
    }
    // ServiceConnection ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
}

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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.InitiativeScore;
import com.massimodz8.collaborativegrouporder.MaxUtils;
import com.massimodz8.collaborativegrouporder.MyActorRoundActivity;
import com.massimodz8.collaborativegrouporder.PreSeparatorDecorator;
import com.massimodz8.collaborativegrouporder.R;
import com.massimodz8.collaborativegrouporder.SendRequest;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.networkio.ProtoBufferEnum;
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
                activateNewActorLocal();
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
                        activateNewActorLocal();
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
            if (battle.interrupted == null) battle.interrupted = new ArrayDeque<>();
            battle.interrupted.push(battle.currentActor);
            target.actor.preparedTriggered = true;
            int newPos = 0;
            for (InitiativeScore el : battle.ordered) {
                if(el.actorID == battle.currentActor) break;
                newPos++;
            }
            battle.currentActor = target.actor.peerKey;
            if(battle.moveCurrentToSlot(newPos, true)) game.pushBattleOrder();
            // This is not necessary, we really go with the interruptor and start back from there.
            //battle.actorCompleted(true);
            if(target.actor.peerKey < game.assignmentHelper.assignment.size()) { // chance it could be remote
                Integer index = game.assignmentHelper.assignment.get(target.actor.peerKey);
                if(index != null && index != PcAssignmentHelper.LOCAL_BINDING) {
                    final MessageChannel pipe = game.assignmentHelper.peers.get(index).pipe;
                    Network.TurnControl activation = new Network.TurnControl();
                    activation.type = Network.TurnControl.T_PREPARED_TRIGGERED;
                    activation.peerKey = target.actor.peerKey;
                    activation.round = battle.round;
                    if(pipe != null) game.assignmentHelper.mailman.out.add(new SendRequest(pipe, ProtoBufferEnum.TURN_CONTROL, activation));
                }
            }
            lister.notifyDataSetChanged();
            activateNewActorLocal();
        }
    }

    @Override
    protected void onDestroy() {
        if(game != null) {
            game.onTurnCompletedRemote.pop();
            game.onActorShuffledRemote.pop();
            game.onActorUpdatedRemote.pop();
        }
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.battle_activity, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.ba_menu_endBattle: {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.ba_endBattleDlg_title)
                        .setMessage(R.string.ba_endBattleDlg_msg)
                        .setPositiveButton(R.string.ba_endBattleDlg_positive, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                setResult(RESULT_OK_AWARD);
                                finish();
                            }
                        })
                        .show();
                break;
            }
        }
        return false;
    }

    static final int RESULT_OK_AWARD = RESULT_FIRST_USER;

    private void actionCompleted() {
        final BattleHelper battle = game.sessionHelper.session.battleState;
        int previous = battle.actorCompleted(true);
        if(battle.prevWasReadied) {
            Network.ActorState was = game.sessionHelper.session.getActorById(previous);
            was.prepareCondition = "";
            was.preparedTriggered = false;
        }
        int currSlot = 0;
        for(int loop = 0; loop < lister.getItemCount(); loop++) {
            final Network.ActorState actor = lister.getActorByPos(loop);
            if(actor.peerKey == battle.currentActor) currSlot = loop;
        }
        lister.notifyDataSetChanged(); // check everything, player might have healed or damaged others
        MaxUtils.beginDelayedTransition(this);
        final TextView status = (TextView) findViewById(R.id.ba_roundCount);
        status.setText(String.format(Locale.ROOT, getString(R.string.ba_roundNumber), battle.round));
        final Network.ActorState actor = game.sessionHelper.session.getActorById(battle.currentActor);
        final MessageChannel pipe = game.assignmentHelper.getMessageChannelByPeerKey(actor.peerKey);
        if(actor.prepareCondition.isEmpty()) {
            if(pipe != null) {
                Network.TurnControl payload = new Network.TurnControl();
                payload.peerKey = actor.peerKey;
                payload.type = Network.TurnControl.T_REGULAR;
                payload.round = battle.round;
                game.assignmentHelper.mailman.out.add(new SendRequest(pipe, ProtoBufferEnum.TURN_CONTROL, payload));
            }
            activateNewActorLocal();
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
                        actor.prepareCondition = "";
                        actor.preparedTriggered = false;
                        MaxUtils.beginDelayedTransition(BattleActivity.this);
                        lister.notifyItemChanged(currentSlot);
                        if(pipe != null) { // we mangle it there. That's nice.
                            final PcAssignmentHelper.PlayingDevice dev = game.assignmentHelper.getDevice(pipe);
                            final Network.ActorState temp = new Network.ActorState();
                            temp.type = Network.ActorState.T_PARTIAL_PREPARE_CONDITION;
                            temp.peerKey = actor.peerKey;
                            game.assignmentHelper.mailman.out.add(new SendRequest(pipe, ProtoBufferEnum.ACTOR_DATA_UPDATE, temp));
                            Network.TurnControl payload = new Network.TurnControl();
                            payload.peerKey = actor.peerKey;
                            payload.type = Network.TurnControl.T_REGULAR;
                            payload.round = battle.round;
                            if(dev.pipe != null) game.assignmentHelper.mailman.out.add(new SendRequest(dev.pipe, ProtoBufferEnum.TURN_CONTROL, payload));
                        }
                        activateNewActorLocal();
                    }
                }).setCancelable(false)
                .show();
    }

    private void activateNewActorLocal() {        // If played here open detail screen. Otherwise, send your-turn message.
        int active = game.sessionHelper.session.battleState.currentActor;
        if(active < game.assignmentHelper.assignment.size()) {
            Integer own = game.assignmentHelper.assignment.get(active);
            if(own != null && own != PcAssignmentHelper.LOCAL_BINDING) {
                Snackbar.make(findViewById(R.id.activityRoot), R.string.ba_actorByPlayerSnack, Snackbar.LENGTH_SHORT).show();
                return;
            }
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
        game.onTurnCompletedRemote.push(new Runnable() {
            @Override
            public void run() { actionCompleted(); }
        });
        game.onActorShuffledRemote.push(new Runnable() {
            @Override
            public void run() {
                actionCompleted();
            }
        });
        game.onActorUpdatedRemote.push(new Runnable() {
            @Override
            public void run() {
                lister.notifyDataSetChanged();
            }
        });
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
    }
    // ServiceConnection ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
}

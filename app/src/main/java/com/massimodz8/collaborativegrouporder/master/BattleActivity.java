package com.massimodz8.collaborativegrouporder.master;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
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

import com.google.firebase.analytics.FirebaseAnalytics;
import com.massimodz8.collaborativegrouporder.InitiativeScore;
import com.massimodz8.collaborativegrouporder.MaxUtils;
import com.massimodz8.collaborativegrouporder.MyActorRoundActivity;
import com.massimodz8.collaborativegrouporder.PreSeparatorDecorator;
import com.massimodz8.collaborativegrouporder.R;
import com.massimodz8.collaborativegrouporder.RunningServiceHandles;
import com.massimodz8.collaborativegrouporder.SendRequest;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.networkio.ProtoBufferEnum;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;
import com.massimodz8.collaborativegrouporder.protocol.nano.UserOf;

import java.util.ArrayDeque;
import java.util.Locale;

public class BattleActivity extends AppCompatActivity {
    private @UserOf PartyJoinOrder game;

    @Override
    public void onBackPressed() {
        backDialog();
    }

    @Override
    public boolean onSupportNavigateUp() {
        backDialog();
        return false;
    }

    private void backDialog() {
        final SessionHelper session = game.session;
        final Network.TurnControl msg = new Network.TurnControl();
        msg.type = Network.TurnControl.T_BATTLE_ENDED;
        new AlertDialog.Builder(this, R.style.AppDialogStyle)
                .setTitle(R.string.generic_carefulDlgTitle)
                .setMessage(R.string.ba_backDlgMessage)
                .setPositiveButton(R.string.ba_backDlgPositive, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // We don't really do it there. The parent activity does.
                        setResult(RESULT_OK_SUSPEND);
                        for (PcAssignmentHelper.PlayingDevice client : game.assignmentHelper.peers) {
                            if(client.pipe == null) continue;
                            game.assignmentHelper.mailman.out.add(new SendRequest(client.pipe, ProtoBufferEnum.TURN_CONTROL, msg, null));
                        }
                        // This could also send session terminate directly but I don't.
                        // It's easier for everyone if I send 'close session' only when not fighting.
                        finish();
                        FirebaseAnalytics.getInstance(BattleActivity.this).logEvent(MaxUtils.FA_EVENT_BATTLE_STOP, null);
                    }
                })
                .setNegativeButton(R.string.generic_discard, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        for (Network.ActorState goner : session.temporaries) session.willFight(goner.peerKey, false);
                        session.temporaries.clear();
                        session.battleState = null;
                        for (PcAssignmentHelper.PlayingDevice client : game.assignmentHelper.peers) {
                            if(client.pipe == null) continue;
                            game.assignmentHelper.mailman.out.add(new SendRequest(client.pipe, ProtoBufferEnum.TURN_CONTROL, msg, null));
                        }
                        finish();
                        Bundle bundle = new Bundle();
                        bundle.putBoolean(MaxUtils.FA_PARAM_BATTLE_DISCARDED, true);
                        FirebaseAnalytics.getInstance(BattleActivity.this).logEvent(MaxUtils.FA_EVENT_BATTLE_STOP, bundle);
                    }
                })
                .show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_battle);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        final ActionBar sab = getSupportActionBar();
        if (null != sab) sab.setDisplayHomeAsUpEnabled(true);
        final RecyclerView rv = (RecyclerView) findViewById(R.id.ba_orderedList);
        rv.setAdapter(lister);
        game = RunningServiceHandles.getInstance().play;
        lister.playState = game.session;
    }

    @Override
    protected void onResume() {
        super.onResume();
        final BattleHelper battle = game.session.battleState;
        if(battle == null) {
            // this happens on devices with 'destroy activities' option
            // The activity needs to be recreated so it can process onActivityResult meh!
            // Do I want to call finish on this one?
            //finish(); // no, I call it onActivityResult instead.
            return;
        }
        lister.notifyDataSetChanged();
        final RecyclerView rv = (RecyclerView) findViewById(R.id.ba_orderedList);
        rv.addItemDecoration(new PreSeparatorDecorator(rv, this) {
            @Override
            protected boolean isEligible(int position) {
                return true;
            }
        });
        rv.setVisibility(View.VISIBLE);
        turnCallback = game.onTurnCompletedRemote.put(new Runnable() {
            @Override
            public void run() { actionCompleted(true); }
        });
        shuffleCallback = game.onActorShuffledRemote.put(new Runnable() {
            @Override
            public void run() {
                actionCompleted(true);
            }
        });
        updatedCallback = game.onActorUpdatedRemote.put(new Runnable() {
            @Override
            public void run() {
                lister.notifyDataSetChanged();
            }
        });
        if(battle.round > 0) { // battle already started...
            MaxUtils.setVisibility(this, View.GONE, R.id.fab, R.id.ba_hint);
            findViewById(R.id.fab).setVisibility(View.GONE);
            final TextView status = (TextView) findViewById(R.id.ba_roundCount);
            status.setText(String.format(Locale.getDefault(), getString(R.string.ba_roundNumber), battle.round));
            actionCompleted(false);
        }

        final FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final BattleHelper battle = game.session.battleState;
                battle.round = 0;
                battle.currentActor = battle.ordered[battle.ordered.length - 1].actorID;

                fab.setVisibility(View.GONE);
                findViewById(R.id.ba_hint).setVisibility(View.GONE);
                MaxUtils.beginDelayedTransition(BattleActivity.this);
                final TextView status = (TextView) findViewById(R.id.ba_roundCount);
                status.setText(String.format(Locale.getDefault(), getString(R.string.ba_roundNumber), battle.round));
                actionCompleted(true);
                lister.notifyItemChanged(0);
            }
        });
    }

    private AdventuringActorWithControlsAdapter lister = new AdventuringActorWithControlsAdapter() {
        @Override
        public AdventuringActorControlsVH onCreateViewHolder(ViewGroup parent, int viewType) {
            final AdventuringActorControlsVH result = new AdventuringActorControlsVH(BattleActivity.this.getLayoutInflater().inflate(R.layout.vh_adventuring_actor_controls, parent, false)) {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (actor == null) return;
                    for (InitiativeScore el : game.session.battleState.ordered) {
                        if(el.actorID == actor.peerKey) {
                            el.enabled = isChecked;
                            break;
                        }
                    }
                }

                @Override
                public void onClick(View v) {
                    if (game == null || game.session == null || game.session.battleState == null) {
                        if (selected.isEnabled())
                            selected.setChecked(!selected.isChecked()); // toggle 'will act next round'
                        return;
                    }
                    final BattleHelper state = game.session.battleState;
                    if (actor.peerKey != state.currentActor) {
                        if(selected.isEnabled()) selected.setChecked(!selected.isChecked()); // toggle 'will act next round'
                    } else {
                        game.session.lastActivated = BattleHelper.INVALID_ACTOR;
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
            return game.session.battleState.ordered.length;
        }

        @Override
        protected boolean isCurrent(Network.ActorState actor) {
            if (game == null) return false;
            final BattleHelper battle = game.session.battleState;
            return actor.peerKey == battle.currentActor;
        }

        @Override
        public Network.ActorState getActorByPos(int position) {
            final SessionHelper session = game.session;
            return session.getActorById(session.battleState.ordered[position].actorID);
        }

        @Override
        protected boolean isChecked(Network.ActorState actor) {
            int index = 0;
            final BattleHelper battle = game.session.battleState;
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
            final BattleHelper battle = game.session.battleState;
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
            if(target.actor.peerKey < game.assignmentHelper.assignment.length) { // chance it could be remote
                int index = game.assignmentHelper.assignment[target.actor.peerKey];
                if(index != PcAssignmentHelper.PlayingDevice.INVALID_ID && index != PcAssignmentHelper.PlayingDevice.LOCAL_ID && target.actor.peerKey != game.session.lastActivated) {
                    MessageChannel pipe = null;
                    for (PcAssignmentHelper.PlayingDevice dev : game.assignmentHelper.peers) {
                        if(dev.keyIndex == index) {
                            pipe = dev.pipe;
                            break;
                        }
                    }
                    Network.TurnControl activation = new Network.TurnControl();
                    activation.type = Network.TurnControl.T_PREPARED_TRIGGERED;
                    activation.peerKey = target.actor.peerKey;
                    activation.round = battle.round;
                    game.session.lastActivated = target.actor.peerKey;
                    if(pipe != null) game.assignmentHelper.mailman.out.add(new SendRequest(pipe, ProtoBufferEnum.TURN_CONTROL, activation, null));
                }
            }
            lister.notifyDataSetChanged();
            activateNewActorLocal();
            FirebaseAnalytics.getInstance(BattleActivity.this).logEvent(MaxUtils.FA_EVENT_READIED_ACTION_TRIGGERED, null);
        }
    }

    @Override
    protected void onPause() {
        if(game != null) {
            game.onTurnCompletedRemote.remove(turnCallback);
            game.onActorShuffledRemote.remove(shuffleCallback);
            game.onActorUpdatedRemote.remove(updatedCallback);
        }
        super.onPause();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // In this case using RSH is fine as for sure we're not getting destroyed... hopefully!
        final PartyJoinOrder game = RunningServiceHandles.getInstance().play;
        SessionHelper session = game.session;
        if(session.battleState == null) {
            finish();
            return;
        }
        if(requestCode != REQUEST_MONSTER_TURN) return;
        if(resultCode != RESULT_OK) {
            MaxUtils.beginDelayedTransition(this);
            // Even if the dude is still doing his round we regen everything. Why?
            // Typical case: he did an attack and made damage. It's not like I want to track those things.
            lister.notifyDataSetChanged();
            return;
        }
        session.lastActivated = BattleHelper.INVALID_ACTOR;
        actionCompleted(true);
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
                new AlertDialog.Builder(this, R.style.AppDialogStyle)
                        .setTitle(R.string.ba_endBattleDlg_title)
                        .setMessage(R.string.ba_endBattleDlg_msg)
                        .setPositiveButton(R.string.ba_endBattleDlg_positive, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                setResult(RESULT_OK_AWARD);
                                Network.TurnControl msg = new Network.TurnControl();
                                msg.type = Network.TurnControl.T_BATTLE_ENDED;
                                for (PcAssignmentHelper.PlayingDevice client : game.assignmentHelper.peers) {
                                    if(client.pipe == null) continue;
                                    game.assignmentHelper.mailman.out.add(new SendRequest(client.pipe, ProtoBufferEnum.TURN_CONTROL, msg, null));
                                }
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
    static final int RESULT_OK_SUSPEND = RESULT_OK_AWARD + 1;

    private void actionCompleted(boolean advance) {
        // Might be called from .onActivityResult, be careful! Hope to use RSH
        final PartyJoinOrder game = RunningServiceHandles.getInstance().play;
        final BattleHelper battle = game.session.battleState;
        int previous = advance? battle.actorCompleted(true) : battle.currentActor;
        if(battle.prevWasReadied) {
            Network.ActorState was = game.session.getActorById(previous);
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
        status.setText(String.format(Locale.getDefault(), getString(R.string.ba_roundNumber), battle.round));
        final Network.ActorState actor = game.session.getActorById(battle.currentActor);
        final MessageChannel pipe = game.assignmentHelper.getMessageChannelByPeerKey(actor.peerKey);
        if(actor.prepareCondition.isEmpty()) {
            if(pipe != null && game.session.lastActivated != actor.peerKey) {
                Network.TurnControl payload = new Network.TurnControl();
                payload.peerKey = actor.peerKey;
                payload.type = Network.TurnControl.T_REGULAR;
                payload.round = battle.round;
                game.assignmentHelper.mailman.out.add(new SendRequest(pipe, ProtoBufferEnum.TURN_CONTROL, payload, null));
            }
            activateNewActorLocal();
            return;
        }
        final int currentSlot = currSlot;
        final FirebaseAnalytics survey = FirebaseAnalytics.getInstance(this);
        final Bundle info = new Bundle();
        new AlertDialog.Builder(this, R.style.AppDialogStyle)
                .setMessage(String.format(getString(R.string.ba_dlg_gotPreparedAction), actor.name))
                .setPositiveButton(R.string.ba_dlg_gotPreparedAction_renew, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Renewing is super cool - the dude will just not act and we're nice with it.
                        actionCompleted(true);
                        info.putBoolean(MaxUtils.FA_PARAM_READIED_ACTION_RENEWED, true);
                        survey.logEvent(MaxUtils.FA_EVENT_READIED_ACTION_TICKED, info);
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
                            game.assignmentHelper.mailman.out.add(new SendRequest(pipe, ProtoBufferEnum.ACTOR_DATA_UPDATE, temp, null));
                            Network.TurnControl payload = new Network.TurnControl();
                            payload.peerKey = actor.peerKey;
                            payload.type = Network.TurnControl.T_REGULAR;
                            payload.round = battle.round;
                            if(dev.pipe != null && game.session.lastActivated != actor.peerKey) game.assignmentHelper.mailman.out.add(new SendRequest(dev.pipe, ProtoBufferEnum.TURN_CONTROL, payload, null));
                            game.session.lastActivated = actor.peerKey;
                        }
                        activateNewActorLocal();
                        survey.logEvent(MaxUtils.FA_EVENT_READIED_ACTION_TICKED, info);
                    }
                }).setCancelable(false)
                .show();
    }

    private void activateNewActorLocal() {        // If played here open detail screen. Otherwise, send your-turn message.
        int active = game.session.battleState.currentActor;
        if(active < game.assignmentHelper.assignment.length) {
            int own = game.assignmentHelper.assignment[active];
            if(own != PcAssignmentHelper.PlayingDevice.INVALID_ID && own != PcAssignmentHelper.PlayingDevice.LOCAL_ID) {
                Snackbar.make(findViewById(R.id.activityRoot), R.string.ba_actorByPlayerSnack, Snackbar.LENGTH_SHORT).show();
                return;
            }
        }
        if(active == game.session.lastActivated) return;
        game.session.lastActivated = active;
        final Intent intent = new Intent(this, MyActorRoundActivity.class)
                .putExtra(MyActorRoundActivity.EXTRA_SUPPRESS_VIBRATION, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivityForResult(intent, REQUEST_MONSTER_TURN);
    }

    private static final int REQUEST_MONSTER_TURN = 1;
    private int turnCallback, shuffleCallback, updatedCallback;
}

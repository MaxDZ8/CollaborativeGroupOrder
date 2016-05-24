package com.massimodz8.collaborativegrouporder;

import android.app.Activity;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.design.widget.Snackbar;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.client.AdventuringService;
import com.massimodz8.collaborativegrouporder.master.BattleHelper;
import com.massimodz8.collaborativegrouporder.master.PartyJoinOrderService;
import com.massimodz8.collaborativegrouporder.networkio.ProtoBufferEnum;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;

import java.util.Locale;

public class MyActorRoundActivity extends AppCompatActivity {
    public static final String EXTRA_SUPPRESS_VIBRATION = "com.massimodz8.collaborativegrouporder.MyActorRoundActivity.EXTRA_SUPPRESS_VIBRATION";
    public static final String EXTRA_CLIENT_MODE = "com.massimodz8.collaborativegrouporder.MyActorRoundActivity.EXTRA_CLIENT_MODE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_actor_round);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        final ActionBar sab = getSupportActionBar();
        if(null != sab) sab.setDisplayHomeAsUpEnabled(true);

        final Network.ActorState actor;
        final int round;
        final String nextActor;
        if(getIntent().getBooleanExtra(EXTRA_CLIENT_MODE, false)) {
            final AdventuringService client = RunningServiceHandles.getInstance().clientPlay;
            actorChangedCall = client.onCurrentActorChanged.put(new Runnable() {
                @Override
                public void run() {
                    finish(); // do I have to do something more here? IDK.
                }
            });
            final Runnable parent = client.onSessionEnded.get();
            endedCall = client.onSessionEnded.put(new Runnable() {
                @Override
                public void run() {
                    finish();
                    if(parent != null) parent.run();
                }
            });
            actor = client.actors.get(client.currentActor).actor;
            round = client.round;
            nextActor = null;
        }
        else {
            final PartyJoinOrderService server = RunningServiceHandles.getInstance().play;
            BattleHelper battle  = server.session.battleState;
            int curid = battle.actorCompleted(false);
            actor = server.session.getActorById(curid);
            round = battle.round;
            nextActor = server.session.getActorById(battle.currentActor).name;
            battle.currentActor = curid;
        }

        View holder = findViewById(R.id.vhRoot);
        AdventuringActorDataVH helper = new AdventuringActorDataVH(holder) {
            @Override
            public void onClick(View v) {
            }
        };
        helper.bindData(actor);
        holder.setVisibility(View.VISIBLE);
        helper.prepared.setEnabled(false);
        ((TextView) findViewById(R.id.mara_round)).setText(String.format(Locale.ROOT, getString(R.string.mara_round), round));
        MaxUtils.setTextUnlessNull((TextView) findViewById(R.id.mara_nextActorName), nextActor, View.GONE);

        if(savedInstanceState != null) return; // when regenerated, probably because of user rotating device, no need to gain more attention.

        if(!getIntent().getBooleanExtra(EXTRA_SUPPRESS_VIBRATION, false)) {
            // We cheat and give out notifications right away. By the time the user reacts we'll have
            // managed to connect to service and pulled the data.
            final Vibrator vibro = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vibro != null && vibro.hasVibrator()) {
                final long[] intervals = {0, 350, 300, 250};
                vibro.vibrate(intervals, -1);
            }
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // This apparently does not work on several devices, including mine.
        ////final AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        ////if(am != null) am.playSoundEffect(AudioManager.FX_KEY_CLICK);

        // Using ringtones is too complicated, seems to require extra permissions and sound is distracting anyway.
        // I would like to try using the notification led but my smartphone does not have it anyway.
    }

    @Override
    protected void onDestroy() {
        final AdventuringService client = RunningServiceHandles.getInstance().clientPlay;
        if(client != null) {
            client.onCurrentActorChanged.remove(actorChangedCall);
            client.onSessionEnded.remove(endedCall);
        }
        if(!isChangingConfigurations()) getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        final AdventuringService client = RunningServiceHandles.getInstance().clientPlay;
        if(client == null) {
            super.onBackPressed();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.generic_nopeDlgTitle)
                .setMessage(R.string.mara_noBackDlgMessage)
                .setPositiveButton(R.string.mara_next_title, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) { turnDone(); }
                })
                .show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        final AdventuringService client = RunningServiceHandles.getInstance().clientPlay;
        if(client == null) return super.onSupportNavigateUp();
        new AlertDialog.Builder(this)
                .setTitle(R.string.generic_nopeDlgTitle)
                .setMessage(R.string.mara_noBackDlgMessage)
                .setPositiveButton(R.string.mara_next_title, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) { turnDone(); }
                })
                .show();
        return false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.my_actor_round_activity, menu);
        imDone = menu.findItem(R.id.mara_menu_doneConfirmed);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch(item.getItemId()) {
            case R.id.mara_menu_done:
                imDone.setVisible(true);
                item.setVisible(false);
                break;
            case R.id.mara_menu_doneConfirmed:
                // That's quite small and remote, unlikely it gets hit by accident.
                turnDone();
                break;
            case R.id.mara_menu_shuffle: {
                final int myIndex;
                final Network.ActorState[] order;
                final PartyJoinOrderService server = RunningServiceHandles.getInstance().play;
                final AdventuringService client = RunningServiceHandles.getInstance().clientPlay;
                if(server == null && client == null) {
                    Snackbar.make(findViewById(R.id.activityRoot), R.string.generic_validButTooEarly, Snackbar.LENGTH_SHORT).show();
                    break; // possible, if user hits button before service connection estabilished. Not likely.
                }
                if(server != null) {
                    BattleHelper battle  = server.session.battleState;
                    order = new Network.ActorState[battle.ordered.length];
                    int cp = 0;
                    int gotcha = 0;
                    for (InitiativeScore el : battle.ordered) {
                        order[cp++] = server.session.getActorById(el.actorID);
                        if(el.actorID == battle.currentActor) gotcha = cp - 1;
                    }
                    myIndex = gotcha;
                }
                else if(client.currentActor == -1) {
                    Snackbar.make(findViewById(R.id.activityRoot), R.string.generic_validButTooEarly, Snackbar.LENGTH_SHORT).show();
                    break; // impossible by construction if we're here, but maybe state is slightly inconsistent as just transitioned. Protect from future changes.
                }
                else {
                    AdventuringService.ActorWithKnownOrder current = client.actors.get(client.currentActor);
                    if(current == null || current.keyOrder == null) break; // impossible if we are here!
                    order = new Network.ActorState[current.keyOrder.length];
                    int dst = 0;
                    for (int key : current.keyOrder) order[dst++] = client.actors.get(key).actor;
                    dst = 0;
                    for (int key : current.keyOrder) {
                        if(key == current.actor.peerKey) break;
                        dst++;
                    }
                    myIndex = dst;
                }
                new InitiativeShuffleDialog(order, myIndex)
                        .show(MyActorRoundActivity.this, new InitiativeShuffleDialog.OnApplyCallback() {
                            @Override
                            public void newOrder(int newPos) {
                                requestNewOrder(newPos);
                                turnDone();
                            }
                        });
            } break;
            case R.id.mara_menu_readiedAction: {
                final AlertDialog dlg = new AlertDialog.Builder(this).setView(R.layout.dialog_ready_action_proposal)
                        .setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface dialog) {
                                InputMethodManager imm = (InputMethodManager)MyActorRoundActivity.this.getSystemService(Activity.INPUT_METHOD_SERVICE);
                                final View focused = MyActorRoundActivity.this.getCurrentFocus();
                                if(focused != null && imm != null && imm.isActive()) {
                                    imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
                                }
                            }
                        }).create();
                dlg.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.mara_dlgRAP_apply), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            EditText text = (EditText) dlg.findViewById(R.id.mara_dlgRAP_optionalMessage);
                            final String s = text.getText().toString();
                            if(!s.isEmpty()) requestReadiedAction(s);
                            else requestReadiedAction(getString(R.string.mara_dlg_readyAction_title));
                            turnDone();
                        }
                    }
                );
                dlg.show();
                dlg.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
            } break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void turnDone() {
        final AdventuringService client = RunningServiceHandles.getInstance().clientPlay;
        if(client != null) {
            AdventuringService.ActorWithKnownOrder current = client.actors.get(client.currentActor);
            if(current == null) return; // impossible
            if(current.actor.preparedTriggered) {
                current.actor.prepareCondition = "";
                current.actor.preparedTriggered = false;
            }
            Network.TurnControl done = new Network.TurnControl();
            done.type = Network.TurnControl.T_FORCE_DONE;
            done.peerKey = client.currentActor;
            client.mailman.out.add(new SendRequest(client.pipe, ProtoBufferEnum.TURN_CONTROL, done, null));
        }
        setResult(RESULT_OK);
        finish();
    }

    /// Called from the 'wait' dialog to request to shuffle my actor somewhere else.
    private void requestNewOrder(int newPos) {
        final PartyJoinOrderService server = RunningServiceHandles.getInstance().play;
        final AdventuringService client = RunningServiceHandles.getInstance().clientPlay;
        if(server == null && client == null) return; // impossible
        if(server != null) {
            if(server.session.battleState.moveCurrentToSlot(newPos, false)) {
                server.pushBattleOrder();
                setResult(RESULT_OK);
                finish();
            }
            return;
        }
        // If request is valid then it will be accepted so no need to track this, it will be accepted.
        final Network.BattleOrder send = new Network.BattleOrder();
        send.asKnownBy = client.currentActor;
        send.order = new int[] { newPos };
        // Nope. We wait the server to update.
        //client.currentActor.keyOrder = send.order;
        client.mailman.out.add(new SendRequest(client.pipe, ProtoBufferEnum.BATTLE_ORDER, send, null));
    }


    private void requestReadiedAction(String s) {
        final PartyJoinOrderService server = RunningServiceHandles.getInstance().play;
        final AdventuringService client = RunningServiceHandles.getInstance().clientPlay;
        if(client == null && server == null) return; // unlikely
        if(server != null) {
            BattleHelper battle  = server.session.battleState;
            server.session.getActorById(battle.currentActor).prepareCondition = s;
            setResult(RESULT_OK);
            finish();
            return;
        }
        Network.ActorState send = new Network.ActorState();
        send.type = Network.ActorState.T_PARTIAL_PREPARE_CONDITION;
        send.peerKey = client.currentActor;
        send.prepareCondition = s;
        client.mailman.out.add(new SendRequest(client.pipe, ProtoBufferEnum.ACTOR_DATA_UPDATE, send, null));
        AdventuringService.ActorWithKnownOrder current = client.actors.get(client.currentActor);
        if(current == null) return; // impossible
        current.actor.prepareCondition = s;
    }

    private MenuItem imDone;
    private int actorChangedCall, endedCall;
}

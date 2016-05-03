package com.massimodz8.collaborativegrouporder;

import android.app.Activity;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Vibrator;
import android.provider.Telephony;
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
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;

import java.util.Locale;

public class MyActorRoundActivity extends AppCompatActivity implements ServiceConnection {


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
        View holder = findViewById(R.id.vhRoot);
        holder.setVisibility(View.INVISIBLE);

        if(getIntent().getBooleanExtra(EXTRA_CLIENT_MODE, false) && !bindService(new Intent(this, AdventuringService.class), this, 0)) {
            MaxUtils.beginDelayedTransition(this);
            TextView ohno = (TextView) findViewById(R.id.mara_instructions);
            ohno.setText(R.string.client_failedServiceBind);
            return;
        }
        else if(!bindService(new Intent(this, PartyJoinOrderService.class), this, 0)) {
            MaxUtils.beginDelayedTransition(this);
            TextView ohno = (TextView) findViewById(R.id.mara_instructions);
            ohno.setText(R.string.master_cannotBindAdventuringService);
            return;
        }
        mustUnbind = true;

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
        if(mustUnbind) unbindService(this);
        if(!isChangingConfigurations()) getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onDestroy();
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
                setResult(RESULT_OK);
                finish();
                break;
            case R.id.mara_menu_shuffle: {
                Network.ActorState[] order = new Network.ActorState[battle.ordered.length];
                int cp = 0;
                for (InitiativeScore el : battle.ordered) order[cp++] = server.sessionHelper.session.getActorById(el.actorID);
                new InitiativeShuffleDialog(order, battle.currentActor)
                        .show(MyActorRoundActivity.this, new InitiativeShuffleDialog.OnApplyCallback() {
                            @Override
                            public void newOrder(Network.ActorState[] target) {
                                requestNewOrder(target);
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
                            requestReadiedAction(text.getText().toString());
                        }
                    }
                );
                dlg.show();
                dlg.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
            } break;
        }
        return super.onOptionsItemSelected(item);
    }

    /// Called from the 'wait' dialog to request to shuffle my actor somewhere else.
    private void requestNewOrder(Network.ActorState[] target) {
        final InitiativeScore me = battle.ordered[battle.currentActor];
        int newPos = 0;
        while(newPos < target.length && target[newPos] != me.actor) newPos++;
        if(newPos == target.length) return; // wut? Impossible!
        if(newPos == battle.currentActor) return; // he hit apply but really did nothing.
        if(server != null) { // This is cool, we approve of ourselves :P
            battle.shuffleCurrent(newPos);
            setResult(RESULT_OK);
            finish();
            return;
        }
        new AlertDialog.Builder(this).setMessage("TODO: ask permission to server").show();
    }


    private void requestReadiedAction(String s) {
        if(server != null) {
            final InitiativeScore me = battle.ordered[battle.currentActor];
            me.actor.prepareCondition = s;
            setResult(RESULT_OK);
            finish();
            return;
        }
        new AlertDialog.Builder(this).setMessage("TODO: send condition to server").show();
    }

    private boolean mustUnbind;
    private PartyJoinOrderService server;
    private AdventuringService client;
    private BattleHelper battle;
    private MenuItem imDone;

    // ServiceConnection vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        server = service instanceof PartyJoinOrderService? (PartyJoinOrderService)service : null;
        final Network.ActorState actor;
        final int round;
        final String nextActor;
        if(server != null) {
            battle = server.sessionHelper.session.battleState;
            actor = battle.triggered == null ? battle.ordered[battle.currentActor].actor : battle.triggered.get(battle.triggered.size() - 1);
            round = battle.round;
            final int pactor = battle.currentActor;
            battle.tickRound();
            nextActor = battle.ordered[battle.currentActor].actor.name;
            battle.currentActor = pactor;
            battle.round = round;
        }
        else {
            client = (AdventuringService)service;
            actor = client.currentActor.actor;
            round = client.round;
            nextActor = null;
        }
        View holder = findViewById(R.id.vhRoot);
        AdventuringActorDataVH helper = new AdventuringActorDataVH(holder) {
            @Override
            public void onClick(View v) {
            }
        };
        MaxUtils.beginDelayedTransition(this);
        helper.bindData(actor);
        holder.setVisibility(View.VISIBLE);
        ((TextView) findViewById(R.id.mara_round)).setText(String.format(Locale.ROOT, getString(R.string.mara_round), round));
        MaxUtils.setTextUnlessNull((TextView) findViewById(R.id.mara_nextActorName), nextActor, View.GONE);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }
    // ServiceConnection ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
}

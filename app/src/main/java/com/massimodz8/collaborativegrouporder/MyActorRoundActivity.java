package com.massimodz8.collaborativegrouporder;

import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
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
import android.widget.CompoundButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.master.AbsLiveActor;
import com.massimodz8.collaborativegrouporder.master.BattleHelper;
import com.massimodz8.collaborativegrouporder.master.PartyJoinOrderService;

import java.util.IdentityHashMap;
import java.util.Locale;

public class MyActorRoundActivity extends AppCompatActivity implements ServiceConnection {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_actor_round);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        final ActionBar sab = getSupportActionBar();
        if(null != sab) sab.setDisplayHomeAsUpEnabled(true);
        RelativeLayout holder = (RelativeLayout) findViewById(R.id.vhAA_actorTypeShort).getParent();
        holder.setVisibility(View.INVISIBLE);
        holder.findViewById(R.id.vhAA_selected).setVisibility(View.GONE);

        if(!bindService(new Intent(this, PartyJoinOrderService.class), this, 0)) {
            MaxUtils.beginDelayedTransition(this);
            TextView ohno = (TextView) findViewById(R.id.mara_instructions);
            ohno.setText(R.string.master_cannotBindAdventuringService);
            return;
        }
        mustUnbind = true;

        if(savedInstanceState != null) return; // when regenerated, probably because of user rotating device, no need to gain more attention.

        // We cheat and give out notifications right away. By the time the user reacts we'll have
        // managed to connect to service and pulled the data.
        final Vibrator vibro = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if(vibro != null && vibro.hasVibrator()) {
            final long[] intervals = { 0, 350, 300, 250 };
            vibro.vibrate(intervals, -1);
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
            case R.id.mara_menu_shuffle:
                new AlertDialog.Builder(this)
                        .setPositiveButton("Ready action", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                new AlertDialog.Builder(MyActorRoundActivity.this)
                                        .setMessage("TODO: ready action!")
                                        .show();
                            }
                        }).setNeutralButton(getString(R.string.mara_dlg_readyActionButtonLabel), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                AbsLiveActor[] order = new AbsLiveActor[battle.ordered.length];
                                IdentityHashMap<AbsLiveActor, Integer> actorId = new IdentityHashMap<AbsLiveActor, Integer>(battle.ordered.length);
                                int cp = 0;
                                for (InitiativeScore el : battle.ordered) {
                                    order[cp] = el.actor;
                                    actorId.put(el.actor, cp);
                                    cp++;
                                }
                                new InitiativeShuffleDialog(order, battle.currentActor, actorId)
                                        .show(MyActorRoundActivity.this, new InitiativeShuffleDialog.OnApplyCallback() {
                                            @Override
                                            public void newOrder(AbsLiveActor[] target) {
                                                new AlertDialog.Builder(MyActorRoundActivity.this).setMessage("changed").show();
                                            }
                                        });
                            }
                        }).show();
        }
        return super.onOptionsItemSelected(item);
    }

    boolean mustUnbind;
    BattleHelper battle;
    private MenuItem imDone;

    // ServiceConnection vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        PartyJoinOrderService game = ((PartyJoinOrderService.LocalBinder)service).getConcreteService();
        battle = game.getPlaySession().battleState;
        AbsLiveActor actor = battle.ordered[battle.currentActor].actor;
        RelativeLayout holder = (RelativeLayout) findViewById(R.id.vhAA_actorTypeShort).getParent();
        AdventuringActorVH helper = new AdventuringActorVH(holder, null, true) {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            }
        };
        MaxUtils.beginDelayedTransition(this);
        helper.bindData(0, actor);
        holder.setVisibility(View.VISIBLE);
        battle.tickRound();
        TextView tv = (TextView) findViewById(R.id.mara_nextActorName);
        tv.setText(String.format(getString(R.string.mara_nextToAct), battle.ordered[battle.currentActor].actor.displayName));
        tv = (TextView) findViewById(R.id.mara_round);
        tv.setText(String.format(Locale.ROOT, getString(R.string.mara_round), battle.round));
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }
    // ServiceConnection ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
}

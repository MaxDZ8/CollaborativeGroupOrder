package com.massimodz8.collaborativegrouporder;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.master.AbsLiveActor;
import com.massimodz8.collaborativegrouporder.master.BattleHelper;
import com.massimodz8.collaborativegrouporder.master.PartyJoinOrderService;

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
    }

    @Override
    protected void onDestroy() {
        if(mustUnbind) unbindService(this);
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.my_actor_round_activity, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch(item.getItemId()) {
            case R.id.mara_menu_done:
                // That's quite small and remote, unlikely it gets hit by accident.
                nextCharacter();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void nextCharacter() {
        new AlertDialog.Builder(this).setMessage("Let's go next man!").show();
    }

    boolean mustUnbind;
    private PartyJoinOrderService game;

    // ServiceConnection vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        PartyJoinOrderService.LocalBinder real = (PartyJoinOrderService.LocalBinder)service;
        game = real.getConcreteService();
        final BattleHelper battle = game.getPlaySession().battleState;
        AbsLiveActor actor = battle.battlers[battle.currentActor];
        RelativeLayout holder = (RelativeLayout) findViewById(R.id.vhAA_actorTypeShort).getParent();
        AdventuringActorVH helper = new AdventuringActorVH(holder, null) {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            }
        };
        MaxUtils.beginDelayedTransition(this);
        helper.bindData(0, actor);
        holder.setVisibility(View.VISIBLE);
        int nextIndex = battle.currentActor + 1;
        nextIndex %= battle.battlers.length;
        while(nextIndex < battle.currentActor && !battle.enabled[nextIndex]) nextIndex++;
        final TextView nextName = (TextView) findViewById(R.id.mara_nextActorName);
        nextName.setText(String.format(getString(R.string.mara_nextToAct), battle.battlers[nextIndex].displayName));
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }
    // ServiceConnection ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
}

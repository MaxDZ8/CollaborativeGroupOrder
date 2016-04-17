package com.massimodz8.collaborativegrouporder.master;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.MaxUtils;
import com.massimodz8.collaborativegrouporder.R;

public class BattleActivity extends Activity implements ServiceConnection {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_battle);

        if(!bindService(new Intent(this, PartyJoinOrderService.class), this, 0)) {
            MaxUtils.beginDelayedTransition(this);
            TextView ohno = (TextView) findViewById(R.id.fra_instructions);
            ohno.setText(R.string.master_cannotBindAdventuringService);
            return;
        }
        mustUnbind = true;
    }

    boolean mustUnbind;
    private PartyJoinOrderService game;

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
        final TextView target = (TextView) findViewById(R.id.ba_temporaryFeedback);
        String meh = "";
        for(int loop = 0; loop < battle.initiative.length; loop++) {
            meh += String.valueOf(battle.initiative[loop]);
            meh += " - ";
            meh += battle.battlers[loop].displayName;
            meh += '\n';
        }
        target.setText(meh);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
    }
    // ServiceConnection ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
}

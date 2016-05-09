package com.massimodz8.collaborativegrouporder.master;

import android.app.ActionBar;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.app.Activity;
import android.os.IBinder;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.ActorId;
import com.massimodz8.collaborativegrouporder.InitiativeScore;
import com.massimodz8.collaborativegrouporder.MaxUtils;
import com.massimodz8.collaborativegrouporder.R;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;

import java.util.ArrayList;
import java.util.Locale;

public class AwardExperienceActivity extends AppCompatActivity implements ServiceConnection {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_award_experience);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        final android.support.v7.app.ActionBar sab = getSupportActionBar();
        if(null != sab) sab.setDisplayHomeAsUpEnabled(true);

        if(!bindService(new Intent(this, PartyJoinOrderService.class), this, 0)) {
            MaxUtils.beginDelayedTransition(this);
            TextView ohno = (TextView) findViewById(R.id.aea_status);
            ohno.setText(R.string.master_cannotBindAdventuringService);
            return;
        }
        mustUnbind = true;

        final FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(findViewById(R.id.activityRoot), "TODO: distribute XP and update actors!", Snackbar.LENGTH_SHORT);
            }
        });
    }

    @Override
    protected void onDestroy() {
        if(mustUnbind) unbindService(this);
        super.onDestroy();
    }

    private void update() {
        int xp = 0, count = 0;
        for (SessionHelper.PlayState.DefeatedData el : game.sessionHelper.session.defeated) {
            if(el.consume) {
                count++;
                xp += xpFrom(el.numerator, el.denominator);
            }
        }
        String countString = count == game.sessionHelper.session.defeated.size()? getString(R.string.aea_selectedAll) : String.valueOf(count);

        String text = getString(R.string.aea_mobReport);
        text = String.format(Locale.ROOT, text, countString, xp);
        TextView report = (TextView) findViewById(R.id.aea_mobReport);
        report.setText(text);

        count = 0;
        for(SessionHelper.PlayState.WinnerData el : game.sessionHelper.session.winners) {
            if(el.award) count++;

        }
        countString = count == game.sessionHelper.session.winners.size()? getString(R.string.aea_selectedAll) : String.valueOf(count);
        text = getString(R.string.aea_winnersListReport);
        text = String.format(Locale.ROOT, text, countString, xp / count);
        report = (TextView) findViewById(R.id.aea_winnersReport);
        report.setText(text);
    }

    public static int xpFrom(int numerator, int denominator) {
        if(denominator != 1) {
            if(numerator != 1) return numerator * xpFrom(1, denominator); // will never trigger but anyway...
            switch(denominator) {
                case 2: return 200;
                case 3: return 135;
                case 4: return 100;
                case 6: return  65;
                case 8: return  50;
            }
            return 0; // again, will never get there
        }
        if(numerator < 1) return 0; // again, will never get there
        if(numerator == 1) return 400;
        if(numerator == 2) return 600;
        return 2 * xpFrom(numerator - 2, 1);
    }

    private boolean mustUnbind;
    private PartyJoinOrderService game;
    private RecyclerView.Adapter mobLister, winnersLister;

    // ServiceConnection vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        game = ((PartyJoinOrderService.LocalBinder) service).getConcreteService();
        SessionHelper.PlayState session = game.sessionHelper.session;
        if(session.battleState != null) { // consume this and get it to 'to be awarded' data.
            session.defeated = new ArrayList<>();
            session.winners = new ArrayList<>();
            for (InitiativeScore el : session.battleState.ordered) {
                Network.ActorState actor = session.getActorById(el.actorID);
                if(actor.type == Network.ActorState.T_MOB && actor.cr != null) session.defeated.add(new SessionHelper.PlayState.DefeatedData(actor.peerKey, actor.cr.numerator, actor.cr.denominator));
                else if(actor.type == Network.ActorState.T_PLAYING_CHARACTER || actor.type == Network.ActorState.T_NPC) session.winners.add(new SessionHelper.PlayState.WinnerData(actor.peerKey));
            }
            game.sessionHelper.session.battleState = null;
        }
        findViewById(R.id.fab).setVisibility(View.VISIBLE);
        mobLister = new ActorListerWithControls<SessionHelper.PlayState.DefeatedData>(session.defeated, getLayoutInflater(), session) {
            @Override
            protected boolean representedProperty(SessionHelper.PlayState.DefeatedData entry, Boolean newValue) {
                if(newValue != null) entry.consume = newValue;
                update();
                return entry.consume;
            }

            @Override
            protected int getPeerKey(SessionHelper.PlayState.DefeatedData entry) { return entry.id; }

            @Override
            protected boolean match(SessionHelper.PlayState.DefeatedData entry, @ActorId int id) { return entry.id == id; }
        };
        winnersLister = new ActorListerWithControls<SessionHelper.PlayState.WinnerData>(session.winners, getLayoutInflater(), session) {
            @Override
            protected boolean representedProperty(SessionHelper.PlayState.WinnerData entry, Boolean newValue) {
                if(newValue != null) entry.award = newValue;
                update();
                return entry.award;
            }


            @Override
            protected int getPeerKey(SessionHelper.PlayState.WinnerData entry) { return entry.id; }


            @Override
            protected boolean match(SessionHelper.PlayState.WinnerData entry, @ActorId int id) { return entry.id == id; }
        };
        MaxUtils.beginDelayedTransition(this);
        findViewById(R.id.aea_status).setVisibility(View.GONE);
        MaxUtils.setVisibility(this, View.VISIBLE,
                R.id.aea_mobListInfo, R.id.aea_mobList, R.id.aea_mobReport,
                R.id.aea_winnersListInfo, R.id.aea_winnersList, R.id.aea_winnersReport);
        RecyclerView rv = (RecyclerView) findViewById(R.id.aea_mobList);
        rv.setAdapter(mobLister);
        rv = (RecyclerView) findViewById(R.id.aea_winnersList);
        rv.setAdapter(winnersLister);
        update();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
    }
    // ServiceConnection ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
}

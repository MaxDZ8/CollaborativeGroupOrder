package com.massimodz8.collaborativegrouporder.master;

import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.ActorId;
import com.massimodz8.collaborativegrouporder.InitiativeScore;
import com.massimodz8.collaborativegrouporder.MaxUtils;
import com.massimodz8.collaborativegrouporder.R;
import com.massimodz8.collaborativegrouporder.SendRequest;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.networkio.ProtoBufferEnum;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;
import com.massimodz8.collaborativegrouporder.protocol.nano.StartData;

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
                int xp = 0;
                for (int loop = 0; loop < game.session.defeated.size(); loop++) {
                    final SessionHelper.DefeatedData el = game.session.defeated.get(loop);
                    if(el.consume) {
                        xp += xpFrom(el.numerator, el.denominator);
                        game.session.defeated.remove(loop);
                        loop--;

                        int match = -1;
                        for (Network.ActorState test : game.session.temporaries) {
                            match++;
                            if(el.id == test.peerKey) {
                                game.session.temporaries.remove(match);
                                break;
                            }
                        }

                    }
                }
                if(game.session.defeated.isEmpty()) game.session.defeated = null;
                int count = 0;
                for(SessionHelper.WinnerData el : game.session.winners) {
                    if(el.award) count++;
                }
                // == 0 can be used to throw away XPs. Bad idea in general but must be supported.
                // E.G. we have discovered we put there the wrong monster and we're rolling back the whole battle
                if(count != 0) {
                    StartData.ActorDefinition[] pcs = game.getPartyOwnerData().party;
                    for (SessionHelper.WinnerData el : game.session.winners) {
                        if (el.award) {
                            Network.ActorState actor = game.session.getActorById(el.id);
                            actor.experience += xp / count;
                            if (el.id < pcs.length) pcs[el.id].experience += xp / count;

                            MessageChannel pipe = game.assignmentHelper.getMessageChannelByPeerKey(actor.peerKey);
                            if (pipe == null) continue;
                            game.assignmentHelper.mailman.out.add(new SendRequest(pipe, ProtoBufferEnum.ACTOR_DATA_UPDATE, actor));
                        }
                    }
                }
                if(game.session.defeated != null) {
                    mobLister.notifyDataSetChanged();
                    return;
                }
                finish();
            }
        });
    }

    @Override
    protected void onDestroy() {
        if(mustUnbind) unbindService(this);
        super.onDestroy();
    }


    @Override
    public void onBackPressed() { confirmDiscardFinish(); }
    @Override
    public boolean onSupportNavigateUp() {
        confirmDiscardFinish();
        return false;
    }

    private void confirmDiscardFinish() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.generic_carefulDlgTitle)
                .setMessage(R.string.aea_noBackDlgMessage)
                .setPositiveButton(R.string.aea_confirmDlgPosButton, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        for (SessionHelper.DefeatedData el : game.session.defeated) el.consume = true;
                        for (SessionHelper.WinnerData el : game.session.winners) el.award = false;
                        final FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
                        fab.performClick();
                    }
                })
                .show();
    }

    private void update() {
        int xp = 0, count = 0;
        for (SessionHelper.DefeatedData el : game.session.defeated) {
            if(el.consume) {
                count++;
                xp += xpFrom(el.numerator, el.denominator);
            }
        }
        String countString = count == game.session.defeated.size()? getString(R.string.aea_selectedAll) : String.valueOf(count);

        String mob = getString(R.string.aea_mobReport);
        mob = String.format(Locale.ROOT, mob, countString, xp);
        TextView report = (TextView) findViewById(R.id.aea_mobReport);
        report.setText(mob);

        count = 0;
        for(SessionHelper.WinnerData el : game.session.winners) {
            if(el.award) count++;
        }
        countString = count == game.session.winners.size()? getString(R.string.aea_selectedAll) : String.valueOf(count);
        String win = getString(R.string.aea_winnersCount);
        win = String.format(win, countString);
        win += '\n' + (count == 0? "" : String.format(Locale.ROOT, getString(R.string.aea_winnersAward), xp / count));
        report = (TextView) findViewById(R.id.aea_winnersReport);
        report.setText(win);
        findViewById(R.id.fab).setVisibility(count == 0? View.GONE : View.VISIBLE);
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
    private RecyclerView.Adapter mobLister;

    // ServiceConnection vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        game = ((PartyJoinOrderService.LocalBinder) service).getConcreteService();
        SessionHelper session = game.session;
        if(session.battleState != null) { // consume this and get it to 'to be awarded' data.
            session.defeated = new ArrayList<>();
            session.winners = new ArrayList<>();
            for (InitiativeScore el : session.battleState.ordered) {
                Network.ActorState actor = session.getActorById(el.actorID);
                if(actor.type == Network.ActorState.T_MOB && actor.cr != null) session.defeated.add(new SessionHelper.DefeatedData(actor.peerKey, actor.cr.numerator, actor.cr.denominator));
                else if(actor.type == Network.ActorState.T_PLAYING_CHARACTER || actor.type == Network.ActorState.T_NPC) session.winners.add(new SessionHelper.WinnerData(actor.peerKey));
            }
            game.session.battleState = null;
        }
        findViewById(R.id.fab).setVisibility(View.VISIBLE);
        mobLister = new ActorListerWithControls<SessionHelper.DefeatedData>(session.defeated, getLayoutInflater(), session) {
            @Override
            protected boolean representedProperty(SessionHelper.DefeatedData entry, Boolean newValue) {
                if(newValue != null) entry.consume = newValue;
                update();
                return entry.consume;
            }

            @Override
            protected int getPeerKey(SessionHelper.DefeatedData entry) { return entry.id; }

            @Override
            protected boolean match(SessionHelper.DefeatedData entry, @ActorId int id) { return entry.id == id; }
        };
        RecyclerView.Adapter winnersLister = new ActorListerWithControls<SessionHelper.WinnerData>(session.winners, getLayoutInflater(), session) {
            @Override
            protected boolean representedProperty(SessionHelper.WinnerData entry, Boolean newValue) {
                if (newValue != null) entry.award = newValue;
                update();
                return entry.award;
            }


            @Override
            protected int getPeerKey(SessionHelper.WinnerData entry) {
                return entry.id;
            }


            @Override
            protected boolean match(SessionHelper.WinnerData entry, @ActorId int id) {
                return entry.id == id;
            }
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

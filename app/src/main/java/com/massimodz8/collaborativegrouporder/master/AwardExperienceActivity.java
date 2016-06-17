package com.massimodz8.collaborativegrouporder.master;

import android.content.DialogInterface;
import android.os.Bundle;
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
import com.massimodz8.collaborativegrouporder.RunningServiceHandles;
import com.massimodz8.collaborativegrouporder.SendRequest;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.networkio.ProtoBufferEnum;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;
import com.massimodz8.collaborativegrouporder.protocol.nano.StartData;
import com.massimodz8.collaborativegrouporder.protocol.nano.UserOf;

import java.util.ArrayList;
import java.util.Locale;

public class AwardExperienceActivity extends AppCompatActivity {
    private @UserOf PartyJoinOrder game;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_award_experience);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        final android.support.v7.app.ActionBar sab = getSupportActionBar();
        if (null != sab) sab.setDisplayHomeAsUpEnabled(true);
        game = RunningServiceHandles.getInstance().play;

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
                                game.session.willFight(el.id, false);
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
                            if (el.id < pcs.length) {
                                pcs[el.id].experience += xp / count;
                                awarded += xp / count;
                            }

                            MessageChannel pipe = game.assignmentHelper.getMessageChannelByPeerKey(actor.peerKey);
                            if (pipe == null) continue;
                            game.assignmentHelper.mailman.out.add(new SendRequest(pipe, ProtoBufferEnum.ACTOR_DATA_UPDATE, actor, null));
                        }
                    }
                }
                if(game.session.defeated != null) {
                    mobLister.notifyDataSetChanged();
                    return;
                }
                if(awarded != 0) setResult(RESULT_OK);
                finish();
            }
        });
        findViewById(R.id.fab).setVisibility(View.VISIBLE);
        SessionHelper session = game.session;
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

        RecyclerView rv = (RecyclerView) findViewById(R.id.aea_mobList);
        rv.setAdapter(mobLister);
        rv = (RecyclerView) findViewById(R.id.aea_winnersList);
        rv.setAdapter(winnersLister);
    }

    @Override
    protected void onResume() {
        super.onResume();
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
        MaxUtils.beginDelayedTransition(this);
        findViewById(R.id.aea_status).setVisibility(View.GONE);
        MaxUtils.setVisibility(this, View.VISIBLE,
                R.id.aea_mobListInfo, R.id.aea_mobList, R.id.aea_mobReport,
                R.id.aea_winnersListInfo, R.id.aea_winnersList, R.id.aea_winnersReport);
        update();
    }

    @Override
    public void onBackPressed() { confirmDiscardFinish(); }
    @Override
    public boolean onSupportNavigateUp() {
        confirmDiscardFinish();
        return false;
    }

    private void confirmDiscardFinish() {
        new AlertDialog.Builder(this, R.style.AppDialogStyle)
                .setTitle(R.string.generic_carefulDlgTitle)
                .setMessage(R.string.aea_noBackDlgMessage)
                .setPositiveButton(R.string.generic_discard, new DialogInterface.OnClickListener() {
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
        String mob = getString(count == 1? R.string.aea_mobReportSingular :
                (count != game.session.defeated.size()? R.string.aea_mobReportPlural : R.string.aea_mobReportAll));
        mob = String.format(Locale.getDefault(), mob, count, xp);
        TextView report = (TextView) findViewById(R.id.aea_mobReport);
        report.setText(mob);

        count = 0;
        for(SessionHelper.WinnerData el : game.session.winners) {
            if(el.award) count++;
        }
        String win;
        if(count == 1) win = getString(R.string.aea_winnersCountSingular);
        else if(count == game.session.winners.size()) win = getString(R.string.aea_winnersCountAll);
        else win = String.format(Locale.getDefault(), getString(R.string.aea_winnersCountPlural), count);
        win += '\n' + (count == 0? "" : String.format(Locale.getDefault(), getString(R.string.aea_winnersAward), xp / count));
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

    private RecyclerView.Adapter mobLister;
    private int awarded;
}

package com.massimodz8.collaborativegrouporder.master;


import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
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
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.AbsLiveActor;
import com.massimodz8.collaborativegrouporder.CharacterActor;
import com.massimodz8.collaborativegrouporder.InitiativeScore;
import com.massimodz8.collaborativegrouporder.MaxUtils;
import com.massimodz8.collaborativegrouporder.PreSeparatorDecorator;
import com.massimodz8.collaborativegrouporder.R;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.networkio.ProtoBufferEnum;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Map;

public class FreeRoamingActivity extends AppCompatActivity implements ServiceConnection {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_free_roaming);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        SearchView swidget = (SearchView)findViewById(R.id.fra_searchMobs);
        swidget.setIconifiedByDefault(false);
        swidget.setQueryHint(getString(R.string.fra_searchable_hint));

        final SearchManager sm = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        final ComponentName compName = new ComponentName(this, SpawnMonsterActivity.class);
        swidget.setSearchableInfo(sm.getSearchableInfo(compName));

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int count = 0;
                int pgCount = 0;
                final SessionHelper.PlayState session = game.sessionHelper.session;
                for(int loop = 0; loop < session.getNumActors(); loop++) {
                    AbsLiveActor actor = session.getActor(loop);
                    int add = session.willFight(actor, null)? 1 : 0;
                    if(actor.type == AbsLiveActor.TYPE_PLAYING_CHARACTER) pgCount += add;
                    else count += add;
                }
                if(pgCount == 0) Snackbar.make(findViewById(R.id.activityRoot), R.string.fra_noBattle_zeroPcs, Snackbar.LENGTH_LONG).show();
                else if(count + pgCount != session.getNumActors()) {
                    new AlertDialog.Builder(FreeRoamingActivity.this)
                            .setMessage(getString(R.string.fra_dlgMsg_missingChars))
                            .setPositiveButton(getString(R.string.fra_dlgActionIgnoreMissingCharacters), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                    sendInitiativeRollRequests();
                                }
                            }).show();
                }
                else sendInitiativeRollRequests();
            }
        });
        final ActionBar sab = getSupportActionBar();
        if(null != sab) sab.setDisplayHomeAsUpEnabled(true);

        RecyclerView rv = (RecyclerView) findViewById(R.id.fra_list);
        rv.setAdapter(lister);
        rv.addItemDecoration(new PreSeparatorDecorator(rv, this) {
            @Override
            protected boolean isEligible(int position) {
                return position != 0;
            }
        });

        if(!bindService(new Intent(this, PartyJoinOrderService.class), this, 0)) {
            MaxUtils.beginDelayedTransition(this);
            TextView ohno = (TextView) findViewById(R.id.fra_instructions);
            ohno.setText(R.string.master_cannotBindAdventuringService);
            return;
        }
        mustUnbind = true;
    }

    @Override
    protected void onResume() { // maybe we got there after a monster has been added.
        super.onResume();
        if(game == null) return; // no connection yet -> nothing really to do.
        final SessionHelper.PlayState session = game.sessionHelper.session;
        final int added = session.getNumActors() - numDefinedActors;
        if(added == 0) return;
        for(int loop = 0; loop < added; loop++) actorId.put(session.getActor(numDefinedActors + loop), numDefinedActors + loop);
        lister.notifyItemRangeInserted(numDefinedActors, added);
        numDefinedActors += added;
    }

    @Override
    protected void onDestroy() {
        if(game != null) {
            game.onRollReceived = null;
            game.assignmentHelper.onDetached = null;
            game.sessionHelper.session.end();
        }
        if(waiting != null) waiting.dlg.dismiss();
        if(mustUnbind) unbindService(this);
        super.onDestroy();
    }

    private boolean mustUnbind;
    private PartyJoinOrderService game;
    private IdentityHashMap<AbsLiveActor, Integer> actorId = new IdentityHashMap<>();
    private AdventuringActorWithControlsAdapter lister = new AdventuringActorWithControlsAdapter(actorId) {
        @Override
        protected boolean isCurrent(AbsLiveActor actor) { return false; }

        @Override
        protected LayoutInflater getLayoutInflater() { return FreeRoamingActivity.this.getLayoutInflater(); }
    };

    private int numDefinedActors; // those won't get expunged, no matter what
    private final SecureRandom randomizer = new SecureRandom();
    private WaitInitiativeDialog waiting;


    private void sendInitiativeRollRequests() {
        final SessionHelper.PlayState session = game.sessionHelper.session;
        final ArrayList<AbsLiveActor> local = new ArrayList<>();
        game.sessionHelper.initiatives = new IdentityHashMap<>();
        for(int loop = 0; loop < session.getNumActors(); loop++) {
            final AbsLiveActor actor = session.getActor(loop);
            if (!session.willFight(actor, null)) continue;
            final CharacterActor pc = actor instanceof CharacterActor ? (CharacterActor) actor : null;
            final MessageChannel pipe = pc == null ? null : game.getMessageChannel(pc.character);
            if (pipe != null) { // send a roll request.
                final Network.Roll rq = new Network.Roll();
                rq.unique = ++game.rollRequest;
                rq.range = 20;
                rq.peerKey = loop;
                rq.type = Network.Roll.T_BATTLE_START;
                game.sessionHelper.initiatives.put(actor, new SessionHelper.Initiative(rq));
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            pipe.writeSync(ProtoBufferEnum.ROLL, rq);
                        } catch (IOException e) {
                            // ignore, it will just timeout and somebody else will take care.
                        }
                    }
                }.start();
            } else {
                game.sessionHelper.initiatives.put(actor, new SessionHelper.Initiative(null));
                local.add(actor);
            }
        }
        if(local.isEmpty()) return;
        // For the time being, those are rolled automatically.
        final int range = 20;
        for (AbsLiveActor actor : local) {
            final SessionHelper.Initiative pair = game.sessionHelper.initiatives.get(actor);
            pair.rolled = randomizer.nextInt(range) + actor.getInitiativeBonus();
        }
        attemptBattleStart();
        if(game.sessionHelper.initiatives != null) waiting = new WaitInitiativeDialog(actorId, game.sessionHelper.initiatives).show(this);
    }

    /// Called every time at least one initiative is written so we can try sorting & starting.
    boolean attemptBattleStart() {
        if(game == null || game.sessionHelper.initiatives == null) return false;
        int count = 0;
        if(waiting != null) waiting.lister.notifyDataSetChanged(); // maybe not but I take it easy.
        for (Map.Entry<AbsLiveActor, SessionHelper.Initiative> entry : game.sessionHelper.initiatives.entrySet()) {
            if(entry.getValue().rolled == null) return false;
            count++;
        }
        // Everyone got a number. We go.
        InitiativeScore[] order = new InitiativeScore[count];
        count = 0;
        final SessionHelper.PlayState session = game.sessionHelper.session;
        for (Map.Entry<AbsLiveActor, SessionHelper.Initiative> entry : game.sessionHelper.initiatives.entrySet()) {
            final AbsLiveActor actor = entry.getKey();
            final Integer irl = entry.getValue().rolled;
            order[count++] = new InitiativeScore(irl, actor.getInitiativeBonus(), randomizer.nextInt(1024), actor);
        }
        Arrays.sort(order, new Comparator<InitiativeScore>() {
            @Override
            public int compare(InitiativeScore left, InitiativeScore right) {
                if(left.initRoll > right.initRoll) return -1;
                else if(left.initRoll < right.initRoll) return 1;
                if(left.bonus > right.bonus) return -1;
                else if(left.bonus < right.bonus) return 1;
                if(left.rand > right.rand) return -1;
                else if(left.rand < right.rand) return 1;
                return 0; // super unlikely!
            }
        });
        session.battleState = new BattleHelper(order);
        startActivity(new Intent(this, BattleActivity.class));
        return true;
    }

    // ServiceConnection vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        PartyJoinOrderService.LocalBinder real = (PartyJoinOrderService.LocalBinder)service;
        game = real.getConcreteService();
        lister.game = game;
        game.assignmentHelper.onDetached = null;
        if(game.sessionHelper.initiatives != null) waiting = new WaitInitiativeDialog(actorId, game.sessionHelper.initiatives).show(this);
        game.onRollReceived = new Runnable() {
            @Override
            public void run() {
                attemptBattleStart();
            }
        };
        // Promote everything to battle pumper.
        for (PcAssignmentHelper.PlayingDevice dev : game.assignmentHelper.peers) {
            if(!dev.assignmentAccepted) continue;
            if(dev.movedToBattlePumper) continue;
            game.battlePumper.pump(game.assignmentHelper.netPump.move(dev.pipe));
        }
        attemptBattleStart();
        final SessionHelper.PlayState session = game.sessionHelper.session;
        session.begin(new Runnable() {
            @Override
            public void run() {
                numDefinedActors = session.getNumActors();
                for(int loop = 0; loop < numDefinedActors; loop++) actorId.put(session.getActor(loop), loop);
                lister.notifyDataSetChanged();
                Snackbar.make(findViewById(R.id.activityRoot), R.string.fra_dataLoadedFeedback, Snackbar.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }
    // ServiceConnection ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
}

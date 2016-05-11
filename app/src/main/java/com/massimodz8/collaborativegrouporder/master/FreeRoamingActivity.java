package com.massimodz8.collaborativegrouporder.master;


import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.StringRes;
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

import com.massimodz8.collaborativegrouporder.AsyncRenamingStore;
import com.massimodz8.collaborativegrouporder.InitiativeScore;
import com.massimodz8.collaborativegrouporder.MaxUtils;
import com.massimodz8.collaborativegrouporder.PersistentDataUtils;
import com.massimodz8.collaborativegrouporder.PreSeparatorDecorator;
import com.massimodz8.collaborativegrouporder.R;
import com.massimodz8.collaborativegrouporder.SendRequest;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.networkio.ProtoBufferEnum;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;
import com.massimodz8.collaborativegrouporder.protocol.nano.StartData;

import java.security.SecureRandom;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
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

        final FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int count = 0;
                int pgCount = 0;
                for(int loop = 0; loop < game.session.getNumActors(); loop++) {
                    Network.ActorState actor = game.session.getActor(loop);
                    int add = game.session.willFight(actor.peerKey, null)? 1 : 0;
                    if(actor.type == Network.ActorState.T_PLAYING_CHARACTER) pgCount += add;
                    else count += add;
                }
                if(pgCount == 0) Snackbar.make(findViewById(R.id.activityRoot), R.string.fra_noBattle_zeroPcs, Snackbar.LENGTH_LONG).show();
                else if(count + pgCount != game.session.getNumActors()) {
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
                else {
                    fab.setVisibility(View.GONE);
                    sendInitiativeRollRequests();
                }
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
        int now = lister.getItemCount();
        if(now > numActors) lister.notifyItemRangeInserted(numActors, now - numActors);
        else if(now < numActors) lister.notifyDataSetChanged();
        numActors = now;
        if(game.session.battleState == null) findViewById(R.id.fab).setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        if(game != null) {
            game.onRollReceived = null;
        }
        if(waiting != null) waiting.dlg.dismiss();
        if(mustUnbind) unbindService(this);
        super.onDestroy();
    }


    @Override
    public void onBackPressed() {
        if(game == null) {
            super.onBackPressed();
            return;
        }
        saveSessionStateAndFinish();
    }

    @Override
    public boolean onSupportNavigateUp() {
        if(game == null) return super.onSupportNavigateUp();
        saveSessionStateAndFinish();
        return false;
    }

    private boolean mustUnbind;
    private PartyJoinOrderService game;
    private int numActors;
    private AdventuringActorWithControlsAdapter lister = new AdventuringActorWithControlsAdapter() {
        @Override
        protected boolean isCurrent(Network.ActorState actor) { return false; }

        @Override
        protected LayoutInflater getLayoutInflater() { return FreeRoamingActivity.this.getLayoutInflater(); }
    };

    private final SecureRandom randomizer = new SecureRandom();
    private WaitInitiativeDialog waiting;


    private void sendInitiativeRollRequests() {
        for (PcAssignmentHelper.PlayingDevice dev : game.assignmentHelper.peers) {
            if(dev.movedToBattlePumper) continue;
            game.battlePumper.pump(game.assignmentHelper.netPump.move(dev.pipe));
            dev.movedToBattlePumper = true;
        }
        final ArrayList<Network.ActorState> local = new ArrayList<>();
        game.session.initiatives = new IdentityHashMap<>();
        for(int loop = 0; loop < game.session.getNumActors(); loop++) {
            final Network.ActorState actor = game.session.getActor(loop);
            if (!game.session.willFight(actor.peerKey, null)) continue;
            final MessageChannel pipe = game.assignmentHelper.getMessageChannelByPeerKey(actor.peerKey);
            if (pipe != null) { // send a roll request.
                final Network.Roll rq = new Network.Roll();
                rq.unique = ++game.rollRequest;
                rq.range = 20;
                rq.peerKey = loop;
                rq.type = Network.Roll.T_BATTLE_START;
                game.session.initiatives.put(actor.peerKey, new SessionHelper.Initiative(rq));
                game.assignmentHelper.mailman.out.add(new SendRequest(pipe, ProtoBufferEnum.ROLL, rq));
            } else {
                game.session.initiatives.put(actor.peerKey, new SessionHelper.Initiative(null));
                local.add(actor);
            }
        }
        if(local.isEmpty()) return;
        // For the time being, those are rolled automatically.
        final int range = 20;
        for (Network.ActorState actor : local) {
            final SessionHelper.Initiative pair = game.session.initiatives.get(actor.peerKey);
            pair.rolled = randomizer.nextInt(range) + actor.initiativeBonus;
        }
        if(attemptBattleStart()) return;
        if(game.session.initiatives != null) waiting = new WaitInitiativeDialog(game.session).show(this);
    }

    /// Called every time at least one initiative is written so we can try sorting & starting.
    boolean attemptBattleStart() {
        if(game == null || game.session.initiatives == null) return false;
        int count = 0;
        if(waiting != null) waiting.lister.notifyDataSetChanged(); // maybe not but I take it easy.
        for (Map.Entry<Integer, SessionHelper.Initiative> entry : game.session.initiatives.entrySet()) {
            if(entry.getValue().rolled == null) return false;
            count++;
        }
        // Everyone got a number. We go.
        if(waiting != null) {
            waiting.dlg.dismiss();
            waiting = null;
        }
        InitiativeScore[] order = new InitiativeScore[count];
        count = 0;
        for (Map.Entry<Integer, SessionHelper.Initiative> entry : game.session.initiatives.entrySet()) {
            final Integer irl = entry.getValue().rolled;
            final Network.ActorState actor = game.session.getActorById(entry.getKey());
            order[count++] = new InitiativeScore(irl, actor.initiativeBonus, randomizer.nextInt(1024), actor.peerKey);
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
        game.session.battleState = new BattleHelper(order);
        game.pushBattleOrder();
        for(int id = 0; id < game.assignmentHelper.assignment.size(); id++) game.pushKnownActorState(id);
        startActivityForResult(new Intent(this, BattleActivity.class), REQUEST_BATTLE);
        findViewById(R.id.fab).setVisibility(View.VISIBLE);
        return true;
    }

    static final int REQUEST_BATTLE = 1;
    static final int REQUEST_AWARD_EXPERIENCE = 2;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode) {
            case REQUEST_BATTLE: {
                switch(resultCode) {
                    case BattleActivity.RESULT_OK_AWARD: {
                        startActivityForResult(new Intent(this, AwardExperienceActivity.class), REQUEST_AWARD_EXPERIENCE);
                        break;
                    }
                    case BattleActivity.RESULT_OK_SUSPEND: {
                        saveSessionStateAndFinish();
                        break;
                    }
                }
                break;
            }
            case REQUEST_AWARD_EXPERIENCE: {
                if(resultCode == RESULT_OK) { // ouch! We need to update defs with the new xp, and maybe else... Luckly everything is already in place!
                    new AsyncRenamingStore<StartData.PartyOwnerData>(getFilesDir(), PersistentDataUtils.DEFAULT_GROUP_DATA_FILE_NAME, PersistentDataUtils.makePartyOwnerData(game.allOwnedGroups)) {
                        @Override
                        protected String getString(@StringRes int res) { return FreeRoamingActivity.this.getString(res); }

                        @Override
                        protected void onPostExecute(Exception e) {
                            if(e == null) return; // that's not even worth noticing, user takes for granted.
                            new AlertDialog.Builder(FreeRoamingActivity.this)
                                    .setTitle(R.string.generic_IOError)
                                    .setMessage(e.getLocalizedMessage())
                                    .show();
                        }
                    };
                }
                break;
            }
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void saveSessionStateAndFinish() {
        new AlertDialog.Builder(this).setMessage("TODO: save session data, possibly with battle state or not.\n\nTODO!")
                .setCancelable(false).setPositiveButton("close FRA", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        }).show();
    }

    // ServiceConnection vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        PartyJoinOrderService.LocalBinder real = (PartyJoinOrderService.LocalBinder)service;
        game = real.getConcreteService();
        lister.playState = game.session;
        game.onRollReceived = new Runnable() {
            @Override
            public void run() {
                attemptBattleStart();
            }
        };
        PersistentDataUtils.SessionStructs stats = game.session.stats;
        if(stats.liveActors != null) { // restore state from previous session!
            HashMap<Integer, Integer> remember = new HashMap<>(); // peerkey remapping is only useful for 'temporary' actors but it makes code more streamlined
            for(int loop = 0; loop < stats.liveActors.length; loop++) { // First we take care of 'byDef' characters, they're easier.
                Network.ActorState live = stats.liveActors[loop];
                if(live.peerKey >= game.session.existByDef.size()) continue;
                game.session.willFight(live.peerKey, stats.roamingSelected[loop]);
                game.session.existByDef.set(live.peerKey, live);
                remember.put(live.peerKey, live.peerKey);
            }
            // All other actors are added. That takes some care as I need to remap peerkeys.
            // This is a simplified version of SpawnMonster spawning, complicated by peerkey remapping.
            for(int loop = 0; loop < stats.liveActors.length; loop++) {
                Network.ActorState live = stats.liveActors[loop];
                if(live.peerKey < game.session.existByDef.size()) continue;
                remember.put(live.peerKey, game.nextActorId);
                live.peerKey = game.nextActorId++;
                game.session.add(live);
                game.session.willFight(live.peerKey, stats.roamingSelected[loop]);
            }
            if(stats.fighting != null) { // a bit ugh
                InitiativeScore[] order = new InitiativeScore[stats.fighting.id.length];
                int slow = 0, fast = 0;
                for (boolean state : stats.fighting.enabled) {
                    final int remapped = remember.get(stats.fighting.id[slow]);
                    order[slow] = new InitiativeScore(stats.fighting.initiative[fast++], stats.fighting.initiative[fast++], stats.fighting.initiative[fast++], remapped);
                    order[slow++].enabled = state;
                }
                game.session.battleState = new BattleHelper(order);
                game.session.battleState.round = stats.fighting.round;
                game.session.battleState.currentActor = remember.get(stats.fighting.currentActor);
                game.session.battleState.prevWasReadied = stats.fighting.prevWasReadied;
                for (int orig : stats.fighting.interrupted) {
                    game.session.battleState.interrupted.push(remember.get(orig));
                }
                game.pushBattleOrder();
                for(int id = 0; id < game.assignmentHelper.assignment.size(); id++) game.pushKnownActorState(id);
                startActivityForResult(new Intent(this, BattleActivity.class), REQUEST_BATTLE);
            }
            else {
                String date = DateFormat.getDateInstance().format(new Date(stats.irl.lastSaved.seconds * 1000));
                String snackMsg = String.format(getString(R.string.fra_restoredSession), date);
                Snackbar.make(findViewById(R.id.activityRoot), snackMsg, Snackbar.LENGTH_SHORT).show();
            }
            stats.liveActors = null;  // put everything back to runtime, no need to keep. Avoid logical leak.
            stats.roamingSelected = null;
            stats.fighting = null;
            lister.notifyDataSetChanged();
            numActors = lister.getItemCount();
            return;
        }
        if(game.session.initiatives != null) waiting = new WaitInitiativeDialog(game.session).show(this);
        attemptBattleStart();
        lister.notifyDataSetChanged();
        Snackbar.make(findViewById(R.id.activityRoot), getString(R.string.fra_startBrandNewSession), Snackbar.LENGTH_SHORT).show();
        numActors = lister.getItemCount();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }
    // ServiceConnection ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
}

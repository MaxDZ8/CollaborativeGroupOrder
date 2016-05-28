package com.massimodz8.collaborativegrouporder.master;


import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
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

import com.google.protobuf.nano.CodedOutputByteBufferNano;
import com.google.protobuf.nano.Timestamp;
import com.massimodz8.collaborativegrouporder.AsyncRenamingStore;
import com.massimodz8.collaborativegrouporder.HoriSwipeOnlyTouchCallback;
import com.massimodz8.collaborativegrouporder.InitiativeScore;
import com.massimodz8.collaborativegrouporder.PersistentDataUtils;
import com.massimodz8.collaborativegrouporder.PreSeparatorDecorator;
import com.massimodz8.collaborativegrouporder.R;
import com.massimodz8.collaborativegrouporder.RunningServiceHandles;
import com.massimodz8.collaborativegrouporder.SendRequest;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.networkio.ProtoBufferEnum;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;
import com.massimodz8.collaborativegrouporder.protocol.nano.Session;
import com.massimodz8.collaborativegrouporder.protocol.nano.StartData;

import java.io.IOException;
import java.security.SecureRandom;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

public class FreeRoamingActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_free_roaming);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        final ActionBar sab = getSupportActionBar();
        if(null != sab) sab.setDisplayHomeAsUpEnabled(true);
        SearchView swidget = (SearchView)findViewById(R.id.fra_searchMobs);
        swidget.setIconifiedByDefault(false);
        swidget.setQueryHint(getString(R.string.fra_searchable_hint));

        final SearchManager sm = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        SpawnMonsterActivity.includePreparedBattles = true;
        final ComponentName compName = new ComponentName(this, SpawnMonsterActivity.class);
        swidget.setSearchableInfo(sm.getSearchableInfo(compName));

        final PartyJoinOrderService game = RunningServiceHandles.getInstance().play;
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
                else if(count + pgCount == 1) Snackbar.make(findViewById(R.id.activityRoot), R.string.fra_noBattle_oneActor, Snackbar.LENGTH_LONG).show();
                else if(count + pgCount != game.session.getNumActors()) {
                    new AlertDialog.Builder(FreeRoamingActivity.this, R.style.AppDialogStyle)
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

        RecyclerView rv = (RecyclerView) findViewById(R.id.fra_list);
        rv.setAdapter(lister);
        rv.addItemDecoration(new PreSeparatorDecorator(rv, this) {
            @Override
            protected boolean isEligible(int position) {
                return position != 0;
            }
        });
        new HoriSwipeOnlyTouchCallback(rv) {
            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                if(viewHolder instanceof AdventuringActorControlsVH) {
                    final AdventuringActorControlsVH real = (AdventuringActorControlsVH) viewHolder;
                    if(real.actor == null) return;
                    final PartyJoinOrderService game = RunningServiceHandles.getInstance().play;
                    game.session.willFight(real.actor.peerKey, false);
                    game.session.temporaries.remove(real.actor);
                    lister.notifyDataSetChanged();
                }
            }

            @Override
            protected boolean disable() { return false; }

            @Override
            protected boolean canSwipe(RecyclerView rv, RecyclerView.ViewHolder vh) {
                if(vh instanceof AdventuringActorControlsVH) {
                    final AdventuringActorControlsVH real = (AdventuringActorControlsVH) vh;
                    return real.actor != null && real.actor.type == Network.ActorState.T_MOB;
                }
                return false;
            }
        };

        lister.playState = game.session;
        game.onRollReceived = new Runnable() {
            @Override
            public void run() {
                attemptBattleStart();
            }
        };
        Session.Suspended stats = game.session.stats;
        if(stats.live.length > 0) { // restore state from previous session!
            HashMap<Integer, Integer> remember = new HashMap<>(); // peerkey remapping is only useful for 'temporary' actors but it makes code more streamlined
            for(int loop = 0; loop < stats.live.length; loop++) { // First we take care of 'byDef' characters, they're easier.
                Network.ActorState live = stats.live[loop];
                if(live.peerKey >= game.session.existByDef.size()) continue;
                int found = 0;
                for (int el : stats.notFighting) {
                    if(el == live.peerKey) break;
                    found++;
                }
                if(found == stats.notFighting.length || stats.notFighting.length == 0) game.session.willFight(live.peerKey, true); // playing characters start deselected
                game.session.existByDef.set(live.peerKey, live);
                remember.put(live.peerKey, live.peerKey);
            }
            // All other actors are added. That takes some care as I need to remap peerkeys.
            // This is a simplified version of SpawnMonster spawning, complicated by peerkey remapping.
            for(int loop = 0; loop < stats.live.length; loop++) {
                Network.ActorState live = stats.live[loop];
                if(live.peerKey < game.session.existByDef.size()) continue;
                int ori = live.peerKey;
                remember.put(ori, game.nextActorId);
                live.peerKey = game.nextActorId++;
                game.session.add(live);
                int found = 0;
                for (int el : stats.notFighting) {
                    if(el == ori) break;
                    found++;
                }
                if(found == stats.notFighting.length || stats.notFighting.length == 0) game.session.willFight(live.peerKey, true);
            }
            if(stats.fighting != null) { // a bit ugh
                InitiativeScore[] order = new InitiativeScore[stats.fighting.id.length];
                int slow = 0, fast = 0;
                for (boolean state : stats.fighting.enabled) {
                    final int remapped = remember.get(stats.fighting.id[slow]);
                    order[slow] = new InitiativeScore(stats.fighting.initiative[fast++], stats.fighting.initiative[fast++], stats.fighting.initiative[fast++], remapped);
                    order[slow++].enabled = state;
                }
                final BattleHelper battle = new BattleHelper(order);
                game.session.battleState = battle;
                battle.round = stats.fighting.round;
                if(battle.round != 0) battle.currentActor = remember.get(stats.fighting.currentActor);
                battle.prevWasReadied = stats.fighting.prevWasReadied;
                for (int orig : stats.fighting.interrupted) {
                    battle.interrupted.push(remember.get(orig));
                }
                game.pushBattleOrder();
                for(int id = 0; id < game.assignmentHelper.assignment.size(); id++) game.pushKnownActorState(id);
                // Note: this is an extra. We cannot use INITIATIVE roll to signal battle start so...
                Network.TurnControl notifyRound = new Network.TurnControl();
                notifyRound.type = Network.TurnControl.T_BATTLE_ROUND;
                notifyRound.round = battle.round;
                for (PcAssignmentHelper.PlayingDevice dev : game.assignmentHelper.peers) {
                    if(dev.pipe == null) continue;
                    game.assignmentHelper.mailman.out.add(new SendRequest(dev.pipe, ProtoBufferEnum.TURN_CONTROL, notifyRound, null));

                    if(dev.movedToBattlePumper) continue;
                    game.battlePumper.pump(game.assignmentHelper.netPump.move(dev.pipe));
                    dev.movedToBattlePumper = true;
                }
                startActivityForResult(new Intent(this, BattleActivity.class), REQUEST_BATTLE);
            }
            else {
                String date = DateFormat.getDateInstance().format(new Date(stats.lastSaved.seconds * 1000));
                String snackMsg = String.format(getString(R.string.fra_restoredSession), date);
                Snackbar.make(findViewById(R.id.activityRoot), snackMsg, Snackbar.LENGTH_SHORT).show();
            }
            stats.live = null;  // put everything back to runtime, no need to keep. Avoid logical leak.
            stats.notFighting = null;
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
    protected void onResume() { // maybe we got there after a monster has been added.
        super.onResume();
        final PartyJoinOrderService game = RunningServiceHandles.getInstance().play;
        if(SpawnMonsterActivity.found != null && SpawnMonsterActivity.found.size() > 0) {
            for (Network.ActorState got : SpawnMonsterActivity.found) {
                got.peerKey = game.nextActorId++;
                game.session.add(got);
                game.session.willFight(got.peerKey, true);
            }
            lister.notifyItemRangeInserted(numActors, SpawnMonsterActivity.found.size());
            numActors += SpawnMonsterActivity.found.size();
            SpawnMonsterActivity.found = null;
            if(game.session.battleState == null) findViewById(R.id.fab).setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onDestroy() {
        final PartyJoinOrderService game = RunningServiceHandles.getInstance().play;
        if(game != null) game.onRollReceived = null;
        if(waiting != null) waiting.dlg.dismiss();
        super.onDestroy();
    }


    @Override
    public void onBackPressed() {
        saveSessionStateAndFinish(false);
    }

    @Override
    public boolean onSupportNavigateUp() {
        saveSessionStateAndFinish(false);
        return false;
    }

    private int numActors;
    private final AdventuringActorWithControlsAdapter lister = new AdventuringActorWithControlsAdapter() {
        @Override
        protected boolean isCurrent(Network.ActorState actor) { return false; }

        @Override
        protected LayoutInflater getLayoutInflater() { return FreeRoamingActivity.this.getLayoutInflater(); }
    };

    private final SecureRandom randomizer = new SecureRandom();
    private WaitInitiativeDialog waiting;


    private void sendInitiativeRollRequests() {
        final PartyJoinOrderService game = RunningServiceHandles.getInstance().play;
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
                rq.type = Network.Roll.T_INITIATIVE;
                game.session.initiatives.put(actor.peerKey, new SessionHelper.Initiative(rq));
                game.assignmentHelper.mailman.out.add(new SendRequest(pipe, ProtoBufferEnum.ROLL, rq, null));
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
        final PartyJoinOrderService game = RunningServiceHandles.getInstance().play;
        if(game.session.initiatives == null) return false;
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
                        saveSessionStateAndFinish(true);
                        break;
                    }
                }
                break;
            }
            case REQUEST_AWARD_EXPERIENCE: {
                if(resultCode == RESULT_OK) { // ouch! We need to update defs with the new xp, and maybe else... Luckly everything is already in place!
                    final PartyJoinOrderService game = RunningServiceHandles.getInstance().play;
                    new AsyncRenamingStore<StartData.PartyOwnerData>(getFilesDir(), PersistentDataUtils.MAIN_DATA_SUBDIR, PersistentDataUtils.DEFAULT_GROUP_DATA_FILE_NAME, PersistentDataUtils.makePartyOwnerData(game.allOwnedGroups)) {
                        @Override
                        protected String getString(@StringRes int res) { return FreeRoamingActivity.this.getString(res); }

                        @Override
                        protected void onPostExecute(Exception e) {
                            if(e == null) return; // that's not even worth noticing, user takes for granted.
                            new AlertDialog.Builder(FreeRoamingActivity.this, R.style.AppDialogStyle)
                                    .setTitle(R.string.generic_IOError)
                                    .setMessage(e.getLocalizedMessage())
                                    .show();
                        }
                    };
                }
                lister.notifyDataSetChanged();
                break;
            }
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private boolean saving;
    private void saveSessionStateAndFinish(boolean confirmed) {
        if(!confirmed) {
            new AlertDialog.Builder(this, R.style.AppDialogStyle)
                    .setTitle(R.string.generic_carefulDlgTitle)
                    .setMessage(R.string.fra_exitDlgMsg)
                    .setPositiveButton(R.string.fra_exitButtonConfirm, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            saveSessionStateAndFinish(true);
                        }
                    })
                    .show();
            return;
        }
        if(saving) return;
        saving = true; // no need to set it to false, we terminate activity
        final PartyJoinOrderService game = RunningServiceHandles.getInstance().play;
        final Session.Suspended save = game.session.stats;
        save.lastSaved = new Timestamp();
        save.lastSaved.seconds = new Date().getTime() / 1000;
        if(save.spent == null) save.spent = new Timestamp();
        save.spent.seconds += save.lastSaved.seconds - save.lastBegin.seconds;
        int idle = 0;
        for(int loop = 0; loop < game.session.getNumActors(); loop++) {
            Network.ActorState actor = game.session.getActor(loop);
            if(!game.session.willFight(actor.peerKey, null)) idle++;
        }
        if(idle != 0) {
            save.notFighting = new int[idle];
            idle = 0;
            for(int loop = 0; loop < game.session.getNumActors(); loop++) {
                Network.ActorState actor = game.session.getActor(loop);
                if(!game.session.willFight(actor.peerKey, null)) save.notFighting[idle++] = actor.peerKey;
            }
        }
        save.live = new Network.ActorState[game.session.getNumActors()];
        for(int loop = 0; loop < game.session.getNumActors(); loop++) {
            save.live[loop] = game.session.getActor(loop);
            // TODO if my serialization == serialization from byDef canonical object then don't serialize me
            // TODO so we can start the new session instead of restoring one... but that's the case only if we have no mobs nor other state to restore!
        }
        if(game.session.battleState != null) save.fighting = game.session.battleState.asProtoBuf();
        int takes = save.getSerializedSize();
        for(int loop = 0; loop < game.session.getNumActors(); loop++) takes += game.session.getActor(loop).getSerializedSize();
        byte[] blob = new byte[takes];
        CodedOutputByteBufferNano out = CodedOutputByteBufferNano.newInstance(blob);
        try {
            save.writeTo(out);
            for(int loop = 0; loop < game.session.getNumActors(); loop++) game.session.getActor(loop).writeTo(out);
        } catch (IOException e) {
            new AlertDialog.Builder(this, R.style.AppDialogStyle)
                    .setTitle(R.string.generic_IOError)
                    .setMessage(String.format(getString(R.string.fra_dlgIOErrorSerializingSession_impossible), e.getLocalizedMessage()))
                    .show();
        }
        Network.PhaseControl end = new Network.PhaseControl();
        end.type = Network.PhaseControl.T_SESSION_ENDED;
        int count = 0;
        for (PcAssignmentHelper.PlayingDevice dev : game.assignmentHelper.peers) {
            if(dev.pipe != null) count++;
        }
        if(game.assignmentHelper.peers.size() > 0) {
            final LatchingHandler lh = new LatchingHandler(count, new Runnable() {
                @Override
                public void run() {
                    new MyRefreshStore(game, save);
                }
            });
            for (PcAssignmentHelper.PlayingDevice dev : game.assignmentHelper.peers) {
                if (dev.pipe != null) {
                    final SendRequest send = new SendRequest(dev.pipe, ProtoBufferEnum.PHASE_CONTROL, end, lh.ticker);
                    game.assignmentHelper.mailman.out.add(send);
                }
            }
            return;
        }
        new MyRefreshStore(game, save);
    }

    private static class LatchingHandler extends Handler {
        final Runnable ticker;

        LatchingHandler(int expect, @NonNull Runnable latched) {
            this.expect = expect;
            this.latched = latched;
            ticker = new Runnable() {
                @Override
                public void run() {
                    LatchingHandler.this.sendEmptyMessage(LatchingHandler.MSG_INCREMENT);
                }
            };
        }

        @Override
        public void handleMessage(Message msg) {
            if(msg.what != MSG_INCREMENT) return;
            count++;
            if(count == expect) {
                final PartyJoinOrderService game = RunningServiceHandles.getInstance().play;
                for (PcAssignmentHelper.PlayingDevice dev : game.assignmentHelper.peers) {
                    if (dev.pipe != null) try {
                        dev.pipe.socket.getOutputStream().flush();
                    } catch (IOException e) {
                        // just ignore, it was simply convenience
                    }
                }
                try {
                    Thread.sleep(125); // be reasonably sure we give time to go client as well, stall the mailman for a while
                } catch (InterruptedException e) {
                    // again, just convenience
                }
                latched.run();
            }
        }

        private int count;
        private final int expect;
        private final Runnable latched;
        private static final int MSG_INCREMENT = 1;
    }

    private class MyRefreshStore extends AsyncRenamingStore<Session.Suspended> {
        public MyRefreshStore(@NonNull PartyJoinOrderService game, @NonNull Session.Suspended suspended) {
            super(getFilesDir(), PersistentDataUtils.SESSION_DATA_SUBDIR, game.getPartyOwnerData().sessionFile, suspended);
        }

        @Override
        protected String getString(@StringRes int res) {
            return FreeRoamingActivity.this.getString(res);
        }

        @Override
        protected void onPostExecute(Exception e) {
            if(e == null) {
                finish();
                return;
            }
            new AlertDialog.Builder(FreeRoamingActivity.this, R.style.AppDialogStyle)
                    .setTitle(R.string.generic_IOError)
                    .setMessage(String.format(getString(R.string.fra_dlgIOErrorSerializingSession_impossible), e.getLocalizedMessage()))
                    .show();
        }
    }
}

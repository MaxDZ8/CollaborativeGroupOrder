package com.massimodz8.collaborativegrouporder.master;


import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
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

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.protobuf.nano.CodedOutputByteBufferNano;
import com.google.protobuf.nano.Timestamp;
import com.massimodz8.collaborativegrouporder.AsyncRenamingStore;
import com.massimodz8.collaborativegrouporder.HoriSwipeOnlyTouchCallback;
import com.massimodz8.collaborativegrouporder.InitiativeScore;
import com.massimodz8.collaborativegrouporder.MaxUtils;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

public class FreeRoamingActivity extends AppCompatActivity {
    private PartyJoinOrder game;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_free_roaming);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        final ActionBar sab = getSupportActionBar();
        if (null != sab) sab.setDisplayHomeAsUpEnabled(true);
        SearchView swidget = (SearchView) findViewById(R.id.fra_searchMobs);
        swidget.setIconifiedByDefault(false);
        swidget.setQueryHint(getString(R.string.fra_searchable_hint));
        game = RunningServiceHandles.getInstance().play;

        final SearchManager sm = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        SpawnMonsterActivity.includePreparedBattles = true;
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        lister.playState = game.session;
        refresh();
    }

    private void refresh() {
        game.onRollReceived = new Runnable() {
            @Override
            public void run() {
                attemptBattleStart();
            }
        };
        Session.Suspended stats = game.session.stats;
        HashMap<Integer, Network.ActorState> remember = new HashMap<>(); // deserialized id -> real structure with new id.
        for (Network.ActorState real : game.session.existByDef) remember.put(real.peerKey, real);
        if(stats.live != null && stats.live.length > 0) { // restore state from previous session!
            for(Network.ActorState live : stats.live) {
                final int original = live.peerKey;
                if(live.peerKey < game.session.existByDef.size()) game.session.existByDef.set(live.peerKey, live);
                else {
                    live.peerKey = game.nextActorId++;
                    game.session.temporaries.add(live);
                }
                remember.put(original, live);
            }
            stats.live = null;  // put everything back to runtime, no need to keep. Avoid logical leak. Note this is not a valid protobuf object anymore!
        }
        if(stats.notFighting != null && stats.notFighting.length > 0) {
            Arrays.sort(stats.notFighting);
            for (Map.Entry<Integer, Network.ActorState> el : remember.entrySet()) {
                final int slot = Arrays.binarySearch(stats.notFighting, el.getKey());
                game.session.willFight(el.getValue().peerKey, slot < 0);
            }
            stats.notFighting = null;
        }
        if(stats.fighting != null) {
            InitiativeScore[] order = new InitiativeScore[stats.fighting.id.length];
            int slow = 0, fast = 0;
            for (boolean state : stats.fighting.enabled) {
                final Network.ActorState real = remember.get(stats.fighting.id[slow]);
                order[slow] = new InitiativeScore(stats.fighting.initiative[fast++], stats.fighting.initiative[fast++], stats.fighting.initiative[fast++], real.peerKey);
                order[slow++].enabled = state;
            }
            final BattleHelper battle = new BattleHelper(order);
            game.session.battleState = battle;
            battle.round = stats.fighting.round;
            if(battle.round != 0) battle.currentActor = remember.get(stats.fighting.currentActor).peerKey;
            battle.prevWasReadied = stats.fighting.prevWasReadied;
            for (int orig : stats.fighting.interrupted) {
                battle.interrupted.push(remember.get(orig).peerKey);
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
            FirebaseAnalytics surveyor = FirebaseAnalytics.getInstance(this);
            Bundle bundle = new Bundle();
            bundle.putInt(MaxUtils.FA_PARAM_STEP, MaxUtils.FA_PARAM_STEP_NEW_BATTLE);
            bundle.putByteArray(MaxUtils.FA_PARAM_ADVENTURING_ID, game.publishToken);
            surveyor.logEvent(MaxUtils.FA_EVENT_PLAYING, bundle);
            stats.fighting = null;
            lister.notifyDataSetChanged();
            return;
        }
        if(game.session.initiatives != null) {
            waiting = new WaitInitiativeDialog(game.session).show(this);
            waiting.dlg.setOnCancelListener(new CancelRolls());
        }
        attemptBattleStart();
        lister.notifyDataSetChanged();
        if(stats.live != null && !game.session.restoreNotified) {
            Snackbar.make(findViewById(R.id.activityRoot), getString(R.string.fra_startBrandNewSession), Snackbar.LENGTH_SHORT).show();
            stats.live = null;
            game.session.restoreNotified = true;
        }
        int numActors = lister.getItemCount();
        if(SpawnMonsterActivity.found != null && SpawnMonsterActivity.found.size() > 0) {
            for (Network.ActorState got : SpawnMonsterActivity.found) {
                got.peerKey = game.nextActorId++;
                game.session.add(got);
                game.session.willFight(got.peerKey, true);
            }
            lister.notifyItemRangeInserted(numActors, SpawnMonsterActivity.found.size());
            SpawnMonsterActivity.found = null;
        }
        findViewById(R.id.fab).setVisibility(game.session.battleState == null? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(game != null) game.onRollReceived = null;
        if(waiting != null) waiting.dlg.dismiss();
    }


    @Override
    public void onBackPressed() {
        syncSaveAndFinish(false);
    }

    @Override
    public boolean onSupportNavigateUp() {
        syncSaveAndFinish(false);
        return false;
    }

    private final AdventuringActorWithControlsAdapter lister = new AdventuringActorWithControlsAdapter() {
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
                rq.type = Network.Roll.T_INITIATIVE;
                game.session.initiatives.put(actor.peerKey, new SessionHelper.Initiative(rq));
                game.assignmentHelper.mailman.out.add(new SendRequest(pipe, ProtoBufferEnum.ROLL, rq, null));
            } else {
                game.session.initiatives.put(actor.peerKey, new SessionHelper.Initiative(null));
                local.add(actor);
            }
        }
        // For the time being, locals are rolled automatically, no questions asked!
        final int range = 20;
        for (Network.ActorState actor : local) {
            final SessionHelper.Initiative pair = game.session.initiatives.get(actor.peerKey);
            pair.rolled = randomizer.nextInt(range) + actor.initiativeBonus;
        }
        if(attemptBattleStart()) return;
        if(game.session.initiatives != null) {
            waiting = new WaitInitiativeDialog(game.session).show(this);
            waiting.dlg.setOnCancelListener(new CancelRolls());
        }
    }

    /// Called every time at least one initiative is written so we can try sorting & starting.
    boolean attemptBattleStart() {
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

        game.session.initiatives = null;
        startActivityForResult(new Intent(this, BattleActivity.class), REQUEST_BATTLE);
        FirebaseAnalytics surveyor = FirebaseAnalytics.getInstance(this);
        Bundle bundle = new Bundle();
        bundle.putInt(MaxUtils.FA_PARAM_STEP, MaxUtils.FA_PARAM_STEP_NEW_BATTLE);
        bundle.putByteArray(MaxUtils.FA_PARAM_ADVENTURING_ID, game.publishToken);
        surveyor.logEvent(MaxUtils.FA_EVENT_PLAYING, bundle);

        findViewById(R.id.fab).setVisibility(View.VISIBLE);
        return true;
    }

    static final int REQUEST_BATTLE = 1;
    static final int REQUEST_AWARD_EXPERIENCE = 2;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Be sure to resist being called before .onCreate!
        // If we're being created we're not being destroyed so it is safe to use the handles directly. Hopefully.
        PartyJoinOrder game = RunningServiceHandles.getInstance().play;
        switch(requestCode) {
            case REQUEST_BATTLE: {
                switch(resultCode) {
                    case BattleActivity.RESULT_OK_AWARD: {
                        startActivityForResult(new Intent(this, AwardExperienceActivity.class), REQUEST_AWARD_EXPERIENCE);
                        break;
                    }
                    case BattleActivity.RESULT_OK_SUSPEND: {
                        syncSaveAndFinish(true);
                        break;
                    }
                }
                break;
            }
            case REQUEST_AWARD_EXPERIENCE: {
                // No matter what, when we're outta there we get the rid of all battle data, including those
                // transient lists.
                game.session.winners = null;
                game.session.defeated = null;
                if(resultCode == RESULT_OK) { // ouch! We need to update defs with the new xp, and maybe else... Luckly everything is already in place!
                    syncDefsToLive();
                    final ArrayList<StartData.PartyOwnerData.Group> allOwned = RunningServiceHandles.getInstance().state.data.groupDefs;
                    new AsyncRenamingStore<StartData.PartyOwnerData>(getFilesDir(),
                            PersistentDataUtils.MAIN_DATA_SUBDIR, PersistentDataUtils.DEFAULT_GROUP_DATA_FILE_NAME,
                            PersistentDataUtils.makePartyOwnerData(allOwned)) {
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
    private void syncSaveAndFinish(boolean confirmed) {
        if(!confirmed) {
            new AlertDialog.Builder(this, R.style.AppDialogStyle)
                    .setTitle(R.string.generic_carefulDlgTitle)
                    .setMessage(R.string.fra_exitDlgMsg)
                    .setPositiveButton(R.string.fra_exitButtonConfirm, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            syncSaveAndFinish(true);
                        }
                    })
                    .show();
            return;
        }
        if(saving) return;
        saving = true; // no need to set it to false, we terminate activity
        // Might be called from .onActivityResult or while active, using RSH directly is safe in both cases
        final PartyJoinOrder game = RunningServiceHandles.getInstance().play;
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
        save.live = persist();
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

    /**
     * This helper function considers the current live actors and puts everything it can in the
     * 'definition' state. At this point the two representations are 'the same'. It then considers
     * extra state and figures out what needs to be serialized as live actors and what not.
     * Actors 'by definitions' don't need to be defined as live actors if they have no extra state.
     * @return A valid 'live' array, possibly empty.
     */
    private @NonNull Network.ActorState[] persist() {
        syncDefsToLive();
        final StartData.ActorDefinition[] playing = game.getPartyOwnerData().party;
        final StartData.ActorDefinition[] npcs = game.getPartyOwnerData().npcs;
        // Now data is sync'd we can try to guess what needs to go in 'live' and what not.
        final ArrayList<Network.ActorState> complex = new ArrayList<>(game.session.getNumActors());
        byte[] storea = new byte[256];
        byte[] storeb = new byte[256];
        for(int loop = 0; loop < game.session.getNumActors(); loop++) {
            final Network.ActorState actor = game.session.getActor(loop);
            if (actor.peerKey >= playing.length + npcs.length) { // if not by def, sure it needs to be live
                complex.add(actor);
                continue;
            }
            // Defined characters are omitted if possible: this is mostly a requirement than everything else,
            // if there are no effects to persist, the session ends, otherwise it must carry on.
            // This is quite complicated and not pretty at all!
            final StartData.ActorDefinition original;
            if (actor.peerKey < playing.length) original = playing[actor.peerKey];
            else original = npcs[actor.peerKey - playing.length];
            final Network.ActorState reference = MaxUtils.makeActorState(original, actor.peerKey, actor.type);
            final int a = reference.getSerializedSize();
            final int b = actor.getSerializedSize();
            if(a == b) { // a chance they are the same
                if(a > storea.length) storea = new byte[a];
                if(b > storeb.length) storeb = new byte[b];
                if(Arrays.equals(serialize(reference, storea), serialize(actor, storeb))) continue;
            }
            complex.add(actor);
        }
        final Network.ActorState[] live = new Network.ActorState[complex.size()];
        int dst = 0;
        for (Network.ActorState el : complex) live[dst++] = el;
        return live;
    }

    private static byte[] serialize(Network.ActorState obj, byte[] storage) {
        final CodedOutputByteBufferNano mangler = CodedOutputByteBufferNano.newInstance(storage);
        try {
            obj.writeTo(mangler);
        } catch (IOException e) {
            // I don't think that's possible with this construction.
        }
        return storage;
    }

    private void syncDefsToLive() {
        // Let's put in definitions everything we can. Currently: experience points.
        final StartData.ActorDefinition[] playing = game.getPartyOwnerData().party;
        final StartData.ActorDefinition[] npcs = game.getPartyOwnerData().npcs;
        for(int loop = 0; loop < game.session.getNumActors(); loop++) {
            final Network.ActorState current = game.session.getActor(loop);
            StartData.ActorDefinition actor = null;
            if(current.peerKey < playing.length) actor = playing[current.peerKey];
            else if(current.peerKey - playing.length < npcs.length) actor = npcs[current.peerKey - playing.length];
            if(actor == null || actor.experience == current.experience) continue;
            actor.experience = current.experience;
        }
    }

    private class MyRefreshStore extends AsyncRenamingStore<Session.Suspended> {
        public MyRefreshStore(@NonNull PartyJoinOrder game, @NonNull Session.Suspended suspended) {
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

    private class CancelRolls implements DialogInterface.OnCancelListener {
        @Override
        public void onCancel(DialogInterface dialog) {
            game.session.initiatives = null;
            waiting = null;
            Network.TurnControl msg = new Network.TurnControl();
            msg.type = Network.TurnControl.T_BATTLE_ENDED;
            for (PcAssignmentHelper.PlayingDevice client : game.assignmentHelper.peers) {
                if(client.pipe == null) continue;
                game.assignmentHelper.mailman.out.add(new SendRequest(client.pipe, ProtoBufferEnum.TURN_CONTROL, msg, null));
            }
            refresh();

        }
    }
}

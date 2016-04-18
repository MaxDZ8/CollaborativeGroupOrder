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
import android.support.v4.util.Pair;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.HealthBar;
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
                final SessionHelper.PlayState session = game.getPlaySession();
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
                                public void onClick(DialogInterface dialog, int which) { sendInitiativeRollRequests(); }
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
        final SessionHelper.PlayState session = game.getPlaySession();
        final int added = session.getNumActors() - numDefinedActors;
        if(added == 0) return;
        for(int loop = 0; loop < added; loop++) actorId.put(session.getActor(numDefinedActors + loop), numDefinedActors + loop);
        lister.notifyItemRangeInserted(numDefinedActors, added);
        numDefinedActors += added;
    }

    @Override
    protected void onDestroy() {
        if(game != null) game.getPlaySession().end();
        if(mustUnbind) unbindService(this);
        super.onDestroy();
    }

    private boolean mustUnbind;
    private PartyJoinOrderService game;
    private AdventuringActorAdapter lister = new AdventuringActorAdapter();
    private IdentityHashMap<AbsLiveActor, Integer> actorId = new IdentityHashMap<>();
    private int numDefinedActors; // those won't get expunged, no matter what
    private final SecureRandom randomizer = new SecureRandom();
    private Map<AbsLiveActor, Pair<Integer, Integer>> initRolls; // if this is null we're not starting a new battle, otherwise
    // There is a key for each 'will battle' actor. Each key value is built non-null.
    // .first is nullable. It is the roll request ID sent to remote players and set to null as soon as we receive a result.
    // .second is the roll.
    // Therefore, only one of the two fields are set at time. When all rolls are there, we build order and go to battle.

    private class AdventuringActorVH extends RecyclerView.ViewHolder implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {
        final CheckBox selected;
        final ImageView avatar;
        final TextView actorShortType, name;
        final HealthBar hbar;
        public AbsLiveActor actor;

        public AdventuringActorVH(View iv) {
            super(iv);
            selected = (CheckBox) iv.findViewById(R.id.vhAA_selected);
            avatar = (ImageView) iv.findViewById(R.id.vhAA_avatar);
            actorShortType = (TextView) iv.findViewById(R.id.vhAA_actorTypeShort);
            name = (TextView) iv.findViewById(R.id.vhAA_name);
            hbar = (HealthBar) iv.findViewById(R.id.vhAA_health);
            iv.setOnClickListener(this);
            selected.setOnCheckedChangeListener(this);
        }

        @Override
        public void onClick(View v) {
            selected.performClick();
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            game.getPlaySession().willFight(actor, isChecked);
        }
    }

    private class AdventuringActorAdapter extends RecyclerView.Adapter<AdventuringActorVH> {
        AdventuringActorAdapter() {
            setHasStableIds(true);
        }

        @Override
        public int getItemCount() { return game != null? game.getPlaySession().getNumActors() : 0; }

        @Override
        public long getItemId(int position) {
            Integer index = actorId.get(game.getPlaySession().getActor(position));
            return index != null ? index : RecyclerView.NO_ID;
        }

        @Override
        public AdventuringActorVH onCreateViewHolder(ViewGroup parent, int viewType) {
            return new AdventuringActorVH(getLayoutInflater().inflate(R.layout.vh_adventuring_actor, parent, false));
        }

        @Override
        public void onBindViewHolder(AdventuringActorVH holder, int position) {
            final SessionHelper.PlayState session = game.getPlaySession();
            AbsLiveActor actor = session.getActor(position);
            holder.actor = actor;
            holder.selected.setChecked(session.willFight(actor, null));
            // TODO holder.avatar
            int res;
            switch(actor.type) {
                case AbsLiveActor.TYPE_PLAYING_CHARACTER: res = R.string.fra_actorType_playingCharacter; break;
                case AbsLiveActor.TYPE_MONSTER: res = R.string.fra_actorType_monster; break;
                case AbsLiveActor.TYPE_NPC: res = R.string.fra_actorType_npc; break;
                default: res = R.string.fra_actorType_unmatched;
            }
            holder.actorShortType.setText(res);
            holder.name.setText(actor.displayName);
            int[] hp = actor.getHealth();
            holder.hbar.currentHp = hp[0];
            holder.hbar.maxHp = hp[1];
            holder.hbar.invalidate();
        }
    }


    private void sendInitiativeRollRequests() {
        final SessionHelper.PlayState session = game.getPlaySession();
        final ArrayList<AbsLiveActor> local = new ArrayList<>();
        initRolls = new IdentityHashMap<>();
        for(int loop = 0; loop < session.getNumActors(); loop++) {
            final AbsLiveActor actor = session.getActor(loop);
            if (!session.willFight(actor, null)) continue;
            final CharacterActor pc = actor instanceof CharacterActor ? (CharacterActor) actor : null;
            final MessageChannel pipe = pc == null ? null : game.getMessageChannel(pc.character);
            if (pipe != null) { // send a roll request.
                final Network.ManualRollRequest rq = new Network.ManualRollRequest();
                rq.note = getString(R.string.fra_rollRequestInitiativeMsg);
                rq.unique = pc.nextRollRequestIndex++;
                rq.what = Network.ManualRollRequest.TWENTY;
                initRolls.put(actor, new Pair<>(rq.unique, (Integer)null));
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            pipe.writeSync(ProtoBufferEnum.ROLL_REQUEST, rq);
                        } catch (IOException e) {
                            // ignore, it will just timeout and somebody else will take care.
                        }
                    }
                }.start();
            } else {
                initRolls.put(actor, new Pair<>((Integer)null, (Integer)null));
                local.add(actor);
            }
        }
        if(local.isEmpty()) return;
        // For the time being, those are rolled automatically.
        final int range = 20;
        for (AbsLiveActor actor : local) {
            final Pair<Integer, Integer> pair = initRolls.get(actor);
            final int init = randomizer.nextInt(range) + actor.getInitiativeBonus();
            initRolls.put(actor, new Pair<>(pair.first, init));
        }
        attemptBattleStart();
    }

    /// Called every time at least one initiative is written so we can try sorting & starting.
    void attemptBattleStart() {
        int count = 0;
        for (Map.Entry<AbsLiveActor, Pair<Integer, Integer>> entry : initRolls.entrySet()) {
            if(entry.getValue().second == null) return;
            count++;
        }
        // Everyone got a number. We go.
        int[] initiative = new int[count]; // guaranteed to be count > 0 by calling context
        AbsLiveActor[] battlers = new AbsLiveActor[count]; // todo in the future a slight optimization might involve those being ints to list so GC doesn't traverse them
        // OFC I would like to pack everything in an integer and be done. Unfortunately, initiative rolls can be negative and would get me screwed so,
        // As much as I'd like to not define anything extra, I'm forced to use an helper class and an extra alloc. Meh.
        // I need to sort by total initiative scores. Solve ties preferring higher bonus. Solve further ties at random.
        // Plus, keep actor index around or we won't know how to shuffle! This is irrelevant to sorting.
        class SortEntry {
            final int initRoll;
            final int bonus;
            final int rand;
            final AbsLiveActor actor;

            SortEntry(int initRoll, int bonus, int rand, AbsLiveActor actor) {
                this.initRoll = initRoll;
                this.bonus = bonus;
                this.rand = rand;
                this.actor = actor;
            }
        }
        SortEntry[] order = new SortEntry[count];
        count = 0;
        final SessionHelper.PlayState session = game.getPlaySession();
        final int numActors = session.getNumActors();
        for (Map.Entry<AbsLiveActor, Pair<Integer, Integer>> entry : initRolls.entrySet()) {
            final AbsLiveActor actor = entry.getKey();
            SortEntry put = new SortEntry(entry.getValue().second, actor.getInitiativeBonus(),
                    randomizer.nextInt(1024), actor);
            order[count++] = put;
        }
        Arrays.sort(order, new Comparator<SortEntry>() {
            @Override
            public int compare(SortEntry left, SortEntry right) {
                if(left.initRoll > right.initRoll) return -1;
                else if(left.initRoll < right.initRoll) return 1;
                if(left.bonus > right.bonus) return -1;
                else if(left.bonus < right.bonus) return 1;
                if(left.rand > right.rand) return -1;
                else if(left.rand < right.rand) return 1;
                return 0; // super unlikely!
            }
        });
        count = 0;
        for (SortEntry se : order) {
            initiative[count] = se.initRoll;
            battlers[count] = se.actor;
            count++;
        }
        session.battleState = new BattleHelper(initiative, battlers);
        startActivity(new Intent(this, BattleActivity.class));
    }

    // ServiceConnection vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        PartyJoinOrderService.LocalBinder real = (PartyJoinOrderService.LocalBinder)service;
        game = real.getConcreteService();
        final SessionHelper.PlayState session = game.getPlaySession();
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

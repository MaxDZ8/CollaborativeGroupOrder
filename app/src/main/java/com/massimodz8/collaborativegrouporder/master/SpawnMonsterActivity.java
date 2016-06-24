package com.massimodz8.collaborativegrouporder.master;

import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Base64;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.protobuf.nano.CodedInputByteBufferNano;
import com.google.protobuf.nano.CodedOutputByteBufferNano;
import com.massimodz8.collaborativegrouporder.InternalStateService;
import com.massimodz8.collaborativegrouporder.MaxUtils;
import com.massimodz8.collaborativegrouporder.MonsterVH;
import com.massimodz8.collaborativegrouporder.PreSeparatorDecorator;
import com.massimodz8.collaborativegrouporder.R;
import com.massimodz8.collaborativegrouporder.RunningServiceHandles;
import com.massimodz8.collaborativegrouporder.SpawnHelper;
import com.massimodz8.collaborativegrouporder.protocol.nano.MonsterData;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;
import com.massimodz8.collaborativegrouporder.protocol.nano.PreparedEncounters;
import com.massimodz8.collaborativegrouporder.protocol.nano.UserOf;

import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public class SpawnMonsterActivity extends AppCompatActivity {
    private @UserOf InternalStateService.Data data;

    public static boolean includePreparedBattles = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spawn_monster);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        final ActionBar sab = getSupportActionBar();
        if(null != sab) sab.setDisplayHomeAsUpEnabled(true);
        data = RunningServiceHandles.getInstance().state.data; // this should be no trouble but comes handy

        list = (RecyclerView) findViewById(R.id.sma_matchedList);
        final MyMatchLister adapter = new MyMatchLister();
        list.addItemDecoration(new PreSeparatorDecorator(list, SpawnMonsterActivity.this) {
            @Override
            protected boolean isEligible(int position) { return position != 0; }
        });
        list.setAdapter(adapter);
        list.setVisibility(View.VISIBLE);

        SpawnableAdventuringActorVH.intCrFormat = getString(R.string.mVH_challangeRatio_integral);
        SpawnableAdventuringActorVH.ratioCrFormat = getString(R.string.mVH_challangeRatio_fraction);
        SpawnableAdventuringActorVH.akaFormat = getString(R.string.sma_alternateNameFormat);

        final FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setVisibility(View.INVISIBLE);
        fab.setEnabled(false);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final SpawnHelper search = RunningServiceHandles.getInstance().search;
                search.spawn = new ArrayList<>();
                for (Map.Entry<MonsterData.Monster, Integer> entry : search.mobCount.entrySet()) {
                    spawn(entry.getKey(), entry.getValue());
                }
                for (Map.Entry<PreparedEncounters.Battle, Integer> entry : search.battleCount.entrySet()) {
                    Integer count = entry.getValue();
                    if(count == null || count < 1) continue;
                    for (Network.ActorState fighter : entry.getKey().actors) {
                        spawn(fighter, count);
                    }
                }
                // My state is no more needed. Getting the rid of everything is done on BattleActivity result,
                // which also manages 'back'.
                finish();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        data = RunningServiceHandles.getInstance().state.data; // this should be no trouble but comes handy
        final SpawnHelper search = RunningServiceHandles.getInstance().search;
        final Runnable onCompleted = new Runnable() {
            @Override
            public void run() {
                search.currentQuery = null;
                matched.addAll(search.customs);
                for (SpawnHelper.MatchedEntry[] arr : search.parMatches) {
                    if(arr == null) continue;
                    Collections.addAll(matched, arr);
                }
                MaxUtils.setVisibility(SpawnMonsterActivity.this, View.GONE, R.id.sma_progress);
                final TextView status = (TextView) findViewById(R.id.sma_status);
                if(matched.size() == 1) status.setText(R.string.sma_status_matchedSingle);
                else status.setText(String.format(getString(R.string.sma_status_matchedMultiple), matched.size()));

                localId = new IdentityHashMap<>(matched.size());
                int temp = 0;
                for (SpawnHelper.MatchedEntry el : matched) localId.put(el, temp++);
                if(search.battleCount == null) {
                    search.battleCount = new IdentityHashMap<>(matched.size());
                    search.mobCount = new IdentityHashMap<>(matched.size());

                    final String query = getIntent().getStringExtra(SearchManager.QUERY).trim();
                    FirebaseAnalytics surveyor = FirebaseAnalytics.getInstance(SpawnMonsterActivity.this);
                    Bundle info = new Bundle();
                    info.putString(FirebaseAnalytics.Param.SEARCH_TERM, query);
                    info.putStringArrayList(MaxUtils.FA_PARAM_SEARCH_RESULTS, mobNames(matched));
                    surveyor.logEvent(FirebaseAnalytics.Event.VIEW_SEARCH_RESULTS, info);
                }
                list.getAdapter().notifyDataSetChanged();
            }
        };
        search.whenReady(new Runnable() {
            @Override
            public void run() {
                Intent intent = getIntent();
                if (!Intent.ACTION_SEARCH.equals(intent.getAction())) return;
                final String trimmed = intent.getStringExtra(SearchManager.QUERY).trim();
                final String query = trimmed.toLowerCase();
                if(search.currentQuery != null && search.currentQuery.equals(query)) { // we have already completed and produced results!
                    onCompleted.run();
                    return;
                }
                search.beginSearch(query, includePreparedBattles, onCompleted);

                Pattern simplifier = Pattern.compile("\\s\\(\\d*\\)");
                FirebaseAnalytics surveyor = FirebaseAnalytics.getInstance(SpawnMonsterActivity.this);
                Bundle info = new Bundle();
                info.putString(FirebaseAnalytics.Param.SEARCH_TERM, trimmed);
                final PartyJoinOrder play = RunningServiceHandles.getInstance().play;
                if(play != null) info.putStringArrayList(MaxUtils.FA_PARAM_MONSTERS, mobNames(play.session.temporaries, simplifier));
                // preparing battles is less interesting for me, less performance path, searching new battles is the critical path
                surveyor.logEvent(FirebaseAnalytics.Event.SEARCH, info);
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        final SpawnHelper search = RunningServiceHandles.getInstance().search;
        search.whenReady(null);
        search.beginSearch(null, false, null);
    }

    private ArrayList<String> mobNames(ArrayList<SpawnHelper.MatchedEntry> matched) {
        ArrayList<String> res = new ArrayList<>(64);
        for (SpawnHelper.MatchedEntry el : matched) {
            MaxUtils.hasher.reset();
            final String name = el.mob != null? getPreferredName(el.mob) : el.battle.desc;
            final String prefix = el.mob != null? "M:" : String.format(Locale.ENGLISH, "PE(%1$d):", el.battle.actors.length);
            res.add(prefix + Base64.encodeToString(MaxUtils.hasher.digest(name.getBytes()), Base64.DEFAULT));
        }
        return res;
    }

    private static ArrayList<String> mobNames(ArrayList<Network.ActorState> mobs, Pattern simplifier) {
        int count = 0;
        for (Network.ActorState as : mobs) {
            if(as.type != Network.ActorState.T_MOB) continue;
            count++;
        }
        ArrayList<String> res = new ArrayList<>(count);
        // Problem: what if the user inputs custom monsters which names are (C), (R), ™ or whatever?
        // I cannot just store them. So what I'm doing: I remove everything I might have added to disambiguate,
        // I remove digits, (), punctuation and then hash it. Same hash, same thing. But! I cannot store those either as
        // bundles have arrays of strings but not arrays of arrays... meh! I just send everything Base64 and be done.
        for (Network.ActorState as : mobs) {
            if(as.type != Network.ActorState.T_MOB) continue;
            String cleared = simplifier.matcher(as.name).replaceAll("");
            MaxUtils.hasher.reset();
            res.add(Base64.encodeToString(MaxUtils.hasher.digest(cleared.getBytes()), Base64.DEFAULT));
        }
        return res;
    }


    private void spawn(MonsterData.Monster mob, int count) {
        if(count < 1) return; // malfunction, but just ignore
        String presentation = getPreferredName(mob);
        final SpawnHelper search = RunningServiceHandles.getInstance().search;
        final HashMap<String, Integer> index = search.nextMobIndex;
        for(int loop = 0; loop < count; loop++) {
            final Integer next = index.get(presentation);
            if (next != null) {
                index.put(presentation, next + 1);
                presentation = String.format(Locale.getDefault(), getString(R.string.sma_monsterNameSpawnNote), presentation, next);
            } else index.put(presentation, 1);
            final Network.ActorState as = actorState(mob, presentation);
            search.spawn.add(as);
        }
    }


    private void spawn(Network.ActorState original, int count) {
        if(count < 1) return; // malfunction, but just ignore
        String presentation = original.name;
        final SpawnHelper search = RunningServiceHandles.getInstance().search;
        final HashMap<String, Integer> index = search.nextMobIndex;

        byte[] ugly = new byte[original.getSerializedSize()];
        CodedOutputByteBufferNano out = CodedOutputByteBufferNano.newInstance(ugly);
        try {
           original.writeTo(out);
        } catch (IOException e) {
        // Can this happen? I don't think so!
            return;
        }
        CodedInputByteBufferNano serialized = CodedInputByteBufferNano.newInstance(ugly);
        for(int loop = 0; loop < count; loop++) {
            final Integer next = index.get(presentation);
            if (next != null) {
                index.put(presentation, next + 1);
                presentation = String.format(Locale.getDefault(), getString(R.string.sma_monsterNameSpawnNote), presentation, next);
            } else index.put(presentation, 1);
            final Network.ActorState as = actorState(serialized, presentation);
            search.spawn.add(as);
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.spawn_monster_activity, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.sma_menu_showMonsterBookInfo: {
                final ArrayList<MonsterData.Monster> flat = new ArrayList<>();
                final ArrayList<String[]> names = new ArrayList<>();
                for (MonsterData.MonsterBook.Entry entry : data.monsters.entries) {
                    final MonsterData.Monster main = entry.main;
                    flat.add(main);
                    names.add(main.header.name);
                    for (MonsterData.Monster modi : entry.variations) {
                        flat.add(modi);
                        names.add(completeVariationNames(main.header.name, modi.header.name));
                    }
                }
                final android.support.v7.app.AlertDialog dlg = new android.support.v7.app.AlertDialog.Builder(this, R.style.AppDialogStyle)
                        .setView(R.layout.dialog_monster_book_info)
                        .show();
                final RecyclerView rv = (RecyclerView) dlg.findViewById(R.id.sma_dlg_smbi_list);
                final CompleteListAdapter la = new CompleteListAdapter(flat, names, rv, MonsterVH.MODE_MINIMAL, null);
                final TextView created = (TextView) dlg.findViewById(R.id.sma_dlg_smbi_created);
                final TextView entries = (TextView) dlg.findViewById(R.id.sma_dlg_smbi_entries);
                final TextView count = (TextView) dlg.findViewById(R.id.sma_dlg_smbi_monstersCount);
                final String when = DateFormat.getDateInstance().format(new Date(data.monsters.created.seconds * 1000));
                created.setText(when);
                entries.setText(String.valueOf(data.monsters.entries.length));
                count.setText(String.valueOf(la.getItemCount()));
                break;
            }
        }
        return false;
    }

    private class CompleteListAdapter extends RecyclerView.Adapter<MonsterVH> {
        final int visMode;

        final ArrayList<MonsterData.Monster> monsters;
        final ArrayList<String[]> names;
        final Map<MonsterData.Monster, Integer> spawnCount;
        Runnable onSpawnableChanged;
        public CompleteListAdapter(ArrayList<MonsterData.Monster> flat, ArrayList<String[]> names, RecyclerView rv, int visMode, @Nullable Map<MonsterData.Monster, Integer> spawnCount) {
            setHasStableIds(true);
            this.visMode = visMode;
            this.spawnCount = spawnCount;
            monsters = flat;
            this.names = names;
            rv.setAdapter(this);
            rv.addItemDecoration(new PreSeparatorDecorator(rv, SpawnMonsterActivity.this) {
                @Override
                protected boolean isEligible(int position) {
                    return position != 0;
                }
            });
        }

        @Override
        public MonsterVH onCreateViewHolder(ViewGroup parent, int viewType) {
            final MonsterVH res = new MonsterVH(getLayoutInflater(), parent, SpawnMonsterActivity.this, spawnCount);
            res.visMode = visMode;
            res.onSpawnableChanged = onSpawnableChanged;
            return res;
        }

        @Override
        public void onBindViewHolder(MonsterVH holder, int position) {
            holder.bindData(names.get(position), monsters.get(position));
        }

        @Override
        public int getItemCount() { return monsters.size(); }

        @Override
        public long getItemId(int position) { return position; }
    }


    private static String[] completeVariationNames(String[] main, String[] modi) {
        if(modi.length == 0) return main;
        String[] all = new String[main.length + modi.length];
        System.arraycopy(main, 0, all, 0, main.length);
        System.arraycopy(modi, 0, all, main.length, modi.length);
        return all;
    }

    /**
     * The main problem here being that mob might be an inner monster, thereby I would have to
     * find its parent and use its 'real' name [0], which is assumed to be preferred over variations.
     * @param mob Monster to search for parents.
     * @return mob name[0] if monster is a parent, otherwise parent.name[0]
     */
    private String getPreferredName(MonsterData.Monster mob) {
        for (MonsterData.MonsterBook.Entry entry : data.monsters.entries) {
            if(mob == entry.main) return entry.main.header.name[0];
            for (MonsterData.Monster inner : entry.variations) {
                if(mob == inner) return entry.main.header.name[0];
            }
        }
        for (MonsterData.MonsterBook.Entry entry : data.monsters.entries) {
            if(mob == entry.main) return entry.main.header.name[0];
            for (MonsterData.Monster inner : entry.variations) {
                if(mob == inner) return entry.main.header.name[0];
            }
        }
        return "!! Not found !!"; // impossible
    }


    private static Network.ActorState actorState(MonsterData.Monster def, String name) {
        Network.ActorState build = new Network.ActorState();
        build.type = Network.ActorState.T_MOB;
        build.peerKey = -1; // <-- watch out for the peerkey!
        build.name = name;
        build.currentHP = build.maxHP = 666; // todo generate(mob.defense.hp)
        build.initiativeBonus = def.header.initiative;
        build.cr = new Network.ActorState.ChallangeRatio();
        build.cr.numerator = def.header.cr.numerator;
        build.cr.denominator = def.header.cr.denominator;
        return build;
    }

    /// We need to copy the original object anyway!
    private static Network.ActorState actorState(CodedInputByteBufferNano ori, String name) {
        Network.ActorState as = new Network.ActorState();
        try {
            as.mergeFrom(ori);
        } catch (IOException e) {
            // impossible in this context
        }
        as.name = name;
        return as;
    }

    private void countFightersAndToggleFab(int positionChanged) {
        int count = 0;
        final SpawnHelper search = RunningServiceHandles.getInstance().search;
        for (Map.Entry<MonsterData.Monster, Integer> el : search.mobCount.entrySet()) {
            final Integer v = el.getValue();
            if(v != null && v > 0) count += v;
        }
        for (Map.Entry<PreparedEncounters.Battle, Integer> el : search.battleCount.entrySet()) {
            final Integer v = el.getValue();
            if (v != null && v > 0) count += v;
        }
        final FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        MaxUtils.beginDelayedTransition(SpawnMonsterActivity.this);
        fab.setEnabled(count != 0);
        fab.setVisibility(count != 0 ? View.VISIBLE : View.INVISIBLE);
        list.getAdapter().notifyItemChanged(positionChanged);
    }

    class MyMatchLister extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        public MyMatchLister() {
            setHasStableIds(true);
        }

        @Override
        public int getItemCount() { return matched.size(); }

        @Override
        public int getItemViewType(int position) { return matched.get(position).mob != null? MONSTER : BATTLE; }
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            if(viewType == MONSTER) {
                return new SpawnableAdventuringActorVH(getLayoutInflater(), parent) {
                    @Override
                    public void onClick(View v) {
                        if (mob == null) return; // impossible by context
                        final IdentityHashMap<MonsterData.Monster, Integer> mobCount = RunningServiceHandles.getInstance().search.mobCount;
                        Integer myCount = mobCount.get(mob);
                        if (myCount != null) mobCount.put(mob, -myCount);
                        else mobCount.put(mob, 1);
                        countFightersAndToggleFab(list.getChildAdapterPosition(itemView));
                    }

                    @Override
                    protected String selectedText() {
                        final IdentityHashMap<MonsterData.Monster, Integer> mobCount = RunningServiceHandles.getInstance().search.mobCount;
                        final Integer count = mobCount.get(mob);
                        if(count == null || count < 1) return null;
                        return String.format(Locale.getDefault(), getString(R.string.mVH_spawnCountFeedback), count);
                    }
                };
            }
            return new PreparedBattleVH(getLayoutInflater(), parent) {
                @Override
                public void onClick(View v) {
                    // Almost copypasted from SAAVH.onClick above!
                    if (battle == null) return; // impossible by context
                    final IdentityHashMap<PreparedEncounters.Battle, Integer> battleCount = RunningServiceHandles.getInstance().search.battleCount;
                    Integer myCount = battleCount.get(battle);
                    if (myCount != null) battleCount.put(battle, -myCount);
                    else battleCount.put(battle, 1);
                    countFightersAndToggleFab(list.getChildAdapterPosition(itemView));
                }

                @Override
                protected String selectedText() {
                    final Integer count = RunningServiceHandles.getInstance().search.battleCount.get(battle);
                    if(count == null || count < 1) return null;
                    return getString(R.string.sma_battleWillFight);
                }
            };
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            final SpawnHelper.MatchedEntry src = matched.get(position);
            if(holder instanceof SpawnableAdventuringActorVH) {
                SpawnableAdventuringActorVH real = (SpawnableAdventuringActorVH)holder;
                real.bindData(src.mob, src.name);
            }
            if(holder instanceof PreparedBattleVH) {
                PreparedBattleVH real = (PreparedBattleVH)holder;
                real.bindData(src.battle);
            }
        }

        @Override
        public long getItemId(int position) {
            Integer id = localId.get(matched.get(position));
            return id == null? RecyclerView.NO_ID : id;
        }

        private static final int MONSTER = 1;
        private static final int BATTLE = 2;
    }
    private final ArrayList<SpawnHelper.MatchedEntry> matched = new ArrayList<>();
    private RecyclerView list;
    public IdentityHashMap<SpawnHelper.MatchedEntry, Integer> localId = new IdentityHashMap<>(); // keeps track of lister ids in case in the future I swipe them away
}

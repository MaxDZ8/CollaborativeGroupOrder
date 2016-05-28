package com.massimodz8.collaborativegrouporder.master;

import android.app.SearchManager;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.protobuf.nano.CodedInputByteBufferNano;
import com.google.protobuf.nano.CodedOutputByteBufferNano;
import com.massimodz8.collaborativegrouporder.MaxUtils;
import com.massimodz8.collaborativegrouporder.MonsterVH;
import com.massimodz8.collaborativegrouporder.PreSeparatorDecorator;
import com.massimodz8.collaborativegrouporder.R;
import com.massimodz8.collaborativegrouporder.protocol.nano.MonsterData;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;
import com.massimodz8.collaborativegrouporder.protocol.nano.PreparedEncounters;

import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

public class SpawnMonsterActivity extends AppCompatActivity {
    public static boolean includePreparedBattles = true;
    public static ArrayList<Network.ActorState> found;
    public static MonsterData.MonsterBook monsters, custom;
    public static PreparedEncounters.Collection preppedBattles;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spawn_monster);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        final ActionBar sab = getSupportActionBar();
        if(null != sab) sab.setDisplayHomeAsUpEnabled(true);

        // Get the intent, verify the action and get the query
        Intent intent = getIntent();
        if(!Intent.ACTION_SEARCH.equals(intent.getAction())) return;
        final String query = intent.getStringExtra(SearchManager.QUERY).trim();

        SpawnableAdventuringActorVH.intCrFormat = getString(R.string.mVH_challangeRatio_integral);
        SpawnableAdventuringActorVH.ratioCrFormat = getString(R.string.mVH_challangeRatio_fraction);

        new AsyncTask<Void, Void, Void>() {
            final ArrayList<MatchedEntry> matched = new ArrayList<>();
            final ArrayList<String[]> names = new ArrayList<>();
            final String lcq = query.toLowerCase();
            final IdentityHashMap<Network.ActorState, MonsterData.Monster> definitions = new IdentityHashMap<>();


            @Override
            protected Void doInBackground(Void... params) {
                final IdentityHashMap<String, String> tolower = new IdentityHashMap<>();
                if(includePreparedBattles) {
                    for (PreparedEncounters.Battle battle : preppedBattles.battles) {
                        String lc = battle.desc.toLowerCase();
                        if(lc.startsWith(lcq)) {
                            MatchedEntry build = new MatchedEntry();
                            build.battle = battle;
                            matched.add(build);

                        }
                    }
                    for (PreparedEncounters.Battle battle : preppedBattles.battles) {
                        String lc = battle.desc.toLowerCase();
                        if(lc.indexOf(lcq) >= 1) {
                            MatchedEntry build = new MatchedEntry();
                            build.battle = battle;
                            matched.add(build);
                        }
                    }
                }
                lowerify(tolower, monsters);
                lowerify(tolower, custom);
                matchStarting(tolower, custom);
                matchStarting(tolower, monsters);
                matchContaining(tolower, custom);
                matchContaining(tolower, monsters);
                return null;
            }

            private void matchStarting(IdentityHashMap<String, String> tolower, MonsterData.MonsterBook book) {
                for (MonsterData.MonsterBook.Entry entry : book.entries) {
                    boolean matched = false;
                    if(anyStarts(entry.main.header.name, lcq, tolower)) {
                        final MatchedEntry built = new MatchedEntry();
                        built.mob = actorState(entry.main, entry.main.header.name[0]);
                        this.matched.add(built);
                        names.add(entry.main.header.name);
                        definitions.put(built.mob, entry.main);
                        matched = true;
                    }
                    for (MonsterData.Monster variation : entry.variations) {
                        if(matched || anyStarts(variation.header.name, lcq, tolower)) {
                            final MatchedEntry built = new MatchedEntry();
                            built.mob = actorState(variation, entry.main.header.name[0]);
                            this.matched.add(built);
                            names.add(completeVariationNames(entry.main.header.name, variation.header.name));
                            definitions.put(built.mob, variation);
                        }
                    }
                }
            }

            private void matchContaining(IdentityHashMap<String, String> tolower, MonsterData.MonsterBook book) {
                for (MonsterData.MonsterBook.Entry entry : book.entries) {
                    boolean matched = false;
                    if(anyContains(entry.main.header.name, lcq, 1, tolower)) {
                        final MatchedEntry built = new MatchedEntry();
                        built.mob = actorState(entry.main, entry.main.header.name[0]);
                        this.matched.add(built);
                        names.add(entry.main.header.name);
                        definitions.put(built.mob, entry.main);
                        matched = true;
                    }
                    for (MonsterData.Monster variation : entry.variations) {
                        if(matched || anyContains(variation.header.name, lcq, 1, tolower)) {
                            final MatchedEntry built = new MatchedEntry();
                            built.mob = actorState(variation, entry.main.header.name[0]);
                            this.matched.add(built);
                            names.add(completeVariationNames(entry.main.header.name, variation.header.name));
                            definitions.put(built.mob, variation);
                        }
                    }
                }
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                SpawnMonsterActivity.this.ids = new IdentityHashMap<>();
                int count = 0;
                for (MatchedEntry as : matched) ids.put(as, count++);
                SpawnMonsterActivity.this.matched = matched;
                SpawnMonsterActivity.this.definitions = definitions;
                MaxUtils.beginDelayedTransition(SpawnMonsterActivity.this);
                list = (RecyclerView) findViewById(R.id.sma_matchedList);
                final MyMatchLister adapter = new MyMatchLister();
                list.addItemDecoration(new PreSeparatorDecorator(list, SpawnMonsterActivity.this) {
                    @Override
                    protected boolean isEligible(int position) { return position != 0; }
                });
                list.setAdapter(adapter);
                list.setVisibility(View.VISIBLE);
                MaxUtils.setVisibility(SpawnMonsterActivity.this, View.GONE, R.id.sma_progress);
                final TextView status = (TextView) findViewById(R.id.sma_status);
                if(this.matched.size() == 1) status.setText(R.string.sma_status_matchedSingle);
                else status.setText(String.format(getString(R.string.sma_status_matchedMultiple), this.matched.size()));
            }
        }.execute();

        final FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setVisibility(View.INVISIBLE);
        fab.setEnabled(false);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                HashMap<String, Integer> nameColl = new HashMap<>();
                if(found == null) found = new ArrayList<>();
                for (Map.Entry<Network.ActorState, Integer> entry : mobCount.entrySet()) spawn(nameColl, entry.getKey(), entry.getValue());
                for (Map.Entry<PreparedEncounters.Battle, Integer> entry : battleCount.entrySet()) {
                    Integer count = entry.getValue();
                    if(count == null || count < 1) continue;
                    for (Network.ActorState fighter : entry.getKey().actors) spawn(nameColl, fighter, count);
                }
                finish();
            }
        });
    }

    private void spawn(HashMap<String, Integer> nameColl, Network.ActorState actorState, Integer count) {
        if(count == null) return; // impossible
        if(count < 1) return;
        final MonsterData.Monster def = definitions.get(actorState);
        final String presentation = def != null? getPreferredName(def) : actorState.name;
        byte[] ugly = new byte[actorState.getSerializedSize()];
        CodedOutputByteBufferNano out = CodedOutputByteBufferNano.newInstance(ugly);
        try {
            actorState.writeTo(out);
        } catch (IOException e) {
            // Can this happen? I don't think so!
            return;
        }
        CodedInputByteBufferNano original = CodedInputByteBufferNano.newInstance(ugly);
        for(int spawn = 0; spawn < count; spawn++) {
            Network.ActorState copy = new Network.ActorState();
            try {
                copy.mergeFrom(original);
            } catch (IOException e) {
                // Can this happen? I don't think so!
                return;
            }
            original.resetSizeCounter();
            String display = presentation;
            Integer previously = nameColl.get(presentation);
            if(previously == null) previously = 0;
            else display = String.format(Locale.getDefault(), getString(R.string.sma_monsterNameSpawnNote), display, previously);
            copy.name = display;
            found.add(copy);
            nameColl.put(presentation, previously + 1);
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
                for (MonsterData.MonsterBook.Entry entry : monsters.entries) {
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
                final String when = DateFormat.getDateInstance().format(new Date(monsters.created.seconds * 1000));
                created.setText(when);
                entries.setText(String.valueOf(monsters.entries.length));
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


    private IdentityHashMap<Network.ActorState, Integer> mobCount = new IdentityHashMap<>();
    private IdentityHashMap<Network.ActorState, MonsterData.Monster> definitions = new IdentityHashMap<>();
    private IdentityHashMap<PreparedEncounters.Battle, Integer> battleCount = new IdentityHashMap<>();


    private static boolean anyStarts(String[] arr, String prefix, IdentityHashMap<String, String> tolower) {
        for (String s : arr) {
            if(tolower.get(s).startsWith(prefix)) return true;
        }
        return false;
    }

    private static boolean anyContains(String[] arr, String prefix, int minIndex, IdentityHashMap<String, String> tolower) {
        for (String s : arr) {
            if(tolower.get(s).indexOf(prefix) >= minIndex) return true;
        }
        return false;
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
        for (MonsterData.MonsterBook.Entry entry : monsters.entries) {
            if(mob == entry.main) return entry.main.header.name[0];
            for (MonsterData.Monster inner : entry.variations) {
                if(mob == inner) return entry.main.header.name[0];
            }
        }
        for (MonsterData.MonsterBook.Entry entry : custom.entries) {
            if(mob == entry.main) return entry.main.header.name[0];
            for (MonsterData.Monster inner : entry.variations) {
                if(mob == inner) return entry.main.header.name[0];
            }
        }
        return "!! Not found !!"; // impossible
    }

    private static void lowerify(IdentityHashMap<String, String> result, MonsterData.MonsterBook book) {
        for (MonsterData.MonsterBook.Entry entry : book.entries) {
            for (String s : entry.main.header.name) result.put(s, s.toLowerCase());
            for (MonsterData.Monster variation : entry.variations) {
                for (String s : variation.header.name) result.put(s, s.toLowerCase());
            }
        }
    }

    private static Network.ActorState actorState(MonsterData.Monster def, String name) {
        Network.ActorState build = new Network.ActorState();
        build.type = Network.ActorState.T_MOB;
        build.peerKey = -1; // <-- watch out for the peerkey!
        build.name = name;
        build.currentHP = build.maxHP = 666; // todo generate(mob.defense.hp)
        build.initiativeBonus = def.header.initiative;  // todo select conditional initiatives.
        build.cr = new Network.ActorState.ChallangeRatio();
        build.cr.numerator = def.header.cr.numerator;
        build.cr.denominator = def.header.cr.denominator;
        return build;
    }

    private void countFightersAndToggleFab(int positionChanged) {
        int count = 0;
        for (Map.Entry<Network.ActorState, Integer> el : mobCount.entrySet()) {
            final Integer v = el.getValue();
            if(v != null && v > 0) count += v;
        }
        for (Map.Entry<PreparedEncounters.Battle, Integer> el : battleCount.entrySet()) {
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
                        if (actor == null) return; // impossible by context
                        Integer myCount = mobCount.get(actor);
                        if (myCount != null) mobCount.put(actor, -myCount);
                        else mobCount.put(actor, 1);
                        countFightersAndToggleFab(list.getChildAdapterPosition(itemView));
                    }

                    @Override
                    protected String selectedText() {
                        final Integer count = mobCount.get(actor);
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
                    Integer myCount = battleCount.get(battle);
                    if (myCount != null) battleCount.put(battle, -myCount);
                    else battleCount.put(battle, 1);
                    countFightersAndToggleFab(list.getChildAdapterPosition(itemView));
                }

                @Override
                protected String selectedText() {
                    final Integer count = battleCount.get(battle);
                    if(count == null || count < 1) return null;
                    return getString(R.string.sma_battleWillFight);
                }
            };
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            final MatchedEntry src = matched.get(position);
            if(holder instanceof SpawnableAdventuringActorVH) {
                SpawnableAdventuringActorVH real = (SpawnableAdventuringActorVH)holder;
                real.bindData(src.mob);
            }
            if(holder instanceof PreparedBattleVH) {
                PreparedBattleVH real = (PreparedBattleVH)holder;
                real.bindData(src.battle);
            }
        }

        @Override
        public long getItemId(int position) {
            Integer id = ids.get(matched.get(position));
            return id == null? RecyclerView.NO_ID : id;
        }

        private static final int MONSTER = 1;
        private static final int BATTLE = 2;
    }

    static class MatchedEntry {
        Network.ActorState mob;
        PreparedEncounters.Battle battle;
    }
    private ArrayList<MatchedEntry> matched;
    private IdentityHashMap<MatchedEntry, Integer> ids;
    private RecyclerView list;
}

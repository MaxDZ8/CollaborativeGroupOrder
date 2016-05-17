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
import java.util.Collections;
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

        new AsyncTask<Void, Void, Void>() {
            final ArrayList<Network.ActorState> mobs = new ArrayList<>();
            final ArrayList<String[]> names = new ArrayList<>();
            final String lcq = query.toLowerCase();
            final IdentityHashMap<Network.ActorState, MonsterData.Monster> definitions = new IdentityHashMap<>();


            @Override
            protected Void doInBackground(Void... params) {
                final IdentityHashMap<String, String> tolower = new IdentityHashMap<>();
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
                        Network.ActorState built = actorState(entry.main, entry.main.header.name[0]);
                        mobs.add(built);
                        names.add(entry.main.header.name);
                        definitions.put(built, entry.main);
                        matched = true;
                    }
                    for (MonsterData.Monster variation : entry.variations) {
                        if(matched || anyStarts(variation.header.name, lcq, tolower)) {
                            Network.ActorState built = actorState(variation, entry.main.header.name[0]);
                            mobs.add(built);
                            names.add(completeVariationNames(entry.main.header.name, variation.header.name));
                            definitions.put(built, variation);
                        }
                    }
                }
            }

            private void matchContaining(IdentityHashMap<String, String> tolower, MonsterData.MonsterBook book) {
                for (MonsterData.MonsterBook.Entry entry : book.entries) {
                    boolean matched = false;
                    if(anyContains(entry.main.header.name, lcq, 1, tolower)) {
                        Network.ActorState built = actorState(entry.main, entry.main.header.name[0]);
                        mobs.add(built);
                        names.add(entry.main.header.name);
                        definitions.put(built, entry.main);
                        matched = true;
                    }
                    for (MonsterData.Monster variation : entry.variations) {
                        if(matched || anyContains(variation.header.name, lcq, 1, tolower)) {
                            Network.ActorState built = actorState(variation, entry.main.header.name[0]);
                            mobs.add(built);
                            names.add(completeVariationNames(entry.main.header.name, variation.header.name));
                            definitions.put(built, variation);
                        }
                    }
                }
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                SpawnMonsterActivity.this.ids = new IdentityHashMap<>();
                int matched = 0;
                for (Network.ActorState as : mobs) ids.put(as, matched++);
                SpawnMonsterActivity.this.mobs = this.mobs;
                SpawnMonsterActivity.this.definitions = definitions;
                MaxUtils.beginDelayedTransition(SpawnMonsterActivity.this);
                final RecyclerView list = (RecyclerView) findViewById(R.id.sma_matchedList);
                SpawnableAdventuringActorVH.countFormat = getString(R.string.vhMLE_spawnCountFeedback);
                SpawnableAdventuringActorVH.intCrFormat = getString(R.string.vhMLE_challangeRatio_integral);
                SpawnableAdventuringActorVH.ratioCrFormat = getString(R.string.vhMLE_challangeRatio_fraction);
                final RecyclerView.Adapter<SpawnableAdventuringActorVH> adapter = new MyMobLister(list);
                list.addItemDecoration(new PreSeparatorDecorator(list, SpawnMonsterActivity.this) {
                    @Override
                    protected boolean isEligible(int position) { return position != 0; }
                });
                list.setAdapter(adapter);
                list.setVisibility(View.VISIBLE);
                MaxUtils.setVisibility(SpawnMonsterActivity.this, View.GONE, R.id.sma_progress);
                final TextView status = (TextView) findViewById(R.id.sma_status);
                if(this.mobs.size() == 1) status.setText(R.string.sma_status_matchedSingleMonster);
                else status.setText(String.format(getString(R.string.sma_status_matchedMultipleMonsters), this.mobs.size()));
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
                for (Map.Entry<Network.ActorState, Integer> entry : spawnCounts.entrySet()) {
                    final Integer count = entry.getValue();
                    if(count == null) continue; // impossible
                    if(count < 1) continue;
                    Network.ActorState as = entry.getKey();
                    final MonsterData.Monster def = definitions.get(as);
                    final String presentation = def != null? getPreferredName(def) : as.name;
                    byte[] ugly = new byte[as.getSerializedSize()];
                    CodedOutputByteBufferNano out = CodedOutputByteBufferNano.newInstance(ugly);
                    try {
                        as.writeTo(out);
                    } catch (IOException e) {
                        // Can this happen? I don't think so!
                        continue;
                    }
                    CodedInputByteBufferNano original = CodedInputByteBufferNano.newInstance(ugly);
                    for(int spawn = 0; spawn < count; spawn++) {
                        Network.ActorState copy = new Network.ActorState();
                        try {
                            copy.mergeFrom(original);
                        } catch (IOException e) {
                            // Can this happen? I don't think so!
                            continue;
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
                finish();
            }
        });
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
                final android.support.v7.app.AlertDialog dlg = new android.support.v7.app.AlertDialog.Builder(this)
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


    private IdentityHashMap<Network.ActorState, Integer> spawnCounts = new IdentityHashMap<>();
    private IdentityHashMap<Network.ActorState, MonsterData.Monster> definitions = new IdentityHashMap<>();


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

    class MyMobLister extends RecyclerView.Adapter<SpawnableAdventuringActorVH> {
        public MyMobLister(RecyclerView list) {
            setHasStableIds(true);
            this.list = list;
}

        @Override
        public SpawnableAdventuringActorVH onCreateViewHolder(ViewGroup parent, int viewType) {
            return new SpawnableAdventuringActorVH(getLayoutInflater(), parent, spawnCounts) {
                @Override
                public void onClick(View v) {
                    if(actor == null) return; // impossible by context
                    Integer myCount = spawnCounts.get(actor);
                    int count = 0;
                    if(myCount == null) {
                        myCount = count = 1;
                        spawnCounts.put(actor, myCount);
                    }
                    else if(myCount < 1) { // re-enabling
                        count = -myCount;
                        spawnCounts.put(actor, -myCount);
                    }
                    else { // taking it down
                        spawnCounts.put(actor, -myCount);
                        for (Map.Entry<Network.ActorState, Integer> sc : spawnCounts.entrySet()) {
                            if (sc.getValue() == null || sc.getValue() < 1) continue;
                            count += sc.getValue();
                        }
                    }
                    final FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
                    MaxUtils.beginDelayedTransition(SpawnMonsterActivity.this);
                    fab.setEnabled(count != 0);
                    fab.setVisibility(count != 0? View.VISIBLE : View.INVISIBLE);
                    list.getAdapter().notifyItemChanged(list.getChildAdapterPosition(this.itemView));
                }
            };
        }

        @Override
        public void onBindViewHolder(SpawnableAdventuringActorVH holder, int position) {
            holder.bindData(mobs.get(position));
        }

        @Override
        public int getItemCount() { return mobs.size(); }

        @Override
        public long getItemId(int position) {
            Integer id = ids.get(mobs.get(position));
            return id == null? RecyclerView.NO_ID : id;
        }
        private final RecyclerView list;
    }

    private ArrayList<Network.ActorState> mobs;
    private IdentityHashMap<Network.ActorState, Integer> ids;
}

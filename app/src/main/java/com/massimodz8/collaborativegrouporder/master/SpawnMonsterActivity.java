package com.massimodz8.collaborativegrouporder.master;

import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
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

import com.massimodz8.collaborativegrouporder.MaxUtils;
import com.massimodz8.collaborativegrouporder.MonsterVH;
import com.massimodz8.collaborativegrouporder.PreSeparatorDecorator;
import com.massimodz8.collaborativegrouporder.R;
import com.massimodz8.collaborativegrouporder.protocol.nano.MonsterData;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

public class SpawnMonsterActivity extends AppCompatActivity implements ServiceConnection {
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
        query = intent.getStringExtra(SearchManager.QUERY);

        // Stop there. We must also create the action bar first.

        final FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setVisibility(View.INVISIBLE);
        fab.setEnabled(false);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                HashMap<String, Integer> nameColl = new HashMap<>();
                for (Map.Entry<MonsterData.Monster, Integer> entry : spawnCounts.entrySet()) {
                    final Integer count = entry.getValue();
                    if(count == null) continue; // impossible
                    if(count < 1) continue;
                    final MonsterData.Monster mob = entry.getKey();
                    final String presentation = getPreferredName(mob);
                    for(int spawn = 0; spawn < count; spawn++) {
                        String display = presentation;
                        Integer previously = nameColl.get(presentation);
                        if(previously == null) previously = 0;
                        else display = String.format(Locale.getDefault(), getString(R.string.sma_monsterNameSpawnNote), display, previously);
                        MonsterActor actor = new MonsterActor(display);
                        actor.currentHealth = actor.maxHealth = 666; // todo generate(mob.defense.hp)
                        actor.initiativeBonus = mob.header.initiative; // todo select conditional initiatives.
                        session.add(actor);
                        session.willFight(actor, true);
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
        showBookInfo = menu.findItem(R.id.sma_menu_showMonsterBookInfo);

        if(!bindService(new Intent(this, PartyJoinOrderService.class), this, 0)) {
            MaxUtils.beginDelayedTransition(this);
            findViewById(R.id.sma_progress).setVisibility(View.GONE);
            TextView ohno = (TextView) findViewById(R.id.sma_status);
            ohno.setText(R.string.master_cannotBindAdventuringService);
        }

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


    private String query;
    private MenuItem showBookInfo;
    private MonsterData.MonsterBook monsters;
    private IdentityHashMap<MonsterData.Monster, Integer> spawnCounts = new IdentityHashMap<>();
    private SessionHelper.PlayState session;


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

    private void showSearchResults(ArrayList<MonsterData.Monster> mobs, ArrayList<String[]> names) {
        MaxUtils.beginDelayedTransition(this);
        final RecyclerView list = (RecyclerView) findViewById(R.id.sma_matchedList);
        final CompleteListAdapter adapter = new CompleteListAdapter(mobs, names, list, MonsterVH.MODE_STANDARD, spawnCounts);
        adapter.onSpawnableChanged = new Runnable() {
            @Override
            public void run() {
                int count = 0;
                for (Map.Entry<MonsterData.Monster, Integer> sc : spawnCounts.entrySet()) {
                    if (sc.getValue() == null || sc.getValue() < 1) continue;
                    count += sc.getValue();
                }
                final FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
                MaxUtils.beginDelayedTransition(SpawnMonsterActivity.this);
                fab.setEnabled(count != 0);
                fab.setVisibility(count != 0? View.VISIBLE : View.INVISIBLE);
            }
        };
        list.setAdapter(adapter);
        list.addItemDecoration(new PreSeparatorDecorator(list, this) {
            @Override
            protected boolean isEligible(int position) {
                return position != 0;
            }
        });
        list.setVisibility(View.VISIBLE);
        MaxUtils.setVisibility(this, View.GONE, R.id.sma_progress);
        final TextView status = (TextView) findViewById(R.id.sma_status);
        if(mobs.size() == 1) status.setText(R.string.sma_status_matchedSingleMonster);
        else status.setText(String.format(getString(R.string.sma_status_matchedMultipleMonsters), mobs.size()));
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
        return "!! Not found !!"; // impossible
    }

    // ServiceConnection vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        PartyJoinOrderService.LocalBinder real = (PartyJoinOrderService.LocalBinder) service;
        final PartyJoinOrderService serv = real.getConcreteService();
        session = serv.getPlaySession();
        monsters = session.monsters;
        showBookInfo.setVisible(true);
        unbindService(this);

        new AsyncTask<Void, Void, Void>() {
            final ArrayList<MonsterData.Monster> mobs = new ArrayList<>();
            final ArrayList<String[]> names = new ArrayList<>();
            final String lcq = query.toLowerCase();


            @Override
            protected Void doInBackground(Void... params) {
                final IdentityHashMap<String, String> tolower = new IdentityHashMap<>();
                for (MonsterData.MonsterBook.Entry entry : monsters.entries) {
                    for (String s : entry.main.header.name) tolower.put(s, s.toLowerCase());
                    for (MonsterData.Monster variation : entry.variations) {
                        for (String s : variation.header.name) tolower.put(s, s.toLowerCase());
                    }
                }

                for (MonsterData.MonsterBook.Entry entry : monsters.entries) {
                    boolean matched = false;
                    if(anyStarts(entry.main.header.name, lcq, tolower)) {
                        mobs.add(entry.main);
                        names.add(entry.main.header.name);
                        matched = true;
                    }
                    for (MonsterData.Monster variation : entry.variations) {
                        if(matched || anyStarts(variation.header.name, lcq, tolower)) {
                            mobs.add(variation);
                            names.add(completeVariationNames(entry.main.header.name, variation.header.name));
                        }
                    }
                }
                for (MonsterData.MonsterBook.Entry entry : monsters.entries) {
                    boolean matched = false;
                    if(anyContains(entry.main.header.name, lcq, 1, tolower)) {
                        mobs.add(entry.main);
                        names.add(entry.main.header.name);
                        matched = true;
                    }
                    for (MonsterData.Monster variation : entry.variations) {
                        if(matched || anyContains(variation.header.name, lcq, 1, tolower)) {
                            mobs.add(variation);
                            names.add(completeVariationNames(entry.main.header.name, variation.header.name));
                        }
                    }
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) { showSearchResults(mobs, names); }
        }.execute();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
    }
    // ServiceConnection ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
}

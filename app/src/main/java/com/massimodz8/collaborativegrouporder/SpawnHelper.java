package com.massimodz8.collaborativegrouporder;

import android.os.Handler;
import android.os.Message;

import com.massimodz8.collaborativegrouporder.protocol.nano.MonsterData;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;
import com.massimodz8.collaborativegrouporder.protocol.nano.PreparedEncounters;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by Massimo on 24/06/2016.
 * Various state to be kept around for the purpose of spawning mobs or mobs from prepared battles.
 * Basically the new state of the much improved SpawnMonsterActivity.
 */
public class SpawnHelper {
    // Support SpawnMonsterActivity persistent state, they are regenerated as soon as new query results are ready
    // and are cleared to null when a new query is launched.
    public IdentityHashMap<MonsterData.Monster, Integer> mobCount;
    public IdentityHashMap<PreparedEncounters.Battle, Integer> battleCount;

    public String currentQuery;
    public ArrayList<Network.ActorState> spawn; // result, if we are spawning something.

    // This accumulates monster spawn strings so they are numbered, easy way to avoid them collide.
    public final HashMap<String, Integer> nextMobIndex = new HashMap<>();

    // This is query result. You can test those against null to see if a query is already running.
    // If you leave them there then they are reused.
    public MatchedEntry[][] parMatches; // from 'core'
    public ArrayList<MatchedEntry> customs;

    SpawnHelper() {
        final int cpus = workerCount();
        searchThreads = Executors.newFixedThreadPool(cpus);
        final InternalStateService.Data data = RunningServiceHandles.getInstance().state.data;
        new Thread(new Runnable() {
            @Override
            public void run() {
                // The time this is launched is fairly time critical for perceived lagginess
                // so give the CPUs some breath, we have plenty of time ahead.
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return; // impossible, unless going down with the app
                }
                // For the purpose of mangling strings, I consider memory indirections/page faults
                // to be the main problem whereas a string being longer or shorter doesn't really
                // matter, so let's look at the number of strings of each data entry and distribute them.
                int refCount = 0;
                for (MonsterData.MonsterBook.Entry el : data.monsters.entries) {
                    for (String str : el.main.header.name) lowerized.put(str, str.toLowerCase());
                    refCount += el.main.header.name.length;
                    for (MonsterData.Monster sub : el.variations) {
                        for (String str : sub.header.name) lowerized.put(str, str.toLowerCase());
                        refCount += sub.header.name.length;
                    }
                }
                refCount /= cpus; // each worker will mangle at most this amount of strings. Exception: last thread mangles everything else!
                // Distributing them however is a bit more involved as each thread mangles an integral amount of Entry objects.
                final int[] bounds = new int[cpus + 1];
                //parMatches = new MatchedEntry[cpus][]; // regenerated every time a new query is launched, if not already there
                int start = 0;
                for(int loop = 0; loop < cpus; loop++) {
                    bounds[loop] = start;
                    int count = 0;
                    while(start < data.monsters.entries.length) {
                        int got = data.monsters.entries[start].main.header.name.length;
                        for (MonsterData.Monster sub : data.monsters.entries[start].variations) got += sub.header.name.length;
                        if(got + count > refCount) break;
                        count += got;
                    }
                    start += count;
                }
                bounds[cpus] = data.monsters.entries.length;
                funnel.sendMessage(funnel.obtainMessage(MSG_STRINGS_MANGLED, bounds));
            }
        }).start();
    }


    public void clear() {
        mobCount = null;
        battleCount = null;
        currentQuery = null;
        spawn = null;
        // nextMobIndex is kept
        // parMatches, custom you cannot trust those, might be updated by late threads

    }
    void shutdown() {
        searchThreads.shutdown();
    }

    public void whenReady(Runnable trigger) {
        onStringsMangled = trigger;
        if(null != trigger && null != assignments) trigger.run();
    }
    public void beginSearch(final String lcQuery, final boolean includePreparedBattles, Runnable onDone) {
        this.onResultsReady = onDone;
        this.currentQuery = lcQuery;
        if(null == lcQuery || onResultsReady == null) return;
        LatchingHandler temp = new LatchingHandler(assignments.length - 1, new Runnable() {
            @Override
            public void run() {
                final InternalStateService.Data data = RunningServiceHandles.getInstance().state.data;
                if(includePreparedBattles) {
                    for (PreparedEncounters.Battle battle : data.customBattles.battles) {
                        if(lowerized.get(battle.desc).startsWith(lcQuery)) {
                            MatchedEntry build = new MatchedEntry();
                            build.battle = battle;
                            customs.add(build);

                        }
                    }
                    for (PreparedEncounters.Battle battle : data.customBattles.battles) {
                        if(lowerized.get(battle.desc).indexOf(lcQuery) >= 1) {
                            MatchedEntry build = new MatchedEntry();
                            build.battle = battle;
                            customs.add(build);
                        }
                    }
                }
                addStarting(customs, lcQuery, data.customMonsters.entries, 0, data.customMonsters.entries.length);
                addContaining(customs, lcQuery, data.customMonsters.entries, 0, data.customMonsters.entries.length);
                if(null != onResultsReady) onResultsReady.run();
            }
        });
        if(null == customs) customs = new ArrayList<>();
        else customs.clear();
        if(null == parMatches) parMatches = new MatchedEntry[workerCount()][]; // regenerated every time a new query is launched, if not already there
        else Arrays.fill(parMatches, null);
        for (int loop = 0; loop < assignments.length; loop++) {
            searchThreads.submit(new RangedSearch(lcQuery, assignments[loop], assignments[loop + 1], loop, temp.ticker));
        }
    }

    public static class MatchedEntry {
        public MonsterData.Monster mob;
        public PreparedEncounters.Battle battle;
        public String[] name; // this is for mob variations only, names to use pulled from container mob
    }

    private final ExecutorService searchThreads;
    private final IdentityHashMap<String, String> lowerized = new IdentityHashMap<>();
    private int[] assignments; // as many entries as cpus, search bounds and marks lowercase strings ready
    private MyHandler funnel = new MyHandler(this);
    private Runnable onStringsMangled, onResultsReady;

    private static class MyHandler extends Handler {
        public MyHandler(SpawnHelper spawnHelper) {
            self = new WeakReference<>(spawnHelper);
        }
        private final WeakReference<SpawnHelper> self;

        @Override
        public void handleMessage(Message msg) {
            SpawnHelper self = this.self.get();
            switch (msg.what) {
                case MSG_STRINGS_MANGLED:
                    self.assignments = (int[])msg.obj;
                    if(null != self.onStringsMangled) self.onStringsMangled.run();
                    self.funnel = null; // can go away now, no more needed.
                    return;
            }
            super.handleMessage(msg);
        }
    }
    private static final int MSG_STRINGS_MANGLED = 1;
    private static int workerCount() {
        return Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    }

    private void addStarting(ArrayList<MatchedEntry> match, String lcq, MonsterData.MonsterBook.Entry[] all, int start, int limit) {
        for(; start < limit; start++) {
            final MonsterData.MonsterBook.Entry entry = all[start];
            boolean matched = false;
            if(anyStarts(entry.main.header.name, lcq)) {
                final MatchedEntry built = new MatchedEntry();
                built.mob = entry.main;
                match.add(built);
                matched = true;
            }
            for (MonsterData.Monster variation : entry.variations) {
                if(matched || anyStarts(variation.header.name, lcq)) {
                    final MatchedEntry built = new MatchedEntry();
                    built.mob = variation;
                    match.add(built);
                }
            }
        }
    }


    private void addContaining(ArrayList<MatchedEntry> match, String lcq, MonsterData.MonsterBook.Entry[] all, int start, int limit) {
        for(; start < limit; start++) {
            final MonsterData.MonsterBook.Entry entry = all[start];
            boolean matched = false;
            if(anyContains(entry.main.header.name, lcq, 1)) {
                final MatchedEntry built = new MatchedEntry();
                built.mob = entry.main;
                match.add(built);
                matched = true;
            }
            for (MonsterData.Monster variation : entry.variations) {
                if(matched || anyContains(variation.header.name, lcq, 1)) {
                    final MatchedEntry built = new MatchedEntry();
                    built.mob = variation;
                    built.name = entry.main.header.name;
                    match.add(built);
                }
            }
        }
    }


    private boolean anyStarts(String[] arr, String prefix) {
        for (String s : arr) {
            if(lowerized.get(s).startsWith(prefix)) return true;
        }
        return false;
    }

    private boolean anyContains(String[] arr, String prefix, int minIndex) {
        for (String s : arr) {
            if(lowerized.get(s).indexOf(prefix) >= minIndex) return true;
        }
        return false;
    }

    private class RangedSearch implements Runnable {
        final String lcQuery;
        final int slot;
        final int from, guard;
        final Runnable onComplete;

        public RangedSearch(String lcQuery, int from, int limit, int dst, Runnable onComplete) {
            this.lcQuery = lcQuery;
            this.from = from;
            this.guard = limit;
            slot = dst;
            this.onComplete = onComplete;
        }

        @Override
        public void run() {
            final ArrayList<MatchedEntry> match = new ArrayList<>(64);
            final MonsterData.MonsterBook.Entry[] many = RunningServiceHandles.getInstance().state.data.monsters.entries;
            addStarting(match, lcQuery, many, from, guard);
            addContaining(match, lcQuery, many, from, guard);
            if(match.isEmpty()) parMatches[slot] = null;
            else {
                MatchedEntry[] arr;
                parMatches[slot] = arr = new MatchedEntry[match.size()];
                int cp = 0;
                for (MatchedEntry el : match) arr[cp++] = el;
            }
            onComplete.run();
        }
    }
}

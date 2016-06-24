package com.massimodz8.collaborativegrouporder;

import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.StringRes;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import com.google.protobuf.nano.Timestamp;
import com.massimodz8.collaborativegrouporder.master.SpawnMonsterActivity;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;
import com.massimodz8.collaborativegrouporder.protocol.nano.PreparedEncounters;
import com.massimodz8.collaborativegrouporder.protocol.nano.UserOf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

public class NewPreparedBattleActivity extends AppCompatActivity {
    private @UserOf PreparedEncounters.Collection custom;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_prepared_battle);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        final android.support.v7.app.ActionBar sab = getSupportActionBar();
        if(sab != null) sab.setDisplayHomeAsUpEnabled(true);
        SearchView swidget = (SearchView)findViewById(R.id.npba_searchMobs);
        swidget.setIconifiedByDefault(true);
        swidget.setQueryHint(getString(R.string.npba_searchHint));

        final SearchManager sm = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        SpawnMonsterActivity.includePreparedBattles = false;
        final ComponentName compName = new ComponentName(this, SpawnMonsterActivity.class);
        swidget.setSearchableInfo(sm.getSearchableInfo(compName));

        RecyclerView rv = (RecyclerView) findViewById(R.id.npba_list);
        rv.setAdapter(new MyMobLister());
        rv.addItemDecoration(new PreSeparatorDecorator(rv, this) {
            @Override
            protected boolean isEligible(int position) {
                return position != 0;
            }
        });
        custom = RunningServiceHandles.getInstance().state.data.customBattles;
    }

    @Override
    protected void onResume() {
        super.onResume();
        final SpawnHelper search = RunningServiceHandles.getInstance().search;
        if(search.spawn != null && search.spawn.size() != 0) {
            int was = mobs.size();
            for (Network.ActorState actor : search.spawn) {
                actor.peerKey = id++;
                mobs.add(actor);
            }
            RecyclerView rv = (RecyclerView) findViewById(R.id.npba_list);
            rv.getAdapter().notifyItemRangeInserted(was, search.spawn.size());
        }
        search.clear();
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.new_prepared_battle_activity, menu);
        saveAction = menu.findItem(R.id.npba_save);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.npba_save: {
                // First I validate the data.
                TextInputLayout til = (TextInputLayout)findViewById(R.id.npba_tilDesc);
                View view = findViewById(R.id.npba_desc);
                final String desc = ((EditText) view).getText().toString();
                if(desc.length() == 0) {
                    til.setError(getString(R.string.npba_emptyDesc));
                    view.requestFocus();
                    return true;
                }
                til.setError(null);
                saveAction.setEnabled(false);
                PreparedEncounters.Battle battle = new PreparedEncounters.Battle();
                battle.desc = desc;
                battle.created = new Timestamp();
                battle.created.seconds = new Date().getTime() / 1000;
                battle.actors = new Network.ActorState[mobs.size()];
                int dst = 0;
                final int clear = new Network.ActorState().peerKey;
                for(Network.ActorState baddie : mobs) {
                    baddie.peerKey = clear;
                    battle.actors[dst++] = baddie;
                }
                PreparedEncounters.Battle[] longer = Arrays.copyOf(custom.battles, custom.battles.length + 1);
                longer[custom.battles.length] = battle;
                custom.battles = longer;
                saving = new AsyncRenamingStore<PreparedEncounters.Collection>(getFilesDir(), PersistentDataUtils.USER_CUSTOM_DATA_SUBDIR, PersistentDataUtils.CUSTOM_ENCOUNTERS_FILE_NAME, custom) {
                    @Override
                    protected String getString(@StringRes int res) {
                        return NewPreparedBattleActivity.this.getString(res);
                    }

                    @Override
                    protected void onPostExecute(Exception e) {
                        setResult(RESULT_OK);
                        finish();
                    }
                };
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    public void onBackPressed() {
        if(saving == null) super.onBackPressed();
    }

    @Override
    public boolean onSupportNavigateUp() { // takes a second anyway.
        return saving == null && super.onSupportNavigateUp();
    }

    // There will hopefully be a day I understand how to write a reusable RV adapter.
    // Until I notice a pattern I keep writing them.
    private class MyMobLister extends RecyclerView.Adapter<AdventuringActorDataVH>{
        MyMobLister() { setHasStableIds(true); }

        @Override
        public AdventuringActorDataVH onCreateViewHolder(ViewGroup parent, int viewType) {
            return new AdventuringActorDataVH(getLayoutInflater().inflate(R.layout.vh_adventuring_actor_data, parent, false)) {
                @Override
                public void onClick(View v) {}
            };
        }

        @Override
        public void onBindViewHolder(AdventuringActorDataVH holder, int position) { holder.bindData(mobs.get(position)); }

        @Override
        public int getItemCount() { return mobs.size(); }

        @Override
        public long getItemId(int position) { return mobs.get(position).peerKey; }
    }

    private ArrayList<Network.ActorState> mobs = new ArrayList<>();
    private int id;
    private MenuItem saveAction;
    private AsyncRenamingStore<PreparedEncounters.Collection> saving;
}

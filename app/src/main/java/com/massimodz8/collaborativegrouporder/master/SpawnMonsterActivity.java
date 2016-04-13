package com.massimodz8.collaborativegrouporder.master;

import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.MaxUtils;
import com.massimodz8.collaborativegrouporder.PreSeparatorDecorator;
import com.massimodz8.collaborativegrouporder.R;
import com.massimodz8.collaborativegrouporder.protocol.nano.MonsterData;

import java.util.Date;
import java.util.Locale;

public class SpawnMonsterActivity extends AppCompatActivity implements ServiceConnection {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spawn_monster);

        // Get the intent, verify the action and get the query
        Intent intent = getIntent();
        if(!Intent.ACTION_SEARCH.equals(intent.getAction())) return;
        query = intent.getStringExtra(SearchManager.QUERY);

        if(!bindService(new Intent(this, PartyJoinOrderService.class), this, 0)) {
            MaxUtils.beginDelayedTransition(this);
            findViewById(R.id.sma_progress).setVisibility(View.GONE);
            TextView ohno = (TextView) findViewById(R.id.sma_status);
            ohno.setText(R.string.master_cannotBindAdventuringService);
            return;
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.spawn_monster_activity, menu);
        showBookInfo = menu.findItem(R.id.sma_menu_showMonsterBookInfo);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.sma_menu_showMonsterBookInfo: {
                final android.support.v7.app.AlertDialog dlg = new android.support.v7.app.AlertDialog.Builder(this)
                        .setTitle(getString(R.string.sma_dlg_monsterBookInfo_title))
                        .setView(R.layout.dialog_monster_book_info)
                        .show();
                final RecyclerView rv = (RecyclerView) dlg.findViewById(R.id.sma_dlg_smbi_list);
                final CompleteListAdapter la = new CompleteListAdapter(monsters, rv);
                final TextView created = (TextView) dlg.findViewById(R.id.sma_dlg_smbi_created);
                final TextView entries = (TextView) dlg.findViewById(R.id.sma_dlg_smbi_entries);
                final TextView count = (TextView) dlg.findViewById(R.id.sma_dlg_smbi_monstersCount);
                created.setText(new Date(monsters.created.seconds * 1000).toString());
                entries.setText(String.valueOf(monsters.entries.length));
                count.setText(String.valueOf(la.getItemCount()));
                break;
            }
        }
        return false;
    }

    private class MonsterVH extends RecyclerView.ViewHolder {
        final TextView name, otherNames, cr, publisherInfo;

        public MonsterVH(LayoutInflater li, ViewGroup parent) {
            super(li.inflate(R.layout.vh_monster_list_entry, parent, false));
            name = (TextView) itemView.findViewById(R.id.vhMLE_primaryName);
            otherNames = (TextView) itemView.findViewById(R.id.vhMLE_otherNames);
            cr = (TextView) itemView.findViewById(R.id.vhMLE_cr);
            publisherInfo = (TextView) itemView.findViewById(R.id.vhMLE_publisherNotes);
        }

        public void bindData(String[] names, MonsterData.Monster.Header data) {
            name.setText(names[0]);
            String concat = "";
            for(int loop = 1; loop < names.length; loop++) {
                if(loop != 1) concat += '\n';
                concat += " aka " + names[loop];
            }
            MaxUtils.setTextUnlessNull(otherNames, concat.length() == 0? null : concat, View.GONE);
            if(data.cr.denominator == 1) {
                final String crInt = getString(R.string.sma_challangeRatio_integral);
                cr.setText(String.format(Locale.ENGLISH, crInt, data.cr.numerator));  // TODO: how are numbers going here?
            }
            else {
                final String crFrac = getString(R.string.sma_challangeRatio_fraction);
                cr.setText(String.format(Locale.ENGLISH, crFrac, data.cr.numerator, data.cr.denominator)); // TODO: how are numbers going here?
            }
            MaxUtils.setTextUnlessNull(publisherInfo, concat.length() == 0? null : concat, View.GONE);
        }
    }

    private class CompleteListAdapter extends RecyclerView.Adapter<MonsterVH> {
        final MonsterData.MonsterBook monsters;
        public CompleteListAdapter(MonsterData.MonsterBook monsters, RecyclerView rv) {
            this.monsters = monsters;
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
            return new MonsterVH(getLayoutInflater(), parent);
        }

        @Override
        public void onBindViewHolder(MonsterVH holder, int position) {
            // As it stands now, variations are identified when the name is the same so...
            for(int loop = 0; loop < monsters.entries.length; loop++) {
                final MonsterData.Monster.Header mh = monsters.entries[loop].main.header;
                if(position == 0) {
                    holder.bindData(mh.name, mh);
                    break;
                }
                position--;
                for(int inner = 0; inner < monsters.entries[loop].variations.length; inner++) {
                    if(position == 0) {
                        final MonsterData.Monster.Header variation = monsters.entries[loop].variations[inner].header;
                        holder.bindData(mh.name, variation);

                    }
                    position--;
                }
            }
        }

        @Override
        public int getItemCount() {
            int count = 0;
            for(int loop = 0; loop < monsters.entries.length; loop++) {
                count++;
                count += monsters.entries[loop].variations.length;
            }
            return count;
        }
    }

    private String query;
    private MenuItem showBookInfo;
    private MonsterData.MonsterBook monsters;

    // ServiceConnection vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        PartyJoinOrderService.LocalBinder real = (PartyJoinOrderService.LocalBinder) service;
        final PartyJoinOrderService serv = real.getConcreteService();
        monsters = serv.getPlaySession().monsters;
        showBookInfo.setVisible(true);
        unbindService(this);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
    }
    // ServiceConnection ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
}

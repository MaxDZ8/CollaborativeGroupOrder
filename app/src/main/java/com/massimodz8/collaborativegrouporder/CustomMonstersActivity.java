package com.massimodz8.collaborativegrouporder;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.protocol.nano.MonsterData;

import java.util.IdentityHashMap;

public class CustomMonstersActivity extends AppCompatActivity {
    public static MonsterData.MonsterBook custom;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_monsters);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        final android.support.v7.app.ActionBar sab = getSupportActionBar();
        if(sab != null) sab.setDisplayHomeAsUpEnabled(true);

        for (MonsterData.MonsterBook.Entry el : custom.entries) {
            ids.put(el.main, nextId++);
            for (MonsterData.Monster inner : el.variations) ids.put(inner, nextId++);
        }


        final RecyclerView rv = (RecyclerView) findViewById(R.id.cma_list);
        rv.setAdapter(new MyAdapter());
        new HoriSwipeOnlyTouchCallback(rv) {
            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                Snackbar.make(findViewById(R.id.activityRoot), "TODO", Snackbar.LENGTH_LONG);
            }

            @Override
            protected boolean disable() { return false; }

            @Override
            protected boolean canSwipe(RecyclerView rv, RecyclerView.ViewHolder vh) { return true; }
        };
        rv.addItemDecoration(new PreSeparatorDecorator(rv, this) {
            @Override
            protected boolean isEligible(int position) {
                return position != 0;
            }
        });

        MaxUtils.beginDelayedTransition(this);
        final TextView tv = (TextView) findViewById(R.id.cma_status);
        if(rv.getAdapter().getItemCount() == 0) tv.setText(R.string.cma_status_empty);
        else tv.setVisibility(View.GONE);
        findViewById(R.id.cma_progress).setVisibility(View.GONE);

        final FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(new Intent(CustomMonstersActivity.this, NewCustomMonsterActivity.class), REQUEST_NEW_MONSTER);
            }
        });
    }

    public static final int REQUEST_NEW_MONSTER = 1;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode != REQUEST_NEW_MONSTER) return;
        if(resultCode != RESULT_OK) return;
        ids.put(custom.entries[custom.entries.length - 1].main, nextId++);
        final RecyclerView rv = (RecyclerView) findViewById(R.id.cma_list);
        rv.getAdapter().notifyItemInserted(custom.entries.length - 1);
    }

    class MyAdapter extends RecyclerView.Adapter<MonsterVH> {
        public MyAdapter() {
            setHasStableIds(true);
        }

        @Override
        public MonsterVH onCreateViewHolder(ViewGroup parent, int viewType) {
            final MonsterVH res = new MonsterVH(getLayoutInflater(), parent, CustomMonstersActivity.this, ids);
            res.visMode = MonsterVH.MODE_STANDARD;
            return res;
        }

        @Override
        public void onBindViewHolder(MonsterVH holder, int position) {
            MonsterData.Monster data = null;
            String[] name = null;
            for (MonsterData.MonsterBook.Entry el : custom.entries) {
                if(position == 0) {
                    data = el.main;
                    name = el.main.header.name;
                    break;
                }
                position--;
                for (MonsterData.Monster inner : el.variations) {
                    if(position == 0) {
                        data = inner;
                        name = el.main.header.name;
                        break;
                    }
                    position--;
                }
            }
            if(data == null) return; // impossible by construction
            holder.bindData(name, data);

        }

        @Override
        public int getItemCount() {
            int count = 0;
            for (MonsterData.MonsterBook.Entry el : custom.entries) {
                count++;
                count += el.variations.length;
            }
            return count;
        }

        @Override
        public long getItemId(int position) {
            for (MonsterData.MonsterBook.Entry el : custom.entries) {
                if(position == 0) return ids.get(el.main);
                position--;
                for (MonsterData.Monster inner : el.variations) {
                    if(position == 0) return ids.get(inner);
                    position--;
                }
            }
            return RecyclerView.NO_ID;
        }
    }

    private final IdentityHashMap<MonsterData.Monster, Integer> ids = new IdentityHashMap<>();
    private int nextId = 0;
}

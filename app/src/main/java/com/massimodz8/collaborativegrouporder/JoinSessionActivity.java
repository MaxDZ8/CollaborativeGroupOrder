package com.massimodz8.collaborativegrouporder;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage;

import java.util.ArrayList;

public class JoinSessionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join_session);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "TODO: explicit connection", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
        final ActionBar sab = getSupportActionBar();
        if(null != sab) sab.setDisplayHomeAsUpEnabled(true);

        final RecyclerView list = (RecyclerView) findViewById(R.id.jsa_partyList);
        list.setLayoutManager(new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
        list.setAdapter(new PartyLister());
    }

    void join(SomePartyThing party) {
    }

    private class PartyVH extends RecyclerView.ViewHolder implements View.OnClickListener {
        ImageView icon;
        TextView name, options, charList, gottaKey;
        Button attemptJoin;
        SomePartyThing party;

        public PartyVH(View v) {
            super(v);
            icon = (ImageView)v.findViewById(R.id.vhJP_statusIcon);
            name = (TextView) v.findViewById(R.id.vhJP_name);
            options = (TextView) v.findViewById(R.id.vhJP_options);
            charList = (TextView) v.findViewById(R.id.vhJP_pcList);
            gottaKey = (TextView) v.findViewById(R.id.vhJP_iGottaKey);
            attemptJoin = (Button) v.findViewById(R.id.vhJP_attemptJoin);
        }

        @Override
        public void onClick(View v) {
            join(party);
        }
    }

    private class PartyLister extends RecyclerView.Adapter<PartyVH> {
        public PartyLister() {
            setHasStableIds(true);
        }

        @Override
        public PartyVH onCreateViewHolder(ViewGroup parent, int viewType) {
            return new PartyVH(getLayoutInflater().inflate(R.layout.vh_joinable_Party, parent, false));
        }

        @Override
        public void onBindViewHolder(PartyVH holder, int position) {
            holder.party = probed.get(position);
            if(0 == holder.party.matches.size()) holder.icon.setImageResource(R.mipmap.ic_lock_black_48dp);
            else holder.icon.setImageResource(R.mipmap.joined_party_icon);
            holder.name.setText(holder.party.base.group.name);
            if(holder.party.base.group.options.length > 0) {
                holder.options.setText(resolveOptionTokens(holder.party.base.group.options));
            }
            MaxUtils.setVisibility(holder.party.base.group.options.length > 0? View.VISIBLE : View.GONE,
                    holder.options,
                    holder.gottaKey,
                    holder.attemptJoin);

            if(null == holder.party.characters) holder.charList.setVisibility(View.GONE);
            else {
                holder.charList.setText(holder.party.listPcs());
                holder.charList.setVisibility(View.VISIBLE);

            }
        }

        @Override
        public int getItemCount() {
            return probed.size();
        }

        @Override
        public long getItemId(int position) {
            return probed.get(position).base.channel.unique;
        }
    }

    private String resolveOptionTokens(String[] options) {
        StringBuilder meh = new StringBuilder();
        for(String s : options) {
            if(0 != meh.length()) meh.append(", ");
            meh.append(s);
        }
        return meh.toString();
    }

    private static class SomePartyThing {
        public SomePartyThing(GroupState base) {
            this.base = base;
        }
        ArrayList<PersistentStorage.PartyClientData.Group> matches = new ArrayList<>();
        static class CharInfo {
            String name;
        }
        ArrayList<CharInfo> characters;

        final GroupState base; // cuz composition is cooler mofo

        String listPcs() {
            StringBuilder maker = new StringBuilder();
            for(CharInfo pc : characters) {
                if(0 != maker.length()) maker.append(", ");
                maker.append(pc.name);
            }
            return maker.toString();
        }
    }

    ArrayList<SomePartyThing> probed = new ArrayList<>();
}

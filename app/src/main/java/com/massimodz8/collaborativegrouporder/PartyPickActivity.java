package com.massimodz8.collaborativegrouporder;

import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage;

public class PartyPickActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pick_party);

        RecyclerView list = (RecyclerView)findViewById(R.id.ppa_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        state = (CrossActivityShare) getApplicationContext();
        list.setAdapter(listAll);
        list.setHasFixedSize(true);
        delicate = new PreSeparatorDecorator(list, this) {
            @Override
            protected boolean isEligible(int position) {
                if(state.groupDefs.size() > 0) {
                    if(position < 2) return false; // header and first entry
                    position--;
                    if(position < state.groupDefs.size()) return true;
                    position -= state.groupDefs.size();
                }
                return position >= 2;
            }
        };
        list.addItemDecoration(delicate);
    }

    CrossActivityShare state;
    RecyclerView.Adapter listAll = new RecyclerView.Adapter() {
        public RecyclerView owner;

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            final LayoutInflater li = getLayoutInflater();
            switch(viewType) {
                case OwnedPartySeparator.LAYOUT: return new OwnedPartySeparator(li, parent);
                case OwnedPartyHolder.LAYOUT: return new OwnedPartyHolder(li, parent);
                case JoinedPartySeparator.LAYOUT: return new JoinedPartySeparator(li, parent);
            }
            // R.layout.card_joined_party
            return new JoinedPartyHolder(li, parent);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            DynamicViewHolder needs;
            try {
                needs = (DynamicViewHolder)holder;
            } catch(ClassCastException nope) {
                return; // it's still ok.
            }
            needs.rebind(position);
        }

        @Override
        public int getItemCount() {
            int count = 0;
            if(state.groupKeys.size() > 0) count++;
            if(state.groupDefs.size() > 0) count++;
            return count + state.groupKeys.size() + state.groupDefs.size();
        }

        @Override
        public int getItemViewType(int position) {
            if(state.groupDefs.size() > 0) {
                if (position == 0) return OwnedPartySeparator.LAYOUT;
                position--;
                if(position < state.groupDefs.size()) return OwnedPartyHolder.LAYOUT;
                position -= state.groupDefs.size();
            }
            if(0 == position) return JoinedPartySeparator.LAYOUT;
            return JoinedPartyHolder.LAYOUT;
        }

        @Override
        public void onAttachedToRecyclerView(RecyclerView recyclerView) {
            owner = recyclerView;
        }
    };
    PreSeparatorDecorator delicate;

    interface DynamicViewHolder {
        void rebind(int position);
    }

    static class OwnedPartySeparator extends RecyclerView.ViewHolder {
        static final int LAYOUT = R.layout.card_owned_parties_separator;

        public OwnedPartySeparator(LayoutInflater li, ViewGroup parent) {
            super(li.inflate(LAYOUT, parent, false));
        }
    }

    static class JoinedPartySeparator extends RecyclerView.ViewHolder {
        static final int LAYOUT = R.layout.card_joined_parties_separator;

        public JoinedPartySeparator(LayoutInflater li, ViewGroup parent) {
            super(li.inflate(LAYOUT, parent, false));
        }
    }

    String list(PersistentStorage.Actor[] party) {
        StringBuilder result = new StringBuilder();
        for(PersistentStorage.Actor actor : party) {
            if(result.length() > 0) result.append(getString(R.string.ppa_playingCharacterNameSeparator));
            result.append(actor.name);
        }
        return result.toString();
    }

    class OwnedPartyHolder extends RecyclerView.ViewHolder implements DynamicViewHolder, View.OnClickListener {
        static final int LAYOUT = R.layout.card_owned_party;

        TextView name, chars, created, lastPlay, currentState;
        PersistentStorage.PartyOwnerData.Group group;

        public OwnedPartyHolder(LayoutInflater li, ViewGroup parent) {
            super(li.inflate(LAYOUT, parent, false));
            name = (TextView) itemView.findViewById(R.id.cardOP_groupName);
            chars = (TextView) itemView.findViewById(R.id.cardOP_pcList);
            created = (TextView) itemView.findViewById(R.id.cardOP_created);
            lastPlay = (TextView) itemView.findViewById(R.id.cardOP_lastPlayed);
            currentState = (TextView) itemView.findViewById(R.id.cardOP_currentState);
            itemView.setOnClickListener(this);
        }

        public void rebind(int position) {
            if(0 != position) position--; // position 0 is for the holder
            if(position >= state.groupDefs.size()) return; // wut? that's impossible
            group = state.groupDefs.elementAt(position);
            name.setText(group.name);
            chars.setText(list(group.usually.party));
            created.setText(R.string.ppa_TODO_creationDate);
            lastPlay.setText(R.string.ppa_TODO_lastPlayDate_owned);
            currentState.setText(R.string.ppa_TODO_currentState);
        }

        @Override
        public void onClick(View v) {
            if(null == group) return; // will never happen.
            new AlertDialog.Builder(PartyPickActivity.this).setMessage("owned " + group.name).show();
        }
    }

    class JoinedPartyHolder extends RecyclerView.ViewHolder implements DynamicViewHolder, View.OnClickListener {
        static final int LAYOUT = R.layout.card_joined_party;

        TextView name, desc;
        PersistentStorage.PartyClientData.Group group;

        public JoinedPartyHolder(LayoutInflater li, ViewGroup parent) {
            super(li.inflate(LAYOUT, parent, false));
            name = (TextView) itemView.findViewById(R.id.cardJP_groupName);
            desc = (TextView) itemView.findViewById(R.id.cardJP_stateDesc);
            itemView.setOnClickListener(this);
        }

        public void rebind(int position) {
            if(state.groupDefs.size() > 0) {
                position--;
                position -= state.groupDefs.size();
            }
            if(0 != position) position--; // position 0 is for the holder
            if(position >= state.groupKeys.size()) return; // wut? that's impossible
            group = state.groupKeys.elementAt(position);
            name.setText(group.name);
            desc.setText(R.string.ppa_TODO_lastPlayDate_joined);
        }

        @Override
        public void onClick(View v) {
            if(null == group) return; // will never happen.
            new AlertDialog.Builder(PartyPickActivity.this).setMessage("joined " + group.name).show();
        }
    }
}

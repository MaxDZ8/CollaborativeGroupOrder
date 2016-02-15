package com.massimodz8.collaborativegrouporder;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage;

public class PartyPickActivity extends AppCompatActivity {

    private ViewPager pager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pick_party);

        state = (CrossActivityShare) getApplicationContext();

        pager = (ViewPager)findViewById(R.id.ppa_pager);
        pager.setAdapter(new MyFragmentPagerAdapter());
    }

    @NonNull
    private Fragment build(Fragment res, int position) {
        Bundle di = new Bundle();
        di.putInt(OwnedPartyFragment.DATA_INDEX, position);
        res.setArguments(di);
        return res;
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
            int match;
            for(match = 0; match < state.groupDefs.size(); match++) {
                if(state.groupDefs.elementAt(match) == group) break;
            } // will always match
            pager.setCurrentItem(2 + match, true);
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
            int match;
            for(match = 0; match < state.groupKeys.size(); match++) {
                if(state.groupKeys.elementAt(match) == group) break;
            } // will always match
            pager.setCurrentItem(2 + state.groupDefs.size() + match, true);
        }
    }

    static public class PickPartyFragment extends Fragment {
        private PartyPickActivity target;

        @Override
        public void onAttach(Context context) {
            super.onAttach(context);
            target = (PartyPickActivity) context;
        }

        @Nullable
        @Override
        public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.frag_pick_party_list, container, false);
            RecyclerView list = (RecyclerView)view.findViewById(R.id.ppa_list);
            list.setLayoutManager(new LinearLayoutManager(target));
            list.setAdapter(target.listAll);
            list.setHasFixedSize(true);
            target.delicate = new PreSeparatorDecorator(list, target) {
                @Override
                protected boolean isEligible(int position) {
                    if(target.state.groupDefs.size() > 0) {
                        if(position < 2) return false; // header and first entry
                        position--;
                        if(position < target.state.groupDefs.size()) return true;
                        position -= target.state.groupDefs.size();
                    }
                    return position >= 2;
                }
            };
            list.addItemDecoration(target.delicate);
            return view;
        }
    }

    static public class NoSelectedPartyFragment extends Fragment {
        @Nullable
        @Override
        public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.frag_pick_party_none_selected, container, false);
        }
    }

    public static class PartyDetailsFragment extends Fragment {
        protected PartyPickActivity target;

        @Override
        public void onAttach(Context context) {
            super.onAttach(context);
            target = (PartyPickActivity)context;
        }

        public static final String DATA_INDEX = "dataIndex";

        private int dataIndex;

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putInt(DATA_INDEX, dataIndex);
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            final Bundle args = null != savedInstanceState? savedInstanceState : getArguments();
            dataIndex = args.getInt(DATA_INDEX, -1);
        }

        protected int getIndex() { return dataIndex; }
    }

    public static class OwnedPartyFragment extends PartyDetailsFragment {
        @Nullable
        @Override
        public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            View layout = inflater.inflate(R.layout.frag_pick_party_owned_details, container, false);
            if(getIndex() < 0 || getIndex() >= target.state.groupDefs.size()) return layout;

            final PersistentStorage.PartyOwnerData.Group party = target.state.groupDefs.elementAt(getIndex());
            final Button go = (Button)layout.findViewById(R.id.ppa_ownedDetails_goAdventuring);
            go.setText(isFighting(party) ? R.string.ppa_ownedDetails_continueBattle : R.string.ppa_ownedDetails_newSession);
            ((TextView)layout.findViewById(R.id.ppa_ownedDetails_created)).setText("creation date TODO");
            ((TextView)layout.findViewById(R.id.ppa_ownedDetails_lastPlayed)).setText("last play date TODO");
            ((TextView)layout.findViewById(R.id.ppa_ownedDetails_currentState)).setText("status string TODO");
            String note = target.getNote(party);
            TextView widget = (TextView)layout.findViewById(R.id.ppa_ownedDetails_note);
            if(null == note) widget.setVisibility(View.GONE);
            else widget.setText(note);
            go.setOnClickListener(new SelectionListener(target, party));
            return layout;
        }
    }

    public static class JoinedPartyFragment extends PartyDetailsFragment {
        @Nullable
        @Override
        public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            View layout = inflater.inflate(R.layout.frag_pick_party_joined_details, container, false);
            if(getIndex() < 0 || getIndex() >= target.state.groupDefs.size()) return layout;

            final PersistentStorage.PartyClientData.Group party = target.state.groupKeys.elementAt(getIndex());
            final Button go = (Button)layout.findViewById(R.id.ppa_joinedDetails_goAdventuring);
            go.setText(isFighting(party)? R.string.ppa_joinedDetails_continueBattle : R.string.ppa_joinedDetails_newSession);
            ((TextView)layout.findViewById(R.id.ppa_ownedDetails_created)).setText("creation date TODO");
            ((TextView)layout.findViewById(R.id.ppa_ownedDetails_lastPlayed)).setText("last play date TODO");
            ((TextView)layout.findViewById(R.id.ppa_ownedDetails_currentState)).setText("status string TODO");
            String note = target.getNote(party);
            TextView widget = (TextView)layout.findViewById(R.id.ppa_ownedDetails_note);
            if(null == note) widget.setVisibility(View.GONE);
            else widget.setText(note);
            go.setOnClickListener(new SelectionListener(target, party));
            return layout;
        }
    }

    private String getNote(PersistentStorage.PartyOwnerData.Group party) {
        return null;
    }

    private String getNote(PersistentStorage.PartyClientData.Group party) {
        return null;
    }

    static boolean isFighting(PersistentStorage.PartyOwnerData.Group party) {
        return false;
    }

    static boolean isFighting(PersistentStorage.PartyClientData.Group party) {
        return false;
    }

    static class SelectionListener implements View.OnClickListener {
        private final PersistentStorage.PartyOwnerData.Group owned;
        private final PersistentStorage.PartyClientData.Group joined;
        private final PartyPickActivity target;

        SelectionListener(PartyPickActivity target, PersistentStorage.PartyOwnerData.Group owned) {
            this.target = target;
            this.owned = owned;
            joined = null;
        }
        SelectionListener(PartyPickActivity target, PersistentStorage.PartyClientData.Group joined) {
            this.target = target;
            this.joined = joined;
            owned = null;
        }

        @Override
        public void onClick(View v) {
            Intent res = new Intent();
            res.putExtra(RESULT_TRUE_IF_PARTY_OWNED, null != owned);
            res.putExtra(RESULT_PARTY_NAME, null != owned? owned.salt : joined.key);
            res.putExtra(RESULT_PARTY_SALT, null != owned? owned.name : joined.name);
            target.setResult(RESULT_OK, res);
            target.finish();
        }
    }

    public static final String RESULT_TRUE_IF_PARTY_OWNED = "com.massimodz8.collaborativegrouporder.PickPartyActivity.owned";
    public static final String RESULT_PARTY_NAME = "com.massimodz8.collaborativegrouporder.PickPartyActivity.name";
    public static final String RESULT_PARTY_SALT = "com.massimodz8.collaborativegrouporder.PickPartyActivity.key";

    private class MyFragmentPagerAdapter extends FragmentPagerAdapter {
        public MyFragmentPagerAdapter() {
            super(PartyPickActivity.this.getSupportFragmentManager());
        }

        @Override
        public int getCount() {
            int count = 1; // list
            count++; // no group selected fragment
            return count + state.groupDefs.size() + state.groupKeys.size();
        }

        @Override
        public Fragment getItem(int position) {
            if(position < 0) return new NoSelectedPartyFragment();
            switch (position) {
                case 0:
                    return new PickPartyFragment();
                case 1:
                    return new NoSelectedPartyFragment();
            }
            position -= 2;
            if (position < state.groupDefs.size())
                return build(new OwnedPartyFragment(), position);
            position -= state.groupDefs.size();
            if (position < state.groupKeys.size())
                return build(new JoinedPartyFragment(), position);
            return null;
        }
    }
}

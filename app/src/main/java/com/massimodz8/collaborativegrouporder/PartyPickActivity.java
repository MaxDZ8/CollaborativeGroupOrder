package com.massimodz8.collaborativegrouporder;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.design.widget.SwipeDismissBehavior;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.google.protobuf.nano.MessageNano;
import com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Vector;

public class PartyPickActivity extends AppCompatActivity {

    private ViewPager pager;
    private RecyclerView partyList;
    CrossActivityShare state;
    RecyclerView.Adapter listAll = new MyPartyListAdapter();
    boolean backToPartyList;
    CoordinatorLayout guiRoot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pick_party);

        state = (CrossActivityShare) getApplicationContext();
        for(PersistentStorage.PartyOwnerData.Group el : state.groupDefs) partyState.put(el, new PartyItemState(el));
        for(PersistentStorage.PartyClientData.Group el : state.groupKeys) partyState.put(el, new PartyItemState(el));

        guiRoot = (CoordinatorLayout) findViewById(R.id.ppa_activityRoot);
pager = (ViewPager)findViewById(R.id.ppa_pager);
        pager.setAdapter(new MyFragmentPagerAdapter());
        partyList = (RecyclerView) findViewById(R.id.ppa_list);
        partyList.setLayoutManager(new LinearLayoutManager(this));
        partyList.setAdapter(listAll);
        partyList.setHasFixedSize(true);
        partyList.addItemDecoration(new PreSeparatorDecorator(partyList, this) {
            @Override
            protected boolean isEligible(int position) {
                if (state.groupDefs.size() > 0) {
                    if (position < 2) return false; // header and first entry
                    position--;
                    if (position < state.groupDefs.size()) return true;
                    position -= state.groupDefs.size();
                }
                return position >= 2;
            }
        });
        final ItemTouchHelper swiper = new ItemTouchHelper(new MyItemTouchCallback());
        partyList.addItemDecoration(swiper);
        swiper.attachToRecyclerView(partyList);
    }

    @NonNull
    private Fragment build(Fragment res, int position) {
        Bundle di = new Bundle();
        di.putInt(OwnedPartyFragment.DATA_INDEX, position);
        res.setArguments(di);
        return res;
    }

    private void showPartyList(boolean detailsIfFalse) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            final ViewGroup root = (ViewGroup) guiRoot;
            TransitionManager.beginDelayedTransition(root);
        }
        partyList.setVisibility(detailsIfFalse? View.VISIBLE : View.GONE);
        pager.setVisibility(detailsIfFalse? View.GONE : View.VISIBLE);

        final ActionBar ab = getSupportActionBar();
        if(null != ab) ab.setTitle(detailsIfFalse? R.string.ppa_title : R.string.ppa_title_details);
    }

    @Override
    public void onBackPressed() {
        if(backToPartyList) showPartyList(true);
        else super.onBackPressed();
        backToPartyList = false;
    }

    @Override
    public boolean onSupportNavigateUp() {
        if(backToPartyList) {
            showPartyList(true);
            backToPartyList = false;
            return false;
        }
        return super.onSupportNavigateUp();
    }

    class MyPartyListAdapter extends RecyclerView.Adapter {
        public MyPartyListAdapter() {
            setHasStableIds(true);
        }

        @Override
        public long getItemId(int position) {
            if(position == 0 && state.groupDefs.size() > 0) return 0;
            position--;
            for (PersistentStorage.PartyOwnerData.Group party : state.groupDefs) {
                final PartyItemState el = partyState.get(party);
                if(null == el || el.hide) continue;
                if(0 == position) return el.unique;
                position--;
            }
            if(position == 0) return 1;
            position--;
            for (PersistentStorage.PartyClientData.Group party : state.groupKeys) {
                final PartyItemState el = partyState.get(party);
                if(el.hide) continue;
                if(0 == position) return el.unique;
                position--;
            }
            return RecyclerView.NO_ID;
        }

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
            int visible = 0;
            int now = visible;
            for(PersistentStorage.PartyOwnerData.Group party : state.groupDefs) {
                if(!partyState.get(party).hide) count++;
                visible++;
            }
            if(now != visible) count++; // "owned parties" kinda-header
            now = visible;
            for(PersistentStorage.PartyClientData.Group party : state.groupKeys) {
                if(!partyState.get(party).hide) count++;
                visible++;
            }
            if(now != visible) count++; // "joined parties" kinda-header
            return count;
        }

        @Override
        public int getItemViewType(int position) {
            if(state.groupDefs.size() > 0) {
                if (0 == position) return OwnedPartySeparator.LAYOUT;
                position--;
                for(PersistentStorage.PartyOwnerData.Group party : state.groupDefs) {
                    if(!partyState.get(party).hide) {
                        if(0 == position) return OwnedPartyHolder.LAYOUT;
                        position--;
                    }
                }
            }
            if(0 == position) return JoinedPartySeparator.LAYOUT;
            return JoinedPartyHolder.LAYOUT; // Well, that's the last thing.
        }
    }

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

    class MyItemTouchCallback extends ItemTouchHelper.SimpleCallback {
        static final int DRAG_FORBIDDEN = 0;
        static final int SWIPE_HORIZONTAL = ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT;

        MyItemTouchCallback() {
            super(DRAG_FORBIDDEN, SWIPE_HORIZONTAL);
        }

        @Override
        public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
            return false;
        }

        @Override
        public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
            final Vector<PersistentStorage.PartyOwnerData.Group> prevDefs = new Vector<>(state.groupDefs);
            final Vector<PersistentStorage.PartyClientData.Group> prevKeys = new Vector<>(state.groupKeys);
            final MessageNano party;
            final String name;
            if(viewHolder instanceof OwnedPartyHolder) {
                OwnedPartyHolder real = (OwnedPartyHolder) viewHolder;
                party = real.group;
                name = real.group.name;
                state.groupDefs.remove(real.group);
            }
            else {
                JoinedPartyHolder real = (JoinedPartyHolder) viewHolder;
                party = real.group;
                name = real.group.name;
                state.groupKeys.remove(real.group);
            }
            final PartyItemState partyState = PartyPickActivity.this.partyState.get(party);
            partyState.hide = true;
            listAll.notifyDataSetChanged(); // for easiness, as the last changes two items (itself and the separator)
            final String msg = String.format(getString(R.string.ppa_deletedParty), name);
            Snackbar.make(guiRoot, msg, Snackbar.LENGTH_LONG)
                    .setAction("Undo", new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            state.groupDefs = prevDefs;
                            state.groupKeys = prevKeys;
                            partyState.hide = false;
                            listAll.notifyDataSetChanged();
                        }
                    }).setCallback(new Snackbar.Callback() {
                @Override
                public void onDismissed(Snackbar snackbar, int event) {
                    if(DISMISS_EVENT_ACTION != event) {
                        junkyard.add(partyState);
                        PartyPickActivity.this.partyState.remove(party);
                    }
                }
            }).show();
        }

        @Override
        public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            int swipe = 0;
            if(viewHolder instanceof OwnedPartyHolder || viewHolder instanceof JoinedPartyHolder) swipe = SWIPE_HORIZONTAL;
            return makeMovementFlags(DRAG_FORBIDDEN, swipe);
        }
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
            backToPartyList = true;
            pager.setCurrentItem(match, true);
            showPartyList(false);
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
            backToPartyList = true;
            pager.setCurrentItem(state.groupDefs.size() + match, true);
            showPartyList(false);
        }
    }

    // To be instantiated and put in the detail pane only if in multi-pane mode.
    //
    //static public class NoSelectedPartyFragment extends Fragment {
    //    @Nullable
    //    @Override
    //    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    //        return inflater.inflate(R.layout.frag_pick_party_none_selected, container, false);
    //    }
    //
    //}

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
            ((TextView)layout.findViewById(R.id.ppa_ownedDetails_groupName)).setText(party.name);
            ((TextView)layout.findViewById(R.id.ppa_ownedDetails_pcList)).setText(target.list(party.usually.party));
            TextView npcList = (TextView) layout.findViewById(R.id.ppa_ownedDetails_accompanyingNpcList);
            if (0 == party.usually.npcs.length) {
                npcList.setVisibility(View.GONE);
            } else {
                final String res = target.getString(R.string.ppa_ownedDetails_npcList);
                npcList.setText(String.format(res, target.list(party.usually.npcs)));
            }
            final Button go = (Button)layout.findViewById(R.id.ppa_ownedDetails_goAdventuring);
            go.setText(target.isFighting(party) ? R.string.ppa_ownedDetails_continueBattle : R.string.ppa_ownedDetails_newSession);
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
            if(getIndex() < 0 || getIndex() >= target.state.groupKeys.size()) return layout;

            final PersistentStorage.PartyClientData.Group party = target.state.groupKeys.elementAt(getIndex());
            ((TextView)layout.findViewById(R.id.ppa_joinedDetails_groupName)).setText(party.name);
            ((TextView)layout.findViewById(R.id.ppa_joinedDetails_lastPlayedPcs)).setText(target.listLastPlayedPcs(party));
            final Button go = (Button)layout.findViewById(R.id.ppa_joinedDetails_goAdventuring);
            go.setText(target.isFighting(party)? R.string.ppa_joinedDetails_continueBattle : R.string.ppa_joinedDetails_newSession);
            ((TextView)layout.findViewById(R.id.ppa_joinedDetails_created)).setText("creation date TODO");
            ((TextView)layout.findViewById(R.id.ppa_joinedDetails_lastPlayed)).setText("last play date TODO");
            ((TextView)layout.findViewById(R.id.ppa_joinedDetails_currentState)).setText("status string TODO");
            String note = target.getNote(party);
            TextView widget = (TextView)layout.findViewById(R.id.ppa_joinedDetails_note);
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

    boolean isFighting(PersistentStorage.PartyOwnerData.Group party) {
        return false;
    }

    boolean isFighting(PersistentStorage.PartyClientData.Group party) {
        return false;
    }

    String listLastPlayedPcs(PersistentStorage.PartyClientData.Group party) {
        // Not really there for the time being.
        final PersistentStorage.Actor res = new PersistentStorage.Actor();
        res.name = "!! STUB !!";
        PersistentStorage.Actor[] arr = new PersistentStorage.Actor[]{res};
        return list(arr);
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
            int match = 0;
            for(int check = 0; check < target.state.groupDefs.size(); check++) {
                if(target.state.groupDefs.elementAt(check) == owned) {
                    match = check;
                    break;
                }
            }
            for(int check = 0; check < target.state.groupKeys.size(); check++) {
                if(target.state.groupKeys.elementAt(check) == joined) {
                    match = check;
                    break;
                }
            }
            Intent intent = new Intent(RESULT_ACTION)
                    .putExtra(EXTRA_PARTY_INDEX, match)
                    .putExtra(EXTRA_TRUE_IF_PARTY_OWNED, owned != null);
            target.setResult(RESULT_OK, intent);
            target.finish();
        }
    }

    private static final String RESULT_ACTION = "com.massimodz8.collaborativegrouporder.PartyPickActivity.RESULT";
    public static final String EXTRA_TRUE_IF_PARTY_OWNED =  "com.massimodz8.collaborativegrouporder.EXTRA_TRUE_IF_PARTY_OWNED";
    public static final String EXTRA_PARTY_INDEX = "com.massimodz8.collaborativegrouporder.EXTRA_PARTY_INDEX";

    private class MyFragmentPagerAdapter extends FragmentPagerAdapter {
        public MyFragmentPagerAdapter() {
            super(PartyPickActivity.this.getSupportFragmentManager());
        }

        @Override
        public int getCount() {
            return state.groupDefs.size() + state.groupKeys.size();
        }

        @Override
        public Fragment getItem(int position) {
            if (position < state.groupDefs.size())
                return build(new OwnedPartyFragment(), position);
            position -= state.groupDefs.size();
            if (position < state.groupKeys.size())
                return build(new JoinedPartyFragment(), position);
            return null;
        }
    }

    /** Also doubles as a place to keep undo information for deleted parties and estabilishes a
     * common order between elements which can be used as stable ids.
     */
    static class PartyItemState {
        private final PersistentStorage.PartyOwnerData.Group owned;
        private final PersistentStorage.PartyClientData.Group joined;
        boolean hide; /// hidden if delete operation started.
        boolean undoExpired; /// true if snackbar control is gone so this is no more undo-able.
        boolean ioCompleted; /// set by async write onPostExecute, storage ok.
        // Snackbar notification (fired when delete took place) sets both pointers to null
        // and then it's gone forever.

        final int unique = 2 + partyStatesCreated++; // 0 reserved for "owned" separator, 1 for "joined"


        public PartyItemState(PersistentStorage.PartyOwnerData.Group owned) {
            joined = null;
            this.owned = owned;
        }
        public PartyItemState(PersistentStorage.PartyClientData.Group joined) {
            owned = null;
            this.joined = joined;
        }

        private static int partyStatesCreated = 0;
    }

    Map<MessageNano, PartyItemState> partyState = new IdentityHashMap<>();
    Vector<PartyItemState> junkyard = new Vector<>(); /// We can restore stuff there from menu
}

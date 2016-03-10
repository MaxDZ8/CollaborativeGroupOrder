package com.massimodz8.collaborativegrouporder;

import android.content.Context;
import android.content.DialogInterface;
import android.database.DataSetObserver;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.google.protobuf.nano.MessageNano;
import com.massimodz8.collaborativegrouporder.master.PcAssignmentHelper;
import com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;


public class PartyPickActivity extends AppCompatActivity {
    // Populate those before launching the activity. They will be updated if we remove a party.
    // In other terms, always fetch back those, regardless activity result status.
    public static ArrayList<PersistentStorage.PartyOwnerData.Group> ioDefs;
    public static ArrayList<PersistentStorage.PartyClientData.Group> ioKeys;

    // One of those two is set if we have selected a party to go adventuring.
    public static PersistentStorage.PartyOwnerData.Group goDef;
    public static PersistentStorage.PartyClientData.Group goKey;

    private boolean[] hideDefKey;

    private ViewPager pager;
    private RecyclerView partyList;
    private RecyclerView.Adapter listAll = new MyPartyListAdapter();
    private boolean backToPartyList;
    private CoordinatorLayout guiRoot;
    private MenuItem restoreDeleted;
    private AsyncRenamingStore pending; // only one undergoing, ignore back, up and delete group while notnull

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pick_party);

        hideDefKey = new boolean[ioDefs.size() + ioKeys.size()];

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
                if (ioDefs.size() > 0) {
                    if (position < 2) return false; // header and first entry
                    position--;
                    if (position < ioDefs.size()) return true;
                    position -= ioDefs.size();
                }
                return position >= 2;
            }
        });
        final ItemTouchHelper swiper = new ItemTouchHelper(new MyItemTouchCallback());
        partyList.addItemDecoration(swiper);
        swiper.attachToRecyclerView(partyList);
    }

    @Override
    protected void onDestroy() {
        ioDefs = condCopy(ioDefs, 0);
        ioKeys = condCopy(ioKeys, ioDefs.size());
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.party_pick_activity, menu);
        restoreDeleted = menu.findItem(R.id.ppa_menu_restoreDeleted);
        return true;
    }

    @Override
     public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.ppa_menu_restoreDeleted: {
                ListAdapter la = new ListAdapter() {
                    @Override
                    public void registerDataSetObserver(DataSetObserver observer) { }

                    @Override
                    public void unregisterDataSetObserver(DataSetObserver observer) { }

                    @Override
                    public int getCount() {
                        int count = 0;
                        for (boolean flag : hideDefKey) {
                            if(flag) count++;
                        }
                        return count;
                    }

                    @Override
                    public Object getItem(int position) {
                        for (int loop = 0; loop < hideDefKey.length; loop++) {
                            if(!hideDefKey[loop]) continue;
                            if(position == 0) {
                                if(loop < ioDefs.size()) return ioDefs.get(loop);
                                return ioKeys.get(loop - ioDefs.size());
                            }
                            position--;
                        }
                        return null;
                    }

                    @Override
                    public long getItemId(int position) {
                        for (int loop = 0; loop < hideDefKey.length; loop++) {
                            if(!hideDefKey[loop]) continue;
                            if(position == 0) return loop;
                            position--;
                        }
                        return -1; // impossible
                    }

                    @Override
                    public boolean hasStableIds() { return true; }

                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        final View layout = getLayoutInflater().inflate(R.layout.dialog_restore_recently_deleted_party, parent, false);
                        final TextView name = (TextView)layout.findViewById(R.id.ppa_dialogName);
                        String partyName = "";
                        for (int loop = 0; loop < hideDefKey.length; loop++) {
                            if(!hideDefKey[loop]) continue;
                            if(position == 0) {
                                if(loop < ioDefs.size()) partyName = ioDefs.get(loop).name;
                                else partyName = ioKeys.get(loop - ioDefs.size()).name;
                            }
                            position--;
                        }
                        name.setText(partyName);
                        return layout;
                    }

                    @Override
                    public int getItemViewType(int position) { return 0; }

                    @Override
                    public int getViewTypeCount() { return 1; }

                    @Override
                    public boolean isEmpty() { return getCount() == 0; }

                    @Override
                    public boolean areAllItemsEnabled() { return true; }

                    @Override
                    public boolean isEnabled(int position) { return true; }
                };
                final AlertDialog.OnClickListener icl = new AlertDialog.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        restoreDeleted(which);
                    }
                };
                new AlertDialog.Builder(this)
                        .setTitle(R.string.ppa_menu_restoreDeleted)
                        .setAdapter(la, icl).show();
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private void showPartyList(boolean detailsIfFalse) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            TransitionManager.beginDelayedTransition(guiRoot);
        }
        partyList.setVisibility(detailsIfFalse? View.VISIBLE : View.GONE);
        pager.setVisibility(detailsIfFalse? View.GONE : View.VISIBLE);

        final ActionBar ab = getSupportActionBar();
        if(null != ab) ab.setTitle(detailsIfFalse? R.string.ppa_title : R.string.ppa_title_details);
    }

    @Override
    public void onBackPressed() {
        if(backToPartyList) showPartyList(true);
        else if(null != pending) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.ppa_cannotLetYouGoWhileWriting)
                    .show();
        }
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
        else if(null != pending) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.ppa_cannotLetYouGoWhileWriting)
                    .show();
        }
        return super.onSupportNavigateUp();
    }

    class MyPartyListAdapter extends RecyclerView.Adapter {
        public MyPartyListAdapter() {
            setHasStableIds(true);
        }

        @Override
        public long getItemId(int position) {
            int count = 0;
            for(int loop = 0; loop < ioDefs.size(); loop++) {
                if(hideDefKey[loop]) continue;
                count++;
            }
            if(count > 0 && 0 == position) return 0;
            position--;
            for(int loop = 0; loop < ioDefs.size(); loop++) {
                if(hideDefKey[loop]) continue;
                if(position == 0) return 2 + loop;
                position--;
            }
            count = 0;
            for(int loop = 0; loop < ioKeys.size(); loop++) {
                if(hideDefKey[ioDefs.size() + loop]) continue;
                count++;
            }
            if(count > 0 && 0 == position) return 1;
            position--;
            for(int loop = 0; loop < ioKeys.size(); loop++) {
                if(hideDefKey[ioDefs.size() + loop]) continue;
                if(position == 0) return 2 + loop + ioDefs.size();
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
            int numDefs = 0;
            for(int loop = 0; loop < ioDefs.size(); loop++) {
                if(hideDefKey[loop]) continue;
                numDefs++;
            }
            if(numDefs > 0) numDefs++; // "owned parties" kinda-header
            int numKeys = 0;
            for(int loop = 0; loop < ioKeys.size(); loop++) {
                if(hideDefKey[loop + ioDefs.size()]) continue;
                numKeys++;
            }
            if(numKeys > 0) numKeys++; // "owned keys" kinda-header
            return numDefs + numKeys;
        }

        @Override
        public int getItemViewType(int position) {
            int count = 0;
            for(int loop = 0; loop < ioDefs.size(); loop++) {
                if(hideDefKey[loop]) continue;
                count++;
            }
            if(count > 0 && 0 == position) return OwnedPartySeparator.LAYOUT;
            position--;
            for(int loop = 0; loop < ioDefs.size(); loop++) {
                if(hideDefKey[loop]) continue;
                if(position == 0) return OwnedPartyHolder.LAYOUT;
                position--;
            }
            count = 0;
            for(int loop = 0; loop < ioKeys.size(); loop++) {
                if(hideDefKey[ioDefs.size() + loop]) continue;
                count++;
            }
            if(count > 0 && 0 == position) return JoinedPartySeparator.LAYOUT;
            return JoinedPartyHolder.LAYOUT;
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

    String list(PersistentStorage.ActorDefinition[] party) {
        StringBuilder result = new StringBuilder();
        for(PersistentStorage.ActorDefinition actor : party) {
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

        @SuppressWarnings("StatementWithEmptyBody")
        @Override
        public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
            final MessageNano party;
            final String name;
            final int clear;
            if (viewHolder instanceof OwnedPartyHolder) {
                OwnedPartyHolder real = (OwnedPartyHolder) viewHolder;
                party = real.group;
                name = real.group.name;
                int loop;
                for(loop = 0; loop < ioDefs.size() && ioDefs.get(loop) != real.group; loop++);
                clear = loop;
            } else {
                JoinedPartyHolder real = (JoinedPartyHolder) viewHolder;
                party = real.group;
                name = real.group.name;
                int loop;
                for(loop = 0; loop < ioKeys.size() && ioKeys.get(loop) != real.group; loop++);
                clear = loop + ioDefs.size();
            }
            hideDefKey[clear] = true;
            listAll.notifyDataSetChanged(); // for easiness, as the last changes two items (itself and the separator)
            final String msg = String.format(getString(R.string.ppa_deletedParty), name);
            final Runnable undo = new Runnable() {
                @Override
                public void run() {
                    hideDefKey[clear] = false;
                    listAll.notifyDataSetChanged();
                    boolean owned = party instanceof PersistentStorage.PartyOwnerData.Group;
                    if(null != pending) pending.cancel(true);
                    if(owned) {
                        pending = new AsyncRenamingStore<>(PersistentDataUtils.DEFAULT_GROUP_DATA_FILE_NAME, makePartyOwnerData(condCopy(ioDefs, 0)), null, null);
                    }
                    else {
                        pending = new AsyncRenamingStore<>(PersistentDataUtils.DEFAULT_KEY_FILE_NAME, makePartyClientData(condCopy(ioKeys, ioDefs.size())), null, null);
                    }
                    int count = 0;
                    for (boolean flag : hideDefKey) {
                        if(flag) count++;
                    }
                    if(restoreDeleted.isEnabled() && count == 0) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                            TransitionManager.beginDelayedTransition(guiRoot);
                        }
                        restoreDeleted.setEnabled(false);
                    }
                }
            };
            Snackbar sb = Snackbar.make(guiRoot, msg, Snackbar.LENGTH_LONG)
                    .setAction(R.string.generic_action_undo, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) { undo.run(); }
                    }).setCallback(new Snackbar.Callback() {
                        @Override
                        public void onShown(Snackbar snackbar) {
                            if(!restoreDeleted.isEnabled()) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                                    TransitionManager.beginDelayedTransition(guiRoot);
                                }
                                restoreDeleted.setEnabled(true);
                            }
                        }
                    });
            if(viewHolder instanceof OwnedPartyHolder) {
                pending = new AsyncRenamingStore<>(PersistentDataUtils.DEFAULT_GROUP_DATA_FILE_NAME, makePartyOwnerData(condCopy(ioDefs, 0)), sb, undo);
            }
            else {
                pending = new AsyncRenamingStore<>(PersistentDataUtils.DEFAULT_KEY_FILE_NAME, makePartyClientData(condCopy(ioKeys, ioDefs.size())), sb, undo);
            }
        }

        @Override
        public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            if(null != pending) return 0;
            int swipe = 0;
            if(viewHolder instanceof OwnedPartyHolder || viewHolder instanceof JoinedPartyHolder) swipe = SWIPE_HORIZONTAL;
            return makeMovementFlags(DRAG_FORBIDDEN, swipe);
        }
    }

    private <X> ArrayList<X> condCopy(ArrayList<X> coll, int offset) {
        ArrayList<X> res = new ArrayList<>();
        for(int loop = 0; loop < coll.size(); loop++) {
            if(hideDefKey[offset + loop]) continue;
            res.add(coll.get(loop));
        }
        return res;
    }

    private static PersistentStorage.PartyOwnerData makePartyOwnerData(ArrayList<PersistentStorage.PartyOwnerData.Group> defs) {
        PersistentStorage.PartyOwnerData all = new PersistentStorage.PartyOwnerData();
        all.version = PersistentDataUtils.OWNER_DATA_VERSION;
        all.everything = new PersistentStorage.PartyOwnerData.Group[defs.size()];
        for(int cp = 0; cp < defs.size(); cp++) all.everything[cp] = defs.get(cp);
        return all;
    }

    private static PersistentStorage.PartyClientData makePartyClientData(ArrayList<PersistentStorage.PartyClientData.Group> defs) {
        PersistentStorage.PartyClientData all = new PersistentStorage.PartyClientData();
        all.version = PersistentDataUtils.CLIENT_DATA_WRITE_VERSION;
        all.everything = new PersistentStorage.PartyClientData.Group[defs.size()];
        for(int cp = 0; cp < defs.size(); cp++) all.everything[cp] = defs.get(cp);
        return all;
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
            for(int loop = 0; loop < ioDefs.size(); loop++) {
                if(hideDefKey[loop]) continue;
                if(position == 0) {
                    group = ioDefs.get(loop);
                    break;
                }
                position--;
            }
            if(group == null) return; // impossible by construction
            name.setText(group.name);
            chars.setText(list(group.party));
            created.setText(R.string.ppa_TODO_creationDate);
            lastPlay.setText(R.string.ppa_TODO_lastPlayDate_owned);
            currentState.setText(R.string.ppa_TODO_currentState);
        }

        @Override
        public void onClick(View v) {
            if(null == group) return; // will never happen.
            int match;
            for(match = 0; match < ioDefs.size(); match++) {
                if(ioDefs.get(match) == group) break;
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
            int count = 0;
            for(int loop = 0; loop < ioDefs.size(); loop++) {
                if(!hideDefKey[loop]) count++;
            }
            if(count > 0) {
                position--;
                position -= count;
            }
            if(0 != position) position--; // position 0 is for the holder
            for(int loop = 0; loop < ioKeys.size(); loop++) {
                if(hideDefKey[loop]) continue;
                if(position == 0) {
                    group = ioKeys.get(loop);
                    break;
                }
                position--;
            }
            if(group == null) return; // impossible by construction
            name.setText(group.name);
            desc.setText(R.string.ppa_TODO_lastPlayDate_joined);
        }

        @Override
        public void onClick(View v) {
            if(null == group) return; // will never happen.
            int match;
            for(match = 0; match < ioKeys.size(); match++) {
                if(ioKeys.get(match) == group) break;
            } // will always match
            backToPartyList = true;
            pager.setCurrentItem(match, true);
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

        public Fragment init(int dataIndex) {
            Bundle di = new Bundle();
            di.putInt(DATA_INDEX, dataIndex);
            setArguments(di);
            return this;
        }
    }

    public static class OwnedPartyFragment extends PartyDetailsFragment {
        @Nullable
        @Override
        public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            View layout = inflater.inflate(R.layout.frag_pick_party_owned_details, container, false);
            if(getIndex() < 0 || getIndex() >= ioDefs.size()) return layout;

            final PersistentStorage.PartyOwnerData.Group party = ioDefs.get(getIndex());
            ((TextView)layout.findViewById(R.id.ppa_ownedDetails_groupName)).setText(party.name);
            ((TextView)layout.findViewById(R.id.ppa_ownedDetails_pcList)).setText(target.list(party.party));
            TextView npcList = (TextView) layout.findViewById(R.id.ppa_ownedDetails_accompanyingNpcList);
            if (0 == party.npcs.length) {
                npcList.setVisibility(View.GONE);
            } else {
                final String res = target.getString(R.string.ppa_ownedDetails_npcList);
                npcList.setText(String.format(res, target.list(party.npcs)));
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
            if(getIndex() < 0 || getIndex() >= ioKeys.size()) return layout;

            final PersistentStorage.PartyClientData.Group party = ioKeys.get(getIndex());
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
        final PersistentStorage.ActorDefinition res = new PersistentStorage.ActorDefinition();
        res.name = "!! STUB !!";
        PersistentStorage.ActorDefinition[] arr = new PersistentStorage.ActorDefinition[]{res};
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
            if(owned != null) goDef = owned;
            else goKey = joined;
            target.setResult(RESULT_OK);
            target.finish();
        }
    }

    private class MyFragmentPagerAdapter extends FragmentPagerAdapter {
        public MyFragmentPagerAdapter() {
            super(PartyPickActivity.this.getSupportFragmentManager());
        }

        @Override
        public int getCount() {
            int count = 0;
            for (boolean flag : hideDefKey) {
                if(!flag) count++;
            }
            return count;
        }

        @Override
        public Fragment getItem(int position) {
            for(int loop = 0; loop < hideDefKey.length; loop++) {
                if(hideDefKey[loop]) continue;
                if(position == 0) {
                    if(loop < ioDefs.size()) return new OwnedPartyFragment().init(position);
                    return new JoinedPartyFragment().init(loop - ioDefs.size());
                }
                position--;
            }
            return null;
        }
    }


    /// If anything fails, trigger a runnable, otherwise a snackbar.
    public class AsyncRenamingStore<Container extends MessageNano> extends AsyncTask<Void, Void, Exception> {
        final String target;
        final Snackbar showOnSuccess;
        final Runnable undo;
        final Container container;

        public AsyncRenamingStore(@NonNull String fileName, @NonNull Container container, @Nullable Snackbar showOnSuccess, @Nullable Runnable undo) {
            target = fileName;
            this.container = container;
            this.showOnSuccess = showOnSuccess;
            this.undo = undo;
            super.execute();
        }

        @Override
        protected Exception doInBackground(Void... params) {
            File previously = new File(getFilesDir(), target);
            File store;
            try {
                store = File.createTempFile(target, ".new", getFilesDir());
            } catch (IOException e) {
                return e;
            }
            new PersistentDataUtils(PcAssignmentHelper.DOORMAT_BYTES) {
                @Override
                protected String getString(int resource) {
                    return PartyPickActivity.this.getString(resource);
                }
            }.storeValidGroupData(store, container);
            if(previously.exists() && !previously.delete()) {
                if(!store.delete()) store.deleteOnExit();
                return new Exception(getString(R.string.ppa_failedOldDelete));
            }
            if(!store.renameTo(previously)) return new Exception(String.format(getString(R.string.ppa_failedNewRenameOldGone), store.getName()));
            return null;
        }

        @Override
        protected void onPostExecute(Exception e) {
            pending = null;
            if(null != e) {
                new AlertDialog.Builder(PartyPickActivity.this)
                        .setMessage(e.getLocalizedMessage())
                        .show();
                if(null != undo) undo.run();
                return;
            }
            if(null != showOnSuccess) showOnSuccess.show();
        }
    }

    private void restoreDeleted(int hiddenPos) {
        MessageNano match = null;
        for(int loop = 0; loop < ioDefs.size(); loop++) {
            if(!hideDefKey[loop]) continue;
            if(hiddenPos == 0) {
                hideDefKey[loop] = false;
                match = ioDefs.get(loop);
                break;
            }
            hiddenPos--;
        }
        for(int loop = match != null? ioKeys.size() : 0; loop < ioKeys.size(); loop++) {
            if(hideDefKey[loop]) continue;
            if(hiddenPos == 0) {
                hideDefKey[loop] = false;
                match = ioKeys.get(loop);
                break;
            }
            hiddenPos--;
        }
        final boolean owned = match instanceof PersistentStorage.PartyOwnerData.Group;
        final Snackbar sb = Snackbar.make(guiRoot, R.string.ppa_partyRecovered, Snackbar.LENGTH_LONG)
                .setCallback(new Snackbar.Callback() {
                    @Override
                    public void onShown(Snackbar snackbar) {
                        listAll.notifyDataSetChanged();
                        int count = 0;
                        for (boolean flag : hideDefKey) {
                            if(flag) count++;
                        }
                        restoreDeleted.setEnabled(count != 0);
                    }
                });
        if(owned) {
            pending = new AsyncRenamingStore<>(PersistentDataUtils.DEFAULT_GROUP_DATA_FILE_NAME, makePartyOwnerData(condCopy(ioDefs, 0)), sb, null);
        }
        else {
            pending = new AsyncRenamingStore<>(PersistentDataUtils.DEFAULT_KEY_FILE_NAME, makePartyClientData(condCopy(ioKeys, ioDefs.size())), sb, null);
        }
    }
}

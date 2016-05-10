package com.massimodz8.collaborativegrouporder;

import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.DataSetObserver;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
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
import com.google.protobuf.nano.Timestamp;
import com.massimodz8.collaborativegrouporder.protocol.nano.StartData;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;


public class PartyPickActivity extends AppCompatActivity implements ServiceConnection {
    private ViewPager pager;
    private RecyclerView partyList;
    private RecyclerView.Adapter listAll = new MyPartyListAdapter();
    private boolean backToPartyList;
    private CoordinatorLayout guiRoot;
    private MenuItem restoreDeleted;
    private AsyncRenamingStore pending; // only one undergoing, ignore back, up and delete group while notnull
    private PartyPickingService helper;
    private boolean mustUnbind;
    private AsyncTask loading;

    // List of all visible (non-deleted) parties, kept in sync with the service state.
    private ArrayList<StartData.PartyOwnerData.Group> denseDefs = new ArrayList<>();
    private ArrayList<StartData.PartyClientData.Group> denseKeys = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pick_party);
        guiRoot = (CoordinatorLayout) findViewById(R.id.activityRoot);
        pager = (ViewPager)findViewById(R.id.ppa_pager);
        partyList = (RecyclerView) findViewById(R.id.ppa_list);
        partyList.setLayoutManager(new LinearLayoutManager(this));
        partyList.setAdapter(listAll);
        partyList.setHasFixedSize(true);
        partyList.addItemDecoration(new PreSeparatorDecorator(partyList, this) {
            @Override
            protected boolean isEligible(int position) {
                // Problem here: positions are stable, but not all of those are to be shown, so...
                if(position == 0) return false; // separator
                if(denseDefs.size() > 0) position--;
                if(position < denseDefs.size()) return position != 0;
                position -= denseDefs.size();
                return position > 1;
            }
        });
        final ItemTouchHelper swiper = new ItemTouchHelper(new MyItemTouchCallback());
        partyList.addItemDecoration(swiper);
        swiper.attachToRecyclerView(partyList);

        if(!bindService(new Intent(this, PartyPickingService.class), this, 0)) {
            MaxUtils.beginDelayedTransition(this);
            final TextView status = (TextView) findViewById(R.id.ga_state);
            status.setText(R.string.ga_cannotBindPartyService);
            MaxUtils.setVisibility(this, View.GONE,
                    R.id.ga_progressBar,
                    R.id.ga_identifiedDevices,
                    R.id.ga_deviceList,
                    R.id.ga_pcUnassignedListDesc,
                    R.id.ga_pcUnassignedList);
        }
    }

    @Override
    protected void onDestroy() {
        if(helper != null) helper.onSessionDataLoaded = null;
        if(mustUnbind) unbindService(this);
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
                final ArrayList<StartData.PartyOwnerData.Group> hiddenDefs = new ArrayList<>();
                final ArrayList<StartData.PartyClientData.Group> hiddenKeys = new ArrayList<>();
                helper.getDense(hiddenDefs, hiddenKeys, true);
                ListAdapter la = new ListAdapter() {
                    @Override
                    public void registerDataSetObserver(DataSetObserver observer) { }

                    @Override
                    public void unregisterDataSetObserver(DataSetObserver observer) { }

                    @Override
                    public int getCount() { return hiddenDefs.size() + hiddenKeys.size(); }

                    @Override
                    public Object getItem(int position) {
                        if(position < hiddenDefs.size()) return hiddenDefs.get(position);
                        position -= hiddenDefs.size();
                        return hiddenKeys.get(position);
                    }

                    @Override
                    public long getItemId(int position) {
                        if(position < hiddenDefs.size()) return helper.indexOf(hiddenDefs.get(position));
                        position -= hiddenDefs.size();
                        return helper.indexOf(hiddenKeys.get(position));
                    }

                    @Override
                    public boolean hasStableIds() { return true; }

                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        final View layout = getLayoutInflater().inflate(R.layout.dialog_restore_recently_deleted_party, parent, false);
                        final TextView name = (TextView)layout.findViewById(R.id.ppa_dialogName);
                        String partyName;
                        if(position < hiddenDefs.size()) partyName = hiddenDefs.get(position).name;
                        else {
                            position -= hiddenDefs.size();
                            partyName = hiddenKeys.get(position).name;
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
        else if(null != pending || null != loading) {
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
        else if(null != pending || null != loading) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.ppa_cannotLetYouGoWhileWriting)
                    .show();
        }
        return super.onSupportNavigateUp();
    }

    private class MyPartyListAdapter extends RecyclerView.Adapter {
        public MyPartyListAdapter() {
            setHasStableIds(true);
        }

        @Override
        public long getItemId(int position) {
            if(denseDefs.size() > 0) {
                if(0 == position) return 0;
                position--;
                if(position < denseDefs.size()) return 2 + helper.indexOf(denseDefs.get(position));
                position -= denseDefs.size();
            }
            if(!denseKeys.isEmpty() && 0 == position) return 1;
            position--;
            if(position < denseKeys.size()) return 2 + helper.indexOf(denseKeys.get(position));
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
            int numDefs = denseDefs.size();
            if(numDefs > 0) numDefs++; // "owned parties" kinda-header
            int numKeys = denseKeys.size();
            if(numKeys > 0) numKeys++; // "owned keys" kinda-header
            return numDefs + numKeys;
        }

        @Override
        public int getItemViewType(int position) {
            int ownedCount = denseDefs.size();
            if(ownedCount > 0) {
                if (0 == position) return OwnedPartySeparator.LAYOUT;
                position--;
                if (position < ownedCount) return OwnedPartyHolder.LAYOUT;
                position -= ownedCount;
            }
            if(denseKeys.size() > 0 && 0 == position) return JoinedPartySeparator.LAYOUT;
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

    String list(StartData.ActorDefinition[] party) {
        StringBuilder result = new StringBuilder();
        for(StartData.ActorDefinition actor : party) {
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
            if (viewHolder instanceof OwnedPartyHolder) {
                OwnedPartyHolder real = (OwnedPartyHolder) viewHolder;
                party = real.group;
                name = real.group.name;
                helper.setDeletionFlag(party, true);
            } else {
                JoinedPartyHolder real = (JoinedPartyHolder) viewHolder;
                party = real.group;
                name = real.group.name;
                helper.setDeletionFlag(party, true);
            }
            denseDefs.clear();
            denseKeys.clear();
            helper.getDense(denseDefs, denseKeys, false);
            listAll.notifyDataSetChanged(); // for easiness, as the last changes two items (itself and the separator)
            pager.setAdapter(new MyFragmentPagerAdapter());
            final String msg = String.format(getString(R.string.ppa_deletedParty), name);
            final Runnable undo = new Runnable() {
                @Override
                public void run() {
                    helper.setDeletionFlag(party, false);
                    denseDefs.clear();
                    denseKeys.clear();
                    helper.getDense(denseDefs, denseKeys, false);
                    listAll.notifyDataSetChanged();
                    pager.setAdapter(new MyFragmentPagerAdapter());
                    boolean owned = party instanceof StartData.PartyOwnerData.Group;
                    if(null != pending) pending.cancel(true);
                    if(owned) {
                        pending = new MyAsyncRenamingStore<>(getFilesDir(), PersistentDataUtils.DEFAULT_GROUP_DATA_FILE_NAME,
                                                             PersistentDataUtils.makePartyOwnerData(denseDefs),
                                                             null, null);
                    }
                    else {
                        pending = new MyAsyncRenamingStore<>(getFilesDir(), PersistentDataUtils.DEFAULT_KEY_FILE_NAME,
                                PersistentDataUtils.makePartyClientData(denseKeys),
                                null, null);
                    }
                    if(restoreDeleted.isEnabled() && denseDefs.size() + denseKeys.size() == 0) {
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
                            if (!restoreDeleted.isEnabled()) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                                    TransitionManager.beginDelayedTransition(guiRoot);
                                }
                                restoreDeleted.setEnabled(true);
                            }
                        }
                    });
            if(viewHolder instanceof OwnedPartyHolder) {
                pending = new MyAsyncRenamingStore<>(getFilesDir(), PersistentDataUtils.DEFAULT_GROUP_DATA_FILE_NAME,
                                                     PersistentDataUtils.makePartyOwnerData(denseDefs),
                                                     sb, undo);
            }
            else {
                pending = new MyAsyncRenamingStore<>(getFilesDir(), PersistentDataUtils.DEFAULT_KEY_FILE_NAME,
                        PersistentDataUtils.makePartyClientData(denseKeys),
                        sb, undo);
            }
        }

        @Override
        public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            if(null != pending || null != loading) return 0;
            int swipe = 0;
            if(viewHolder instanceof OwnedPartyHolder || viewHolder instanceof JoinedPartyHolder) swipe = SWIPE_HORIZONTAL;
            return makeMovementFlags(DRAG_FORBIDDEN, swipe);
        }
    }

    class OwnedPartyHolder extends RecyclerView.ViewHolder implements DynamicViewHolder, View.OnClickListener {
        static final int LAYOUT = R.layout.vh_owned_party;

        TextView name, pgCount, level, lastPlay;
        StartData.PartyOwnerData.Group group;

        public OwnedPartyHolder(LayoutInflater li, ViewGroup parent) {
            super(li.inflate(LAYOUT, parent, false));
            name = (TextView) itemView.findViewById(R.id.vhOP_name);
            pgCount = (TextView) itemView.findViewById(R.id.vhOP_pgCount);
            level = (TextView) itemView.findViewById(R.id.vhOP_level);
            lastPlay = (TextView) itemView.findViewById(R.id.vhOP_lastPlayed);
            itemView.setOnClickListener(this);
        }

        public void rebind(int position) {
            if(0 != position) position--; // position 0 is for the holder
            group = denseDefs.get(position);
            if(group == null) return; // impossible by construction
            name.setText(group.name);
            String str = getString(group.party.length == 1? R.string.vhOP_charCount_singular : R.string.vhOP_charCount_plural);
            if(group.party.length > 1) str = String.format(str, group.party.length);
            pgCount.setText(str);
            int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
            for (StartData.ActorDefinition actor : group.party) {
                min = Math.min(min, actor.level);
                max = Math.max(max, actor.level);
            }
            if(min == max) str = String.format(getString(R.string.vhOP_charLevel_same), min);
            else str = String.format(getString(R.string.vhOP_charLevel_different), max, min);
            level.setText(str);
            if(helper == null || helper.sessionData == null) lastPlay.setVisibility(View.GONE);
            else {
                PersistentDataUtils.SessionStructs structs = helper.sessionData.get(group);
                if(structs == null || structs.irl == null || structs.irl.lastSaved == null) lastPlay.setVisibility(View.GONE);
                else {
                    lastPlay.setVisibility(View.VISIBLE);
                    lastPlay.setText(getNiceDate(structs.irl.lastSaved));
                }
            }
        }

        @Override
        public void onClick(View v) {
            if(null == group) return; // will never happen.
            backToPartyList = true;
            pager.setCurrentItem(denseDefs.indexOf(group), true);
            showPartyList(false);
        }
    }

    class JoinedPartyHolder extends RecyclerView.ViewHolder implements DynamicViewHolder, View.OnClickListener {
        static final int LAYOUT = R.layout.vh_joined_party;

        TextView name, date;
        StartData.PartyClientData.Group group;

        public JoinedPartyHolder(LayoutInflater li, ViewGroup parent) {
            super(li.inflate(LAYOUT, parent, false));
            name = (TextView) itemView.findViewById(R.id.vhJP_partyName);
            date = (TextView) itemView.findViewById(R.id.vhJP_lastPlayed);
            itemView.setOnClickListener(this);
        }

        public void rebind(int position) {
            if(!denseDefs.isEmpty()) {
                if (position != 0) position--;
                if (position >= denseDefs.size()) position -= denseDefs.size();
            }
            if(denseKeys.size() > 0 && position != 0) position--;
            group = denseKeys.get(position);
            if(group == null) return; // impossible by construction
            name.setText(group.name);
            if(helper == null || helper.sessionData == null) date.setVisibility(View.GONE);
            else {
                PersistentDataUtils.SessionStructs structs = helper.sessionData.get(group);
                if(structs == null || structs.irl == null || structs.irl.lastSaved == null) date.setVisibility(View.GONE);
                else {
                    date.setVisibility(View.VISIBLE);
                    date.setText(getNiceDate(structs.irl.lastSaved));
                }
            }
        }

        @Override
        public void onClick(View v) {
            if(null == group) return; // will never happen.
            backToPartyList = true;
            pager.setCurrentItem(denseDefs.size() + denseKeys.indexOf(group), true);
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

        @StringRes int sessionButton(MessageNano party, boolean owned) {
            if(target.helper == null || target.helper.sessionData == null)
                return owned? R.string.ppa_ownedDetails_newSession : R.string.ppa_joinedDetails_newSession;
            PersistentDataUtils.SessionStructs structs = target.helper.sessionData.get(party);
            if(structs == null || structs.liveActors == null)
                return owned? R.string.ppa_ownedDetails_newSession : R.string.ppa_joinedDetails_newSession;
            if(structs.fighting == null)
                return owned? R.string.ppa_ownedDetails_continueSession : R.string.ppa_joinedDetails_continueSession;
            return owned? R.string.ppa_ownedDetails_continueBattle : R.string.ppa_joinedDetails_continueBattle;
        }

        protected void note(MessageNano party, @IdRes int view, View container) {
            String got = null;
            if(target.helper != null && target.helper.sessionData != null) {
                PersistentDataUtils.SessionStructs structs = target.helper.sessionData.get(party);
                if (structs != null && structs.irl != null && !structs.irl.note.isEmpty()) got = structs.irl.note;
            }
            MaxUtils.setTextUnlessNull((TextView) container.findViewById(view), got, View.GONE);
        }

        protected void state(MessageNano party, @IdRes int view, View container) {
            String got = null;
            if(target.helper != null && target.helper.sessionData != null) {
                PersistentDataUtils.SessionStructs structs = target.helper.sessionData.get(party);
                if(structs.fighting != null) got = target.getString(R.string.ppa_status_battle);
                else if(structs.liveActors != null) got = target.getString(R.string.ppa_status_adventure);
                else got = target.getString(R.string.ppa_status_asDefined);
            }
            MaxUtils.setTextUnlessNull((TextView) container.findViewById(view), got, View.GONE);
        }

        protected String lastPlayed(MessageNano party, TextView view) {
            String got = null;
            if(target.helper != null && target.helper.sessionData != null) {
                PersistentDataUtils.SessionStructs structs = target.helper.sessionData.get(party);
                if(structs.irl == null) got = getString(R.string.ppa_neverPlayed);
                else if(structs.irl.lastSaved == null) got = getString(R.string.ppa_lastSavedInconsistent);
                else got = target.getNiceDate(structs.irl.lastSaved);
            }
            MaxUtils.setTextUnlessNull(view, got, View.GONE);
            return got;
        }
    }

    public static class OwnedPartyFragment extends PartyDetailsFragment {
        @Nullable
        @Override
        public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            View layout = inflater.inflate(R.layout.frag_pick_party_owned_details, container, false);
            if(getIndex() < 0 || getIndex() >= target.denseDefs.size()) return layout;

            final StartData.PartyOwnerData.Group party = target.helper.getOwned(getIndex());
            ((TextView)layout.findViewById(R.id.fragPPAOD_partyName)).setText(party.name);
            ((TextView)layout.findViewById(R.id.fragPPAOD_pcList)).setText(target.list(party.party));
            TextView npcList = (TextView) layout.findViewById(R.id.fragPPAOD_accompanyingNpcList);
            if (0 == party.npcs.length) {
                npcList.setVisibility(View.GONE);
            } else {
                final String res = target.getString(R.string.ppa_ownedDetails_npcList);
                npcList.setText(String.format(res, target.list(party.npcs)));
            }
            final Button go = (Button)layout.findViewById(R.id.fragPPAOD_goAdventuring);
            go.setText(sessionButton(party, true));
            ((TextView)layout.findViewById(R.id.fragPPAOD_created)).setText(target.getNiceDate(party.created));
            lastPlayed(party, (TextView) layout.findViewById(R.id.fragPPAOD_lastPlayed));
            note(party, R.id.fragPPAOD_note, layout);
            state(party, R.id.fragPPAOD_currentState, layout);
            go.setOnClickListener(new SelectionListener(target, party));
            return layout;
        }
    }

    public static class JoinedPartyFragment extends PartyDetailsFragment {
        @Nullable
        @Override
        public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            View layout = inflater.inflate(R.layout.frag_pick_party_joined_details, container, false);
            if(getIndex() < 0 || getIndex() >= target.denseKeys.size()) return layout;

            final StartData.PartyClientData.Group party = target.helper.getJoined(getIndex());
            ((TextView)layout.findViewById(R.id.fragPPAJD_partyName)).setText(party.name);
            MaxUtils.setTextUnlessNull((TextView) layout.findViewById(R.id.fragPPAJD_lastPlayedPcs), target.listLastPlayedPcs(party), View.GONE);
            final Button go = (Button)layout.findViewById(R.id.fragPPAJD_goAdventuring);
            go.setText(sessionButton(party, false));
            ((TextView)layout.findViewById(R.id.fragPPAJD_created)).setText(target.getNiceDate(party.received));
            lastPlayed(party, (TextView) layout.findViewById(R.id.fragPPAJD_lastPlayed));
            note(party, R.id.fragPPAJD_note, layout);
            state(party, R.id.fragPPAJD_currentState, layout);
            go.setOnClickListener(new SelectionListener(target, party));
            return layout;
        }
    }

    String listLastPlayedPcs(StartData.PartyClientData.Group party) {
        //// Not really there for the time being.
        //// TODO
        //final StartData.ActorDefinition res = new StartData.ActorDefinition();
        //res.name = "!! STUB !!";
        //StartData.ActorDefinition[] arr = new StartData.ActorDefinition[]{res};
        //return list(arr);
        return null;
    }

    static class SelectionListener implements View.OnClickListener {
        private final StartData.PartyOwnerData.Group owned;
        private final StartData.PartyClientData.Group joined;
        private final PartyPickActivity target;

        SelectionListener(PartyPickActivity target, StartData.PartyOwnerData.Group owned) {
            this.target = target;
            this.owned = owned;
            joined = null;
        }
        SelectionListener(PartyPickActivity target, StartData.PartyClientData.Group joined) {
            this.target = target;
            this.joined = joined;
            owned = null;
        }

        @Override
        public void onClick(View v) {
            if(owned != null) target.helper.sessionParty = owned;
            else target.helper.sessionParty = joined;
            target.setResult(RESULT_OK);
            target.finish();
        }
    }

    private class MyFragmentPagerAdapter extends FragmentPagerAdapter {
        public MyFragmentPagerAdapter() {
            super(PartyPickActivity.this.getSupportFragmentManager());
        }

        @Override
        public int getCount() { return denseDefs.size() + denseKeys.size(); }

        @Override
        public Fragment getItem(int position) {
            int owned = denseDefs.size();
            if(position < owned) return new OwnedPartyFragment().init(position);
            return new JoinedPartyFragment().init(position - owned);
        }
    }


    /// If anything fails, trigger a runnable, otherwise a snackbar.
    public class MyAsyncRenamingStore<Container extends MessageNano> extends AsyncRenamingStore<Container> {
        final String target;
        final Snackbar showOnSuccess;
        final Runnable undo;
        final Container container;

        public MyAsyncRenamingStore(@NonNull File filesDir, @NonNull String fileName, @NonNull Container container, Snackbar showOnSuccess, Runnable undo) {
            super(filesDir, fileName, container);
            this.target = fileName;
            this.showOnSuccess = showOnSuccess;
            this.undo = undo;
            this.container = container;
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

        @Override
        protected String getString(@StringRes int res) { return PartyPickActivity.this.getString(res); }
    }

    private void restoreDeleted(int hiddenPos) {
        ArrayList<StartData.PartyOwnerData.Group> owned = new ArrayList<>();
        ArrayList<StartData.PartyClientData.Group> joined = new ArrayList<>();
        helper.getDense(owned, joined, true);
        MessageNano match;
        if(hiddenPos < owned.size()) match = owned.get(hiddenPos);
        else match = joined.get(hiddenPos - owned.size());
        helper.setDeletionFlag(match, false);
        denseDefs.clear();
        denseKeys.clear();
        helper.getDense(denseDefs, denseKeys, false);
        pager.setAdapter(new MyFragmentPagerAdapter());
        listAll.notifyDataSetChanged();
        restoreDeleted.setEnabled(owned.size() + joined.size() > 1);
        final Snackbar sb = Snackbar.make(guiRoot, R.string.ppa_partyRecovered, Snackbar.LENGTH_LONG)
                .setCallback(new Snackbar.Callback() {
                    @Override
                    public void onShown(Snackbar snackbar) {
                        pending = null;
                    }
                });
        if(match instanceof StartData.PartyOwnerData.Group) {
            pending = new MyAsyncRenamingStore<>(getFilesDir(), PersistentDataUtils.DEFAULT_GROUP_DATA_FILE_NAME, PersistentDataUtils.makePartyOwnerData(denseDefs), sb, null);
        }
        else {
            pending = new MyAsyncRenamingStore<>(getFilesDir(), PersistentDataUtils.DEFAULT_KEY_FILE_NAME, PersistentDataUtils.makePartyClientData(denseKeys), sb, null);
        }
    }


    private DateFormat local = DateFormat.getDateInstance(DateFormat.MEDIUM);
    private String getNiceDate(Timestamp ts) {
        return local.format(new Date(ts.seconds * 1000));
    }

    // ServiceConnection vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        PartyPickingService.LocalBinder binder = (PartyPickingService.LocalBinder) service;
        helper = binder.getConcreteService();
        mustUnbind = true;
        helper.getDense(denseDefs, denseKeys, false);

        helper.onSessionDataLoaded = new Runnable() {
            @Override
            public void run() {
                loading = null;
                listAll.notifyDataSetChanged();
                pager.setAdapter(new MyFragmentPagerAdapter());

                if(!helper.sessionErrors.isEmpty()) {
                    new AlertDialog.Builder(PartyPickActivity.this)
                            .setMessage(R.string.ppa_inconsistentSessionDataDlgMsg)
                            .setCancelable(false)
                            .setPositiveButton(R.string.ppa_backMainMenuDlgPosBtn, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    finish();
                                }
                            })
                            .show();
                }
            }
        };
        final AsyncTask<Void, Void, Integer> go = helper.makeSessionLoadingTask();
        if(go != null) {
            loading = go;
            go.execute();
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }
    // ServiceConnection ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
}

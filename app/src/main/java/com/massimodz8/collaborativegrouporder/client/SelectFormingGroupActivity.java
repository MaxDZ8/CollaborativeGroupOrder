package com.massimodz8.collaborativegrouporder.client;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.AccumulatingDiscoveryListener;
import com.massimodz8.collaborativegrouporder.ConnectionAttempt;
import com.massimodz8.collaborativegrouporder.ExplicitConnectionActivity;
import com.massimodz8.collaborativegrouporder.MaxUtils;
import com.massimodz8.collaborativegrouporder.MyDialogsFactory;
import com.massimodz8.collaborativegrouporder.PartyInfo;
import com.massimodz8.collaborativegrouporder.R;
import com.massimodz8.collaborativegrouporder.RunningServiceHandles;
import com.massimodz8.collaborativegrouporder.SendRequest;
import com.massimodz8.collaborativegrouporder.networkio.ProtoBufferEnum;
import com.massimodz8.collaborativegrouporder.networkio.Pumper;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;
import com.massimodz8.collaborativegrouporder.protocol.nano.UserOf;

public class SelectFormingGroupActivity extends AppCompatActivity {
    private @UserOf PartySelection state;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        setContentView(R.layout.activity_select_forming_group);

        RecyclerView groupList = (RecyclerView) findViewById(R.id.selectFormingGroupActivity_groupList);
        groupList.setLayoutManager(new LinearLayoutManager(this));
        groupList.setAdapter(new GroupListAdapter());
        state = RunningServiceHandles.getInstance().partySelection;
        final Snackbar temp = Snackbar.make(findViewById(R.id.activityRoot), getString(R.string.client_missingMyParty), Snackbar.LENGTH_LONG);
        temp.setAction(R.string.generic_help, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MyDialogsFactory.showNetworkDiscoveryTroubleshoot(SelectFormingGroupActivity.this, true);
            }
        });
        temp.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        final PartySelection.Listener callbacks = new PartySelection.Listener() {
            @Override
            public void onDisconnected(GroupState gs) {
                new AlertDialog.Builder(SelectFormingGroupActivity.this, R.style.AppDialogStyle)
                        .setMessage(String.format(getString(R.string.sfga_lostConnection), gs.group.name, gs.disconnected.getLocalizedMessage()))
                        .show();
            }

            @Override
            public void onPartyFoundOrLost() {
                refreshGUI();
            }

            @Override
            public void onPartyInfoChanged(GroupState gs) {
                refreshGUI();
            }

            @Override
            public void onPartyCharBudgetChanged(GroupState gs) {
                refreshGUI();
            }

            @Override
            public void onKeyReceived(GroupState got) {
                // Nothing here to do, a 'done' will soon follow.
                // TODO: get the rid of this callback?
            }

            @Override
            public void onDone() {
                setResult(RESULT_OK);
                finish();
            }
        };
        eventid = state.onEvent.put(callbacks);
        refreshGUI();
    }

    int eventid;

    @Override
    protected void onPause() {
        super.onPause();
        state.onEvent.remove(eventid);
    }

    private class GroupListAdapter extends RecyclerView.Adapter<GroupListAdapter.GroupViewHolder> {
        public GroupListAdapter() {
            setHasStableIds(true);
        }

        // View holder pattern <-> keep handles to internal Views so I don't need to look em up.
        protected class GroupViewHolder extends RecyclerView.ViewHolder implements TextWatcher, TextView.OnEditorActionListener {
            TextView name, options; // those two change only when rebound.
            TextView curLen, lenLimit; // the first changes when message.getText() changes, lenLimit changes on send or receive of CharBudget message.
            TextView message;
            final Typeface usual;

            GroupState source;
            public GroupViewHolder(View itemView) {
                super(itemView);
                name = (TextView)itemView.findViewById(R.id.card_joinableGroup_name);
                options = (TextView)itemView.findViewById(R.id.card_joinableGroup_options);
                curLen = (TextView)itemView.findViewById(R.id.card_joinableGroup_currentLength);
                lenLimit = (TextView)itemView.findViewById(R.id.card_joinableGroup_lengthLimit);
                message = (TextView)itemView.findViewById(R.id.card_joinableGroup_message);
                message.setEnabled(false);
                message.addTextChangedListener(this);
                message.setOnEditorActionListener(this);
                usual = curLen.getTypeface();
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if(s.length() > source.charBudget) curLen.setTypeface(usual, Typeface.BOLD);
                else curLen.setTypeface(usual, Typeface.NORMAL);
                curLen.setText(String.valueOf(s.length()));
            }

            @Override
            public void afterTextChanged(Editable s) {}

            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean handled = false;
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    final CharSequence msg = message.getText();
                    if(msg.length() == 0) {
                        new AlertDialog.Builder(SelectFormingGroupActivity.this, R.style.AppDialogStyle)
                                .setMessage(R.string.sfga_emptyMessageForbidden)
                                .show();
                        return false;
                    }
                    if(msg.length() > source.charBudget) {
                        new AlertDialog.Builder(SelectFormingGroupActivity.this, R.style.AppDialogStyle)
                                .setMessage(R.string.sfga_messageTooLong)
                                .show();
                        return false;
                    }
                    sendMessageToPartyOwner(source, msg);
                    handled = true;
                }
                return handled;
            }
        }

        @Override
        public GroupViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View layout = getLayoutInflater().inflate(R.layout.card_joinable_group, parent, false);
            return new GroupViewHolder(layout);
        }

        @Override
        public void onBindViewHolder(GroupViewHolder holder, int position) {
            final GroupState info = state.candidates.get(position);
            final int current = holder.message.getText().length();
            final int allowed = info.charBudget;
            holder.source = info;
            holder.name.setText(info.group.name);
            holder.curLen.setText(String.valueOf(current));
            holder.lenLimit.setText(String.valueOf(allowed));
            holder.message.setHint(info.lastMsgSent != null? info.lastMsgSent : getString(R.string.sfga_talkHint));
            if(info.group.options == null || info.group.options.length == 0) holder.options.setVisibility(View.GONE);
            else {
                String total = getString(R.string.card_group_options);
                for(int app = 0; app < info.group.options.length; app++) {
                    if(app != 0) total += ", ";
                    total += info.group.options[app]; /// TODO this should be localized by device language! Not server language! Map enums/tokens to localized strings somehow!
                }
                holder.options.setText(total);
                holder.options.setVisibility(View.VISIBLE);
            }
            final boolean status = allowed != 0 && info.nextEnabled_ms < SystemClock.elapsedRealtime();
            holder.message.setEnabled(status);
        }

        @Override
        public long getItemId(int position) {
            int match = 0;
            for(GroupState gs : state.candidates) {
                if(gs.group == null) continue;
                if(match == position) return gs.channel.unique;
                match++;
            }
            return RecyclerView.NO_ID;
        }

        @Override
        public int getItemCount() {
            int count = 0;
            for(GroupState gs : state.candidates) if(gs.group != null) count++;
            return count;
        }
    }


    void sendMessageToPartyOwner(final GroupState gs, CharSequence msg) {
        gs.charBudget -= msg.length();
        gs.nextEnabled_ms = SystemClock.elapsedRealtime() + gs.nextMsgDelay_ms;
        final String send = msg.toString();
        Network.PeerMessage payload = new Network.PeerMessage();
        payload.text = send;
        state.sender.out.add(new SendRequest(gs.channel, ProtoBufferEnum.PEER_MESSAGE, payload, null));
        gs.lastMsgSent = send;
    }

    private void refreshGUI() {
        boolean discovering = state.explorer.getDiscoveryStatus() == AccumulatingDiscoveryListener.EXPLORING;
        int talked = 0;
        for (GroupState gs : state.candidates) {
            if (gs.lastMsgSent != null) talked++;
        }
        MaxUtils.setVisibility(this, discovering ? View.VISIBLE : View.GONE,
                R.id.selectFormingGroupActivity_progressBar);
        findViewById(R.id.selectFormingGroupActivity_groupList).setVisibility(state.candidates.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        findViewById(R.id.sfga_confirmInstructions).setVisibility(talked == 0? View.GONE : View.VISIBLE);

        findViewById(R.id.sfga_lookingForGroups).setVisibility(discovering && state.candidates.size() == 0? View.VISIBLE : View.GONE);
        ((RecyclerView) findViewById(R.id.selectFormingGroupActivity_groupList)).getAdapter().notifyDataSetChanged();
    }


    private static final int EXPLICIT_CONNECTION_REQUEST = 1;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode != EXPLICIT_CONNECTION_REQUEST) return;
        final RunningServiceHandles handles = RunningServiceHandles.getInstance();
        if(resultCode == RESULT_OK) {
            Pumper.MessagePumpingThread pumper = handles.connectionAttempt.resMaster;
            Network.GroupInfo probed = handles.connectionAttempt.resParty;
            if(!probed.forming) {
                pumper.interrupt();
                new AlertDialog.Builder(this, R.style.AppDialogStyle)
                        .setMessage(R.string.sfga_connectedNotForming)
                        .show();
                return;
            }
            if(probed.doormat.length != 0) {
                pumper.interrupt();
                new AlertDialog.Builder(this, R.style.AppDialogStyle)
                        .setMessage(getString(R.string.sfga_connectedGotDoormat))
                        .show();
                return;
            }

            final GroupState add = new GroupState(pumper.getSource()).explicit();
            add.group = new PartyInfo(probed.version, probed.name);
            add.group.options = probed.options;
            // It seems on some devices .onActivityResult can be called BEFORE .onCreate... WTF!!!
            // Not a problem anymore now state is unified and semi-persistent.
            final PartySelection state = handles.partySelection;
            state.candidates.add(add);
            state.netPump.pump(pumper);
        }
        handles.connectionAttempt = null;
        refreshGUI();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.select_forming_group_activity, menu);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch(item.getItemId()) {
            case R.id.sfga_connectionAttempt: {
                RunningServiceHandles.getInstance().connectionAttempt = new ConnectionAttempt();
                startActivityForResult(new Intent(this, ExplicitConnectionActivity.class), EXPLICIT_CONNECTION_REQUEST);
                break;
            }
        }
        return false;
    }
}

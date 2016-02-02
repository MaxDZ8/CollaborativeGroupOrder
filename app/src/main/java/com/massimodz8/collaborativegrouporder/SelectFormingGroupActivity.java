package com.massimodz8.collaborativegrouporder;

import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Typeface;
import android.net.nsd.NsdManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.networkio.Events;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.networkio.ProtoBufferEnum;
import com.massimodz8.collaborativegrouporder.networkio.PumpTarget;
import com.massimodz8.collaborativegrouporder.networkio.Pumper;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

public class SelectFormingGroupActivity extends AppCompatActivity implements ServiceConnection {
    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        setContentView(R.layout.activity_select_forming_group);
        final ActionBar bar = getSupportActionBar();
        if(bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
            bar.setDisplayUseLogoEnabled(true);
        }

        if(savedState != null) {
            shareKey = savedState.getLong(EXTRA_SERVICED_STATE_KEY, 0);
        }

        groupListAdapter = new GroupListAdapter();
        RecyclerView groupList = (RecyclerView) findViewById(R.id.selectFormingGroupActivity_groupList);
        groupList.setLayoutManager(new LinearLayoutManager(this));
        groupList.setAdapter(groupListAdapter);

        guiHandler = new MyHandler(this);

        Intent sharing = new Intent(this, CrossActivityService.class);
        if(!bindService(sharing, this, 0)) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.couldNotBindInternalService)
                    .setCancelable(false)
                    .setPositiveButton(R.string.giveUpAndGoBack, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .show();
        }
    }

    @Override
    protected void onDestroy() {
        if(xdata != null) {
            if (xdata.discoveryStartAttempted() != null) xdata.stopDiscovery();
            unbindService(this);
        }
        if(binder != null) binder.release(shareKey);
        if(checkDiscoveries != null) checkDiscoveries.cancel();
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if(shareKey != 0) out.putLong(EXTRA_SERVICED_STATE_KEY, shareKey);
    }


    public void startExplicitConnectionActivity_callback(View btn) {

        new AlertDialog.Builder(this)
                .setTitle("Not implemented!")
                .setMessage("explicit connection!")
                .show();

        //Intent intent = new Intent(this, ExplicitConnectionActivity.class);
        //startActivityForResult(intent, EXPLICIT_CONNECTION_REQUEST);
    }

    // ServiceConnection vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        binder = (CrossActivityService.ProxyBinder) service;
        final NsdManager nsd = (NsdManager)getSystemService(Context.NSD_SERVICE);
        if(shareKey == 0) shareKey = binder.generate();
        xdata = binder.get(shareKey);
        showBasicControls();
        if(xdata.discoveryStartAttempted() == null) xdata.beginDiscovery(MainMenuActivity.GROUP_FORMING_SERVICE_TYPE, nsd);
        // Now, in every moment the device could be rotated and this activity would be destroyed and recreated.
        // The service will keep searching and maintaining a list of found services I can later pull with ease,
        // it keeps churning even when I am destroyed... more or less. When I am destroyed I just need to pull
        // the previous data, which is very easy as keys are always nonzero.
        // However, in the meanwhile I have to check out the services found every once in a while.
        checkDiscoveries = new Timer();
        checkDiscoveries.schedule(new TimerTask() {
            @Override
            public void run() {
                guiHandler.sendMessage(guiHandler.obtainMessage(MSG_CHECK_NETWORK_SERVICES));
            }
        }, INITIAL_SERVICE_POLLING_DELAY_MS, SERVICE_POLLING_PERIOD_MS);

    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.selectFormingGroupActivity_lostCrossActivitySharingService))
                .setCancelable(false)
                .setPositiveButton(R.string.giveUpAndGoBack, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .show();
    }
    // ServiceConnection ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^


    static final int EXPLICIT_CONNECTION_REQUEST = 1;
    static final int INITIAL_SERVICE_POLLING_DELAY_MS = 2500;
    static final int SERVICE_POLLING_PERIOD_MS = 1000;

    static final String EXTRA_SERVICED_STATE_KEY = "com.massimodz8.collaborativegrouporder.SelectFormingGroupActivity.servicedStateKey";

    static final int MSG_CHECK_NETWORK_SERVICES = 1;
    static final int MSG_SOCKET_DISCONNECTED = 2;
    static final int MSG_GROUP_INFO = 3;
    static final int MSG_CHAR_BUDGET = 4;


    Vector<GroupState> candidates = new Vector<>();
    GroupListAdapter groupListAdapter;
    long shareKey;
    CrossActivityService.ProxyBinder binder;
    CrossActivityService.DataPack xdata;
    Timer checkDiscoveries;
    Handler guiHandler;
    Pumper netPump;


    static class GroupState {
        final MessageChannel channel;
        PartyInfo group;

        int charBudget;
        int nextMsgDelay_ms;
        String lastMsgSent;
        volatile long nextEnabled_ms = 0; // SystemClock.elapsedRealtime(); /// if now() is >= this, controls are updated if charBudget > 0

        public GroupState(MessageChannel pipe) { channel = pipe; }
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
                        new AlertDialog.Builder(SelectFormingGroupActivity.this)
                                .setMessage(R.string.saySomethingToServer_emptyForbidden)
                                .show();
                        return false;
                    }
                    if(msg.length() > source.charBudget) {
                        new AlertDialog.Builder(SelectFormingGroupActivity.this)
                                .setMessage(R.string.saySomethingToServer_tooLong)
                                .show();
                        return false;
                    }
                    sendMessageToPartyOwner(source, msg);
                    message.setHint(msg);
                    message.setText("");
                    message.setEnabled(false);
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
            final GroupState info = candidates.elementAt(position);
            final int current = holder.message.getText().length();
            final int allowed = info.charBudget;
            holder.source = info;
            holder.name.setText(info.group.name);
            holder.curLen.setText(String.valueOf(current));
            holder.lenLimit.setText(String.valueOf(allowed));
            holder.message.setHint(info.lastMsgSent != null? info.lastMsgSent : getString(R.string.card_joinableGroup_talkHint));
            if(info.group.options == null) holder.options.setVisibility(View.GONE);
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
            for(GroupState gs : candidates) {
                if(gs.group == null) continue;
                if(match == position) return gs.channel.unique;
                match++;
            }
            return RecyclerView.NO_ID;
        }

        @Override
        public int getItemCount() {
            int count = 0;
            for(GroupState gs : candidates) if(gs.group != null) count++;
            return count;
        }
    }

    static class MyHandler extends Handler {
        public MyHandler(SelectFormingGroupActivity target) { this.target = new WeakReference<>(target); }

        final WeakReference<SelectFormingGroupActivity> target;

        @Override
        public void handleMessage(Message msg) {
            SelectFormingGroupActivity target = this.target.get();
            switch(msg.what) {
                case MSG_CHECK_NETWORK_SERVICES: target.checkNetwork(); break;
                case MSG_SOCKET_DISCONNECTED: {
                    final Events.SocketDisconnected real = (Events.SocketDisconnected) msg.obj;
                    target.socketDisconnected(real.which, real.reason);
                } break;
                case MSG_GROUP_INFO: {
                    final Events.GroupInfo real = (Events.GroupInfo) msg.obj;
                    target.groupInfo(real.which, real.payload);
                } break;
                case MSG_CHAR_BUDGET: {
                    final Events.CharBudget real = (Events.CharBudget) msg.obj;
                    target.charBudget(real.which, real.payload);
                } break;
                default: super.handleMessage(msg);
            }
        }
    }

    void sendMessageToPartyOwner(final GroupState gs, CharSequence msg) {
        gs.charBudget -= msg.length();
        gs.nextEnabled_ms = SystemClock.elapsedRealtime() + gs.nextMsgDelay_ms;
        final String send = msg.toString();
        new AsyncTask<Void, Void, Exception>() {
            @Override
            protected Exception doInBackground(Void... params) {
                Network.PeerMessage payload = new Network.PeerMessage();
                payload.text = send;
                try {
                    gs.channel.writeSync(ProtoBufferEnum.PEER_MESSAGE, payload);
                } catch (IOException e) {
                    return e;
                }
                try {
                    Thread.sleep(gs.nextMsgDelay_ms);
                } catch (InterruptedException e) {
                    return e; // Very unlikely
                }
                return null;
            }

            @Override
            protected void onPostExecute(Exception error) {
                if(error != null) {
                    new AlertDialog.Builder(SelectFormingGroupActivity.this)
                            .setMessage(getString(R.string.sendMessageErrorDesc) + error.getLocalizedMessage())
                            .show();
                    return;
                }
                groupListAdapter.notifyDataSetChanged(); // maybe not, but time elapsed maybe restore those buttons
            }
        }.execute();
    }

    void checkNetwork() {
        if(xdata == null) return;
        if(xdata.discoveryStartAttempted() != null) {
            switch(xdata.getDiscoveryStartStatus()) {
                case CrossActivityService.DataPack.DISCOVERY_START_STATUS_OK: {
                    ViewUtils.setVisibility(this, View.VISIBLE,
                            R.id.selectFormingGroupActivity_lookingForGroups,
                            R.id.selectFormingGroupActivity_progressBar);
                    break;
                }
                case CrossActivityService.DataPack.DISCOVERY_START_STATUS_FAIL: {
                    final TextView v = (TextView)findViewById(R.id.selectFormingGroupActivity_initializing);
                    v.setText(R.string.selectFormingGroup_networkDiscoveryFail);
                    v.setVisibility(View.VISIBLE);
                    break;
                }
            }
        }
        if(xdata.foundServices.size() == 0 && candidates.size() == 0) return;
        for(CrossActivityService.FoundService el : xdata.foundServices) {
            int match = el.persist == null? candidates.size() : 0;
            for(; match < candidates.size(); match++) {
                if(candidates.elementAt(match).channel.socket == el.persist) break;
            }
            if(match == candidates.size()) {
                candidates.add(new GroupState(new MessageChannel(el.persist)));
                if(netPump == null) {
                    netPump = new Pumper(guiHandler, MSG_SOCKET_DISCONNECTED);
                    netPump.add(ProtoBufferEnum.GROUP_INFO, new PumpTarget.Callbacks<Network.GroupInfo>() {
                        @Override
                        public Network.GroupInfo make() {
                            return new Network.GroupInfo();
                        }

                        @Override
                        public void mangle(MessageChannel from, Network.GroupInfo msg) throws IOException {
                            guiHandler.sendMessage(guiHandler.obtainMessage(MSG_GROUP_INFO, new Events.GroupInfo(from, msg)));
                        }
                    }).add(ProtoBufferEnum.CHAR_BUDGET, new PumpTarget.Callbacks<Network.CharBudget>() {
                        @Override
                        public Network.CharBudget make() { return new Network.CharBudget(); }

                        @Override
                        public void mangle(MessageChannel from, Network.CharBudget msg) throws IOException {
                            guiHandler.sendMessage(guiHandler.obtainMessage(MSG_CHAR_BUDGET, new Events.CharBudget(from, msg)));
                        }
                    });
                }
                netPump.pump(candidates.lastElement().channel);
            }
        }
        for(int loop = 0; loop < candidates.size(); loop++) {
            CrossActivityService.FoundService match = null;
            for(CrossActivityService.FoundService el : xdata.foundServices) {
                if(el.persist == candidates.elementAt(loop).channel.socket) {
                    match = el;
                    break;
                }
            }
            if(match == null) candidates.remove(loop--);
        }
        int sent = 0;
        for(GroupState check : candidates) if(check.lastMsgSent != null) sent++;
        findViewById(R.id.selectFormingGroupActivity_joiningInstructions).setVisibility(candidates.isEmpty() || sent > 0? View.INVISIBLE : View.VISIBLE);
        findViewById(R.id.selectFormingGroupActivity_confirmInstructions).setVisibility(sent > 0? View.VISIBLE : View.INVISIBLE);
        groupListAdapter.notifyDataSetChanged();
    }

    /// The basic controls are those which can be used when the cross-activity data sharing service is on.
    private void showBasicControls() {
        findViewById(R.id.selectFormingGroupActivity_initializing).setVisibility(View.GONE);
        ViewUtils.setVisibility(this, View.VISIBLE,
                R.id.selectFormingGroupActivity_explicitConnectionInstructions,
                R.id.selectFormingGroupActivity_startExplicitConnection);
    }

    private GroupState getParty(MessageChannel c) {
        for(GroupState gs : candidates) {
            if(gs.channel == c) return gs;
        }
        return null;
    }


    private void charBudget(MessageChannel which, Network.CharBudget payload) {
        GroupState gs = getParty(which);
        if(gs == null) return; // This might happen if we're shutting down or if lost connection to discovered services and already removed
        if(payload.charSpecific != 0) return; // no playing charactes defined there!
        gs.charBudget = payload.total;
        gs.nextMsgDelay_ms = payload.period;
        groupListAdapter.notifyDataSetChanged();
    }

    private void groupInfo(MessageChannel which, Network.GroupInfo payload) {
        GroupState gs = getParty(which);
        if(gs == null) return;
        if(payload.name.isEmpty()) return; // I consider those malicious.
        PartyInfo keep = new PartyInfo(payload.version, payload.name);
        keep.options = payload.options; /// TODO: those should be localized
        gs.group = keep;
        groupListAdapter.notifyDataSetChanged();
    }

    private void socketDisconnected(MessageChannel which, Exception reason) {
        GroupState gs = getParty(which);
        if(gs == null) return; // already erased
        if(gs.group != null) {
            new AlertDialog.Builder(this)
                    .setMessage(String.format(getString(R.string.selectFormingGroupActivity_lostConnection), gs.group.name, reason.getLocalizedMessage()))
                    .show();
        }
        gs.group = null; // I keep the socket around for later matching
    }
}

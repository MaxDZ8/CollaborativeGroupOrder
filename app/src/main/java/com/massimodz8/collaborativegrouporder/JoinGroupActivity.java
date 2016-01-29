package com.massimodz8.collaborativegrouporder;


import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.nsd.NsdManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.support.v7.widget.RecyclerView;
import android.widget.Button;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.networkio.Events;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.networkio.ProtoBufferEnum;
import com.massimodz8.collaborativegrouporder.networkio.joiningClient.GroupJoining;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Vector;

public class JoinGroupActivity extends AppCompatActivity implements PlayingCharacterListAdapter.DataPuller {
    public static final int CLIENT_PROTOCOL_VERSION = 1;
    GroupJoining helper;
    Handler guiThreadHandler;
    private RecyclerView.Adapter groupListAdapter, pcListAdapter;


    public static String mismatchAdvice(int version, AppCompatActivity activity) {
        if(version == CLIENT_PROTOCOL_VERSION) return null;
        String desc = activity.getString(R.string.protocolVersionMismatch);
        String which = activity.getString(CLIENT_PROTOCOL_VERSION < version ? R.string.protocolVersionMismatch_upgradeThis : R.string.protocolVersionMismatch_upgradeServer);
        return String.format(desc, version, CLIENT_PROTOCOL_VERSION, which);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join_group);

        RecyclerView pcList = (RecyclerView)findViewById(R.id.pcList);
        pcListAdapter = new PlayingCharacterListAdapter(this, PlayingCharacterListAdapter.MODE_CLIENT_INPUT);
        pcList.setLayoutManager(new LinearLayoutManager(this));
        pcList.setAdapter(pcListAdapter);

        NsdManager nsd = (NsdManager) getSystemService(Context.NSD_SERVICE);
        if(nsd == null) {
            AlertDialog.Builder build = new AlertDialog.Builder(this);
            build.setTitle(R.string.nullNSDService_title)
                    .setMessage(R.string.nullNSDService_msg);
            build.show();
            return;
        }
        final JoinGroupActivity self = this;
        guiThreadHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                switch(msg.what) {
                    case MSG_GROUP_FOUND: {
                        final GroupConnection real = (GroupConnection)msg.obj;
                        candidates.add(new GroupState(real.channel, real.group));
                        groupListAdapter.notifyDataSetChanged();
                        break;
                    }
                    case MSG_SOCKET_DISCONNECTED: {
                        final Events.SocketDisconnected real = (Events.SocketDisconnected)msg.obj;
                        final GroupState gone = getByChannel(real.which);
                        if(gone == null) { // can happen if something is an "early disconnect" before we get GroupInfo --> MSG_GROUP_FOUND
                            new AlertDialog.Builder(self)
                                    .setMessage(R.string.joinForming_earlyDisconnect)
                                    .show();
                            break;
                        }
                        candidates.remove(gone);
                        String readme = getString(R.string.joinForming_groupLostReport);
                        String addr = gone.channel.socket.getInetAddress().toString();
                        if(addr.charAt(0) == '/') addr = addr.substring(1);
                        readme = String.format(readme, gone.group.name, addr);
                        new AlertDialog.Builder(self)
                                .setMessage(readme)
                                .show();
                        self.groupListAdapter.notifyDataSetChanged();
                        break;
                    }
                    case MSG_CHAR_BUDGET: {
                        Events.CharBudget real = (Events.CharBudget)msg.obj;
                        GroupState group = getByChannel(real.which);
                        if(group == null) break; // just ignore
                        group.charBudget = real.count;
                        group.nextMsgDelay_ms = real.delay_ms;
                        groupListAdapter.notifyDataSetChanged();
                        break;
                    }
                    case MSG_GROUP_FORMING: beginDefiningPC((Events.GroupKey) msg.obj); break;
                    case MSG_PLAYING_CHARACTER_CONFIRM_STATUS: characterConfirmationStatus((Events.CharacterAcceptStatus)msg.obj); break;
                }
                return false;
            }
        });
        helper = new GroupJoining(guiThreadHandler, true, nsd, MSG_SOCKET_DISCONNECTED, MSG_GROUP_FOUND, MSG_CHAR_BUDGET, MSG_GROUP_FORMING, MSG_PLAYING_CHARACTER_CONFIRM_STATUS) {
            @Override
            protected void onDiscoveryStart(ServiceDiscoveryStartStop status) {
                if(status.successful) {
                    findViewById(R.id.progressBar2).setVisibility(View.VISIBLE);
                    groupListAdapter = new GroupListAdapter(self);
                    RecyclerView groupList = (RecyclerView) findViewById(R.id.groupList);
                    groupList.setLayoutManager(new LinearLayoutManager(self));
                    groupList.setAdapter(groupListAdapter);
                }
                else {
                    AlertDialog.Builder build = new AlertDialog.Builder(self);
                    build.setMessage(String.format(getString(R.string.networkDiscoveryStartFailed), nsdErrorString(status.error)));
                    build.show();
                }
            }

            @Override
            protected void onDiscoveryStop(ServiceDiscoveryStartStop status) {

            }
        };
    }

    public static final int MSG_SOCKET_DISCONNECTED = 5;
    public static final int MSG_GROUP_FOUND = 6;
    public static final int MSG_CHAR_BUDGET = 8;
    public static final int MSG_GROUP_FORMING = 9;
    public static final int MSG_PLAYING_CHARACTER_CONFIRM_STATUS = 10;



    private static String nsdErrorString(int error) {
        switch(error) {
            case NsdManager.FAILURE_ALREADY_ACTIVE: return "FAILURE_ALREADY_ACTIVE";
            case NsdManager.FAILURE_INTERNAL_ERROR: return "FAILURE_INTERNAL_ERROR";
            case NsdManager.FAILURE_MAX_LIMIT: return "FAILURE_MAX_LIMIT";
        }
        return String.format("%1$d", error);
    }

    private static class GroupListAdapter extends RecyclerView.Adapter<GroupListAdapter.GroupViewHolder> {
        final JoinGroupActivity activity;

        public GroupListAdapter(JoinGroupActivity activity) {
            this.activity = activity;
            setHasStableIds(true);
        }

        // View holder pattern <-> keep handles to internal Views so I don't need to look em up.
        protected class GroupViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, TextWatcher {
            TextView name, options; // those two change only when rebound.
            TextView curLen, lenLimit; // the first changes when message.getText() changes, lenLimit changes on send or receive of CharBudget message.
            TextView message;
            Button send;
            final Typeface usual;

            GroupState source;
            public GroupViewHolder(View itemView) {
                super(itemView);
                name = (TextView)itemView.findViewById(R.id.card_group_name);
                options = (TextView)itemView.findViewById(R.id.card_group_options);
                curLen = (TextView)itemView.findViewById(R.id.card_group_currentLength);
                lenLimit = (TextView)itemView.findViewById(R.id.card_group_lengthLimit);
                message = (TextView)itemView.findViewById(R.id.card_group_message);
                message.setEnabled(false);
                send = (Button)itemView.findViewById(R.id.card_group_buttonSend);
                send.setEnabled(false);
                send.setOnClickListener(this);
                message.addTextChangedListener(this);
                usual = curLen.getTypeface();
            }

            @Override
            public void onClick(View v) {
                final CharSequence msg = message.getText();
                if(msg.length() == 0) {
                    new AlertDialog.Builder(activity)
                            .setMessage(R.string.saySomethingToServer_emptyForbidden)
                            .show();
                    return;
                }
                if(msg.length() > source.charBudget) {
                    new AlertDialog.Builder(activity)
                            .setMessage(R.string.saySomethingToServer_tooLong)
                            .show();
                    return;
                }
                activity.sayHello(source, msg);
                message.setHint(msg);
                message.setText("");
                message.setEnabled(false);
                send.setEnabled(false);
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
        }

        @Override
        public GroupViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inf = activity.getLayoutInflater();
            View layout = inf.inflate(R.layout.card_joinable_group, parent, false);
            return new GroupViewHolder(layout);
        }

        @Override
        public void onBindViewHolder(GroupViewHolder holder, int position) {
            final GroupState info = activity.candidates.elementAt(position);
            final int current = holder.message.getText().length();
            final int allowed = info.charBudget;
            holder.source = info;
            holder.name.setText(info.group.name);
            holder.curLen.setText(String.valueOf(current));
            holder.lenLimit.setText(String.valueOf(allowed));
            if(info.group.options == null) holder.options.setVisibility(View.GONE);
            else {
                String total = activity.getString(R.string.card_group_options);
                for(int app = 0; app < info.group.options.length; app++) {
                    if(app != 0) total += ", ";
                    total += info.group.options[app]; /// TODO this should be localized by device language! Not server language! Map enums/tokens to localized strings somehow!
                }
                holder.options.setText(total);
                holder.options.setVisibility(View.VISIBLE);
            }
            final boolean status = allowed != 0 && info.nextEnabled_ms < SystemClock.elapsedRealtime();
            holder.message.setEnabled(status);
            holder.send.setEnabled(status);
        }

        @Override
        public long getItemId(int position) {
            return activity.candidates.elementAt(position).channel.unique;
        }

        @Override
        public int getItemCount() {
            return activity.candidates.size();
        }
    }

    public static class GroupConnection {
        MessageChannel channel;
        ConnectedGroup group;

        public GroupConnection(MessageChannel channel, ConnectedGroup group) {
            this.channel = channel;
            this.group = group;
        }
    }

    static class GroupState extends GroupConnection {
        int charBudget;
        int nextMsgDelay_ms;
        volatile long nextEnabled_ms = 0; // SystemClock.elapsedRealtime(); /// if now() is >= this, controls are updated if charBudget > 0

        public GroupState(MessageChannel pipe, ConnectedGroup group) { super(pipe, group); }
    }

    private Vector<GroupState> candidates = new Vector<>();
    private byte[] groupKey;
    private Vector<BuildingPlayingCharacter> pcs;

    GroupState getByChannel(MessageChannel pipe) {
        for(GroupState gs : candidates) {
            if(gs.channel == pipe) return gs;
        }
        return null;
    }


    void sayHello(final GroupState gs, final CharSequence newMsg) {
        gs.charBudget -= newMsg.length();
        gs.nextEnabled_ms = SystemClock.elapsedRealtime() + gs.nextMsgDelay_ms;
        final String send = newMsg.toString();
        final AppCompatActivity self = this;
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
                    new AlertDialog.Builder(self)
                            .setMessage(getString(R.string.sendMessageErrorDesc) + error.getLocalizedMessage())
                            .show();
                    return;
                }
                groupListAdapter.notifyDataSetChanged(); // maybe not, but time elapsed maybe restore those buttons
            }
        }.execute();
    }


    static final int EXPLICIT_CONNECTION_REQUEST = 1;

    public void startExplicitConnectionActivity(View btn) {
        Intent intent = new Intent(this, ExplicitConnectionActivity.class);
        startActivityForResult(intent, EXPLICIT_CONNECTION_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode) {
            case EXPLICIT_CONNECTION_REQUEST: {
                if (resultCode != RESULT_OK) return; // RESULT_CANCELLED
                final String addr = data.getStringExtra(ExplicitConnectionActivity.RESULT_EXTRA_INET_ADDR);
                final int port = data.getIntExtra(ExplicitConnectionActivity.RESULT_EXTRA_PORT, -1);
                new AsyncTask<Void, Void, ExplicitConnectionActivity.Error>() {
                    @Override
                    protected ExplicitConnectionActivity.Error doInBackground(Void... params) {
                        ExplicitConnectionActivity.Error ohno = null;
                        Socket socket = null;
                        try {
                            socket = new Socket(addr, port);
                        } catch (UnknownHostException e) {
                            ohno = new ExplicitConnectionActivity.Error(null, getString(R.string.badHost_msg));
                            ohno.refocus = R.id.in_explicit_inetAddr;
                        } catch (IOException e) {
                            ohno = new ExplicitConnectionActivity.Error(getString(R.string.explicitConn_IOException_title), String.format(getString(R.string.explicitConn_IOException_msg), e.getLocalizedMessage()));
                        }
                        if(ohno != null) return ohno;

                        try {
                            helper.beginHandshake(socket);
                        } catch (IOException e) {
                            ohno = new ExplicitConnectionActivity.Error(getString(R.string.explicitConn_IOException_title), String.format(getString(R.string.explicitConn_IOException_msg), e.getLocalizedMessage()));
                        } catch (ClassCastException e) {
                            ohno = new ExplicitConnectionActivity.Error(null, getString(R.string.unexpectedInitialServerReply_msg));
                        }
                        return ohno;
                    }

                    /*
                    @Override
                    protected void onPostExecute(ConnectionAttempt shaken) {
                        if (shaken.ohno != null) {
                            AlertDialog.Builder build = new AlertDialog.Builder(self);
                            if (shaken.ohno.title != null && !shaken.ohno.title.isEmpty())
                                build.setTitle(shaken.ohno.title);
                            if (shaken.ohno.msg != null && !shaken.ohno.msg.isEmpty())
                                build.setMessage(shaken.ohno.msg);
                            if (shaken.ohno.refocus != null)
                                findViewById(shaken.ohno.refocus).requestFocus();
                            build.show();
                            return;
                        } // ^ same as ExplicitConnectionActivity
                        candidates.add(new EnumeratedGroup(shaken.group));
                        groupListAdapter.notifyDataSetChanged();
                    }
                    */
                }.execute();
            }
        }
    }


    private void beginDefiningPC(Events.GroupKey obj) {
        if(groupKey != null) {
            if (obj.origin != candidates.elementAt(0).channel) {
                new AlertDialog.Builder(this)
                        .setMessage(getString(R.string.joinGroupActivity_multiKeyMismatching))
                        .show();
                return;
            } else {
                new AlertDialog.Builder(this)
                        .setMessage(R.string.joinGroupActivity_multipleGroupKeys)
                        .show();
            }
        }
        else {
            helper.stopDiscovering();
            helper.keepOnly(obj.origin);
            final GroupState gs = getByChannel(obj.origin);
            candidates.clear();
            candidates.add(gs);
        }
        final GroupState connected = candidates.elementAt(0);
        groupKey = obj.key; // it doesn't really change a thing!
        int hide[] = new int[] {
                R.id.txt_lookingForGroups, R.id.progressBar2, R.id.groupList,
                R.id.txt_explicitConnectHint, R.id.btn_startExplicitConnection
        };
        for(int h : hide) findViewById(h).setVisibility(View.GONE);

        findViewById(R.id.pcList).setVisibility(View.VISIBLE);
        final String localized = getString(R.string.phaseDefiningCharacters);
        final ActionBar actionBar = getSupportActionBar();
        if(actionBar != null) actionBar.setTitle(String.format("%1$s - %2$s", connected.group.name, localized));

        int show[] = new int[] {
                R.id.activity_join_group_addCharacterButton, R.id.pcList
        };
        for(int h : show) findViewById(h).setVisibility(View.VISIBLE);
        addCharacterCard();
    }

    public void addCharacterCard() {
        if(pcs == null) pcs = new Vector<>();
        pcs.add(new BuildingPlayingCharacter());
        pcListAdapter.notifyDataSetChanged();
    }
    public void addCharacterCardCallback(View unused) { addCharacterCard(); }



    private void characterConfirmationStatus(Events.CharacterAcceptStatus obj) {
        if(pcs == null) return; // ignore bad formed input, can happen if a malicious server sends me a character accept before it sends the salt.
        BuildingPlayingCharacter match = null;
        for(BuildingPlayingCharacter test : pcs) {
            if(test.id == obj.key) {
                match = test;
                break;
            }
        }
        if(null == match) return;
        match.status = obj.accepted? BuildingPlayingCharacter.STATUS_ACCEPTED : BuildingPlayingCharacter.STATUS_BUILDING;
        if(!obj.accepted) {
            new AlertDialog.Builder(this)
                    .setMessage(String.format(getString(R.string.joinGroupActivity_characterRejectedRetryMessage), match.name))
                    .show();
        }
        pcListAdapter.notifyDataSetChanged();
        enableNewPCButton();
    }

    //
    // PlayingCharacterListAdapter.PlayingCharacterPuller __________________________________________
    @Override
    public int getVisibleCount() {
        return pcs != null? pcs.size() : 0;
    }

    @Override
    public void action(final BuildingPlayingCharacter who, int what) {
        // PlayingCharacterListAdapter.SEND: {
        final MessageChannel channel = candidates.elementAt(0).channel;
        final JoinGroupActivity self = this;
        new AsyncTask<Void, Void, Exception>() {
            @Override
            protected Exception doInBackground(Void... params) {
                Network.PlayingCharacterDefinition wire = new Network.PlayingCharacterDefinition();
                wire.name = who.name;
                wire.initiativeBonus = who.initiativeBonus;
                wire.healthPoints = who.fullHealth;
                wire.experience = who.experience;
                wire.peerKey = who.id;
                try {
                    channel.writeSync(ProtoBufferEnum.PLAYING_CHARACTER_DEFINITION, wire);
                } catch (IOException e) {
                    return e;
                }
                return null;
            }

            @Override
            protected void onPostExecute(Exception e) {
                if(e != null) {
                    new AlertDialog.Builder(self)
                            .setMessage(getString(R.string.joinGroupActivity_failedPCSend) + e.getLocalizedMessage());
                    return;
                }
                who.status = BuildingPlayingCharacter.STATUS_SENT;
                pcListAdapter.notifyDataSetChanged();
                enableNewPCButton();
            }
        }.execute();
    }

    private void enableNewPCButton() {
        boolean status = true;
        for(BuildingPlayingCharacter c : pcs) {
            if (BuildingPlayingCharacter.STATUS_BUILDING == c.status) {
                status = false;
                break;
            }
        }
        findViewById(R.id.activity_join_group_addCharacterButton).setEnabled(status);
    }

    @Override
    public AlertDialog.Builder makeDialog() {
        return new AlertDialog.Builder(this);
    }

    @Override
    public View inflate(int resource, ViewGroup root, boolean attachToRoot) {
        return getLayoutInflater().inflate(resource, root, attachToRoot);
    }

    @Override
    public BuildingPlayingCharacter get(int position) {
        if(pcs == null) return null;
        int count = 0;
        for(BuildingPlayingCharacter c : pcs) {
            if(count == position) return c;
            count++;
        }
        return null; // uhm
    }

    @Override
    public long getStableId(int position) {
        if(pcs == null || position >= pcs.size()) return RecyclerView.NO_ID;
        return pcs.get(position).id;
    }
}

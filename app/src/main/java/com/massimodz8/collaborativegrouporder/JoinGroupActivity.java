package com.massimodz8.collaborativegrouporder;


import android.content.Context;
import android.content.Intent;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.support.v7.widget.RecyclerView;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.networkio.joiningClient.GroupJoining;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Vector;

public class JoinGroupActivity extends AppCompatActivity {
    public static final int CLIENT_PROTOCOL_VERSION = 1;
    GroupJoining helper;
    Handler guiThreadHandler;
    private RecyclerView.Adapter groupListAdapter;


    public static String mismatchAdvice(int version, AppCompatActivity activity) {
        if(version == CLIENT_PROTOCOL_VERSION) return null;
        String desc = activity.getString(R.string.protocolVersionMismatch);
        String which = activity.getString(CLIENT_PROTOCOL_VERSION < version ? R.string.protocolVersionMismatch_upgradeThis : R.string.protocolVersionMismatch_upgradeServer);
        return String.format(desc, version, CLIENT_PROTOCOL_VERSION, which);
    }

    public static class GroupConnection {
        MessageChannel channel;
        ConnectedGroup group;

        public GroupConnection(MessageChannel channel, ConnectedGroup group) {
            this.channel = channel;
            this.group = group;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join_group);
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
                    case MSG_DISCONNECTED_WHILE_NEGOTIATING: {
                        // Uhm, is this a problem? Do the user cares?
                        // For the time being, just drop the thing.
                        break;
                    }
                    case MSG_GROUP_GONE: {
                        final MessageChannel c = (MessageChannel)msg.obj;
                            AlertDialog.Builder build = new AlertDialog.Builder(self);
                            build.setMessage("group is gone!"); /// TODO! Add behaviour for group disconnect.
                            build.show();
                        break;
                    }
                }
                return false;
            }
        });
        helper = new GroupJoining(guiThreadHandler, true, nsd, MSG_DISCONNECTED_WHILE_NEGOTIATING, MSG_GROUP_FOUND, MSG_GROUP_GONE) {
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

    public static final int MSG_DISCONNECTED_WHILE_NEGOTIATING = 5;
    public static final int MSG_GROUP_FOUND = 6;
    public static final int MSG_GROUP_GONE = 7;

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
        }

        // View holder pattern <-> keep handles to internal Views so I don't need to look em up.
        protected class GroupViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
            TextView name;
            TextView options;
            TextView version;
            long index = -1;
            public GroupViewHolder(View itemView) {
                super(itemView);
                name = (TextView)itemView.findViewById(R.id.card_groupName);
                options = (TextView)itemView.findViewById(R.id.card_options);
                version = (TextView)itemView.findViewById(R.id.card_protoVersion);
                itemView.setOnClickListener(this);
            }

            @Override
            public void onClick(View v) {
                activity.sayHello(index);
            }
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
            holder.index = info.pipe.unique;
            holder.name.setText(info.group.name);
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
            String mismatch = mismatchAdvice(info.group.version, activity);
            if(mismatch != null) holder.version.setText(mismatch);
        }

        @Override
        public int getItemCount() {
            return activity.candidates.size();
        }
    }

    static class GroupState {
        MessageChannel pipe;
        ConnectedGroup group;
        int charBudget;

        public GroupState(MessageChannel pipe, ConnectedGroup group) {
            this.pipe = pipe;
            this.group = group;
        }
    }

    private Vector<GroupState> candidates = new Vector<>();


    void sayHello(long index) {
        AlertDialog.Builder build = new AlertDialog.Builder(this);
        /*
        final ReadyGroup rg =  candidates.elementAt(index).rg;
        final View body = getLayoutInflater().inflate(R.layout.dialog_joining_hello, null);
        final TextView name = (TextView)body.findViewById(R.id.groupName);
        name.setText(rg.cg.name);
        final EditText msg = (EditText)body.findViewById(R.id.masterMessage);
        final View btn = body.findViewById(R.id.sendHelloBtn);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final PeerMessage sending = new PeerMessage(msg.getText().toString());
                body.findViewById(R.id.postSendInfos).setVisibility(View.VISIBLE);
                msg.setEnabled(false);
                btn.setEnabled(false);
                new AsyncTask<Void, Void, Void>() {
                    @Override
                    protected Void doInBackground(Void... params) {
                        try {
                            rg.s.writer.writeObject(msg);
                        } catch (IOException e) {
                            return null; // let's forget about this. Hopefully user will try again.
                        }
                        SystemClock.sleep(SEND_MESSAGE_PERIOD_MS);
                        return null;
                    }

                    @Override
                    protected void onPostExecute(Void aVoid) {
                        msg.setEnabled(true);
                        msg.setText("");
                        msg.requestFocus();
                        btn.setEnabled(true);
                    }
                }.execute();
            }
        });
        build.setView(body);*/
        build.setMessage("TODO_MOFO! STUB TODO!");
        build.show();
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
}

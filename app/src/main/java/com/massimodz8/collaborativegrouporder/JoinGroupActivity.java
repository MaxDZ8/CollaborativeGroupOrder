package com.massimodz8.collaborativegrouporder;


import android.content.Context;
import android.content.Intent;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.support.v7.widget.RecyclerView;
import android.widget.EditText;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.networkMessage.PeerMessage;
import com.massimodz8.collaborativegrouporder.networkMessage.ServerInfoRequest;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Vector;

public class JoinGroupActivity extends AppCompatActivity {
    public static final int SEND_MESSAGE_PERIOD_MS = 2000;
    private RecyclerView.Adapter groupListAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join_group);

        NsdManager nsdService = (NsdManager) getSystemService(Context.NSD_SERVICE);
        if(nsdService == null) {
            AlertDialog.Builder build = new AlertDialog.Builder(this);
            build.setTitle(R.string.nullNSDService_title)
                    .setMessage(R.string.nullNSDService_msg);
            build.show();
        }

        findViewById(R.id.progressBar2).setVisibility(View.VISIBLE);
        groupListAdapter = new GroupListAdapter();
        RecyclerView groupList = (RecyclerView) findViewById(R.id.groupList);
        groupList.setLayoutManager(new LinearLayoutManager(this));
        groupList.setAdapter(groupListAdapter);
        final JoinGroupActivity self = this;
        guiThreadHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                switch(msg.what) {
                    case MSG_SERVICE_DISCOVERY_STARTED:
                    case MSG_SERVICE_DISCOVERY_STOPPED: {
                        ServiceDiscoveryStartStop meh = (ServiceDiscoveryStartStop) msg.obj;
                        if (meh == null) break; // wut? Impossible for the time being
                        if (meh.successful) {
                            findViewById(R.id.txt_lookingForGroups).setVisibility(View.VISIBLE);
                            findViewById(R.id.groupList).setVisibility(View.VISIBLE);
                            findViewById(R.id.txt_explicitConnectHint).setVisibility(View.VISIBLE);
                            findViewById(R.id.btn_startExplicitConnection).setVisibility(View.VISIBLE);
                            break;
                        }
                        else {
                            findViewById(R.id.progressBar2).setEnabled(false);
                        }
                        AlertDialog.Builder build = new AlertDialog.Builder(self);
                        build.setTitle(getString(R.string.serviceDiscErr))
                                .setMessage(String.format(getString(R.string.serviceDiscoveryFailed_msg), nsdErrorString(meh.error)));
                        build.show();
                        break;
                    }
                    case MSG_SERVICE_FOUND:
                    case MSG_SERVICE_LOST: {
                        NsdServiceInfo info = (NsdServiceInfo) msg.obj;
                        if (info == null) break;
                        AlertDialog.Builder build = new AlertDialog.Builder(self);
                        build.setTitle("Service " + (msg.what == MSG_SERVICE_FOUND? "found" : "lost"))
                                .setMessage(
                                        info.toString()
                                );
                        build.show();
                        break;
                    }
                }
                return false;
            }
        });
        nsdService.discoverServices("_groupInitiative._tcp", NsdManager.PROTOCOL_DNS_SD, new DiscoveryPumper());
    }

    private class DiscoveryPumper implements NsdManager.DiscoveryListener {
        @Override
        public void onStartDiscoveryFailed(String serviceType, int errorCode) {
            Message msg = Message.obtain(guiThreadHandler, MSG_SERVICE_DISCOVERY_STARTED, new ServiceDiscoveryStartStop(errorCode));
            guiThreadHandler.sendMessage(msg);
        }

        @Override
        public void onDiscoveryStarted(String serviceType) {
            Message msg = Message.obtain(guiThreadHandler, MSG_SERVICE_DISCOVERY_STARTED, new ServiceDiscoveryStartStop());
            guiThreadHandler.sendMessage(msg);
        }

        @Override
        public void onStopDiscoveryFailed(String serviceType, int errorCode) {
            Message msg = Message.obtain(guiThreadHandler, MSG_SERVICE_DISCOVERY_STOPPED, new ServiceDiscoveryStartStop(errorCode));
            guiThreadHandler.sendMessage(msg);
        }

        @Override
        public void onDiscoveryStopped(String serviceType) {
            Message msg = Message.obtain(guiThreadHandler, MSG_SERVICE_DISCOVERY_STOPPED, new ServiceDiscoveryStartStop());
            guiThreadHandler.sendMessage(msg);
        }

        @Override
        public void onServiceFound(NsdServiceInfo serviceInfo) {
            Message msg = Message.obtain(guiThreadHandler, MSG_SERVICE_FOUND, serviceInfo);
            guiThreadHandler.sendMessage(msg);

        }

        @Override
        public void onServiceLost(NsdServiceInfo serviceInfo) {
            Message msg = Message.obtain(guiThreadHandler, MSG_SERVICE_LOST, serviceInfo);
            guiThreadHandler.sendMessage(msg);
        }
    }

    private class GroupListAdapter extends RecyclerView.Adapter<GroupListAdapter.GroupViewHolder> {
        // View holder pattern <-> keep handles to internal Views so I don't need to look em up.
        protected class GroupViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
            TextView name;
            TextView options;
            TextView version;
            int index = -1;
            public GroupViewHolder(View itemView) {
                super(itemView);
                name = (TextView)itemView.findViewById(R.id.card_groupName);
                options = (TextView)itemView.findViewById(R.id.card_options);
                version = (TextView)itemView.findViewById(R.id.card_protoVersion);
                itemView.setOnClickListener(this);
            }

            @Override
            public void onClick(View v) {
                sayHello(index);
            }
        }

        @Override
        public GroupViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inf = getLayoutInflater();
            View layout = inf.inflate(R.layout.card_joinable_group, parent, false);
            return new GroupViewHolder(layout);
        }

        @Override
        public void onBindViewHolder(GroupViewHolder holder, int position) {
            holder.index = position;
            final ConnectedGroup cg = candidates.elementAt(position).rg.cg;
            holder.name.setText(cg.name);
            if(cg.options == null) holder.options.setVisibility(View.GONE);
            else {
                String total = getString(R.string.card_group_options);
                for(int app = 0; app < cg.options.length; app++) {
                    if(app != 0) total += ", ";
                    total += cg.options[app]; /// TODO this should be localized by device language! Not server language! Map enums/tokens to localized strings somehow!
                }
                holder.options.setText(total);
                holder.options.setVisibility(View.VISIBLE);
            }
            final String ver = getString(R.string.card_group_version);
            String comparison = getString(R.string.card_group_sameVersion);
            if(NetworkListeningActivity.PROTOCOL_VERSION < cg.version) comparison = getString(R.string.card_group_oldProtocol_upgradeMe);
            else if(NetworkListeningActivity.PROTOCOL_VERSION > cg.version) comparison = getString(R.string.card_group_oldProtocol_upgradeGroupOwner);
            holder.version.setText(String.format(ver, cg.version, comparison));
        }

        @Override
        public int getItemCount() {
            return candidates.size();
        }

        @Override
        public long getItemId(int position) {
            return super.getItemId(position);
        }
    }


    private static String nsdErrorString(int error) {
        switch(error) {
            case NsdManager.FAILURE_ALREADY_ACTIVE: return "FAILURE_ALREADY_ACTIVE";
            case NsdManager.FAILURE_INTERNAL_ERROR: return "FAILURE_INTERNAL_ERROR";
            case NsdManager.FAILURE_MAX_LIMIT: return "FAILURE_MAX_LIMIT";
        }
        return String.format("%1$d", error);
    }

    public static final int MSG_SERVICE_DISCOVERY_STARTED = 1;
    public static final int MSG_SERVICE_DISCOVERY_STOPPED = 2;
    public static final int MSG_SERVICE_FOUND = 3;
    public static final int MSG_SERVICE_LOST = 4;

    static class ServiceDiscoveryStartStop {
        public ServiceDiscoveryStartStop() { successful = true; }
        public ServiceDiscoveryStartStop(int err) {
            error = err;
            successful = false;
        }

        public int error;
        public boolean successful;
    }

    Handler guiThreadHandler;

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
                final JoinGroupActivity self = this;

                new AsyncTask<Void, Void, ConnectionAttempt>() {
                    @Override
                    protected ConnectionAttempt doInBackground(Void... params) {
                        ConnectionAttempt result = new ConnectionAttempt();
                        try {
                            result.group = initialConnect(addr, port);
                        } catch (UnknownHostException e) {
                            result.ohno = new ExplicitConnectionActivity.Shaken.Error(null, getString(R.string.badHost_msg));
                            result.ohno.refocus = R.id.in_explicit_inetAddr;
                        } catch (IOException e) {
                            result.ohno = new ExplicitConnectionActivity.Shaken.Error(getString(R.string.explicitConn_IOException_title), String.format(getString(R.string.explicitConn_IOException_msg), e.getLocalizedMessage()));
                        } catch (ClassNotFoundException e) {
                            result.ohno = new ExplicitConnectionActivity.Shaken.Error(null, getString(R.string.badInitialServerReply_msg));
                        } catch (ClassCastException e) {
                            result.ohno = new ExplicitConnectionActivity.Shaken.Error(null, getString(R.string.unexpectedInitialServerReply_msg));
                        }
                        return result;
                    }

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
                }.execute();
            }
        }

    }


    public static class ReadyGroup {
        public ConnectedGroup cg;
        public Socket sock;
        public ObjectOutputStream writer;
        public ObjectInputStream reader;

        public ReadyGroup(Socket sock, ConnectedGroup cg) {
            this.sock = sock;
            this.cg = cg;
        }
    }
    private static class ConnectionAttempt {
        ExplicitConnectionActivity.Shaken.Error ohno;
        ReadyGroup group;
    }
    private static class EnumeratedGroup {
        ReadyGroup rg;
        final long index;
        static long generated = 0;
        EnumeratedGroup(ReadyGroup rg) {
            this.rg = rg;
            index = generated++;
        }
    }

    private Vector<EnumeratedGroup> candidates = new Vector<>();

    public static ReadyGroup initialConnect(String addr, int port) throws /*UnknownHostException,*/ IOException, ClassNotFoundException, ClassCastException {
        Socket pipe = new Socket(addr, port);
        ObjectOutputStream writer = new ObjectOutputStream(pipe.getOutputStream());
        ServerInfoRequest tellme = new ServerInfoRequest();
        writer.writeObject(tellme);

        ObjectInputStream reader = new ObjectInputStream(pipe.getInputStream());
        ReadyGroup result = new ReadyGroup(pipe, (ConnectedGroup)reader.readObject());
        result.writer = writer;
        result.reader = reader;
        return result;
    }

    void sayHello(int index) {
        AlertDialog.Builder build = new AlertDialog.Builder(this);
        final ReadyGroup rg =  candidates.elementAt(index).rg;
        final View body = getLayoutInflater().inflate(R.layout.dialog_joining_hello, null);
        final TextView name = (TextView)body.findViewById(R.id.groupName);
        name.setText(rg.cg.name);
        final EditText msg = (EditText)body.findViewById(R.id.masterMessage);
        final View btn = body.findViewById(R.id.sendHelloBtn);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    rg.writer.writeObject(new PeerMessage(msg.getText().toString()));
                } catch (IOException e) {
                    return; // let's forget about this. Hopefully user will try again.
                }
                body.findViewById(R.id.postSendInfos).setVisibility(View.VISIBLE);
                msg.setEnabled(false);
                btn.setEnabled(false);
                new AsyncTask<Void, Void, Void>() {
                    @Override
                    protected Void doInBackground(Void... params) {
                        SystemClock.sleep(SEND_MESSAGE_PERIOD_MS);
                        return null;
                    }

                    @Override
                    protected void onPostExecute(Void aVoid) {
                        msg.setEnabled(true);
                        btn.setEnabled(true);
                    }
                }.execute();
            }
        });
        build.setView(body);
        build.show();
    }
}

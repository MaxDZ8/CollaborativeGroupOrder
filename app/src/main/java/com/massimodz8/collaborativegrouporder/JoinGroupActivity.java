package com.massimodz8.collaborativegrouporder;

import android.app.Activity;
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
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.massimodz8.collaborativegrouporder.networkMessage.ServerInfoRequest;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Vector;

public class JoinGroupActivity extends AppCompatActivity {
    private NsdManager nsdService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join_group);

        nsdService = (NsdManager)getSystemService(Context.NSD_SERVICE);
        if(nsdService == null) {
            AlertDialog.Builder build = new AlertDialog.Builder(this);
            build.setTitle(R.string.nullNSDService_title)
                    .setMessage(R.string.nullNSDService_msg);
            build.show();
        }

        findViewById(R.id.progressBar2).setVisibility(View.VISIBLE);
        candidates = new ArrayAdapter<ReadyGroup>(this, R.layout.group_view_layout) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                return super.getView(position, convertView, parent);
            }
        };
        ((ListView)findViewById(R.id.groupList)).setAdapter(candidates);
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

        NsdManager.DiscoveryListener explorer = new NsdManager.DiscoveryListener() {
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
        };
        nsdService.discoverServices("_groupInitiative._tcp", NsdManager.PROTOCOL_DNS_SD, explorer);
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
        if(requestCode == EXPLICIT_CONNECTION_REQUEST) {
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
                    if(shaken.ohno != null) {
                        AlertDialog.Builder build = new AlertDialog.Builder(self);
                        if(shaken.ohno.title != null && !shaken.ohno.title.isEmpty()) build.setTitle(shaken.ohno.title);
                        if(shaken.ohno.msg != null && !shaken.ohno.msg.isEmpty()) build.setMessage(shaken.ohno.msg);
                        if(shaken.ohno.refocus != null) findViewById(shaken.ohno.refocus).requestFocus();
                        build.show();
                        return;
                    } // ^ same as ExplicitConnectionActivity
                    candidates.add(shaken.group);
                    candidates.notifyDataSetChanged();
                }
            }.execute();
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

    private ArrayAdapter<ReadyGroup> candidates;

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
}

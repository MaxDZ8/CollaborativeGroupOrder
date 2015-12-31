package com.massimodz8.collaborativegrouporder;

import android.content.Context;
import android.content.Intent;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import java.net.Socket;
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
        if(requestCode != EXPLICIT_CONNECTION_REQUEST) return;
        if(resultCode != RESULT_OK) return; // RESULT_CANCELLED
        final Bundle bundle = data.getBundleExtra(ExplicitConnectionActivity.RESULT_BUNDLE_NAME);
        ConnectedGroup cg = (ConnectedGroup)bundle.get(ExplicitConnectionActivity.RESULT_BUNDLE_GROUP_INFO);
        Socket sock = (Socket)bundle.get(ExplicitConnectionActivity.RESULT_BUNDLE_SOCKET);
        candidates.add(new ReadyGroup(sock, cg));
    }

    private static class ReadyGroup {
        public ConnectedGroup cg;
        public Socket sock;

        public ReadyGroup(Socket sock, ConnectedGroup cg) {
            this.sock = sock;
            this.cg = cg;
        }
    }
    private Vector<ReadyGroup> candidates = new Vector<>();
}

package com.massimodz8.collaborativegrouporder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.PeerListListener;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import java.util.Collection;
import java.util.Iterator;

/* This activity is started by the MainMenuActivity when the user wants to assemble a new party.
We open a Wi-Fi direct bubble and a proper service and listen to network to find users joining.
 */
/** todo: this is an excellent moment to provide some ads: after the GM started scanning
 * he has to wait for users to join and I can push to him whatever I want.  */
public class NetworkListeningActivity extends AppCompatActivity {
    WifiP2pManager wifi;
    WifiP2pManager.Channel peerNetwork;

    class WifiBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(final Context context, Intent intent) {
            String action = intent.getAction();
            if(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
                int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                if(state != WifiP2pManager.WIFI_P2P_STATE_ENABLED) p2pFailed();
            } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
                /*
                This involves stuff such as
                (WifiP2pDevice) intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
                Which apparently provide some information about this device... I don't care about those things really...
                 */
                /// TODO: figure out what to do with WIFI_P2P_THIS_DEVICE_CHANGED_ACTION
            } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                /*
                Again, I'm not sure what this really is, I'll have to look at it.
                 */
                /// TODO: figure out what to do with WIFI_P2P_CONNECTION_CHANGED_ACTION
            } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                wifi.requestPeers(peerNetwork, new PeerListListener() {
                    @Override
                    public void onPeersAvailable(WifiP2pDeviceList peers) {
                        Collection<WifiP2pDevice> list = peers.getDeviceList();
                        String shit = "";
                        for(WifiP2pDevice el : list) {
                            shit += el.toString() + '\n';
                        }

                        AlertDialog.Builder build = new AlertDialog.Builder(context);
                        build.setTitle("Peers updated")
                                .setMessage(shit);
                        build.show();
                        return;
                    }
                });
             }
        }
    }


    WifiBroadcastReceiver receiver;
    IntentFilter intentFilter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_network_listening);

        intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
    }

    @Override
    public void onResume() {
        super.onResume();
        receiver = new WifiBroadcastReceiver();
        registerReceiver(receiver, intentFilter);
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }

    public void initiatePartyHandshake(View view) {
        final EditText groupNameView = (EditText)findViewById(R.id.in_partyName);
        final String gname = groupNameView.getText().toString();
        if(gname.isEmpty()) {
            AlertDialog.Builder build = new AlertDialog.Builder(this);
            build.setTitle(R.string.groupNameIsEmpty_title)
                    .setMessage(R.string.groupNameIsEmpty_msg)
                    .setPositiveButton(R.string.groupName_retry, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            groupNameView.requestFocus();
                        }
                    });
            build.show();
            return;
        }

        wifi = (WifiP2pManager)getSystemService(Context.WIFI_P2P_SERVICE);
        if(wifi == null) {
            AlertDialog.Builder build = new AlertDialog.Builder(this);
            build.setTitle(R.string.nullWifi_title)
                    .setMessage(R.string.nullWifi_msg);
            build.show();
            return;
        }
        receiver = new WifiBroadcastReceiver();
        registerReceiver(receiver, intentFilter);
        peerNetwork = wifi.initialize(this, getMainLooper(), null);

        /*
        The goal of this activity is to create the "group initiative" service but in P2P networking
        the difference is subtle and peer discovery must take place anyway.
        */
        final NetworkListeningActivity self = this;
        wifi.discoverPeers(peerNetwork, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() { /* assume it'll work */ }

            @Override
            public void onFailure(int reasonCode) { self.peerDiscoveryFailed(reasonCode); }
        });

        WifiP2pDnsSdServiceInfo servDesc = WifiP2pDnsSdServiceInfo.newInstance(gname, "_groupInitiative._tcp", null);
        wifi.addLocalService(peerNetwork, servDesc, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() { /* assume completed. */ }

            @Override
            public void onFailure(int reasonCode) { self.serviceInstallFailed(reasonCode); }
        });

        /*
        wifi.discoverPeers(peerNetwork, new WifiP2pManager.ActionListener() {
        @Override
        public void onSuccess() { /* assume discover init is ok * / }

        @Override
        public void onFailure(int reasonCode) { self.discoveryFailed(reasonCode); }
        });
        */


        /*
         more peer network work. I will have to think at this carefully.
         First find the peers, then handshake them, show them on the list... but the list
         is about characters!
        */



        view.setEnabled(false);
        groupNameView.setEnabled(false);
        findViewById(R.id.txt_scanning).setVisibility(View.VISIBLE);
        findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
        findViewById(R.id.list_characters).setVisibility(View.VISIBLE);
        View closeGroupBtn = findViewById(R.id.btn_closeGroup);
        closeGroupBtn.setVisibility(View.VISIBLE);
        closeGroupBtn.setEnabled(false);


        // Initiate WiFi-direct handshake and services.

        // Eventually show service name if colliding!

    }

    private void serviceInstallFailed(int reason) {
        disableButtons();

        String readable = "<Unknown error>";
        switch(reason) {
            case WifiP2pManager.P2P_UNSUPPORTED: readable = "P2P_UNSUPPORTED"; break;
            case WifiP2pManager.ERROR: readable = "ERROR"; break;
            case WifiP2pManager.BUSY: readable = "BUSY"; break;
        }

        AlertDialog.Builder build = new AlertDialog.Builder(this);
        build.setTitle(R.string.discoveryFailed_title)
                .setMessage(String.format(getString(R.string.discoveryFailed_msg), readable));
        build.show();
    }

    private void disableButtons() {
        findViewById(R.id.btn_closeGroup).setEnabled(false);
        findViewById(R.id.progressBar).setEnabled(false);
        findViewById(R.id.list_characters).setEnabled(false);
    }


    private void peerDiscoveryFailed(int reason) {
        disableButtons();

        String readable = "<Unknown error>";
        switch(reason) {
            case WifiP2pManager.P2P_UNSUPPORTED: readable = "P2P_UNSUPPORTED"; break;
            case WifiP2pManager.ERROR: readable = "ERROR"; break;
            case WifiP2pManager.BUSY: readable = "BUSY"; break;
        }

        AlertDialog.Builder build = new AlertDialog.Builder(this);
        build.setTitle(R.string.serviceInstallFailed_title)
                .setMessage(String.format(getString(R.string.serviceInstallFailed_msg), readable));
        build.show();
    }
    private void p2pFailed() {
        disableButtons();

        AlertDialog.Builder build = new AlertDialog.Builder(this);
        build.setTitle(R.string.unexpectedP2PStateChange_title)
                .setMessage(getString(R.string.unexpectedP2PStateChange_msg));
        build.show();
    }
}

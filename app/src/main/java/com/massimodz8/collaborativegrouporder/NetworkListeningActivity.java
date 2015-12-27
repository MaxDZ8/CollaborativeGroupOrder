package com.massimodz8.collaborativegrouporder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.wifi.p2p.WifiP2pManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

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
        public void onReceive(Context context, Intent intent) {
            // TODO: 27/12/2015
        }
    };
    WifiBroadcastReceiver receiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_network_listening);
    }

    @Override
    public void onResume() {
        super.onResume();
        receiver = new WifiBroadcastReceiver(mManager, mChannel, this);
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
            build.setTitle(getString(R.string.nullWifi_title))
                    .setMessage(getString(R.string.nullWifi_msg));
            build.show();
            return;
        }
        peerNetwork = wifi.initialize(this, getMainLooper(), null);

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
}

package com.massimodz8.collaborativegrouporder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Collection;
import java.util.Iterator;

/* This activity is started by the MainMenuActivity when the user wants to assemble a new party.
We open a Wi-Fi direct bubble and a proper service and listen to network to find users joining.
 */
/** todo: this is an excellent moment to provide some ads: after the GM started scanning
 * he has to wait for users to join and I can push to him whatever I want.  */
public class NetworkListeningActivity extends AppCompatActivity implements NsdManager.RegistrationListener {
    private ServerSocket landing;
    private NsdManager nsdService;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_network_listening);
    }
    protected void onDestroy() {
        nsdService.unregisterService(this);
        super.onDestroy();
    }

    @Override
    public void onResume() {
        // regen service if I decide to tear it down.
        super.onResume();
    }

    @Override
    public void onPause() {
        // nsdService.unregisterService(this);
        // I don't do this. I really want the service to stay on.
        super.onPause();
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

        try {
            landing = new ServerSocket(0);
        } catch (IOException e) {
            AlertDialog.Builder build = new AlertDialog.Builder(this);
            build.setTitle(R.string.serverSocketFailed_title)
                    .setMessage(R.string.serverSocketFailed_msg);
            build.show();
            return;
        }

        nsdService = (NsdManager)getSystemService(Context.NSD_SERVICE);
        if(nsdService == null) {
            AlertDialog.Builder build = new AlertDialog.Builder(this);
            build.setTitle(R.string.nullNSDService_title)
                    .setMessage(R.string.nullNSDService_msg);
            build.show();
        }

        final NetworkListeningActivity self = this;
        serviceRegistrationHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                switch(msg.what) {
                    case MSG_SERVICE_REGISTRATION_COMPLETE:
                        ServiceRegistrationResult res = (ServiceRegistrationResult)msg.obj;
                        if(res == null) res = new ServiceRegistrationResult(-1); // impossible by construction... for now.
                        if(res.successful) {
                            if(!res.netName.equals(gname)) {
                                TextView note = (TextView)findViewById(R.id.txt_renamedService);
                                String text = getString(R.string.renamedGroup);
                                note.setText(String.format(text, res.netName));
                                note.setVisibility(View.VISIBLE);
                            }

                            findViewById(R.id.txt_scanning).setVisibility(View.VISIBLE);
                            findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
                            findViewById(R.id.list_characters).setVisibility(View.VISIBLE);
                            View closeGroupBtn = findViewById(R.id.btn_closeGroup);
                            closeGroupBtn.setVisibility(View.VISIBLE);
                            closeGroupBtn.setEnabled(false);
                        }
                        else {
                            AlertDialog.Builder build = new AlertDialog.Builder(self);
                            String readable;
                            switch(res.error) {
                                case NsdManager.FAILURE_ALREADY_ACTIVE: readable = "FAILURE_ALREADY_ACTIVE"; break;
                                case NsdManager.FAILURE_INTERNAL_ERROR: readable = "FAILURE_INTERNAL_ERROR"; break;
                                case NsdManager.FAILURE_MAX_LIMIT: readable = "FAILURE_MAX_LIMIT"; break;
                                default: readable = String.format("%1$d", res.error);
                            }
                            build.setTitle(R.string.serviceRegFailed_title)
                                    .setMessage(String.format(getString(R.string.serviceRegFailed_msg), readable));
                            build.show();
                        }
                        return true;
                }
                return false;
            }
        });
        NsdServiceInfo servInfo  = new NsdServiceInfo();
        servInfo.setServiceName(gname);
        servInfo.setServiceType("_groupInitiative._tcp");
        servInfo.setPort(landing.getLocalPort());
        nsdService.registerService(servInfo, NsdManager.PROTOCOL_DNS_SD, this);

        view.setEnabled(false);
        groupNameView.setEnabled(false);
    }


    private void disableButtons() {
        findViewById(R.id.btn_closeGroup).setEnabled(false);
        findViewById(R.id.progressBar).setEnabled(false);
        findViewById(R.id.progressBar).setVisibility(View.INVISIBLE);
        findViewById(R.id.list_characters).setEnabled(false);
    }

    // NsdManager.RegistrationListener() is async AND on a different thread so I cannot just
    // modify the various controls from there. Instead, wait for success/fail and then
    // pass a notification to the UI thread.
    public static final int MSG_SERVICE_REGISTRATION_COMPLETE = 1;
    class ServiceRegistrationResult {
        public ServiceRegistrationResult(String netName) {
            this.netName = netName;
            successful = true;
        }
        public ServiceRegistrationResult(int err) {
            error = err;
            successful = false;
        }

        public int error;
        public boolean successful;
        public String netName;
    }
    Handler serviceRegistrationHandler;

    //
    // NsdManager.RegistrationListener() ___________________________________________________________
    @Override
    public void onServiceRegistered(NsdServiceInfo info) {
        final String netName = info.getServiceName();
        Message msg = Message.obtain(serviceRegistrationHandler, MSG_SERVICE_REGISTRATION_COMPLETE, new ServiceRegistrationResult(netName));
        serviceRegistrationHandler.sendMessage(msg);
    }

    @Override
    public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
        Message msg = Message.obtain(serviceRegistrationHandler, MSG_SERVICE_REGISTRATION_COMPLETE, new ServiceRegistrationResult(errorCode));
        serviceRegistrationHandler.sendMessage(msg);
    }

    @Override
    public void onServiceUnregistered(NsdServiceInfo arg0) { }

    @Override
    public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) { }
}

package com.massimodz8.collaborativegrouporder;

import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

public class MainMenuActivity extends AppCompatActivity {
    public static final int REALLY_BAD_EXIT_REASON_INCOHERENT_CODE = -1;
    public static final String GROUP_FORMING_SERVICE_TYPE = "_formingGroupInitiative._tcp";
    public static final int NETWORK_VERSION = 1;

    private CrossActivityService.Binder binder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_menu);

        Intent sharing = new Intent(this, CrossActivityService.class);
        if(!bindService(sharing, serviceConn, BIND_AUTO_CREATE)) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.mainMenuActivity_couldNotBindInternalService)
                    .setCancelable(false)
                    .setPositiveButton(R.string.mainMenuActivity_couldNotBindExit, new DialogInterface.OnClickListener() {
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
        if(binder != null) {
            binder = null;
            unbindService(serviceConn);
        }
        super.onDestroy();
    }

    public void startCreateParty_callback(View btn) {
        new AlertDialog.Builder(this)
                .setTitle("Not implemented!")
                .setMessage("Create new party!")
                .show();


        //Intent go = new Intent(this, CreatePartyActivity.class);
        //startActivityForResult(go, GROUP_CREATED);
    }

    public void startJoinGroupActivity_callback(View btn) {
        Intent go = new Intent(this, SelectFormingGroupActivity.class);
        startActivityForResult(go, GROUP_JOINED);
    }
    public void startGoAdventuringActivity_callback(View btn) { startGoAdventuringActivity(null, null); }


    private static final int GROUP_CREATED = 1;
    private static final int GROUP_JOINED = 2;


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode) {
            case GROUP_CREATED: {
                if (resultCode != RESULT_OK) return; // RESULT_CANCELLED
                /// TODO: get results using cross-activity storage!
                //if(!data.getBooleanExtra(CreatePartyActivity.RESULT_EXTRA_START_SESSION, false)) return;
                //final String name = data.getStringExtra(CreatePartyActivity.RESULT_EXTRA_CREATED_PARTY_NAME);
                //startNewSessionActivity(name, data.getByteArrayExtra(CreatePartyActivity.RESULT_EXTRA_CREATED_PARTY_KEY));
                break;
            }
            case GROUP_JOINED: {
                if(resultCode != RESULT_OK) return;
                /// TODO: now this gets back by using the cross-activity service!
                //if(!data.getBooleanExtra(JoinGroupActivity.RESULT_EXTRA_GO_ADVENTURING, false)) return;
                //final String name = data.getStringExtra(JoinGroupActivity.RESULT_EXTRA_JOINED_PARTY_NAME);
                //final byte[] key = data.getByteArrayExtra(JoinGroupActivity.RESULT_EXTRA_JOINED_PARTY_KEY);
                //startGoAdventuringActivity(name, key);
                //break;
            }
        }
    }

    private void startNewSessionActivity(String name, byte[] groupKey) {
        new AlertDialog.Builder(this)
                .setTitle("Not implemented!")
                .setMessage("new session!")
                .show();
    }

    void startGoAdventuringActivity(String autojoin, byte[] key) {
        new AlertDialog.Builder(this)
                .setTitle("Not implemented!")
                .setMessage("going adventuring!")
                .show();
    }

    ServiceConnection serviceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            binder = (CrossActivityService.Binder) service;
            // I'm not really interested in using the binder, I just keep it around so the service is on.

        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if(binder == null) return; // it's ok, we are shutting down
            new AlertDialog.Builder(MainMenuActivity.this)
                    .setMessage(getString(R.string.mainMenuActivity_lostCrossActivitySharingService))
                    .setCancelable(false)
                    .setPositiveButton(R.string.mainMenuActivity_couldNotBindExit, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .show();
        }
    };
}

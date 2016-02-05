package com.massimodz8.collaborativegrouporder;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Vector;

public class MainMenuActivity extends AppCompatActivity {
    public static final int REALLY_BAD_EXIT_REASON_INCOHERENT_CODE = -1;
    public static final String GROUP_FORMING_SERVICE_TYPE = "_formingGroupInitiative._tcp";
    public static final int NETWORK_VERSION = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_menu);

        new AsyncTask<Void, Void, Exception>() {
            PersistentStorage.PartyOwnerData loaded;
            final PersistentDataUtils loader = new PersistentDataUtils() {
                @Override
                protected String getString(int resource) {
                    return MainMenuActivity.this.getString(resource);
                }
            };

            @Override
            protected Exception doInBackground(Void... params) {
                PersistentStorage.PartyOwnerData pull = new PersistentStorage.PartyOwnerData();
                File source = new File(PersistentDataUtils.DEFAULT_GROUP_DATA_FILE_NAME);
                if(source.exists()) loader.mergeExistingGroupData(pull, source);
                else pull.version = PersistentDataUtils.OWNER_DATA_VERSION;
                loaded = pull;
                return null;
            }

            @Override
            protected void onPostExecute(Exception e) {
                if(null != e) {
                    new AlertDialog.Builder(MainMenuActivity.this)
                            .setMessage(R.string.mma_failedOwnedPartyLoad)
                            .setPositiveButton(R.string.mma_exitApp, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    finish();
                                }
                            })
                            .show();
                    return;
                }
                if(PersistentDataUtils.OWNER_DATA_VERSION != loaded.version) upgrade(loaded);
                setGroups(loaded, loader);
            }
        }.execute();
    }

    private void setGroups(PersistentStorage.PartyOwnerData loaded, PersistentDataUtils loader) {
        final ArrayList<String> errors = loader.validateLoadedDefinitions(loaded);
        if(null != errors) {
            StringBuilder sb = new StringBuilder();
            for(String str : errors) sb.append("\n").append(str);
            new AlertDialog.Builder(this)
                    .setMessage(String.format(getString(R.string.mma_invalidPartyOwnerLoadedData), sb.toString()))
                    .show();
            return;
        }
        final CrossActivityShare state = (CrossActivityShare) getApplicationContext();
        state.groups = new Vector<>();
        Collections.addAll(state.groups, loaded.everything);
        findViewById(R.id.mma_newParty).setEnabled(true);
    }

    /// Called when party owner data loaded version != from current.
    private void upgrade(PersistentStorage.PartyOwnerData loaded) {
        new AlertDialog.Builder(this)
                .setMessage(String.format(getString(R.string.mma_noUpgradeAvailable), loaded.version, PersistentDataUtils.OWNER_DATA_VERSION))
                .show();
    }

    public void startCreateParty_callback(View btn) {
        Intent go = new Intent(this, NewPartyDeviceSelectionActivity.class);
        startActivityForResult(go, GROUP_CREATED);
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
}

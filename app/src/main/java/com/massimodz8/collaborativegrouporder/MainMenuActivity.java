package com.massimodz8.collaborativegrouporder;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import com.massimodz8.collaborativegrouporder.networkio.Pumper;
import com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Vector;

public class MainMenuActivity extends AppCompatActivity {
    public static final String GROUP_FORMING_SERVICE_TYPE = "_formingGroupInitiative._tcp";
    public static final int NETWORK_VERSION = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_menu);

        new AsyncLoadAll().execute();
    }

    @Override
    protected void onResume() {
        super.onResume();
        final CrossActivityShare state = (CrossActivityShare) getApplicationContext();
        if(null == state.newGroupName) return;
        final String newName = state.newGroupName;
        final byte[] newKey = state.newGroupKey;
        final Pumper.MessagePumpingThread[] peers = state.pumpers;
        state.newGroupName = null;
        state.newGroupKey = null;
        state.pumpers = null;
        new AsyncLoadAll() {
            @Override
            protected void onSuccessfullyRefreshed() {
                if(null == peers) return;
                // go adventuring, but am I client or server?
                for(PersistentStorage.PartyOwnerData.Group check : state.groupDefs) {
                    if(Arrays.equals(newKey, check.salt) && newName.equals(check.name)) {
                        startGoAdventuringActivity(newName, newKey, peers);
                        return;
                    }
                }
                for(PersistentStorage.PartyClientData.Group check : state.groupKeys) {
                    if(Arrays.equals(newKey, check.key) && newName.equals(check.name)) {
                        startNewSessionActivity(newName, newKey, peers);
                        return;
                    }
                }
                int errors = 0;
                for(Pumper.MessagePumpingThread worker : peers) {
                    worker.interrupt();
                    try {
                        worker.getSource().socket.close();
                    } catch (IOException e) {
                        errors++;
                    }
                }
                String ohno = "";
                if(0 != errors) ohno = ' ' + String.format(getString(R.string.mma_failedMatchErroReport), errors);
                new AlertDialog.Builder(MainMenuActivity.this)
                        .setTitle(R.string.mma_impossible)
                        .setMessage(String.format(getString(R.string.mma_failedMatch), ohno))
                        .setCancelable(false)
                        .setPositiveButton(R.string.mma_exit, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }
                        }).show();
            }
        }.execute();
    }

    private void setGroups(PersistentStorage.PartyOwnerData owned, PersistentStorage.PartyClientData joined, PersistentDataUtils loader) {
        final ArrayList<String> errors = loader.validateLoadedDefinitions(owned);
        if(null != errors) {
            StringBuilder sb = new StringBuilder();
            for(String str : errors) sb.append("\n").append(str);
            new AlertDialog.Builder(this)
                    .setMessage(String.format(getString(R.string.mma_invalidPartyOwnerLoadedData), sb.toString()))
                    .show();
            return;
        }
        final CrossActivityShare state = (CrossActivityShare) getApplicationContext();
        state.groupDefs = new Vector<>();
        state.groupKeys = new Vector<>();
        Collections.addAll(state.groupDefs, owned.everything);
        Collections.addAll(state.groupKeys, joined.everything);
        ViewUtils.setEnabled(this, true,
                R.id.mma_newParty,
                R.id.mma_joinParty,
                R.id.mma_goAdventuring);
    }

    /// Called when party owner data loaded version != from current.
    private void upgrade(PersistentStorage.PartyOwnerData loaded) {
        new AlertDialog.Builder(this)
                .setMessage(String.format(getString(R.string.mma_noOwnerDataUpgradeAvailable), loaded.version, PersistentDataUtils.OWNER_DATA_VERSION))
                .show();
    }

    private void upgrade(PersistentStorage.PartyClientData loaded) {
        new AlertDialog.Builder(this)
                .setMessage(String.format(getString(R.string.mma_noClientDataUpgradeAvailable), loaded.version, PersistentDataUtils.OWNER_DATA_VERSION))
                .show();
    }

    public void startCreateParty_callback(View btn) {
        startActivity(new Intent(this, NewPartyDeviceSelectionActivity.class));
    }

    public void startJoinGroupActivity_callback(View btn) {
        startActivity(new Intent(this, SelectFormingGroupActivity.class));
    }

    public void startGoAdventuringActivity_callback(View btn) {
        new AlertDialog.Builder(this)
                .setTitle("Not implemented!")
                .setMessage("party selection activity!")
                .show();
    }


    private void startNewSessionActivity(String name, byte[] groupKey, Pumper.MessagePumpingThread[] workers) {
        new AlertDialog.Builder(this)
                .setTitle("Not implemented!")
                .setMessage("new session!")
                .show();
    }

    void startGoAdventuringActivity(String autojoin, byte[] key, Pumper.MessagePumpingThread[] workers) {
        new AlertDialog.Builder(this)
                .setTitle("Not implemented!")
                .setMessage("going adventuring!")
                .show();
    }

    private class AsyncLoadAll extends AsyncTask<Void, Void, Exception> {
        PersistentStorage.PartyOwnerData owned;
        PersistentStorage.PartyClientData joined;
        final PersistentDataUtils loader = new PersistentDataUtils() {
            @Override
            protected String getString(int resource) {
                return MainMenuActivity.this.getString(resource);
            }
        };

        @Override
        protected Exception doInBackground(Void... params) {
            PersistentStorage.PartyOwnerData pullo = new PersistentStorage.PartyOwnerData();
            File srco = new File(PersistentDataUtils.DEFAULT_GROUP_DATA_FILE_NAME);
            if(srco.exists()) loader.mergeExistingGroupData(pullo, srco);
            else pullo.version = PersistentDataUtils.OWNER_DATA_VERSION;
            owned = pullo;

            PersistentStorage.PartyClientData pullk = new PersistentStorage.PartyClientData();
            File srck = new File(PersistentDataUtils.DEFAULT_KEY_FILE_NAME);
            if(srck.exists()) loader.mergeExistingGroupData(pullk, srck);
            else pullk.version = PersistentDataUtils.CLIENT_DATA_WRITE_VERSION;
            joined = pullk;

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
            }
            else {
                if(PersistentDataUtils.OWNER_DATA_VERSION != owned.version) upgrade(owned);
                if(PersistentDataUtils.CLIENT_DATA_WRITE_VERSION != joined.version) upgrade(joined);
                setGroups(owned, joined, loader);
                onSuccessfullyRefreshed();
            }
        }
        protected void onSuccessfullyRefreshed() { }
    }
}

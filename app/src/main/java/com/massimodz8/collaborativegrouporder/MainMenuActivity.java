package com.massimodz8.collaborativegrouporder;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import com.massimodz8.collaborativegrouporder.client.CharSelectionActivity;
import com.massimodz8.collaborativegrouporder.networkio.Pumper;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;
import com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Vector;

public class MainMenuActivity extends AppCompatActivity {
    public static final String GROUP_FORMING_SERVICE_TYPE = "_formingGroupInitiative._tcp";
    public static final String PARTY_GOING_ADVENTURING_SERVICE_TYPE = "_partyInitiative._tcp";
    public static final int NETWORK_VERSION = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_menu);

        new AsyncLoadAll().execute();
    }

    private void refreshData() {
        final CrossActivityShare state = (CrossActivityShare) getApplicationContext();
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
                        startNewSessionActivity(check, peers);
                        return;
                    }
                }
                for(PersistentStorage.PartyClientData.Group check : state.groupKeys) {
                    if(Arrays.equals(newKey, check.key) && newName.equals(check.name)) {
                        startGoAdventuringActivity(check, peers[0]);
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
        MaxUtils.setEnabled(this, true,
                R.id.mma_newParty,
                R.id.mma_joinParty);
        findViewById(R.id.mma_goAdventuring).setEnabled(state.groupDefs.size() + state.groupKeys.size() > 0);
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
        startActivityForResult(new Intent(this, NewPartyDeviceSelectionActivity.class), REQUEST_NEW_PARTY);
    }

    public void startJoinGroupActivity_callback(View btn) {
        startActivityForResult(new Intent(this, SelectFormingGroupActivity.class), REQUEST_JOIN_FORMING);
    }

    public void pickParty_callback(View btn) {
        startActivityForResult(new Intent(this, PartyPickActivity.class), REQUEST_PICK_PARTY);
    }


    private void startNewSessionActivity(PersistentStorage.PartyOwnerData.Group party, Pumper.MessagePumpingThread[] workers) {
        final CrossActivityShare state = (CrossActivityShare) getApplicationContext();
        state.pumpers = workers;
        state.gaState = new GatheringActivity.State(party);
        startActivity(new Intent(this, GatheringActivity.class));
    }

    void startGoAdventuringActivity(PersistentStorage.PartyClientData.Group party, Pumper.MessagePumpingThread worker) {
        final CrossActivityShare state = (CrossActivityShare) getApplicationContext();
        if(null != worker) state.pumpers = new Pumper.MessagePumpingThread[] { worker };
        else state.pumpers = null; // be safe-r. Sort of.
        state.jsaState = new JoinSessionActivity.State(party);
        startActivityForResult(new Intent(this, JoinSessionActivity.class), REQUEST_PULL_CHAR_LIST);
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
            File srco = new File(getFilesDir(), PersistentDataUtils.DEFAULT_GROUP_DATA_FILE_NAME);
            if(srco.exists()) loader.mergeExistingGroupData(pullo, srco);
            else pullo.version = PersistentDataUtils.OWNER_DATA_VERSION;

            PersistentStorage.PartyClientData pullk = new PersistentStorage.PartyClientData();
            File srck = new File(getFilesDir(), PersistentDataUtils.DEFAULT_KEY_FILE_NAME);
            if(srck.exists()) loader.mergeExistingGroupData(pullk, srck);
            else pullk.version = PersistentDataUtils.CLIENT_DATA_WRITE_VERSION;

            owned = pullo;
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(RESULT_OK != resultCode) return;
        switch(requestCode) {
            case REQUEST_NEW_PARTY: {
                final Intent intent = new Intent(this, NewCharactersApprovalActivity.class);
                startActivityForResult(intent, REQUEST_APPROVE_CHARACTERS);
            } break;
            case REQUEST_JOIN_FORMING: {
                final Intent intent = new Intent(this, NewCharactersProposalActivity.class);
                startActivityForResult(intent, REQUEST_PROPOSE_CHARACTERS);
            } break;
            case REQUEST_APPROVE_CHARACTERS:
            case REQUEST_PROPOSE_CHARACTERS: {
                refreshData();
            } break;
            case REQUEST_PICK_PARTY: {
                int linear = data.getIntExtra(PartyPickActivity.EXTRA_PARTY_INDEX, -1);
                boolean owned = data.getBooleanExtra(PartyPickActivity.EXTRA_TRUE_IF_PARTY_OWNED, false);
                if(linear < 0) return;
                final CrossActivityShare state = (CrossActivityShare) getApplicationContext();
                if(owned) {
                    startNewSessionActivity(state.groupDefs.elementAt(linear), null);
                }
                else {
                    final PersistentStorage.PartyClientData.Group party = state.groupKeys.elementAt(linear);
                    startGoAdventuringActivity(party, null);
                }
            } break;
            case REQUEST_PULL_CHAR_LIST: {
                final CrossActivityShare state = (CrossActivityShare) getApplicationContext();
                CharSelectionActivity.prepare(state.jsaResult.worker, state.jsaResult.party, state.jsaResult.first);
                state.jsaResult = null;
                startActivityForResult(new Intent(this, CharSelectionActivity.class), REQUEST_BIND_CHARACTERS);
            } break;
            case REQUEST_BIND_CHARACTERS: {
                final PersistentStorage.PartyClientData.Group party = CharSelectionActivity.movePlayingParty();
                final ArrayList<Network.PlayingCharacterDefinition> here = CharSelectionActivity.movePlayChars();
                final Pumper.MessagePumpingThread worker = CharSelectionActivity.moveServerWorker();
                            worker.interrupt();
                String s = party.name + " > ";
                for(Network.PlayingCharacterDefinition pc : here) s += pc.name + " > ";
                new AlertDialog.Builder(this).setMessage(s).show();
            } break;
        }
    }

    static final int REQUEST_NEW_PARTY = 1;
    static final int REQUEST_JOIN_FORMING = 2;
    static final int REQUEST_APPROVE_CHARACTERS = 3;
    static final int REQUEST_PROPOSE_CHARACTERS = 4;
    static final int REQUEST_PICK_PARTY = 5;
    static final int REQUEST_PULL_CHAR_LIST = 6;
    static final int REQUEST_BIND_CHARACTERS = 7;
}

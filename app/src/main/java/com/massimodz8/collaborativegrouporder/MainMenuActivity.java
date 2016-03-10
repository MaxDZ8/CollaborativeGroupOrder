package com.massimodz8.collaborativegrouporder;

import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import com.google.protobuf.nano.MessageNano;
import com.massimodz8.collaborativegrouporder.client.CharSelectionActivity;
import com.massimodz8.collaborativegrouporder.master.GatheringActivity;
import com.massimodz8.collaborativegrouporder.master.NewCharactersApprovalActivity;
import com.massimodz8.collaborativegrouporder.master.NewPartyDeviceSelectionActivity;
import com.massimodz8.collaborativegrouporder.master.PartyCreationService;
import com.massimodz8.collaborativegrouporder.master.PartyJoinOrderService;
import com.massimodz8.collaborativegrouporder.master.PcAssignmentHelper;
import com.massimodz8.collaborativegrouporder.networkio.Pumper;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;
import com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;

public class MainMenuActivity extends AppCompatActivity implements ServiceConnection {
    public static final int NETWORK_VERSION = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_menu);

        try {
            MaxUtils.hasher = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            new AlertDialog.Builder(this)
                    .setCancelable(false)
                    .setMessage(R.string.mma_failedToInitHasher)
                    .setPositiveButton("Quit", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .show();
            return;
        }

        new AsyncLoadAll().execute();
    }

    private void dataRefreshed() {
        findViewById(R.id.mma_goAdventuring).setEnabled(groupDefs.size() + groupKeys.size() > 0);
        if (null == activeConnections) {
            asyncCloseSockets(null, activeLanding, null);
            activeLanding = null;
            activeParty = null;
            return;
        }
        if (activeParty instanceof PersistentStorage.PartyOwnerData.Group) {
            startNewSessionActivity();
            return;
        }
        if (activeParty instanceof PersistentStorage.PartyClientData.Group) {
            startGoAdventuringActivity();
            return;
        }
        asyncCloseSockets(activeConnections, activeLanding, new ErrorFeedbackFunc() {
            @Override
            public void feedback(int errors) {
                String ohno = "";
                if (0 != errors)
                    ohno = ' ' + String.format(getString(R.string.mma_failedMatchErroReport), errors);
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
        });
        activeConnections = null;
        activeLanding = null;
        activeParty = null;
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
        final Intent servName = new Intent(this, PartyCreationService.class);
        startService(servName); // this service spans device selection and character approval activities.
        if(!bindService(servName, this, 0)) {
            stopService(servName);
            new AlertDialog.Builder(this)
                    .setMessage(R.string.mma_failedNewSessionServiceBind)
                    .show();
        }
    }

    public void startJoinGroupActivity_callback(View btn) {
        startActivityForResult(new Intent(this, SelectFormingGroupActivity.class), REQUEST_JOIN_FORMING);
    }

    public void pickParty_callback(View btn) {
        PartyPickActivity.ioDefs = groupDefs;
        PartyPickActivity.ioKeys = groupKeys;
        startActivityForResult(new Intent(this, PartyPickActivity.class), REQUEST_PICK_PARTY);
    }


    private void startNewSessionActivity() {
        final Intent servName = new Intent(this, PartyJoinOrderService.class);
        startService(servName);
        if(!bindService(servName, this, 0)) {
            stopService(servName);
            new AlertDialog.Builder(this)
                    .setMessage(R.string.mma_failedNewSessionServiceBind)
                    .show();
            activeParty = null;
            asyncCloseSockets(activeConnections, activeLanding, null);
            activeConnections = null;
            activeLanding = null;
        }
    }

    void startGoAdventuringActivity() {
        final CrossActivityShare state = (CrossActivityShare) getApplicationContext();
        if(null != activeConnections && activeConnections.length > 0) state.pumpers = new Pumper.MessagePumpingThread[] { activeConnections[0] };
        else state.pumpers = null; // be safe-r. Sort of.
        activeConnections = null;
        // activeLanding is unused in client
        state.jsaState = new JoinSessionActivity.State((PersistentStorage.PartyClientData.Group) activeParty);
        activeParty = null;
        startActivityForResult(new Intent(this, JoinSessionActivity.class), REQUEST_PULL_CHAR_LIST);
    }


    private class AsyncLoadAll extends AsyncTask<Void, Void, Exception> {
        PersistentStorage.PartyOwnerData owned;
        PersistentStorage.PartyClientData joined;
        final PersistentDataUtils loader = new PersistentDataUtils(PcAssignmentHelper.DOORMAT_BYTES) {
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
                // TODO: if the activity has been rotated in the meanwhile, this will have issues. Usual problem with AsyncTask reporting.
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
            if(PersistentDataUtils.OWNER_DATA_VERSION != owned.version) upgrade(owned);
            if(PersistentDataUtils.CLIENT_DATA_WRITE_VERSION != joined.version) upgrade(joined);
            final ArrayList<String> errors = loader.validateLoadedDefinitions(owned);
            if(null != errors) {
                StringBuilder sb = new StringBuilder();
                for(String str : errors) sb.append("\n").append(str);
                new AlertDialog.Builder(MainMenuActivity.this)
                        .setMessage(String.format(getString(R.string.mma_invalidPartyOwnerLoadedData), sb.toString()))
                        .show();
                return;
            }
            Collections.addAll(groupDefs, owned.everything);
            Collections.addAll(groupKeys, joined.everything);
            MaxUtils.setEnabled(MainMenuActivity.this, true,
                    R.id.mma_newParty,
                    R.id.mma_joinParty);
            findViewById(R.id.mma_goAdventuring).setEnabled(groupDefs.size() + groupKeys.size() > 0);
            onSuccessfullyRefreshed();
        }
        protected void onSuccessfullyRefreshed() { }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode) { // stuff to shut down no matter what
            case REQUEST_NEW_SESSION:
                // Does not produce output.
                stopService(new Intent(this, PartyJoinOrderService.class));
                break;
            case REQUEST_PICK_PARTY: { // sync our data with what was produced
                groupDefs = PartyPickActivity.ioDefs;
                groupKeys = PartyPickActivity.ioKeys;
                break;
            }
        }
        if(RESULT_OK != resultCode) {
            switch(requestCode) { // stuff would be used on success... but was not successful so goodbye
                case REQUEST_NEW_PARTY:
                    unbindService(this);
                    stopService(new Intent(this, PartyCreationService.class));
                    break;
            }
            return;
        }
        switch(requestCode) {
            case REQUEST_NEW_PARTY: {
                boolean goAdventuringWithCreated = data.getBooleanExtra(NewCharactersApprovalActivity.RESULT_EXTRA_GO_ADVENTURING, false);
                groupDefs = pcServ.defs;
                if(goAdventuringWithCreated) {
                    activeParty = pcServ.generatedParty;
                    activeLanding = pcServ.getLanding(true);
                    activeConnections = pcServ.moveClients();
                }
                pcServ = null;
                unbindService(this);
                stopService(new Intent(this, PartyCreationService.class));
                dataRefreshed();
                break;
            }
            case REQUEST_JOIN_FORMING: {
                final Intent intent = new Intent(this, NewCharactersProposalActivity.class);
                startActivityForResult(intent, REQUEST_PROPOSE_CHARACTERS);
            } break;
            case REQUEST_PROPOSE_CHARACTERS: {
                final CrossActivityShare state = (CrossActivityShare) getApplicationContext();
                if(state.pumpers != null) { // go adventuring
                    activeConnections = state.pumpers;
                    state.pumpers = null;
                }
                groupKeys.add(state.newKey);
                activeParty = state.newKey;
                state.newKey = null;
                dataRefreshed();
            } break;
            case REQUEST_PICK_PARTY: {
                activeConnections = null;
                activeLanding = null;
                groupDefs = PartyPickActivity.ioDefs;
                groupKeys = PartyPickActivity.ioKeys;
                PartyPickActivity.ioDefs = null;
                PartyPickActivity.ioKeys = null;
                dataRefreshed();
                if(PartyPickActivity.goDef != null) {
                    activeParty = PartyPickActivity.goDef;
                    PartyPickActivity.goDef = null;
                    startNewSessionActivity();
                }
                else {
                    activeParty = PartyPickActivity.goKey;
                    PartyPickActivity.goKey = null;
                    startGoAdventuringActivity();
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
    static final int REQUEST_PROPOSE_CHARACTERS = 4;
    static final int REQUEST_PICK_PARTY = 5;
    static final int REQUEST_PULL_CHAR_LIST = 6;
    static final int REQUEST_BIND_CHARACTERS = 7;
    static final int REQUEST_NEW_SESSION = 8;

    // Those must be fields to ensure a communication channel to the asynchronous onServiceConnected callbacks.
    private MessageNano activeParty; // PersistentStorage.PartyOwnerData.Group or PersistentStorage.PartyClientData.Group
    private ServerSocket activeLanding;
    private Pumper.MessagePumpingThread[] activeConnections; // Client: a single connection to a server or Owner: list of connections to client

    // ServiceConnection ___________________________________________________________________________
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        if(service instanceof PartyJoinOrderService.LocalBinder) {
            PartyJoinOrderService.LocalBinder binder = (PartyJoinOrderService.LocalBinder)service;
            PartyJoinOrderService real =  binder.getConcreteService();
            PersistentStorage.PartyOwnerData.Group owned = (PersistentStorage.PartyOwnerData.Group) activeParty;
            JoinVerificator keyMaster = new JoinVerificator(owned.devices, MaxUtils.hasher);
            real.initializePartyManagement(owned, keyMaster);
            real.pumpClients(activeConnections);
            // TODO: reuse landing if there!
            if(activeLanding != null) {
                final ServerSocket landing = activeLanding;
                activeLanding = null;
                new AsyncTask<Void, Void, Void>() {
                    @Override
                    protected Void doInBackground(Void... params) {
                        try {
                            landing.close();
                        } catch (IOException e) {
                            // suppress
                        }
                        return null;
                    }
                }.execute();
            }
            activeConnections = null;

            startActivityForResult(new Intent(this, GatheringActivity.class), REQUEST_NEW_SESSION);
            unbindService(this);
        }
        if(service instanceof PartyCreationService.LocalBinder) {
            PartyCreationService.LocalBinder binder = (PartyCreationService.LocalBinder) service;
            pcServ = binder.getConcreteService();
            pcServ.defs = groupDefs;
            final Intent intent = new Intent(this, NewPartyDeviceSelectionActivity.class);
            startActivityForResult(intent, REQUEST_NEW_PARTY);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        // Apparently this is called even when we call unbind, meh!
    }

    // We keep everything that exists in memory. This is a compact representation and makes
    // some things easier as we can compare by reference.
    private ArrayList<PersistentStorage.PartyOwnerData.Group> groupDefs = new ArrayList<>();
    private ArrayList<PersistentStorage.PartyClientData.Group> groupKeys = new ArrayList<>();

    interface ErrorFeedbackFunc {
        void feedback(int errors);
    }

    static private void asyncCloseSockets(@Nullable final Pumper.MessagePumpingThread[] activeConnections, @Nullable final ServerSocket activeLanding,
                                          @Nullable final ErrorFeedbackFunc feedback) {
        new AsyncTask<Void, Void, Integer>() {
            @Override
            protected Integer doInBackground (Void...params){
                int errors = 0;
                if (null != activeConnections) {
                    for (Pumper.MessagePumpingThread worker : activeConnections) {
                        worker.interrupt();
                        try {
                            worker.getSource().socket.close();
                        } catch (IOException e) {
                            errors++;
                        }
                    }
                }
                if (null != activeLanding) {
                    try {
                        activeLanding.close();
                    } catch (IOException e) {
                        errors++;
                    }
                }
                return errors;
            }

            @Override
            protected void onPostExecute(Integer count) {
                if(null != count && feedback != null) feedback.feedback(count);
            }
        }.execute();
    }
    private PartyCreationService pcServ;
}

package com.massimodz8.collaborativegrouporder;

import android.app.Notification;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.AssetManager;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.NotificationCompat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.google.protobuf.nano.CodedInputByteBufferNano;
import com.google.protobuf.nano.MessageNano;
import com.massimodz8.collaborativegrouporder.client.ActorOverviewActivity;
import com.massimodz8.collaborativegrouporder.client.CharSelectionActivity;
import com.massimodz8.collaborativegrouporder.master.FreeRoamingActivity;
import com.massimodz8.collaborativegrouporder.master.GatheringActivity;
import com.massimodz8.collaborativegrouporder.master.NewCharactersApprovalActivity;
import com.massimodz8.collaborativegrouporder.master.NewPartyDeviceSelectionActivity;
import com.massimodz8.collaborativegrouporder.master.PartyCreationService;
import com.massimodz8.collaborativegrouporder.master.PartyJoinOrderService;
import com.massimodz8.collaborativegrouporder.master.PcAssignmentHelper;
import com.massimodz8.collaborativegrouporder.networkio.Pumper;
import com.massimodz8.collaborativegrouporder.protocol.nano.MonsterData;
import com.massimodz8.collaborativegrouporder.protocol.nano.Session;
import com.massimodz8.collaborativegrouporder.protocol.nano.StartData;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;

public class MainMenuActivity extends AppCompatActivity implements ServiceConnection {
    public static final int NETWORK_VERSION = 1;
    private static final int NOTIFICATION_ID = 1234;

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
                    .setPositiveButton(getString(R.string.mma_quit), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .show();
            return;
        }
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                String[] need = {
                        PersistentDataUtils.MAIN_DATA_SUBDIR,
                        PersistentDataUtils.SESSION_DATA_SUBDIR,
                        PersistentDataUtils.USER_CUSTOM_DATA_SUBDIR
                };
                for (String sub : need) {
                    File dir = new File(getFilesDir(), sub);
                    if(dir.exists()) {
                        if(dir.isDirectory()) continue; // :-)
                        return false;
                    }
                    if(!dir.mkdir()) return false;
                }
                return true;
            }

            @Override
            protected void onPostExecute(Boolean success) {
                if(!success) {
                    new AlertDialog.Builder(MainMenuActivity.this)
                            .setTitle(R.string.mma_dirStructDlgInitError_title)
                            .setMessage(R.string.mma_dirStructDlgInitError_message)
                            .setCancelable(false)
                            .setPositiveButton(R.string.generic_quit, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    finish();
                                }
                            })
                            .setNegativeButton(R.string.mma_dirStructDlgNegContinue, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    new AsyncLoadAll().execute();
                                }
                            })
                            .show();
                    return;
                }
                new AsyncLoadAll().execute();
            }
        }.execute();
    }

    private void dataRefreshed() {
        findViewById(R.id.mma_goAdventuring).setEnabled(groupDefs.size() + groupKeys.size() > 0);
        if (null == activeConnections) {
            asyncCloseSockets(null, activeLanding, null);
            activeLanding = null;
            activeParty = null;
            return;
        }
        if (activeParty instanceof StartData.PartyOwnerData.Group) {
            startNewSessionActivity();
            return;
        }
        if (activeParty instanceof StartData.PartyClientData.Group) {
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu_activity, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.mma_ogl: {
                startActivity(new Intent(this, OpenGameLicenseActivity.class));
                break;
            }
        }
        return false;
    }

    /// Called when party owner data loaded version != from current.
    private void upgrade(StartData.PartyOwnerData loaded) {
        new AlertDialog.Builder(this)
                .setMessage(String.format(getString(R.string.mma_noOwnerDataUpgradeAvailable), loaded.version, PersistentDataUtils.OWNER_DATA_VERSION))
                .show();
    }

    private void upgrade(StartData.PartyClientData loaded) {
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
        final Intent servName = new Intent(this, PartyPickingService.class);
        startService(servName);
        if(!bindService(servName, this, 0)) {
            if (!bindService(servName, this, 0)) {
                stopService(servName);
                new AlertDialog.Builder(this)
                        .setMessage(R.string.mma_failedNewSessionServiceBind)
                        .show();
            }
        }
    }

    public void custom_callback(View btn) {
        switch(btn.getId()) {
            case R.id.mma_customMonsters: {
                CustomMonstersActivity.custom = customMonsters;
                startActivity(new Intent(this, CustomMonstersActivity.class));
                break;
            }
            case R.id.mma_preparedBattles: {
                startActivity(new Intent(this, PreparedBattlesActivity.class));
                break;
            }
        }
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
        state.jsaState = new JoinSessionActivity.State((StartData.PartyClientData.Group) activeParty);
        activeParty = null;
        startActivityForResult(new Intent(this, JoinSessionActivity.class), REQUEST_PULL_CHAR_LIST);
    }


    private class AsyncLoadAll extends AsyncTask<Void, Void, Exception> {
        StartData.PartyOwnerData owned;
        StartData.PartyClientData joined;
        MonsterData.MonsterBook monsterBook;
        MonsterData.MonsterBook customMobs;

        final PersistentDataUtils loader = new PersistentDataUtils(PcAssignmentHelper.DOORMAT_BYTES) {
            @Override
            protected String getString(int resource) {
                return MainMenuActivity.this.getString(resource);
            }
        };
        final AssetManager rawRes = MainMenuActivity.this.getAssets();

        @Override
        protected Exception doInBackground(Void... params) {
            StartData.PartyOwnerData pullo = new StartData.PartyOwnerData();
            final File mainDataDir = new File(getFilesDir(), PersistentDataUtils.MAIN_DATA_SUBDIR);
            File srco = new File(mainDataDir, PersistentDataUtils.DEFAULT_GROUP_DATA_FILE_NAME);
            if(srco.exists()) loader.mergeExistingGroupData(pullo, srco);
            else pullo.version = PersistentDataUtils.OWNER_DATA_VERSION;

            StartData.PartyClientData pullk = new StartData.PartyClientData();
            File srck = new File(mainDataDir, PersistentDataUtils.DEFAULT_KEY_FILE_NAME);
            if(srck.exists()) loader.mergeExistingGroupData(pullk, srck);
            else pullk.version = PersistentDataUtils.CLIENT_DATA_WRITE_VERSION;

            MonsterData.MonsterBook pullMon = new MonsterData.MonsterBook();
            final byte[] loadBuff = new byte[128 * 1024];
            try {
                final InputStream srcMon = rawRes.open("monsterData.bin");
                final MaxUtils.TotalLoader loaded = new MaxUtils.TotalLoader(srcMon, loadBuff);
                srcMon.close();
                pullMon.mergeFrom(CodedInputByteBufferNano.newInstance(loaded.fullData, 0, loaded.validBytes));
            } catch (IOException e) {
                // Nightly impossible!
                return e;
            }

            final File customDataDir = new File(getFilesDir(), PersistentDataUtils.USER_CUSTOM_DATA_SUBDIR);
            File cmobs = new File(customDataDir, PersistentDataUtils.CUSTOM_MOBS_FILE_NAME);
            MonsterData.MonsterBook custBook = new MonsterData.MonsterBook();
            try {
                final FileInputStream fis = new FileInputStream(cmobs);
                final MaxUtils.TotalLoader loaded = new MaxUtils.TotalLoader(fis, loadBuff);
                fis.close();
                custBook.mergeFrom(CodedInputByteBufferNano.newInstance(loaded.fullData, 0, loaded.validBytes));
            } catch (FileNotFoundException e) {
                // No problem really. Go ahead.
            } catch (IOException e) {
                return e;
            }

            owned = pullo;
            joined = pullk;
            monsterBook = pullMon;
            customMobs = custBook;

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
            monsters = monsterBook;
            customMonsters = customMobs;
            Collections.addAll(groupDefs, owned.everything);
            Collections.addAll(groupKeys, joined.everything);
            MaxUtils.beginDelayedTransition(MainMenuActivity.this);
            MaxUtils.setEnabled(MainMenuActivity.this, true,
                    R.id.mma_newParty,
                    R.id.mma_joinParty,
                    R.id.mma_customMonsters,
                    R.id.mma_preparedBattles);
            findViewById(R.id.mma_goAdventuring).setEnabled(groupDefs.size() + groupKeys.size() > 0);
            MaxUtils.setVisibility(MainMenuActivity.this, View.GONE, R.id.mma_progress, R.id.mma_waitMessage);
            onSuccessfullyRefreshed();
        }
        protected void onSuccessfullyRefreshed() { }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode) { // stuff to shut down no matter what
            case REQUEST_PICK_PARTY: { // sync our data with what was produced/modified
                groupDefs.clear();
                groupKeys.clear();
                pickServ.getDense(groupDefs, groupKeys, false);
                dataRefreshed();
                break;
            }
            case REQUEST_PLAY: {
                stopService(new Intent(this, PartyJoinOrderService.class));
                return;
            }
        }
        if(RESULT_OK != resultCode) {
            switch(requestCode) { // stuff would be used on success... but was not successful so goodbye
                case REQUEST_NEW_PARTY:
                    pcServ = null;
                    unbindService(this);
                    stopService(new Intent(this, PartyCreationService.class));
                    break;
                case REQUEST_PICK_PARTY: { // sync our data with what was produced/modified
                    pickServ.stopForeground(true);
                    pickServ = null;
                    unbindService(this);
                    stopService(new Intent(this, PartyPickingService.class));
                    break;
                }
                case REQUEST_GATHER_DEVICES:
                    stopService(new Intent(this, PartyJoinOrderService.class));
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
                    activeStats = pcServ.generatedStat;
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
                activeParty = pickServ.sessionParty;
                activeStats = pickServ.sessionData.get(activeParty);
                pickServ.stopForeground(true);
                pickServ = null;
                unbindService(this);
                stopService(new Intent(this, PartyPickingService.class));

                activeConnections = null;
                activeLanding = null;
                if(activeParty instanceof StartData.PartyOwnerData.Group) {
                    startNewSessionActivity();
                }
                else if(activeParty instanceof StartData.PartyClientData.Group) {
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
                ActorOverviewActivity.prepare(CharSelectionActivity.movePlayingParty(),
                        CharSelectionActivity.movePlayChars(),
                        CharSelectionActivity.moveServerWorker());
                startActivity(new Intent(this, ActorOverviewActivity.class));
            } break;
            case REQUEST_GATHER_DEVICES: {
                startActivityForResult(new Intent(this, FreeRoamingActivity.class), REQUEST_PLAY);
                break;
            }
        }
    }

    static final int REQUEST_NEW_PARTY = 1;
    static final int REQUEST_JOIN_FORMING = 2;
    static final int REQUEST_PROPOSE_CHARACTERS = 4;
    static final int REQUEST_PICK_PARTY = 5;
    static final int REQUEST_PULL_CHAR_LIST = 6;
    static final int REQUEST_BIND_CHARACTERS = 7;
    static final int REQUEST_GATHER_DEVICES = 8;
    static final int REQUEST_PLAY = 9;

    // Those must be fields to ensure a communication channel to the asynchronous onServiceConnected callbacks.
    private MessageNano activeParty; // StartData.PartyOwnerData.Group or StartData.PartyClientData.Group
    private Session.Suspended activeStats;
    private ServerSocket activeLanding;
    private Pumper.MessagePumpingThread[] activeConnections; // Client: a single connection to a server or Owner: list of connections to client

    // ServiceConnection ___________________________________________________________________________
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        if(service instanceof PartyJoinOrderService.LocalBinder) {
            PartyJoinOrderService.LocalBinder binder = (PartyJoinOrderService.LocalBinder)service;
            PartyJoinOrderService real =  binder.getConcreteService();
            real.allOwnedGroups = groupDefs;
            StartData.PartyOwnerData.Group owned = (StartData.PartyOwnerData.Group) activeParty;
            JoinVerificator keyMaster = new JoinVerificator(owned.devices, MaxUtils.hasher);
            real.initializePartyManagement(owned, activeStats, keyMaster, monsters);
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
            activeParty = null;
            activeStats = null;
            startActivityForResult(new Intent(this, GatheringActivity.class), REQUEST_GATHER_DEVICES);
            unbindService(this);
        }
        if(service instanceof PartyCreationService.LocalBinder) {
            PartyCreationService.LocalBinder binder = (PartyCreationService.LocalBinder) service;
            pcServ = binder.getConcreteService();
            pcServ.defs = groupDefs;
            final Intent intent = new Intent(this, NewPartyDeviceSelectionActivity.class);
            startActivityForResult(intent, REQUEST_NEW_PARTY);
        }
        if(service instanceof PartyPickingService.LocalBinder) {
            final PartyPickingService.LocalBinder binder = (PartyPickingService.LocalBinder) service;
            pickServ = binder.getConcreteService();
            pickServ.setKnownParties(groupDefs, groupKeys);
            startActivityForResult(new Intent(this, PartyPickActivity.class), REQUEST_PICK_PARTY);
            // The pick party server is different, looks like I can just pull it up now.
            final android.support.v4.app.NotificationCompat.Builder help = new NotificationCompat.Builder(this)
                    .setOngoing(true)
                    .setWhen(System.currentTimeMillis())
                    .setContentTitle(getString(R.string.mma_partyManagerNotificationTitle))
                    .setSmallIcon(R.drawable.ic_notify_icon)
                    .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.placeholder_todo));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                help.setCategory(Notification.CATEGORY_SERVICE);
            }
            pickServ.startForeground(NOTIFICATION_ID, help.build());
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        // Apparently this is called even when we call unbind, meh!
    }

    // We keep everything that exists in memory. This is a compact representation and makes
    // some things easier as we can compare by reference.
    private ArrayList<StartData.PartyOwnerData.Group> groupDefs = new ArrayList<>();
    private ArrayList<StartData.PartyClientData.Group> groupKeys = new ArrayList<>();
    private MonsterData.MonsterBook monsters, customMonsters;

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
    private PartyPickingService pickServ;
}

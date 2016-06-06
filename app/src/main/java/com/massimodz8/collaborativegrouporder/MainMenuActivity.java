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
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.NotificationCompat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.protobuf.nano.CodedInputByteBufferNano;
import com.google.protobuf.nano.MessageNano;
import com.massimodz8.collaborativegrouporder.client.ActorOverviewActivity;
import com.massimodz8.collaborativegrouporder.client.AdventuringService;
import com.massimodz8.collaborativegrouporder.client.CharSelectionActivity;
import com.massimodz8.collaborativegrouporder.master.FreeRoamingActivity;
import com.massimodz8.collaborativegrouporder.master.GatheringActivity;
import com.massimodz8.collaborativegrouporder.master.NewCharactersApprovalActivity;
import com.massimodz8.collaborativegrouporder.master.NewPartyDeviceSelectionActivity;
import com.massimodz8.collaborativegrouporder.master.PartyCreationService;
import com.massimodz8.collaborativegrouporder.master.PartyJoinOrderService;
import com.massimodz8.collaborativegrouporder.master.PcAssignmentHelper;
import com.massimodz8.collaborativegrouporder.master.SpawnMonsterActivity;
import com.massimodz8.collaborativegrouporder.networkio.Pumper;
import com.massimodz8.collaborativegrouporder.protocol.nano.MonsterData;
import com.massimodz8.collaborativegrouporder.protocol.nano.PreparedEncounters;
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
    private static final int PJOS_NOTIFICATION_ID = 123, PPS_NOTIFICATION_ID = 456, AS_NOTIFICATION_ID = 789;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_menu);

        try {
            MaxUtils.hasher = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            new AlertDialog.Builder(this, R.style.AppDialogStyle)
                    .setCancelable(false)
                    .setMessage(R.string.mma_failedToInitHasher)
                    .setPositiveButton(getString(R.string.generic_quit), new DialogInterface.OnClickListener() {
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
                    new AlertDialog.Builder(MainMenuActivity.this, R.style.AppDialogStyle)
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

        FirebaseAnalytics.getInstance(this); // not really relevant, it's for initialization!
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
                new AlertDialog.Builder(MainMenuActivity.this, R.style.AppDialogStyle)
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
                return true;
            }
            case R.id.mma_about: {
                startActivity(new Intent(this, AboutActivity.class));
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    /// Called when party owner data loaded version != from current.
    private void upgrade(StartData.PartyOwnerData loaded) {
        new AlertDialog.Builder(this, R.style.AppDialogStyle)
                .setMessage(String.format(getString(R.string.mma_noOwnerDataUpgradeAvailable), loaded.version, PersistentDataUtils.OWNER_DATA_VERSION))
                .show();
    }

    private void upgrade(StartData.PartyClientData loaded) {
        new AlertDialog.Builder(this, R.style.AppDialogStyle)
                .setMessage(String.format(getString(R.string.mma_noClientDataUpgradeAvailable), loaded.version, PersistentDataUtils.OWNER_DATA_VERSION))
                .show();
    }

    public void startCreateParty_callback(View btn) {
        final Intent servName = new Intent(this, PartyCreationService.class);
        startService(servName); // this service spans device selection and character approval activities.
        if(!bindService(servName, this, 0)) {
            stopService(servName);
            new AlertDialog.Builder(this, R.style.AppDialogStyle)
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
                stopService(servName);
                new AlertDialog.Builder(this, R.style.AppDialogStyle)
                        .setMessage(R.string.mma_failedNewSessionServiceBind)
                        .show();
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
                PreparedBattlesActivity.custom = customBattles;
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
            new AlertDialog.Builder(this, R.style.AppDialogStyle)
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
        PreparedEncounters.Collection custBattles;

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

            File cbattles = new File(customDataDir, PersistentDataUtils.CUSTOM_ENCOUNTERS_FILE_NAME);
            PreparedEncounters.Collection allBattles = new PreparedEncounters.Collection();
            try {
                final FileInputStream fis = new FileInputStream(cbattles);
                final MaxUtils.TotalLoader loaded = new MaxUtils.TotalLoader(fis, loadBuff);
                fis.close();
                allBattles.mergeFrom(CodedInputByteBufferNano.newInstance(loaded.fullData, 0, loaded.validBytes));
            } catch (FileNotFoundException e) {
                // No problem really. Go ahead.
            } catch (IOException e) {
                return e;
            }

            owned = pullo;
            joined = pullk;
            monsterBook = pullMon;
            customMobs = custBook;
            custBattles = allBattles;

            return null;
        }

        @Override
        protected void onPostExecute(Exception e) {
            if(null != e) {
                // TODO: if the activity has been rotated in the meanwhile, this will have issues. Usual problem with AsyncTask reporting.
                new AlertDialog.Builder(MainMenuActivity.this, R.style.AppDialogStyle)
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
                new AlertDialog.Builder(MainMenuActivity.this, R.style.AppDialogStyle)
                        .setMessage(String.format(getString(R.string.mma_invalidPartyOwnerLoadedData), sb.toString()))
                        .show();
                return;
            }
            monsters = SpawnMonsterActivity.monsters = monsterBook;
            customMonsters = SpawnMonsterActivity.custom = customMobs;
            customBattles = SpawnMonsterActivity.preppedBattles = custBattles;
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
        final RunningServiceHandles handles = RunningServiceHandles.getInstance();
        switch(requestCode) { // stuff to shut down no matter what
            case REQUEST_PICK_PARTY: { // sync our data with what was produced/modified
                groupDefs.clear();
                groupKeys.clear();
                handles.pick.getDense(groupDefs, groupKeys, false); // sync our data with what was produced/modified
                dataRefreshed();
                break;
            }
            case REQUEST_PLAY: {
                handles.play = null;
                stopService(new Intent(this, PartyJoinOrderService.class));
                return;
            }
            case REQUEST_CLIENT_PLAY: {
                handles.clientPlay = null;
                stopService(new Intent(this, AdventuringService.class));
                if(resultCode == ActorOverviewActivity.RESULT_GOODBYE) {
                    Snackbar.make(findViewById(R.id.activityRoot), R.string.mma_endedSessionByebye, Snackbar.LENGTH_LONG).show();
                }
                return;
            }
        }
        if(RESULT_OK != resultCode) {
            switch(requestCode) { // stuff would be used on success... but was not successful so goodbye
                case REQUEST_NEW_PARTY:
                    handles.create = null;
                    stopService(new Intent(this, PartyCreationService.class));
                    break;
                case REQUEST_PICK_PARTY: {
                    handles.pick.stopForeground(true);
                    handles.pick = null;
                    stopService(new Intent(this, PartyPickingService.class));
                    break;
                }
                case REQUEST_GATHER_DEVICES:
                    handles.play = null;
                    stopService(new Intent(this, PartyJoinOrderService.class));
                    break;
            }
            return;
        }
        switch(requestCode) {
            case REQUEST_NEW_PARTY: {
                boolean goAdventuringWithCreated = data.getBooleanExtra(NewCharactersApprovalActivity.RESULT_EXTRA_GO_ADVENTURING, false);
                groupDefs = handles.create.defs;
                if(goAdventuringWithCreated) {
                    activeParty = handles.create.generatedParty;
                    activeLanding = handles.create.getLanding(true);
                    activeConnections = handles.create.moveClients();
                    activeStats = handles.create.generatedStat;
                }
                handles.create = null;
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
                activeParty = handles.pick.sessionParty;
                activeStats = handles.pick.sessionData.get(activeParty);
                handles.pick.stopForeground(true);
                handles.pick = null;
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
                final Intent intent = new Intent(this, AdventuringService.class);
                startService(intent);
                bindService(intent, this, 0);
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
    static final int REQUEST_CLIENT_PLAY = 10;

    // Those must be fields to ensure a communication channel to the asynchronous onServiceConnected callbacks.
    private MessageNano activeParty; // StartData.PartyOwnerData.Group or StartData.PartyClientData.Group
    private Session.Suspended activeStats;
    private ServerSocket activeLanding;
    private Pumper.MessagePumpingThread[] activeConnections; // Client: a single connection to a server or Owner: list of connections to client

    // ServiceConnection ___________________________________________________________________________
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        final RunningServiceHandles handles = RunningServiceHandles.getInstance();
        if(service instanceof PartyJoinOrderService.LocalBinder) {
            PartyJoinOrderService real = ((PartyJoinOrderService.LocalBinder)service).getConcreteService();
            real.allOwnedGroups = groupDefs;
            StartData.PartyOwnerData.Group owned = (StartData.PartyOwnerData.Group) activeParty;
            JoinVerificator keyMaster = new JoinVerificator(owned.devices, MaxUtils.hasher);
            real.initializePartyManagement(owned, activeStats, keyMaster, monsters, customMonsters, customBattles);
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
            unbindService(this);
            handles.play = real;

            // first time activity is launched. Data has been pushed to the service by previous activity and I just need to elevate priority.
            final android.support.v4.app.NotificationCompat.Builder help = new NotificationCompat.Builder(this)
                    .setOngoing(true)
                    .setWhen(System.currentTimeMillis())
                    .setShowWhen(true)
                    .setContentTitle(real.getPartyOwnerData().name)
                    .setContentText(getString(R.string.ga_notificationDesc))
                    .setSmallIcon(R.drawable.ic_notify_icon)
                    .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                help.setCategory(Notification.CATEGORY_SERVICE);
            }
            real.startForeground(PJOS_NOTIFICATION_ID, help.build());

            startActivityForResult(new Intent(this, GatheringActivity.class), REQUEST_GATHER_DEVICES);
        }
        if(service instanceof PartyCreationService.LocalBinder) {
            handles.create = ((PartyCreationService.LocalBinder) service).getConcreteService();
            handles.create.defs = groupDefs;
            unbindService(this);
            final Intent intent = new Intent(this, NewPartyDeviceSelectionActivity.class);
            startActivityForResult(intent, REQUEST_NEW_PARTY);
        }
        if(service instanceof PartyPickingService.LocalBinder) {
            handles.pick = ((PartyPickingService.LocalBinder) service).getConcreteService();
            handles.pick.setKnownParties(groupDefs, groupKeys);
            startActivityForResult(new Intent(this, PartyPickActivity.class), REQUEST_PICK_PARTY);
            // The pick party server is different, looks like I can just pull it up now.
            final android.support.v4.app.NotificationCompat.Builder help = new NotificationCompat.Builder(this)
                    .setOngoing(true)
                    .setWhen(System.currentTimeMillis())
                    .setContentTitle(getString(R.string.mma_partyManagerNotificationTitle))
                    .setSmallIcon(R.drawable.ic_notify_icon)
                    .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                help.setCategory(Notification.CATEGORY_SERVICE);
            }
            unbindService(this);
            handles.pick.startForeground(PPS_NOTIFICATION_ID, help.build());
        }
        if(service instanceof AdventuringService.LocalBinder) {
            handles.clientPlay = ((AdventuringService.LocalBinder) service).getConcreteService();
            unbindService(this);
            final StartData.PartyClientData.Group temp = CharSelectionActivity.movePlayingParty();
            ActorOverviewActivity.prepare(temp,
                    CharSelectionActivity.movePlayChars(),
                    CharSelectionActivity.moveServerWorker());
            startActivityForResult(new Intent(this, ActorOverviewActivity.class), REQUEST_CLIENT_PLAY);
            final android.support.v4.app.NotificationCompat.Builder help = new NotificationCompat.Builder(this)
                    .setOngoing(true)
                    .setWhen(System.currentTimeMillis())
                    .setShowWhen(true)
                    .setContentTitle(temp.name)
                    .setContentText(getString(R.string.mma_notificationDesc))
                    .setSmallIcon(R.drawable.ic_notify_icon)
                    .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                help.setCategory(Notification.CATEGORY_SERVICE);
            }
            handles.clientPlay.startForeground(AS_NOTIFICATION_ID, help.build());
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
    private PreparedEncounters.Collection customBattles;

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
}

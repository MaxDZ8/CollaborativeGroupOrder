package com.massimodz8.collaborativegrouporder;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.nsd.NsdManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import com.google.android.gms.ads.MobileAds;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.protobuf.nano.MessageNano;
import com.massimodz8.collaborativegrouporder.client.ActorOverviewActivity;
import com.massimodz8.collaborativegrouporder.client.Adventure;
import com.massimodz8.collaborativegrouporder.client.CharSelectionActivity;
import com.massimodz8.collaborativegrouporder.client.JoinSessionActivity;
import com.massimodz8.collaborativegrouporder.client.NewCharactersProposalActivity;
import com.massimodz8.collaborativegrouporder.client.PartySelection;
import com.massimodz8.collaborativegrouporder.client.PcAssignmentState;
import com.massimodz8.collaborativegrouporder.client.SelectFormingGroupActivity;
import com.massimodz8.collaborativegrouporder.master.FreeRoamingActivity;
import com.massimodz8.collaborativegrouporder.master.GatheringActivity;
import com.massimodz8.collaborativegrouporder.master.NewCharactersApprovalActivity;
import com.massimodz8.collaborativegrouporder.master.NewPartyDeviceSelectionActivity;
import com.massimodz8.collaborativegrouporder.master.PartyCreator;
import com.massimodz8.collaborativegrouporder.master.PartyJoinOrder;
import com.massimodz8.collaborativegrouporder.networkio.Pumper;
import com.massimodz8.collaborativegrouporder.protocol.nano.Session;
import com.massimodz8.collaborativegrouporder.protocol.nano.StartData;

import java.io.IOException;
import java.net.ServerSocket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

public class MainMenuActivity extends AppCompatActivity implements ServiceConnection {
    public static final int NETWORK_VERSION = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_menu);

        if(MaxUtils.hasher == null) { // first run
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
        }
        Intent launch = new Intent(this, InternalStateService.class);
        startService(launch);
        if(!bindService(launch, this, 0)) {
            stopService(launch);
            new AlertDialog.Builder(this, R.style.AppDialogStyle)
                    .setMessage(R.string.mma_failedNewSessionServiceBind)
                    .show();
        }
    }

    @Override
    protected void onDestroy() {
        InternalStateService state = RunningServiceHandles.getInstance().state;
        if(state != null) {
            if(dataStatusCallback != null) state.data.onStatusChanged.remove(dataStatusCallback);
            if(state.notification == null) stopService(new Intent(this, InternalStateService.class));
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        InternalStateService state = RunningServiceHandles.getInstance().state;
        if(state != null) state.notification = null;
        super.onBackPressed();
    }

    @Override
    public boolean onSupportNavigateUp() {
        InternalStateService state = RunningServiceHandles.getInstance().state;
        if(state != null) state.notification = null;
        return super.onNavigateUp();
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

    public void startCreateParty_callback(View btn) {
        InternalStateService state = RunningServiceHandles.getInstance().state;
        Notification build = state.buildNotification(getString(R.string.pcs_label), null);
        NotificationManager serv = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if(serv != null) serv.notify(InternalStateService.INTERNAL_STATE_NOTIFICATION_ID, build);
        state.notification = build;

        final RunningServiceHandles handles = RunningServiceHandles.getInstance();
        handles.create = new PartyCreator(this);
        startActivityForResult(new Intent(this, NewPartyDeviceSelectionActivity.class), REQUEST_NEW_PARTY);
    }

    public void startJoinGroupActivity_callback(View btn) {
        InternalStateService state = RunningServiceHandles.getInstance().state;
        Notification build = state.buildNotification(getString(R.string.jsa_title), null);
        NotificationManager serv = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if(serv != null) serv.notify(InternalStateService.INTERNAL_STATE_NOTIFICATION_ID, build);
        state.notification = build;

        final NsdManager nsd = (NsdManager) getSystemService(Context.NSD_SERVICE);
        if (nsd == null) {
            new AlertDialog.Builder(this, R.style.AppDialogStyle)
                    .setMessage(R.string.both_noDiscoveryManager)
                    .show();
            return;
        }
        RunningServiceHandles.getInstance().partySelection = new PartySelection(nsd);
        startActivityForResult(new Intent(this, SelectFormingGroupActivity.class), REQUEST_JOIN_FORMING);
    }

    public void pickParty_callback(View btn) {
        RunningServiceHandles handles = RunningServiceHandles.getInstance();
        handles.pick = new PartyPicker();

        startActivityForResult(new Intent(this, PartyPickActivity.class), REQUEST_PICK_PARTY);
    }

    public void custom_callback(View btn) {
        switch(btn.getId()) {
            case R.id.mma_customMonsters: {
                startActivity(new Intent(this, CustomMonstersActivity.class));
                break;
            }
            case R.id.mma_preparedBattles: {
                startActivity(new Intent(this, PreparedBattlesActivity.class));
                break;
            }
        }
    }

    private void startNewSessionActivity(StartData.PartyOwnerData.Group activeParty, ServerSocket activeLanding, Pumper.MessagePumpingThread[] activeConnections, Session.Suspended activeStats) {
        PartyJoinOrder real = RunningServiceHandles.getInstance().play = new PartyJoinOrder();
        JoinVerificator keyMaster = new JoinVerificator(activeParty.devices, MaxUtils.hasher);
        NsdManager nsdm = (NsdManager) getSystemService(NSD_SERVICE);
        real.initializePartyManagement(activeParty, activeStats, keyMaster);
        real.pumpClients(activeConnections);
        try {
            real.startListening(activeLanding);
        } catch (IOException e) {
            new AlertDialog.Builder(this, R.style.AppDialogStyle)
                    .setMessage(R.string.master_badServerSocket)
                    .show();
            return;
        }
        real.beginPublishing(nsdm, activeParty.name, PartyJoinOrder.PARTY_GOING_ADVENTURING_SERVICE_TYPE);
        real.accept();
        // Update notification with more stuff.
        InternalStateService state = RunningServiceHandles.getInstance().state;
        StartData.PartyOwnerData.Group party = real.getPartyOwnerData();
        state.buildNotification(party.name, getString(R.string.ga_title));
        startActivityForResult(new Intent(this, GatheringActivity.class), REQUEST_GATHER_DEVICES);

        String easygoing = String.format(Locale.ENGLISH, "name: %1$s, published: %2$d, charCount=%3$d, devCount=%4$d, created=%5$d. Measure userbase health.",
                party.name, System.currentTimeMillis(),
                party.party.length + party.npcs.length, party.devices.length, party.created.seconds);
        MaxUtils.hasher.reset();
        real.publishToken = MaxUtils.hasher.digest(easygoing.getBytes());
        FirebaseAnalytics surveyor = FirebaseAnalytics.getInstance(this);
        Bundle bundle = new Bundle();
        bundle.putInt(MaxUtils.FA_PARAM_STEP, MaxUtils.FA_PARAM_STEP_GATHER);
        bundle.putByteArray(MaxUtils.FA_PARAM_ADVENTURING_ID, real.publishToken);
        surveyor.logEvent(MaxUtils.FA_EVENT_PLAYING, bundle);
    }


    private void startGoAdventuringActivity(@NonNull StartData.PartyClientData.Group activeParty, @Nullable Pumper.MessagePumpingThread serverConn) {
        JoinSessionActivity.prepare(activeParty, serverConn);
        startActivityForResult(new Intent(this, JoinSessionActivity.class), REQUEST_PULL_CHAR_LIST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        final RunningServiceHandles handles = RunningServiceHandles.getInstance();
        switch(requestCode) { // stuff to shut down no matter what
            case REQUEST_NEW_PARTY: {
                if(resultCode == RESULT_CANCELED) handles.state.baseNotification();
                else {
                    guiRefreshDataChanged.run(); // might have been updated as result of party creation.
                    boolean goAdventuringWithCreated = data.getBooleanExtra(NewCharactersApprovalActivity.RESULT_EXTRA_GO_ADVENTURING, false);
                    if(goAdventuringWithCreated) startNewSessionActivity(handles.create.generatedParty, handles.create.getLanding(true), handles.create.moveClients(), handles.create.generatedStat);
                    else handles.state.baseNotification();
                }
                handles.create.shutdown();
                handles.create = null;
                break;
            }
            case REQUEST_PICK_PARTY: { // sync our data with what was produced/modified
                InternalStateService.Data everything = handles.state.data;
                everything.groupDefs.clear();
                everything.groupKeys.clear();
                handles.pick.getDense(everything.groupDefs, everything.groupKeys, false); // sync our data with what was produced/modified
                guiRefreshDataChanged.run();
                if(resultCode == RESULT_OK) {
                    final MessageNano activeParty = handles.pick.sessionParty;
                    Session.Suspended activeStats = activeParty != null ? handles.pick.sessionData.get(activeParty) : null;
                    if (activeParty instanceof StartData.PartyOwnerData.Group) {
                        StartData.PartyOwnerData.Group real = (StartData.PartyOwnerData.Group) activeParty;
                        startNewSessionActivity(real, null, null, activeStats);
                    } else if (activeParty instanceof StartData.PartyClientData.Group) {
                        StartData.PartyClientData.Group real = (StartData.PartyClientData.Group) activeParty;
                        startGoAdventuringActivity(real, null);
                    }
                }
                handles.pick = null;
                break;
            }
            case REQUEST_PULL_CHAR_LIST: {
                if(resultCode == RESULT_OK) {
                    JoinSessionActivity.Result res = JoinSessionActivity.result;
                    handles.bindChars = new PcAssignmentState(res.worker, res.party, res.first);
                    JoinSessionActivity.result = null;
                    startActivityForResult(new Intent(this, CharSelectionActivity.class), REQUEST_BIND_CHARACTERS);
                }
                else handles.state.baseNotification();
            } break;
            case REQUEST_BIND_CHARACTERS: {
                if(resultCode == RESULT_OK) {
                    handles.clientPlay = new Adventure();
                    ActorOverviewActivity.prepare(
                            handles.bindChars.playChars,
                            handles.bindChars.server);
                    startActivityForResult(new Intent(this, ActorOverviewActivity.class), REQUEST_CLIENT_PLAY);

                    Notification updated = handles.state.buildNotification(handles.bindChars.party.name, getString(R.string.mma_notificationDesc));
                    NotificationManager man = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    if(man != null) man.notify(InternalStateService.INTERNAL_STATE_NOTIFICATION_ID, updated);
                    handles.state.notification = updated;
                }
                else handles.state.baseNotification();
                handles.bindChars.shutdown(resultCode == RESULT_OK);
                handles.bindChars = null;
            } break;
            case REQUEST_JOIN_FORMING: {
                if(resultCode == RESULT_OK) {
                    NewCharactersProposalActivity.prepare(handles.partySelection.resParty, handles.partySelection.resWorker);
                    startActivityForResult(new Intent(this, NewCharactersProposalActivity.class), REQUEST_PROPOSE_CHARACTERS);
                    Notification build = handles.state.buildNotification(handles.play.getPartyOwnerData().name, getString(R.string.ncpa_title));
                    NotificationManager serv = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    if(serv != null) serv.notify(InternalStateService.INTERNAL_STATE_NOTIFICATION_ID, build);
                    handles.state.notification = build;
                }
                else handles.state.baseNotification();
                handles.partySelection.shutdown();
                handles.partySelection = null;
            } break;
            case REQUEST_PROPOSE_CHARACTERS: {
                if(resultCode == RESULT_OK) {
                    InternalStateService.Data everything = RunningServiceHandles.getInstance().state.data;
                    everything.groupKeys.add(NewCharactersProposalActivity.resJoined);
                    Pumper.MessagePumpingThread serverConn = NewCharactersProposalActivity.resMaster;
                    if (serverConn != null) startGoAdventuringActivity(NewCharactersProposalActivity.resJoined, serverConn);
                    else handles.state.baseNotification();
                    guiRefreshDataChanged.run();
                    NewCharactersProposalActivity.resJoined = null;
                    NewCharactersProposalActivity.resMaster = null;
                }
                else handles.state.baseNotification();
                NewCharactersProposalActivity.resJoined = null;
            } break;
            case REQUEST_GATHER_DEVICES: {
                if(resultCode == RESULT_OK) {
                    startActivityForResult(new Intent(this, FreeRoamingActivity.class), REQUEST_PLAY);

                    Notification build = handles.state.buildNotification(handles.play.getPartyOwnerData().name, getString(R.string.fra_title));
                    NotificationManager serv = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    if(serv != null) serv.notify(InternalStateService.INTERNAL_STATE_NOTIFICATION_ID, build);
                    handles.state.notification = build;

                    FirebaseAnalytics surveyor = FirebaseAnalytics.getInstance(this);
                    Bundle bundle = new Bundle();
                    bundle.putInt(MaxUtils.FA_PARAM_STEP, MaxUtils.FA_PARAM_STEP_ASSEMBLED);
                    bundle.putByteArray(MaxUtils.FA_PARAM_ADVENTURING_ID, RunningServiceHandles.getInstance().play.publishToken);
                    surveyor.logEvent(MaxUtils.FA_EVENT_PLAYING, bundle);
                }
                else {
                    handles.state.baseNotification();
                    handles.play.shutdownPartyManagement();
                    handles.play.stopPublishing();
                    handles.play.stopListening(true);
                    handles.play = null;
                }
                break;
            }
            case REQUEST_PLAY: {
                handles.state.baseNotification();
                handles.play.shutdownPartyManagement();
                handles.play.stopPublishing();
                handles.play.stopListening(true);
                handles.play = null;
                break;
            }
            case REQUEST_CLIENT_PLAY: {
                handles.clientPlay.mailman.out.add(new SendRequest());
                handles.clientPlay.mailman.interrupt();
                handles.clientPlay = null;
                if(resultCode == ActorOverviewActivity.RESULT_GOODBYE) {
                    Snackbar.make(findViewById(R.id.activityRoot), R.string.mma_endedSessionByebye, Snackbar.LENGTH_LONG).show();
                }
                break;
            }
        }
    }

    private final Runnable guiRefreshDataChanged = new Runnable() {
        @Override
        public void run() {
            InternalStateService state = RunningServiceHandles.getInstance().state;
            MaxUtils.beginDelayedTransition(MainMenuActivity.this);
            boolean errors = state.data.error != null && state.data.error.size() > 0;
            boolean canPick = !errors && state.data.groupDefs != null && state.data.groupKeys != null && state.data.groupDefs.size() + state.data.groupKeys.size() > 0;
            MaxUtils.setEnabled(MainMenuActivity.this, !errors,
                    R.id.mma_newParty, R.id.mma_joinParty,
                    R.id.mma_customMonsters, R.id.mma_preparedBattles);
            findViewById(R.id.mma_goAdventuring).setEnabled(canPick);

            MaxUtils.setVisibility(MainMenuActivity.this,
                    state.data.status == InternalStateService.DATA_LOADING? View.VISIBLE : View.GONE,
                    R.id.mma_waitMessage, R.id.mma_progress);

            if(errors) {
                new AlertDialog.Builder(MainMenuActivity.this)
                        .setTitle(R.string.generic_initError)
                        .setMessage(R.string.mma_badDataLoadMsg)
                        .show();
            }

            if(!canPick && !errors) {
                new SuccessiveSnackbars(findViewById(R.id.activityRoot), Snackbar.LENGTH_SHORT,
                        getString(R.string.mma_emptyHint_1),
                        String.format(getString(R.string.mma_emptyHint_2), ((Button)findViewById(R.id.mma_newParty)).getText().toString()),
                        String.format(getString(R.string.mma_emptyHint_3), ((Button)findViewById(R.id.mma_joinParty)).getText().toString()))
                        .show();
            }
        }
    };

    private static final int REQUEST_NEW_PARTY = 1;
    static final int REQUEST_JOIN_FORMING = 2;
    static final int REQUEST_PROPOSE_CHARACTERS = 4;
    static final int REQUEST_PICK_PARTY = 5;
    static final int REQUEST_PULL_CHAR_LIST = 6;
    static final int REQUEST_BIND_CHARACTERS = 7;
    static final int REQUEST_GATHER_DEVICES = 8;
    static final int REQUEST_PLAY = 9;
    static final int REQUEST_CLIENT_PLAY = 10;

    private Integer dataStatusCallback;

    // ServiceConnection vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        InternalStateService state = RunningServiceHandles.getInstance().state = ((InternalStateService.LocalBinder) service).getConcreteService();
        dataStatusCallback = state.data.onStatusChanged.put(guiRefreshDataChanged);
        if(state.data.status != InternalStateService.DATA_EMPTY) {
            guiRefreshDataChanged.run();
            return;
        }
        if(state.notification == null) {
            Notification build = state.buildNotification(getString(R.string.app_name), null);
            state.startForeground(InternalStateService.INTERNAL_STATE_NOTIFICATION_ID, build);
            state.notification = build;
            FirebaseAnalytics.getInstance(this); // not really relevant, it's for initialization!
            MobileAds.initialize(this, getResources().getString(R.string.admob_app_id));
        }
        state.data.loadAll();
        unbindService(this);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        // Not really possible as I disconnect right away and keep the handle.
    }
    // ServiceConnection ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
}

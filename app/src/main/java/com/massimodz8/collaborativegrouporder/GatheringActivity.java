package com.massimodz8.collaborativegrouporder;

import android.app.Notification;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.BitmapFactory;
import android.net.nsd.NsdManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.NotificationCompat;
import android.support.v7.widget.RecyclerView;
import android.transition.TransitionManager;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.master.PartyJoinOrderService;
import com.massimodz8.collaborativegrouporder.master.PcAssignmentHelper;
import com.massimodz8.collaborativegrouporder.networkio.Pumper;
import com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

/** The server is 'gathering' player devices so they can join a new session.
 * This is important and we must be able to navigate back there every time needed in case
 * players get disconnected.
 */
public class GatheringActivity extends AppCompatActivity implements ServiceConnection {
    public static void prepare(@NonNull PersistentStorage.PartyOwnerData.Group toPlay, Pumper.MessagePumpingThread[] connected) {
        startingParty = toPlay;
        alreadyConnectedPeers = connected;
    }

    // Those will be consumed and set to null as soon as the activity connects to the service.
    private static PersistentStorage.PartyOwnerData.Group startingParty;
    private static Pumper.MessagePumpingThread[] alreadyConnectedPeers;

    private PartyJoinOrderService room;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gathering);

        // Now let's get to the real deal: create or start the state-maintaining service.
        Intent temp = new Intent(this, PartyJoinOrderService.class);
        if(!bindService(temp, this, BIND_AUTO_CREATE)) {
            failedServiceBind();
        }
    }

    private void failedServiceBind() {
        beginDelayedTransition();
        final TextView status = (TextView) findViewById(R.id.ga_state);
        status.setText(R.string.ga_cannotBindPartyService);
        MaxUtils.setVisibility(this, View.GONE,
                R.id.ga_progressBar,
                R.id.ga_identifiedDevices,
                R.id.ga_deviceList,
                R.id.ga_pcUnassignedListDesc,
                R.id.ga_pcUnassignedList);
    }

    @Override
    protected void onDestroy() {
        if(null != room) {
            if(!isChangingConfigurations()) room.stopForeground(true); // being destroyed for real.
            unbindService(this);
        }
        ticker.cancel();
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gathering_activity, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.ga_menu_explicitConnInfo: {
                int serverPort = room == null? 0 : room.getServerPort();
                new ConnectionInfoDialog(this, serverPort).show();
                break;
            }
        }
        return true;
    }

    private void availablePcs(int itemCount) {
        beginDelayedTransition();
        findViewById(R.id.ga_pcUnassignedList).setVisibility(itemCount > 0 ? View.VISIBLE : View.GONE);
        final TextView label = (TextView) findViewById(R.id.ga_pcUnassignedListDesc);
        label.setText(itemCount > 0 ? R.string.ga_playingCharactersAssignment : R.string.ga_allAssigned);
    }


    private Timer ticker = new Timer();
    private int lastPublishStatus = PartyJoinOrderService.PUBLISHER_IDLE;

    private static class MyHandler extends Handler {
        private final WeakReference<GatheringActivity> target;

        MyHandler(GatheringActivity target) {
            this.target = new WeakReference<>(target);
        }

        @Override
        public void handleMessage(Message msg) {
            final GatheringActivity target = this.target.get();
            switch(msg.what) {
                case MSG_TICK: {
                    if(null == target.room) return; // spurious signal or connection not estabilished yet or lost.
                    final int now = target.room.getPublishStatus();
                    if(now != target.lastPublishStatus) {
                        target.publisher(now, target.room.getPublishError());
                        target.lastPublishStatus = now;
                        return; // only change a single one per tick, accumulate the rest to give a chance of seeing something is going on.
                    }
                    Vector<Exception> failedAccept = target.room.getNewAcceptErrors();
                    if(null != failedAccept && !failedAccept.isEmpty()) {
                        new AlertDialog.Builder(target)
                                .setMessage(target.getString(R.string.ga_failedAccept))
                                .show();
                        return;
                    }
                    target.room.promoteNewClients();
                    target.availablePcs(target.room.getUnboundedPcs().size());
                }
            }
        }
    }

    private void publisher(int state, int err) {
        if(state == PublishedService.STATUS_STARTING) return;
        beginDelayedTransition();
        final TextView dst = (TextView) findViewById(R.id.ga_state);
        switch(state) {
            case PublishedService.STATUS_START_FAILED:
                dst.setText(R.string.ga_publisherFailedStart);
                new AlertDialog.Builder(this).setMessage(String.format(getString(R.string.ga_failedServiceRegistration), MaxUtils.NsdManagerErrorToString(err, this))).show();
                break;
            case PublishedService.STATUS_PUBLISHING:
                dst.setText(R.string.ga_publishing);
                break;
            case PublishedService.STATUS_STOP_FAILED:
                dst.setText(R.string.ga_publishingStopFailed);
                break;
            case PublishedService.STATUS_STOPPED:
                dst.setText(R.string.ga_noMorePublishing);
        }
    }

    private static final int MSG_TICK = 1;

    private static final int TIMER_DELAY_MS = 1000;
    private static final int TIMER_INTERVAL_MS = 250;

    public void startSession_callback(View btn) {
        room.adventuring();
    }

    // ServiceConnection ___________________________________________________________________________
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        room = ((PartyJoinOrderService.LocalBinder) service).getConcreteService();
        if(startingParty != null) { // first time after a prepare the activity has started. Push data to service.
            JoinVerificator keyMaster;
            try {
                keyMaster = new JoinVerificator();
            } catch (NoSuchAlgorithmException e) {
                new AlertDialog.Builder(this)
                        .setMessage(R.string.ga_noDigestDialogMessage)
                        .show();
                return;
            }

            final android.support.v4.app.NotificationCompat.Builder help = new NotificationCompat.Builder(this)
                    .setOngoing(true)
                    .setWhen(System.currentTimeMillis())
                    .setShowWhen(true)
                    .setContentTitle(startingParty.name)
                    .setContentText(getString(R.string.ga_notificationDesc))
                    .setSmallIcon(R.drawable.ic_notify_icon)
                    .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.placeholder_todo));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                help.setCategory(Notification.CATEGORY_SERVICE);
            }
            room.startForeground(NOTIFICATION_ID, help.build());
            room.initializePartyManagement(startingParty, keyMaster);
            startingParty = null;

            room.pumpClients(alreadyConnectedPeers);
            alreadyConnectedPeers = null;
        }
        beginDelayedTransition();
        findViewById(R.id.ga_pcUnassignedListDesc).setVisibility(View.VISIBLE);
        final RecyclerView devList = (RecyclerView) findViewById(R.id.ga_deviceList);
        devList.setAdapter(room.setNewAuthDevicesAdapter(new PcAssignmentHelper.AuthDeviceHolderFactoryBinder<AuthDeviceViewHolder>() {
            @Override
            public AuthDeviceViewHolder createUnbound(ViewGroup parent, int viewType) {
                return new AuthDeviceViewHolder(getLayoutInflater().inflate(R.layout.card_identified_device_chars_assigned, parent, false));
            }

            @Override
            public void bind(@NonNull AuthDeviceViewHolder target, @NonNull String deviceName, @Nullable ArrayList<Integer> characters) {
                target.name.setText(deviceName);
                if (null == characters) {
                    target.pcList.setText(R.string.ga_noPcsOnDevice);
                    return;
                }
                String list = "";
                for (Integer index : characters) {
                    if (list.length() > 0) list += ", ";
                    list += room.getPartyOwnerData().usually.party[index].name;
                }
            }
        }));
        final RecyclerView unboundPcList = (RecyclerView) findViewById(R.id.ga_pcUnassignedList);
        unboundPcList.setAdapter(room.setNewUnassignedPcsAdapter(new PcAssignmentHelper.UnassignedPcHolderFactoryBinder<PcViewHolder>() {
            @Override
            public PcViewHolder createUnbound(ViewGroup parent, int viewType) {
                return new PcViewHolder(getLayoutInflater().inflate(R.layout.card_assignable_character_server_list, parent, false));
            }

            @Override
            public void bind(@NonNull PcViewHolder target, int index) {
                target.actor = room.getPartyOwnerData().usually.party[index];
                target.name.setText(target.actor.name);
                target.levels.setText("<class_todo> " + target.actor.level); // TODO

            }
        }));
        MaxUtils.setVisibility(View.VISIBLE, devList, unboundPcList, findViewById(R.id.ga_startSession));

        if(room.getPublishStatus() == PartyJoinOrderService.PUBLISHER_IDLE) {
            try {
                room.startListening();
            } catch (IOException e) {
                new AlertDialog.Builder(this)
                        .setMessage(R.string.badServerSocket)
                        .setPositiveButton(R.string.giveUpAndGoBack, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }
                        }).show();
                return;
            }
            room.beginPublishing((NsdManager) getSystemService(NSD_SERVICE), room.getPartyOwnerData().name);
        }
        final Handler funnel = new MyHandler(this);
        ticker.schedule(new TimerTask() {
            @Override
            public void run() {
                funnel.sendMessage(funnel.obtainMessage(MSG_TICK));
            }
        }, TIMER_DELAY_MS, TIMER_INTERVAL_MS);
    }

    private void beginDelayedTransition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            TransitionManager.beginDelayedTransition((ViewGroup) findViewById(R.id.ga_activityRoot));
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        room = null;
        new AlertDialog.Builder(this)
                .setMessage(R.string.ga_lostServiceConnection)
                .show();
    }

    private static final int NOTIFICATION_ID = 1;


    private static class AuthDeviceViewHolder extends RecyclerView.ViewHolder {
        TextView name;
        TextView pcList;

        public AuthDeviceViewHolder(View itemView) {
            super(itemView);
            name = (TextView) itemView.findViewById(R.id.cardIDACA_name);
            pcList = (TextView) itemView.findViewById(R.id.cardIDACA_assignedPcs);
        }
    }

    private class PcViewHolder extends RecyclerView.ViewHolder implements View.OnLongClickListener, ActionMode.Callback {
        TextView name;
        TextView levels;
        PersistentStorage.Actor actor;

        public PcViewHolder(View itemView) {
            super(itemView);
            name = (TextView)itemView.findViewById(R.id.cardACSL_name);
            levels = (TextView)itemView.findViewById(R.id.cardACSL_classesAndLevels);
            itemView.setOnLongClickListener(this);
        }

        @Override
        public boolean onLongClick(View v) {
            startActionMode(this);
            // What if a character gets bound while the user is ready the menu and deciding what to do?
            // Not really my problem, we'll re bind it.
            return true;
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            getMenuInflater().inflate(R.menu.ga_unassigned_pc_context, menu);
            if(null != actor) mode.setTitle(actor.name);
            mode.setTag(actor);
            return true;
    }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) { return false; }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch(item.getItemId()) {
                case R.id.ga_ctx_unassigned_pc_playHere: {
                    PersistentStorage.Actor was = (PersistentStorage.Actor) mode.getTag();
                    if(null != room && null != was) room.local(was);
                    mode.finish();
                    return true;
}
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) { }
    }
}

package com.massimodz8.collaborativegrouporder.master;

import android.content.DialogInterface;
import android.net.nsd.NsdManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.transition.TransitionManager;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.protobuf.nano.Timestamp;
import com.massimodz8.collaborativegrouporder.ConnectionInfoDialog;
import com.massimodz8.collaborativegrouporder.MaxUtils;
import com.massimodz8.collaborativegrouporder.PublishedService;
import com.massimodz8.collaborativegrouporder.R;
import com.massimodz8.collaborativegrouporder.RunningServiceHandles;
import com.massimodz8.collaborativegrouporder.SendRequest;
import com.massimodz8.collaborativegrouporder.networkio.ProtoBufferEnum;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;
import com.massimodz8.collaborativegrouporder.protocol.nano.StartData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;

/** The server is 'gathering' player devices so they can join a new session.
 * This is important and we must be able to navigate back there every time needed in case
 * players get disconnected.
 */
public class GatheringActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gathering);

        final PartyJoinOrderService room = RunningServiceHandles.getInstance().play;
        room.onNewPublishStatus = new PublishAcceptService.NewPublishStatusCallback() {
            @Override
            public void onNewPublishStatus(int state) {
                if(state == PublishedService.STATUS_STARTING) return;
                beginDelayedTransition();
                final TextView dst = (TextView) findViewById(R.id.ga_state);
                switch(state) {
                    case PublishedService.STATUS_START_FAILED: {
                        int err = room.getPublishError();
                        dst.setText(R.string.ga_publisherFailedStart);
                        new AlertDialog.Builder(GatheringActivity.this)
                                .setMessage(String.format(getString(R.string.ga_failedServiceRegistration), MaxUtils.NsdManagerErrorToString(err, GatheringActivity.this)))
                                .show();
                        break;
                    }
                    case PublishedService.STATUS_PUBLISHING:
                        dst.setText(R.string.master_publishing);
                        break;
                    case PublishedService.STATUS_STOP_FAILED:
                        dst.setText(R.string.ga_publishingStopFailed);
                        break;
                    case PublishedService.STATUS_STOPPED:
                        dst.setText(R.string.ga_noMorePublishing);
                }

            }
        };
        room.setUnassignedPcsCountListener(new PcAssignmentHelper.OnBoundPcCallback() {
            @Override
            public void onUnboundCountChanged(int stillToBind) {
                availablePcs(stillToBind);
            }
        });
        room.accept();
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
                    list += room.getPartyOwnerData().party[index].name;
                }
                target.pcList.setText(list);
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
                target.actor = room.getPartyOwnerData().party[index];
                target.name.setText(target.actor.name);
                target.levels.setText("<class_todo> " + target.actor.level); // TODO

            }
        }));
        MaxUtils.setVisibility(View.VISIBLE, devList, unboundPcList, findViewById(R.id.ga_startSession));
    }

    @Override
    protected void onResume() {
        super.onResume();
        final PartyJoinOrderService room = RunningServiceHandles.getInstance().play;
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
        final NsdManager sys = (NsdManager) getSystemService(NSD_SERVICE);
        room.beginPublishing(sys, room.getPartyOwnerData().name, PartyJoinOrderService.PARTY_GOING_ADVENTURING_SERVICE_TYPE);
    }

    @Override
    protected void onDestroy() {
        final PartyJoinOrderService room = RunningServiceHandles.getInstance().play;
        if(room != null) {
            room.setNewAuthDevicesAdapter(null);
            room.setNewUnassignedPcsAdapter(null);
            room.onNewPublishStatus = null;
        }
        super.onDestroy();
    }

    @Override
    protected void onStop() {
        final PartyJoinOrderService room = RunningServiceHandles.getInstance().play;
        if(room != null) room.stopListening(false); // in case this is called before .onDestroy
        super.onStop();
    }

    @Override
    protected void onStart() {
        final PartyJoinOrderService room = RunningServiceHandles.getInstance().play;
        room.accept();
        super.onStart();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gathering_activity, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final PartyJoinOrderService room = RunningServiceHandles.getInstance().play;
        switch(item.getItemId()) {
            case R.id.ga_menu_explicitConnInfo: {
                int serverPort = room == null? 0 : room.getServerPort();
                new ConnectionInfoDialog(this, serverPort).show();
                break;
            }
        }
        return false;
    }

    private void availablePcs(int itemCount) {
        beginDelayedTransition();
        findViewById(R.id.ga_pcUnassignedList).setVisibility(itemCount > 0 ? View.VISIBLE : View.GONE);
        final TextView label = (TextView) findViewById(R.id.ga_pcUnassignedListDesc);
        label.setText(itemCount > 0 ? R.string.ga_playingCharactersAssignment : R.string.ga_allAssigned);
    }

    public void startSession_callback(View btn) {
        final PartyJoinOrderService room = RunningServiceHandles.getInstance().play;
        final ArrayList<StartData.ActorDefinition> free = room.getUnboundedPcs();
        if(!free.isEmpty()) {
            String firstLine = free.size() == 1? getString(R.string.ga_oneCharNotBound)
                    : String.format(getString(R.string.ga_someCharsNotBound), free.size());
            String message = getString(R.string.ga_unboundCharsDlgMsg);
            new AlertDialog.Builder(this)
                    .setMessage(String.format(message, firstLine))
                    .show();
            return;
        }
        room.stopPublishing();
        room.stopListening(false);
        findViewById(R.id.ga_startSession).setEnabled(false);
        new AsyncTask<Void, Void, Void>() {
            int errorCount;

            @Override
            protected Void doInBackground(Void... params) {
                Network.PhaseControl yours = new Network.PhaseControl();
                yours.type = Network.PhaseControl.T_DEFINITIVE_CHAR_ASSIGNMENT;
                final StartData.ActorDefinition[] playingChars = room.assignmentHelper.party.party;
                int devIndex = -1;
                for (PcAssignmentHelper.PlayingDevice known : room.assignmentHelper.peers) {
                    devIndex++;
                    if(known.pipe == null) continue; // not very likely but possible if connection has just gone down!
                    int count = 0;
                    for(int index = 0; index < playingChars.length; index++) {
                        final Integer which = room.assignmentHelper.assignment.get(index);
                        if(which == null) continue;
                        if(which == devIndex) count++;
                    }
                    yours.yourChars = new int[count];
                    count = 0;
                    for(int index = 0; index < playingChars.length; index++) {
                        final Integer which = room.assignmentHelper.assignment.get(index);
                        if(which == null) continue;
                        if(which == devIndex) {
                            yours.yourChars[count] = index;
                            count++;
                        }
                    }
                    room.assignmentHelper.mailman.out.add(new SendRequest(known.pipe, ProtoBufferEnum.GROUP_READY, yours));
                }
                // Send actor defs to clients.
                int id = -1;
                for (Integer index : room.assignmentHelper.assignment) {
                    id++;
                    if(index == null) continue; // impossible, but let's try
                    if(index == PcAssignmentHelper.LOCAL_BINDING) continue;
                    PcAssignmentHelper.PlayingDevice dev = room.assignmentHelper.peers.get(index);
                    if(dev.pipe == null) continue; // connection temporarily lost
                    final Network.ActorState actorData = room.session.getActorById(id);
                    room.assignmentHelper.mailman.out.add(new SendRequest(dev.pipe, ProtoBufferEnum.ACTOR_DATA_UPDATE, actorData));
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                if(errorCount == 0) {
                    // Ideally do nothing. We wait until the various devices give us back the ACTOR_DATA_REQUEST.
                    // However, if no devices are there nothing will ever detach so... have an extra check
                    room.session.stats.lastBegin = new Timestamp();
                    room.session.stats.lastBegin.seconds = new Date().getTime() / 1000;
                    room.session.stats.numSessions++;
                    setResult(RESULT_OK);
                    finish();
                    return;
                }
                new AlertDialog.Builder(GatheringActivity.this)
                        .setTitle(R.string.ga_dlg_errorWhileFormingGroup_title)
                        .setMessage(R.string.ga_dlg_errorWhileFormingGroup_msg)
                        .show();
            }
        }.execute();
    }

    private void beginDelayedTransition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            TransitionManager.beginDelayedTransition((ViewGroup) findViewById(R.id.ga_activityRoot));
        }
    }

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
        StartData.ActorDefinition actor;

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
                    StartData.ActorDefinition was = (StartData.ActorDefinition) mode.getTag();
                    final PartyJoinOrderService room = RunningServiceHandles.getInstance().play;
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

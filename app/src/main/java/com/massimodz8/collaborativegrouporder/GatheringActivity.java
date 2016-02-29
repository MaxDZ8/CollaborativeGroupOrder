package com.massimodz8.collaborativegrouporder;

import android.app.Notification;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.BitmapFactory;
import android.net.nsd.NsdManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.app.NotificationCompat;
import android.support.v7.widget.RecyclerView;
import android.transition.TransitionManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.protobuf.nano.MessageNano;
import com.massimodz8.collaborativegrouporder.master.PartyJoinOrderService;
import com.massimodz8.collaborativegrouporder.networkio.Events;
import com.massimodz8.collaborativegrouporder.networkio.LandingServer;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.networkio.ProtoBufferEnum;
import com.massimodz8.collaborativegrouporder.networkio.PumpTarget;
import com.massimodz8.collaborativegrouporder.networkio.Pumper;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;
import com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

/** The server is 'gathering' player devices so they can join a new session.
 * This is important and we must be able to navigate back there every time needed in case
 * players get disconnected.
 */
public class GatheringActivity extends AppCompatActivity implements ServiceConnection {
    private PartyJoinOrderService room;
    private PartyJoinOrderService.LocalBinder serviceConnection;

    public static class State {
        final PersistentStorage.PartyOwnerData.Group party;
        //^ When the Activity is launched this always contains the thing we need. It is just assumed.

        /**
         * Build characters-to-device assignments, it's just a simple pair system. Should that be a set?
         * Entry [i] corresponds to character party.group.usually.party[i]. The i-th character is
         * managed by playerDevices[assignment[i]]. Special cases:
         * - assignment[i] == null: character is unassigned
         * - assignment[i] < 0: character is managed locally
         */
        ArrayList<Integer> assignment;

        /**
         * Devices connected, in some order. When a device provides a key it might be promoted
         * to a different slot and take over the previously assigned characters.
         * In a certain sense, they are 'kept unique', OFC we know that only after a device
         * has provided its key. Identified devices get moved to playerDevice.
         */
        ArrayList<PlayingDevice> unidentified = new ArrayList<>();
        ArrayList<PlayingDevice> playerDevices = new ArrayList<>();

        public State(PersistentStorage.PartyOwnerData.Group party) {
            this.party = party;
        }
    }

    /// Not sure what should I put there, but it seems I might want to track state besides connection channel in the future.
    public static class PlayingDevice {
        public boolean versionMismatchSignaled;
        public byte[] doormat;

        /// Use null to mark "this device", to assign playing characters to this device.
        public PlayingDevice(@Nullable MessageChannel pipe) {
            this.pipe = pipe;
        }

        MessageChannel pipe;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gathering);
        final CrossActivityShare appState = (CrossActivityShare) getApplicationContext();
        myState = appState.gaState;
        appState.gaState = null;

        // Now let's get to the real deal: create or start the state-maintaining service.
        Intent temp = new Intent(this, PartyJoinOrderService.class);
        if(!bindService(temp, this, BIND_AUTO_CREATE)) {
            failedServiceBind();
            return;
        }

        if(null == myState.assignment) {
            myState.assignment = new ArrayList<>();
            for (PersistentStorage.Actor aParty : myState.party.usually.party) myState.assignment.add(null);
        }

        ((RecyclerView) findViewById(R.id.ga_deviceList)).setAdapter(new AuthDeviceAdapter());
        ((RecyclerView) findViewById(R.id.ga_pcUnassignedList)).setAdapter(new UnassignedPcsAdapter(null));

        preparePumper(appState);
    }

    private void failedServiceBind() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            TransitionManager.beginDelayedTransition((ViewGroup) findViewById(R.id.ga_activityRoot));
        }
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
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        final CrossActivityShare appState = (CrossActivityShare) getApplicationContext();
        appState.gaState = myState;
        appState.pumpers = null != pumper? pumper.move() : null;
        // Not so fast. As we go out of scope, several things needs to be shut down, onDestroy.
        myState = null;
    }

    @Override
    protected void onDestroy() {
        if(null != room) {
            if(!isChangingConfigurations()) room.stopForeground(true); // being destroyed for real.
            room.unbindService(this);
        }
        if(null != pumper) pumper.shutdown(); // if saving instance state this is empty
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
            case R.id.ga_menu_pcOnThisDevice: {
                final AlertDialog dialog = new AlertDialog.Builder(this)
                        .setView(R.layout.dialog_make_pc_local).show();
                final RecyclerView list = (RecyclerView) dialog.findViewById(R.id.ga_localPcsDialog_list);
                dialogList = new UnassignedPcsAdapter(new OnUnassignedPcClick() {
                    @Override
                    public void click(PersistentStorage.Actor actor) {
                        dialog.dismiss();
                        for(int slot = 0; slot < myState.party.usually.party.length; slot++) {
                            if(actor == myState.party.usually.party[slot]) {
                                myState.assignment.set(slot, -1);
                                RecyclerView.Adapter lister = ((RecyclerView) findViewById(R.id.ga_pcUnassignedList)).getAdapter();
                                lister.notifyDataSetChanged();
                                availablePcs(lister.getItemCount());
                                int boundToMe = Network.CharacterOwnership.BOUND;
                                sendAvailability(boundToMe, slot, null, ++nextValidRequest);
                                break;
                            }
                        }
                        dialogList = null;
                    }
                });
                list.setAdapter(dialogList);
                list.addItemDecoration(new PreSeparatorDecorator(list, this) {
                    @Override
                    protected boolean isEligible(int position) {
                        return true;
                    }
                });
            }
        }
        return true;
    }

    private void availablePcs(int itemCount) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            TransitionManager.beginDelayedTransition((ViewGroup) findViewById(R.id.ga_activityRoot));
        }
        findViewById(R.id.ga_pcUnassignedList).setVisibility(itemCount > 0 ? View.VISIBLE : View.GONE);
        findViewById(R.id.ga_startSession    ).setVisibility(itemCount > 0 ? View.GONE : View.VISIBLE);
        final TextView label = (TextView) findViewById(R.id.ga_pcUnassignedListDesc);
        label.setText(itemCount > 0 ? R.string.ga_playingCharactersAssignment : R.string.ga_allAssigned);
    }

    void preparePumper(CrossActivityShare appState) {
        funnel = new MyHandler(this);
        pumper = new Pumper(funnel, MSG_DISCONNECTED, MSG_DETACHED)
                .add(ProtoBufferEnum.HELLO, new PumpTarget.Callbacks<Network.Hello>() {
                    @Override
                    public Network.Hello make() { return new Network.Hello(); }

                    @Override
                    public boolean mangle(MessageChannel from, Network.Hello msg) throws IOException {
                        message(MSG_HELLO, new Events.Hello(from, msg));
                        return false;
                    }
                }).add(ProtoBufferEnum.CHARACTER_OWNERSHIP, new PumpTarget.Callbacks<Network.CharacterOwnership>() {
                    @Override
                    public Network.CharacterOwnership make() { return new Network.CharacterOwnership(); }

                    @Override
                    public boolean mangle(MessageChannel from, Network.CharacterOwnership msg) throws IOException {
                        if(msg.type != Network.CharacterOwnership.REQUEST) return false; // non-requests are silently ignored.
                        message(MSG_CHARACTER_OWNERSHIP_REQUEST, new Events.CharOwnership(from, msg));
                        return false;
                    }
                });
        if(null != appState.pumpers) {
            for (Pumper.MessagePumpingThread worker : appState.pumpers) pumper.pump(worker);
            appState.pumpers = null;
        }
    }

    synchronized byte[] random(int count) {
        if(null == randomizer) randomizer = new SecureRandom();
        if(count < 0) count = -count;
        byte[] blob = new byte[count];
        randomizer.nextBytes(blob);
        return blob;
    }

    void message(int code, Object payload) {
        funnel.sendMessage(funnel.obtainMessage(code, payload));
    }


    private Timer ticker = new Timer();
    private int lastPublishStatus = PartyJoinOrderService.PUBLISHER_IDLE;
    private State myState;
    private Pumper pumper;
    private MyHandler funnel;
    private SecureRandom randomizer;
    private RecyclerView.Adapter dialogList; /// so if a player takes a PC, we can update the list.


    private static class MyHandler extends Handler {
        private final WeakReference<GatheringActivity> target;

        MyHandler(GatheringActivity target) {
            this.target = new WeakReference<>(target);
        }

        @Override
        public void handleMessage(Message msg) {
            final GatheringActivity target = this.target.get();
            switch(msg.what) {
                case MSG_DISCONNECTED:
                    new AlertDialog.Builder(target).setMessage("todo MSG_DISCONNECTED!").show();
                    return;
                case MSG_DETACHED:
                    new AlertDialog.Builder(target).setMessage("todo! MSG_DETACHED").show();
                    return;
                case MSG_NO_VERIFY:
                    new AlertDialog.Builder(target).setMessage("todo! MSG_NO_VERIFY").show();
                    return;
                case MSG_AUTH_TOKEN_SENT:
                    new AlertDialog.Builder(target).setMessage("todo! MSG_AUTH_TOKEN_SENT").show();
                    return;
                case MSG_HELLO: {
                    final Events.Hello real = (Events.Hello)msg.obj;
                    target.hello(real.origin, real.payload);
                    break;
                }
                case MSG_CHARACTER_OWNERSHIP_REQUEST: {
                    final Events.CharOwnership real = (Events.CharOwnership)msg.obj;
                    target.charOwnership(real.origin, real.payload);
                } break;
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
                    final Vector<MessageChannel> clients = target.room.getNewClients();
                    if(null != clients) {
                        for (MessageChannel c : clients) {
                            target.myState.unidentified.add(new PlayingDevice(c));
                            target.pumper.pump(c);
                        }
                    }
                }
            }
        }
    }

    PlayingDevice getDevice(MessageChannel origin) {
        for (PlayingDevice check : myState.unidentified) {
            if(check.pipe == origin) return check;
        }
        for (PlayingDevice check : myState.playerDevices) {
            if(check.pipe == origin) return check;
        }
        return null;
    }

    String getDeviceName(PlayingDevice dev) {
        for(int loop = 0; loop < myState.unidentified.size(); loop++) {
            if(dev == myState.unidentified.get(loop)) return String.format("unidentified[%1$d]", loop); // TODO, but almost ok
        }
        for(int loop = 0; loop < myState.playerDevices.size(); loop++) {
            if(dev == myState.playerDevices.get(loop)) return String.format("player[%1$d]", loop); // TODO, resolve based on unique device keys and device labels
        }
        return "";
    }

    private void hello(final MessageChannel origin, Network.Hello payload) {
        PlayingDevice dev = getDevice(origin);
        if(null == dev) return; // impossible
        if(payload.version != MainMenuActivity.NETWORK_VERSION && !dev.versionMismatchSignaled) {
            dev.versionMismatchSignaled = true;
            final String update = getString(payload.version > MainMenuActivity.NETWORK_VERSION? R.string.ga_pleaseUpdateYourself : R.string.ga_pleaseUpdateFriend);
            String msg = getString(R.string.ga_versionMismatchDialogMessage);
            msg = String.format(msg, getDeviceName(dev), payload.version, MainMenuActivity.NETWORK_VERSION, update);
            new AlertDialog.Builder(this)
                    .setMessage(msg)
                    .show();
        }
        if(payload.authorize.length > 0) {
            // For the time being, this must be the same for all devices.
            // TODO: upgrade to device-specific keys!
            byte[] hash;
            try {
                hash = new JoinVerificator().mangle(dev.doormat, myState.party.salt);
            } catch (NoSuchAlgorithmException e) {
                new AlertDialog.Builder(this)
                        .setMessage(R.string.ga_noDigestDialogMessage)
                        .show();
                return;
            }
            if(Arrays.equals(hash, payload.authorize)) {
                dev.doormat = null;
                if(myState.unidentified.contains(dev)) { // move this to identified, otherwise it's just refresh.
                    // TODO match identity by key and manage reconnect
                    myState.unidentified.remove(dev);
                    int last = myState.playerDevices.size();
                    myState.playerDevices.add(dev);
                    ((RecyclerView)findViewById(R.id.ga_deviceList)).getAdapter().notifyItemInserted(last);
                    sendPlayingCharacterList(dev);
                }
            }
            else {
                // Device tried to authenticate but I couldn't recognize it.
                // Most likely using an absolete key -> kicked from party!
                // Very odd but why not? Better to just ignore and disconnect the guy.
                removeDevice(dev);
                pumper.move(origin).interrupt();
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            origin.socket.close();
                        } catch (IOException e) {
                            // nothing. It's a goner anyway.
                        }
                    }
                }.start();
            }
            return;
        }
        final Network.GroupInfo send = new Network.GroupInfo();
        send.forming = false;
        send.name = myState.party.name;
        send.version = MainMenuActivity.NETWORK_VERSION;
        send.doormat = random(DOORMAT_BYTES);
        dev.doormat = send.doormat;
        new Thread() {
            @Override
            public void run() {
                try {
                    origin.writeSync(ProtoBufferEnum.GROUP_INFO, send);
                } catch (IOException e) {
                    // just suppress. If it doesn't go... I can do nothing with it, as the device could rotate in the meanwhile.
                }
            }
        }.start();
    }

    private void sendPlayingCharacterList(PlayingDevice dev) {
        // TODO: for the time being I just send everything. In the future I might want to do that differently, for example send only a subset of characters depending on device key
        int peerKey = 0;
        final ArrayList<Network.PlayingCharacterDefinition> stream = new ArrayList<>(myState.party.usually.party.length);
        for (PersistentStorage.Actor actor : myState.party.usually.party) {
            stream.add(simplify(actor, peerKey++));
        }
        // Also send a notification for each character which is already assigned to someone else.
        final ArrayList<Integer> bound = new ArrayList<>(myState.party.usually.party.length);
        for(int loop = 0; loop < myState.assignment.size(); loop++) {
            if(myState.assignment.get(loop) != null) bound.add(loop);
        }
        final Network.CharacterOwnership notification = new Network.CharacterOwnership();
        notification.ticket = nextValidRequest;
        notification.type = Network.CharacterOwnership.BOUND;
        final MessageChannel dst = dev.pipe;
        new Thread() {
            @Override
            public void run() {
                for (Network.PlayingCharacterDefinition character : stream) {
                    try {
                        dst.writeSync(ProtoBufferEnum.PLAYING_CHARACTER_DEFINITION, character);
                    } catch (IOException e) {
                        // suppress and let's hope it's a persistent thing which will cause stuff to go awry.
                        // TODO: in the future, tick for 'operation completeness' and have a queue of things to be checked. Ugly.
                    }
                }
                for(Integer key : bound) {
                    notification.character = key;
                    try {
                        dst.writeSync(ProtoBufferEnum.CHARACTER_OWNERSHIP, notification);
                    } catch (IOException e) {
                    }

                }
            }
        }.start();
        /// TODO: we should really be careful about letting the thread finish first and then do further procesesing,
        // theres some slight chance we might try to send something before the the tread completes and then the messages
        // would end up being delivered out of order... meh!
    }

    private Network.PlayingCharacterDefinition simplify(PersistentStorage.Actor actor, int loop) {
        Network.PlayingCharacterDefinition res = new Network.PlayingCharacterDefinition();
        PersistentStorage.ActorStatistics currently = actor.stats[0];
        res.name = actor.name;
        res.initiativeBonus = currently.initBonus;
        res.healthPoints = currently.healthPoints;
        res.experience = currently.experience;
        res.level = actor.level;
        res.peerKey = loop;
        return res;
    }

    private void removeDevice(PlayingDevice dev) {
        if(myState.unidentified.remove(dev)) return;
        // If the device is identified it might have bound characters we need to unbind.
        int slot = myState.playerDevices.indexOf(dev);
        for(int loop = 0; loop < myState.assignment.size(); loop++) {
            Integer check = myState.assignment.get(loop);
            if(null != check && check == slot) {
                myState.assignment.set(slot, null);
            }
        }
        // We also must move back one binding all the characters binding to subsequent devices. Meh.
        for(int loop = 0; loop < myState.assignment.size(); loop++) {
            Integer check = myState.assignment.get(loop);
            if(null != check && check > slot) {
                myState.assignment.set(loop, check - 1);
            }
        }
        myState.unidentified.remove(dev);
        myState.playerDevices.remove(dev);
    }


    private void publisher(int state, int err) {
        if(state == PublishedService.STATUS_STARTING) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            TransitionManager.beginDelayedTransition((ViewGroup) findViewById(R.id.ga_activityRoot));
        }
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

    private static final int MSG_DISCONNECTED = 1;
    private static final int MSG_DETACHED = 2;
    private static final int MSG_NO_VERIFY = 3;
    private static final int MSG_AUTH_TOKEN_SENT = 5;
    private static final int MSG_PUBLISHER_STATUS_CHANGED = 6;
    private static final int MSG_HELLO = 9;
    private static final int MSG_CHARACTER_OWNERSHIP_REQUEST = 10;
    private static final int MSG_TICK = 11;

    private static final int DOORMAT_BYTES = 32;

    private static final int TIMER_DELAY_MS = 1000;
    private static final int TIMER_INTERVAL_MS = 250;

    public void startSession_callback(View btn) {
        final ArrayList<MessageChannel> target = new ArrayList<>();
        final ArrayList<ArrayList<Network.PlayingCharacterDefinition>> payload = new ArrayList<>();
        for (PlayingDevice playa : myState.playerDevices) {
            target.add(playa.pipe);
            ArrayList<Network.PlayingCharacterDefinition> matched = new ArrayList<>();
            for (int loop = 0; loop < myState.assignment.size(); loop++) {
                Integer owner = myState.assignment.get(loop);
                if(null == owner) continue; // impossible, really
                if(owner < 0) continue;
                if(myState.playerDevices.get(owner) == playa) {
                    matched.add(simplify(myState.party.usually.party[loop], loop));
                }
            }
            payload.add(matched);
        }
        new Thread(){
            @Override
            public void run() {
                Network.GroupReady send = new Network.GroupReady();
                for(int loop = 0; loop < target.size(); loop++) {
                    ArrayList<Network.PlayingCharacterDefinition> matched = payload.get(loop);
                    send.yours = new Network.PlayingCharacterDefinition[matched.size()];
                    for(int cp = 0; cp < matched.size(); cp++) send.yours[cp] = matched.get(cp);
                    try {
                        target.get(loop).writeSync(ProtoBufferEnum.GROUP_READY, send);
                    } catch (IOException e) {
                        // uhm... someone else will check this in the future... hopefully.
                    }
                }
            }
        }.start();
        new AlertDialog.Builder(this).setMessage("TODO: at this point the clients are in sequence mode. BUT... I need to refactor my architecture as this activity needs to stay afloat and go back there on need.").setTitle("TODO").show();
    }

    private class AuthDeviceViewHolder extends RecyclerView.ViewHolder {
        TextView name;
        TextView pcList;

        public AuthDeviceViewHolder(View itemView) {
            super(itemView);
            name = (TextView) itemView.findViewById(R.id.cardIDACA_name);
            pcList = (TextView) itemView.findViewById(R.id.cardIDACA_assignedPcs);
        }
    }

    private class AuthDeviceAdapter extends RecyclerView.Adapter<AuthDeviceViewHolder> {
        @Override
        public AuthDeviceViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new AuthDeviceViewHolder(getLayoutInflater().inflate(R.layout.card_identified_device_chars_assigned, parent, false));
        }

        @Override
        public void onBindViewHolder(AuthDeviceViewHolder holder, int position) {
            PlayingDevice dev = myState.playerDevices.get(position);
            holder.name.setText(getDeviceName(dev));
            String list = "";
            for (int loop = 0; loop < myState.assignment.size(); loop++) {
                Integer match = myState.assignment.get(loop);
                if(null != match && match == position) {
                    if(list.length() > 0) list += ", ";
                    list += myState.party.usually.party[loop].name;
                }
            }

            if(list.length() == 0) list = getString(R.string.ga_noPcsOnDevice);
            holder.pcList.setText(list);
        }

        @Override
        public int getItemCount() {
            return myState.playerDevices.size();
        }
    }

    private class PcViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private final OnUnassignedPcClick clickTarget;
        TextView name;
        TextView levels;
        PersistentStorage.Actor actor;

        public PcViewHolder(View itemView, OnUnassignedPcClick click) {
            super(itemView);
            clickTarget = click;
            name = (TextView)itemView.findViewById(R.id.cardACSL_name);
            levels = (TextView)itemView.findViewById(R.id.cardACSL_classesAndLevels);
            if(null != clickTarget) itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            if(null != actor && null != clickTarget) clickTarget.click(actor);
        }
    }

    private interface OnUnassignedPcClick {
        void click(PersistentStorage.Actor actor);
    }

    private class UnassignedPcsAdapter extends RecyclerView.Adapter<PcViewHolder> {
        final OnUnassignedPcClick click;

        public UnassignedPcsAdapter(OnUnassignedPcClick click) {
            setHasStableIds(true);
            this.click = click;
        }

        @Override
        public long getItemId(int position) {
            for(int scan = 0; scan < myState.assignment.size(); scan++) {
                if(null != myState.assignment.get(scan)) continue;
                if(0 == position) return scan;
                position--;
            }
            return RecyclerView.NO_ID;
        }

        @Override
        public PcViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new PcViewHolder(getLayoutInflater().inflate(R.layout.card_assignable_character_server_list, parent, false), click);
        }

        @Override
        public void onBindViewHolder(PcViewHolder holder, int position) {
            int slot;
            for(slot = 0; slot < myState.assignment.size(); slot++) {
                if(null != myState.assignment.get(slot)) continue;
                if(0 == position) break;
                position--;
            }
            holder.actor = myState.party.usually.party[slot];
            holder.name.setText(holder.actor.name);
            holder.levels.setText("<class_todo> " + holder.actor.level); // TODO
        }

        @Override
        public int getItemCount() {
            int count = 0;
            for (Integer dev : myState.assignment) {
                if(null == dev) count++;
            }
            return count;
        }
    }

    private int nextValidRequest = 0;

    private void charOwnership(MessageChannel origin, Network.CharacterOwnership payload) {
        final int ticket = payload.ticket;
        payload.ticket = nextValidRequest;
        if(ticket != nextValidRequest) {
            payload.type = Network.CharacterOwnership.OBSOLETE;
            sendingThread(ProtoBufferEnum.CHARACTER_OWNERSHIP, payload, origin);
            return;
        }
        // For this server implementation, the peer keys are just indices in an array!
        PlayingDevice requester = null;
        for (PlayingDevice match : myState.playerDevices) {
            if(origin == match.pipe) {
                requester = match;
                break;
            }
        }
        if(payload.character >= myState.party.usually.party.length || requester == null) { // requester asked chars before providing key.
            payload.type = Network.CharacterOwnership.REJECTED;
            sendingThread(ProtoBufferEnum.CHARACTER_OWNERSHIP, payload, origin);
            return;
        }
        Integer currentCharOwner = myState.assignment.get(payload.character);
        if(currentCharOwner != null && currentCharOwner < 0) { // bound to me, silently rejected as mapping to me is a very strong action
            payload.type = Network.CharacterOwnership.REJECTED;
            sendingThread(ProtoBufferEnum.CHARACTER_OWNERSHIP, payload, origin);
            return;
        }
        // taking ownership from AVAIL is silently accepted, as it giving ownership away, the first is common, the latter has human factors involved, cannot force someone to play
        if(currentCharOwner == null || myState.playerDevices.get(currentCharOwner) == requester) {
            boolean away = currentCharOwner != null && myState.playerDevices.get(currentCharOwner) == requester;
            Integer newMapping = away? null : myState.playerDevices.indexOf(requester);
            myState.assignment.set(payload.character, newMapping);
            payload.type = Network.CharacterOwnership.ACCEPTED;
            payload.ticket = ++nextValidRequest;
            sendingThread(ProtoBufferEnum.CHARACTER_OWNERSHIP, payload, origin);
            int type = away? Network.CharacterOwnership.AVAIL : Network.CharacterOwnership.BOUND;
            sendAvailability(type, payload.character, origin, nextValidRequest);
            RecyclerView rv = (RecyclerView) findViewById(R.id.ga_deviceList);
            rv.getAdapter().notifyDataSetChanged();
            rv = (RecyclerView)findViewById(R.id.ga_pcUnassignedList);
            RecyclerView.Adapter lister = rv.getAdapter();
            lister.notifyDataSetChanged();
            availablePcs(lister.getItemCount());
            if(null != dialogList) dialogList.notifyDataSetChanged();
            return;
        }
        // Serious shit. We have a collision. In a first implementation I spawned a dialog message asking the master to choose
        // but now requests must be sequential, receiving a second request would make the ticket obsolete and
        // I don't want all the other requests to be somehow blocked or rejected in the meanwhile...
        // So reject this instead. Looks like this is the only viable option.
        payload.type = Network.CharacterOwnership.REJECTED;
        sendingThread(ProtoBufferEnum.CHARACTER_OWNERSHIP, payload, origin);
    }

    private void sendAvailability(int type, int charIndex, MessageChannel excluding, int request) {
        final Network.CharacterOwnership notification = new Network.CharacterOwnership();
        notification.ticket = request;
        notification.character = charIndex;
        notification.type = type;

        final ArrayList<MessageChannel> sendTo = new ArrayList<>(myState.playerDevices.size() - 1);
        for (PlayingDevice peer : myState.playerDevices) {
            if(peer.pipe == excluding) continue; // got it already asyncronously
            sendTo.add(peer.pipe);
        }
        new Thread() {
            @Override
            public void run() {
                for (MessageChannel peer : sendTo) {
                    try {
                        peer.writeSync(ProtoBufferEnum.CHARACTER_OWNERSHIP, notification);
                    } catch (IOException e) {
                        // TODO figure out how to do this reporting errors with care!
                    }
                }
            }
        }.start();
    }

    /// TODO: transition to better activity architecture
    private void sendingThread(final int protoEnumCode, final MessageNano payload, final MessageChannel dst) {
        new Thread() {
            @Override
            public void run() {
                try {
                    dst.writeSync(protoEnumCode, payload);
                } catch (IOException e) {
                    // ughm...
                    // TODO transition to long-running resistant architecture
                }
            }
        }.start();
    }

    // ServiceConnection ___________________________________________________________________________
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        room = ((PartyJoinOrderService.LocalBinder) service).getConcreteService();
        if(!room.isForeground) {
            final android.support.v4.app.NotificationCompat.Builder help = new NotificationCompat.Builder(this)
                    .setOngoing(true)
                    .setWhen(System.currentTimeMillis())
                    .setShowWhen(true)
                    .setContentTitle(myState.party.name)
                    .setContentText(getString(R.string.ga_notificationDesc))
                    .setSmallIcon(R.drawable.ic_notify_icon)
                    .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.placeholder_todo));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                help.setCategory(Notification.CATEGORY_SERVICE);
            }
            room.startForeground(NOTIFICATION_ID, help.build());
            room.isForeground = true;
        }
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
            room.beginPublishing((NsdManager) getSystemService(NSD_SERVICE), myState.party.name);
        }
        ticker.schedule(new TimerTask() {
            @Override
            public void run() {
                funnel.sendMessage(funnel.obtainMessage(MSG_TICK));
            }
        }, TIMER_DELAY_MS, TIMER_INTERVAL_MS);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        room = null;
        new AlertDialog.Builder(this)
                .setMessage(R.string.ga_lostServiceConnection)
                .show();
    }

    private static final int NOTIFICATION_ID = 1;
}

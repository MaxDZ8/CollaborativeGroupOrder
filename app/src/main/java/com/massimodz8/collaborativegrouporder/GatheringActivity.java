package com.massimodz8.collaborativegrouporder;

import android.content.DialogInterface;
import android.net.nsd.NsdManager;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.transition.TransitionManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

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
import java.net.ServerSocket;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;

/** The server is 'gathering' player devices so they can join a new session.
 * This is important and we must be able to navigate back there every time needed in case
 * players get disconnected.
 */
public class GatheringActivity extends AppCompatActivity implements PublishedService.OnStatusChanged {
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

        private ServerSocket landing;
        private int serverPort;
        private PublishedService publisher;

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

        if(null == myState.assignment) {
            myState.assignment = new ArrayList<>();
            for (PersistentStorage.Actor aParty : myState.party.usually.party) myState.assignment.add(null);
        }

        ((RecyclerView) findViewById(R.id.ga_deviceList)).setAdapter(new AuthDeviceAdapter());
        ((RecyclerView) findViewById(R.id.ga_pcUnassignedList)).setAdapter(new UnassignedPcsAdapter(null));

        preparePumper(appState);
        if(null == myState.publisher) startPublishing();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        myState.publisher.unregisterCallback();
        final CrossActivityShare appState = (CrossActivityShare) getApplicationContext();
        appState.gaState = myState;
        appState.pumpers = null != pumper? pumper.move() : null;
        // Not so fast. As we go out of scope, several things needs to be shut down, onDestroy.
        myState = null;
    }

    @Override
    protected void onDestroy() {
        if(null != pumper) pumper.shutdown(); // if saving instance state this is empty
        if(null != acceptor) acceptor.shutdown();
        if(null != myState) { // we are not going to be recovered, so clear the persistent state
            final ServerSocket landing = myState.landing;
            final PublishedService publisher = myState.publisher;
            if(null != publisher) {
                publisher.unregisterCallback();
                new Thread() {
                    @Override
                    public void run() {
                        publisher.stopPublishing();
                        try {
                            landing.close();
                        } catch (IOException e) {
                            // It's going away anyway.
                        }
                        }
                }.start();
            }
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        this.menu = menu;
        getMenuInflater().inflate(R.menu.gathering_activity, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.ga_menu_explicitConnInfo:
                new ConnectionInfoDialog(this, myState.serverPort).show();
                break;
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
                                break;
                            }
                        }

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

    private void startPublishing() {
        if(null == myState.landing) { // then no socket can exist
            try {
                myState.landing = new ServerSocket(myState.serverPort); // if here this is zero, otherwise likely bad things will happen.
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
            myState.serverPort = myState.landing.getLocalPort();
            acceptor = new LandingServer(myState.landing) {
                @Override
                public void failedAccept() { message(MSG_FAILED_ACCEPT, null); }

                @Override
                public void connected(MessageChannel newComer) { message(MSG_CONNECTED, newComer); }
            };
        }
        if(null == myState.publisher) {
            NsdManager sys = (NsdManager) getSystemService(NSD_SERVICE);
            myState.publisher = new PublishedService(sys);
            myState.publisher.beginPublishing(myState.landing, myState.party.name, MainMenuActivity.PARTY_GOING_ADVENTURING_SERVICE_TYPE, this);
        }
        else myState.publisher.setCallback(this);
    }

    // PublishedService.OnStatusChanged() vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv
    @Override
    public void newStatus(int old, int current) { message(MSG_PUBLISHER_STATUS_CHANGED, null); }
    // PublishedService.OnStatusChanged() ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^



    private State myState;
    private Pumper pumper;
    private MyHandler funnel;
    private SecureRandom randomizer;
    private LandingServer acceptor;
    private RecyclerView.Adapter dialogList; /// so if a player takes a PC, we can update the list.
    private Menu menu;


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
                case MSG_PUBLISHER_STATUS_CHANGED: {
                    if(null == target.myState.publisher) return; // spurious signal, might happen
                    final int now = target.myState.publisher.getStatus();
                    target.publisher(now, target.myState.publisher.getErrorCode());
                    return;
                }
                case MSG_FAILED_ACCEPT:
                    new AlertDialog.Builder(target)
                            .setMessage(target.getString(R.string.ga_failedAccept))
                            .show();
                    return;
                case MSG_CONNECTED: {
                    final MessageChannel real = (MessageChannel)msg.obj;
                    target.myState.unidentified.add(new PlayingDevice(real));
                    target.pumper.pump(real);
                    // Don't signal that, comes easily.
                    break;
                }
                case MSG_HELLO: {
                    final Events.Hello real = (Events.Hello)msg.obj;
                    target.hello(real.origin, real.payload);
                    break;
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
                    sendPlayingCharacterLists(dev);
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

    private void sendPlayingCharacterLists(PlayingDevice dev) {
        final Network.PlayingCharacterList avail = new Network.PlayingCharacterList();
        final Network.PlayingCharacterList yours = new Network.PlayingCharacterList();
        final Network.PlayingCharacterList ready = new Network.PlayingCharacterList();
        Network.PlayingCharacterDefinition[] list = new Network.PlayingCharacterDefinition[0];
        for(int loop = 0; loop < myState.assignment.size(); loop++) {
            if(null != myState.assignment.get(loop)) {
                Network.PlayingCharacterDefinition[] longer = Arrays.copyOf(list, list.length + 1);
                longer[list.length] = simplify(myState.party.usually.party[loop], loop);
                list = longer;
            }
        }
        ready.payload = list;
        ready.set = Network.PlayingCharacterList.READY;
        // The avail and yours are a bit more complicated, mostly because...
        // TODO further logic to be deployed there! Or perhaps before this is even called, IDK. Requires server to remember last assignments and device keys.
        // TODO For the time being, yours is easy but I still implement it like something else already assigned me my chars.list = new Network.PlayingCharacterDefinition[0];
        int devIndex = 0;
        for (int loop = 0; loop < myState.playerDevices.size(); loop++) {
            if(dev == myState.playerDevices.get(loop)) {
                devIndex = loop;
                break;
            }
        }
        for(int loop = 0; loop < myState.assignment.size(); loop++) {
            Integer binding = myState.assignment.get(loop);
            if(null != binding && binding == devIndex) {
                Network.PlayingCharacterDefinition[] longer = Arrays.copyOf(list, list.length + 1);
                longer[list.length] = simplify(myState.party.usually.party[loop], loop);
                list = longer;
            }
        }
        yours.payload = list;
        yours.set = Network.PlayingCharacterList.YOURS;
        // TODO for the time being, available chars are everything which is not ready but in the future, I might restrict character selection on a pe-device basis.list = new Network.PlayingCharacterDefinition[0];
        for(int loop = 0; loop < myState.assignment.size(); loop++) {
            if(null == myState.assignment.get(loop)) {
                Network.PlayingCharacterDefinition[] longer = Arrays.copyOf(list, list.length + 1);
                longer[list.length] = simplify(myState.party.usually.party[loop], loop);
                list = longer;
            }
        }
        avail.payload = list;
        avail.set = Network.PlayingCharacterList.AVAIL;
        // An now send... as usual there's the "what if" the activity goes away before we complete.
        // What I do: nothing. The socket will fail later... I hope. IDK. I still cannot find any decent solution.
        final MessageChannel dst = dev.pipe;
        new Thread() {
            @Override
            public void run() {
                try {
                    dst.writeSync(ProtoBufferEnum.CHARACTER_LIST, yours);
                    dst.writeSync(ProtoBufferEnum.CHARACTER_LIST, ready);
                    dst.writeSync(ProtoBufferEnum.CHARACTER_LIST, avail);
                } catch (IOException e) {
                    // suppress and let's hope it's a persistent thing which will cause stuff to go awry.
                    // TODO: in the future, tick for 'operation completeness' and have a queue of things to be checked. Ugly.
                }
            }
        }.start();
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
        boolean clear = false;
        switch(state) {
            case PublishedService.STATUS_START_FAILED:
                dst.setText(R.string.ga_publisherFailedStart);
                new AlertDialog.Builder(this).setMessage(String.format(getString(R.string.ga_failedServiceRegistration), MaxUtils.NsdManagerErrorToString(err, this))).show();
                clear = true;
                break;
            case PublishedService.STATUS_PUBLISHING:
                dst.setText(R.string.ga_publishing);
                break;
            case PublishedService.STATUS_STOP_FAILED:
                dst.setText(R.string.ga_publishingStopFailed);
                break;
            case PublishedService.STATUS_STOPPED:
                dst.setText(R.string.ga_noMorePublishing);
                clear = true;
        }
        if(clear) myState.publisher = null;
    }

    private static final int MSG_DISCONNECTED = 1;
    private static final int MSG_DETACHED = 2;
    private static final int MSG_NO_VERIFY = 3;
    private static final int MSG_AUTH_TOKEN_SENT = 5;
    private static final int MSG_PUBLISHER_STATUS_CHANGED = 6;
    private static final int MSG_FAILED_ACCEPT = 7;
    private static final int MSG_CONNECTED = 8;
    private static final int MSG_HELLO = 9;

    private static final int DOORMAT_BYTES = 32;

    public void startSession_callback(View btn) {
        new AlertDialog.Builder((this)).setMessage("TODO").show();
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
}

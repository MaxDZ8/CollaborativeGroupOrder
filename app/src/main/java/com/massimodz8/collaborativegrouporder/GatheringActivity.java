package com.massimodz8.collaborativegrouporder;

import android.content.DialogInterface;
import android.net.nsd.NsdManager;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.transition.TransitionManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
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
import java.security.SecureRandom;
import java.util.Timer;
import java.util.TimerTask;

/** The server is 'gathering' player devices so they can join a new session.
 * This is important and we must be able to navigate back there every time needed in case
 * players get disconnected.
 */
public class GatheringActivity extends AppCompatActivity {
    public static class State {
        final PersistentStorage.PartyOwnerData.Group party;
        //^ When the Activity is launched this always contains the thing we need. It is just assumed.

        private ServerSocket landing;
        private int serverPort;
        private PublishedService publisher;
        private int prevPublisherStatus = PublishedService.STATUS_IDLE;

        public State(PersistentStorage.PartyOwnerData.Group party) {
            this.party = party;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gathering);
        final CrossActivityShare appState = (CrossActivityShare) getApplicationContext();
        myState = appState.gaState;
        appState.gaState = null;

        ((RecyclerView) findViewById(R.id.ga_deviceList)).setAdapter(new AuthDeviceAdapter());
        ((RecyclerView) findViewById(R.id.ga_pcStatusList)).setAdapter(new PlayingCharactersAdapter());

        preparePumper(appState);
        if(null == myState.publisher) startPublishing();
        ticker = new Timer("publish status refresh");
        ticker.schedule(new TimerTask() {
            @Override
            public void run() {
                message(MSG_TICK, null);
            }
        }, PUBLISH_CHECK_DELAY_MS, PUBLISH_CHECK_INTERVAL_MS);
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
        if(null != ticker) ticker.cancel();
        if(null != pumper) pumper.shutdown(); // if saving instance state this is empty
        if(null != acceptor) acceptor.shutdown();
        if(null != myState) { // we are not going to be recovered, so clear the persistent state
            final ServerSocket landing = myState.landing;
            final PublishedService publisher = myState.publisher;
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
                final AlertDialog custom = new AlertDialog.Builder(this).create();
                final FrameLayout content = (FrameLayout) custom.findViewById(android.R.id.custom);
                getLayoutInflater().inflate(R.layout.dialog_make_pc_local, content, true);
                final RecyclerView list = (RecyclerView) content.findViewById(R.id.ga_localPcsDialog_list);
                dialogList = new UnassignedPcsLister(custom);
                list.setAdapter(dialogList);
            }
        }
        return true;
    }

    void preparePumper(CrossActivityShare appState) {
        funnel = new MyHandler(this);
        pumper = new Pumper(funnel, MSG_DISCONNECTED, MSG_DETACHED)
                .add(ProtoBufferEnum.HELLO, new PumpTarget.Callbacks<Network.Hello>() {
                    @Override
                    public Network.Hello make() { return new Network.Hello(); }

                    @Override
                    public boolean mangle(MessageChannel from, Network.Hello msg) throws IOException {
                        if(msg.version != MainMenuActivity.NETWORK_VERSION) {
                            message(MSG_VERSION_MISMATCH, from);
                            return true;
                        }
                        Network.GroupInfo send = new Network.GroupInfo();
                        send.forming = false;
                        send.name = myState.party.name;
                        send.version = MainMenuActivity.NETWORK_VERSION;
                        if(msg.verifyMe) { // we play nice with the probing clients here, they're legit after all.
                            send.doormat = random(DOORMAT_BYTES);
                        }
                        from.writeSync(ProtoBufferEnum.GROUP_INFO, send);
                        if(msg.verifyMe) message(MSG_AUTH_TOKEN_SENT, new Events.AuthToken(from, send.doormat));
                        else message(MSG_NO_VERIFY, from);
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
            myState.publisher.beginPublishing(myState.landing, MainMenuActivity.PARTY_GOING_ADVENTURING_SERVICE_TYPE, myState.party.name);
        }
    }



    private State myState;
    private Pumper pumper;
    private MyHandler funnel;
    private SecureRandom randomizer;
    private Timer ticker;
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
                case MSG_VERSION_MISMATCH:
                    new AlertDialog.Builder(target).setMessage("todo! MSG_VERSION_MISMATCH").show();
                    return;
                case MSG_AUTH_TOKEN_SENT:
                    new AlertDialog.Builder(target).setMessage("todo! MSG_AUTH_TOKEN_SENT").show();
                    return;
                case MSG_TICK: {
                    final int now = target.myState.publisher.getStatus();
                    if(now == target.myState.prevPublisherStatus) return;
                    target.publisher(now);
                    target.myState.prevPublisherStatus = now;
                    return;
                }
                case MSG_FAILED_ACCEPT:
                    new AlertDialog.Builder(target).setMessage("todo! MSG_FAILED_ACCEPT").show();
                    return;
                case MSG_CONNECTED:
                    new AlertDialog.Builder(target).setMessage("todo! MSG_CONNECTED").show();
            }
        }
    }


    private void publisher(int state) {
        if(state == PublishedService.STATUS_STARTING) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            TransitionManager.beginDelayedTransition((ViewGroup) findViewById(R.id.ga_activityRoot));
        }
        final TextView dst = (TextView) findViewById(R.id.ga_state);
        boolean clear = false;
        ticker.cancel();
        ticker = null;
        switch(state) {
            case PublishedService.STATUS_START_FAILED:
                dst.setText(R.string.ga_publisherFailedStart);
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
    private static final int MSG_VERSION_MISMATCH = 4;
    private static final int MSG_AUTH_TOKEN_SENT = 5;
    private static final int MSG_TICK = 6;
    private static final int MSG_FAILED_ACCEPT = 7;
    private static final int MSG_CONNECTED = 8;

    private static final int DOORMAT_BYTES = 32;
    private static final int PUBLISH_CHECK_DELAY_MS = 1000;
    private static final int PUBLISH_CHECK_INTERVAL_MS = 1000;

    private class UnassignedPcsLister extends RecyclerView.Adapter {
        final AlertDialog dialog; // TODO: dismiss this when one is clicked.

        public UnassignedPcsLister(AlertDialog dialog) {
            this.dialog = dialog;
        }

        class StubHolder extends RecyclerView.ViewHolder {
            public StubHolder(View itemView) {
                super(itemView);
            }
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            TextView meh = new TextView(GatheringActivity.this);
            meh.setText("TODO");
            return new StubHolder(meh);
            // TODO
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            // TODO
        }

        @Override
        public int getItemCount() {
            // TODO
            return 0;
        }
    }

    private class AuthDeviceViewHolder extends RecyclerView.ViewHolder {
        public AuthDeviceViewHolder(View itemView) {
            super(itemView);
        }
    }

    private class AuthDeviceAdapter extends RecyclerView.Adapter<AuthDeviceViewHolder> {
        @Override
        public AuthDeviceViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            TextView meh = new TextView(GatheringActivity.this);
            meh.setText("TODO");
            return new AuthDeviceViewHolder(meh);
        }

        @Override
        public void onBindViewHolder(AuthDeviceViewHolder holder, int position) {
            // TODO
        }

        @Override
        public int getItemCount() {
            // TODO
            return 0;
        }
    }

    private class PcViewHolder extends RecyclerView.ViewHolder {
        public PcViewHolder(View itemView) {
            super(itemView);
        }
    }

    private class PlayingCharactersAdapter extends RecyclerView.Adapter<PcViewHolder> {
        @Override
        public PcViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            TextView meh = new TextView(GatheringActivity.this);
            meh.setText("TODO");
            return new PcViewHolder(meh);
        }

        @Override
        public void onBindViewHolder(PcViewHolder holder, int position) {
            // TODO
        }

        @Override
        public int getItemCount() {
            // TODO
            return 0;
        }
    }
}

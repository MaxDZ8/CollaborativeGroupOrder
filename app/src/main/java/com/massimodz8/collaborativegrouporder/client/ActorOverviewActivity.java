package com.massimodz8.collaborativegrouporder.client;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.design.widget.Snackbar;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.AdventuringActorAdapter;
import com.massimodz8.collaborativegrouporder.AdventuringActorDataVH;
import com.massimodz8.collaborativegrouporder.InterstitialAdPlaceholderActivity;
import com.massimodz8.collaborativegrouporder.MaxUtils;
import com.massimodz8.collaborativegrouporder.MyActorRoundActivity;
import com.massimodz8.collaborativegrouporder.PreSeparatorDecorator;
import com.massimodz8.collaborativegrouporder.R;
import com.massimodz8.collaborativegrouporder.RunningServiceHandles;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.networkio.ProtoBufferEnum;
import com.massimodz8.collaborativegrouporder.networkio.Pumper;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;
import com.massimodz8.collaborativegrouporder.protocol.nano.StartData;

import java.io.IOException;
import java.util.Locale;

/**
 * Start this when assignment completed. It is signaled by a GroupReady message with a YOURS field.
 * However, when this starts, the service does not contain list of played here ids! It is taken from
 * static data and copied into the service so we can understand if this is first start.
 *
 * The first thing to do is to wait the server to send the definitions.
 * Then the server will send a Roll request with type T_BATTLE_START.
 * We then wait for definition of all actors involved and known orders in this sequence.
 * Server implementation guarantees all actors to be defined before orders are sent so all IDs referred
 * by orders are defined in advance.
 * Then we have TurnControl and real game.
 */
public class ActorOverviewActivity extends AppCompatActivity {
    // Before starting this activity, make sure to populate its connection parameters and friends.
    // Those will be cleared as soon as the activity goes onCreate and then never reused again.
    // onCreate assumes those non-null. Just call prepare(...)
    private static Pumper.MessagePumpingThread serverWorker; // in
    private static StartData.PartyClientData.Group connectedParty; // in
    private static int[] actorKeys; // in, server ids of actors to manage here

    public static void prepare(StartData.PartyClientData.Group group, int[] actorKeys_, Pumper.MessagePumpingThread serverWorker_) {
        connectedParty = group;
        actorKeys = actorKeys_;
        serverWorker = serverWorker_;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_actor_overview);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        final ActionBar sab = getSupportActionBar();
        if(null != sab) sab.setDisplayHomeAsUpEnabled(true);

        final AdventuringService ticker = RunningServiceHandles.getInstance().clientPlay;
        ticker.onActorUpdated.push(new Runnable() {
            boolean first = true;
            @Override
            public void run() {
                int known = 0, knownOrders = 0;
                for (int id : ticker.playedHere) {
                    final AdventuringService.ActorWithKnownOrder awo = ticker.actors.get(id);
                    if (awo != null && awo.actor != null) {
                        known++;
                        if(awo.keyOrder != null) knownOrders++;
                        if(awo.xpReceived != 0) {
                            String text = getString(R.string.aoa_xpReward);
                            text = String.format(Locale.getDefault(), text, awo.actor.name, awo.xpReceived, awo.actor.experience);
                            Snackbar.make(findViewById(R.id.activityRoot), text, Snackbar.LENGTH_INDEFINITE).show();
                            awo.xpReceived = 0;
                            awo.updated = false;
                        }
                    }
                }
                if (known == 0) return;
                if (first) {
                    first = false;
                    MaxUtils.beginDelayedTransition(ActorOverviewActivity.this);
                    final RecyclerView rv = (RecyclerView) findViewById(R.id.aoa_list);
                    rv.setAdapter(lister);
                    rv.addItemDecoration(new PreSeparatorDecorator(rv, ActorOverviewActivity.this) {
                        @Override
                        protected boolean isEligible(int position) {
                            return position != 0;
                        }
                    });
                    rv.setVisibility(View.VISIBLE);
                    if(ticker.playedHere.length > 1) return;
                }
                MaxUtils.beginDelayedTransition(ActorOverviewActivity.this);
                final TextView status = (TextView) findViewById(R.id.aoa_status);
                if(ticker.round == AdventuringService.ROUND_NOT_FIGHTING) {
                    status.setText(R.string.aoa_waitingForBattleStart);
                    final ActionBar sab = getSupportActionBar();
                    if(sab != null) sab.setTitle(R.string.aoa_title);
                }
                else if(ticker.round == 0) status.setText(R.string.aoa_waitingOtherPlayersRoll);
                else status.setText(R.string.aoa_waitingMyTurn);
                lister.notifyDataSetChanged();
                if(knownOrders == ticker.playedHere.length) rollDialog = null;
            }
        });
        ticker.onRollRequestPushed.push(new Runnable() {
            @Override
            public void run() {
                if (rollDialog != null) return;
                if (ticker.rollRequests.isEmpty()) return; // async recursion stop
                final Network.Roll request = ticker.rollRequests.getFirst();
                final Vibrator vibro = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (vibro != null && vibro.hasVibrator()) {
                    final long[] intervals = {0, 350, 300, 250};
                    vibro.vibrate(intervals, -1);
                }
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                if(request.type == Network.Roll.T_INITIATIVE) {
                    final ActionBar sab = getSupportActionBar();
                    if(sab != null) sab.setTitle(R.string.aoa_title_fighting);
                    // todo: update round count into title bar!
                }
                if(request.type == Network.Roll.T_INITIATIVE) {
                    final TextView status = (TextView) findViewById(R.id.aoa_status);
                    status.setText(R.string.aoa_waitingOtherPlayersRoll);
                }

                final Network.ActorState actor = ticker.actors.get(request.peerKey).actor;
                rollDialog = new RollInitiativeDialog(actor, request, new SendRollCallback(), ActorOverviewActivity.this);
            }
        });
        ticker.onCurrentActorChanged.push(new Runnable() {
            @Override
            public void run() {
                final TextView status = (TextView) findViewById(R.id.aoa_status);
                if(ticker.round == AdventuringService.ROUND_NOT_FIGHTING) {
                    status.setText(R.string.aoa_waitingForBattleStart);
                    final ActionBar sab = getSupportActionBar();
                    if(sab != null) sab.setTitle(R.string.aoa_title);
                }
                else if(ticker.round == 0) status.setText(R.string.aoa_waitingOtherPlayersRoll);
                else status.setText(R.string.aoa_waitingMyTurn);

                final ActionBar sab = getSupportActionBar();
                if(sab != null) sab.setTitle(ticker.round == AdventuringService.ROUND_NOT_FIGHTING? R.string.aoa_title : R.string.aoa_title_fighting);
                if(ticker.round == AdventuringService.ROUND_NOT_FIGHTING) return;
                boolean here = false;
                for (int key : ticker.playedHere) {
                    if (key == ticker.currentActor) {
                        here = true;
                        break;
                    }
                }
                if (!here) return; // in the future maybe current actor will be signaled to other peers as well, not now!
                final Intent intent = new Intent(ActorOverviewActivity.this, MyActorRoundActivity.class)
                        .putExtra(MyActorRoundActivity.EXTRA_CLIENT_MODE, true)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivityForResult(intent, REQUEST_TURN);
            }
        });
        if(serverWorker != null) { // Initialize service and start pumping.
            serverPipe = serverWorker.getSource();
            ticker.playedHere = actorKeys;
            ticker.pipe = serverWorker.getSource();
            ticker.netPump.pump(serverWorker);
            serverWorker = null;
            actorKeys = null;
        }
        ticker.onActorUpdated.getFirst().run();
        ticker.onRollRequestPushed.getFirst().run();
        ticker.onCurrentActorChanged.getFirst().run();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        final AdventuringService ticker = RunningServiceHandles.getInstance().clientPlay;
        if(ticker != null) {
            ticker.onActorUpdated.pop();
            ticker.onCurrentActorChanged.pop();
            ticker.onRollRequestPushed.pop();
        }
        if(rollDialog != null) {
            rollDialog.dlg.dismiss(); // I'm going to regenerate this next time anyway.
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onBackPressed() { confirmLeaveFinish(); }
    @Override
    public boolean onSupportNavigateUp() {
        confirmLeaveFinish();
        return false;
    }

    private void confirmLeaveFinish() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.generic_carefulDlgTitle)
                .setMessage(R.string.aoa_confirmBackDlgMessage)
                .setPositiveButton(R.string.aoa_confirmBackDlgPositiveButton, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) { ActorOverviewActivity.super.onBackPressed(); }
                })
                .show();
    }

    private RollInitiativeDialog rollDialog;
    private MessageChannel serverPipe;
    private AdventuringActorAdapter<AdventuringActorDataVH> lister = new AdventuringActorAdapter<AdventuringActorDataVH>() {
        final AdventuringService ticker = RunningServiceHandles.getInstance().clientPlay;
        @Override
        public AdventuringActorDataVH onCreateViewHolder(ViewGroup parent, int viewType) {
            return new AdventuringActorDataVH(getLayoutInflater().inflate(R.layout.vh_adventuring_actor_data, parent, false)) {
                @Override
                public void onClick(View v) { }
            };
        }

        @Override
        public void onBindViewHolder(AdventuringActorDataVH holder, int position) {
            holder.bindData(ticker.actors.get(ticker.playedHere[position]).actor);
            holder.prepared.setEnabled(false);
        }

        @Override
        public int getItemCount() {
            if(ticker == null || ticker.playedHere == null) return 0;
            int count = 0;
            for (int key : ticker.playedHere) {
                final AdventuringService.ActorWithKnownOrder known = ticker.actors.get(key);
                if(known != null && known.actor != null) count++;
            }
            return count;
        }

        @Override
        protected boolean isCurrent(Network.ActorState actor) {
            return ticker == null || ticker.currentActor == actor.peerKey;
        }

        @Override
        public Network.ActorState getActorByPos(int position) {
            for (int key : ticker.playedHere) {
                final AdventuringService.ActorWithKnownOrder known = ticker.actors.get(key);
                if(known != null && known.actor != null) {
                    if(position == 0) return known.actor;
                }
                position--;
            }
            return null;
        }

        @Override
        protected LayoutInflater getLayoutInflater() {
            return ActorOverviewActivity.this.getLayoutInflater();
        }
    };

    private class SendRollCallback implements Runnable {
        @Override
        public void run() {
            final AdventuringService ticker = RunningServiceHandles.getInstance().clientPlay;
            final Network.Roll ready = ticker.rollRequests.pop();
            final Network.Roll reply = new Network.Roll();
            reply.result = ready.result;
            reply.unique = ready.unique;
            reply.peerKey = ready.peerKey;
            new Thread() {
                @Override
                public void run() {
                    try {
                        serverPipe.writeSync(ProtoBufferEnum.ROLL, reply);
                    } catch (IOException e) {
                        // todo: collect those in the service mailman maybe and have better error support.
                    }
                }
            }.start();
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            rollDialog.dlg.dismiss();
            rollDialog = null;
            ticker.onRollRequestPushed.getFirst().run(); // there might be more rolls pending.
        }
    }

    private static final int REQUEST_TURN = 1;

    private static final int CLIENT_ONLY_INTERSTITIAL_FREQUENCY_DIVIDER = 2;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == REQUEST_TURN) {
            final AdventuringService ticker = RunningServiceHandles.getInstance().clientPlay;
            lister.notifyDataSetChanged();
            ticker.onCurrentActorChanged.getFirst().run(); // maybe not, but easy to check
            if (resultCode == RESULT_OK) {
                ticker.ticksSinceLastAd++;
                boolean admobReady = true;
                if (ticker.ticksSinceLastAd >= ticker.playedHere.length * CLIENT_ONLY_INTERSTITIAL_FREQUENCY_DIVIDER && admobReady) {
                    ticker.ticksSinceLastAd -= ticker.playedHere.length * CLIENT_ONLY_INTERSTITIAL_FREQUENCY_DIVIDER;
                    startActivity(new Intent(this, InterstitialAdPlaceholderActivity.class));
                }
            }
            else { // we have somehow got out. MARA is protected against accidental exit so...
                ticker.onCurrentActorChanged.getFirst().run();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onResume() {
        final AdventuringService ticker = RunningServiceHandles.getInstance().clientPlay;
        ticker.onCurrentActorChanged.getFirst().run(); // maybe not. But convenient to mangle round and update UI.
        ticker.onActorUpdated.getFirst().run();
        super.onResume();
    }
}

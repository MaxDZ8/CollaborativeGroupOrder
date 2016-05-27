package com.massimodz8.collaborativegrouporder.client;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.ActorId;
import com.massimodz8.collaborativegrouporder.AdventuringActorDataVH;
import com.massimodz8.collaborativegrouporder.HoriSwipeOnlyTouchCallback;
import com.massimodz8.collaborativegrouporder.InterstitialAdPlaceholderActivity;
import com.massimodz8.collaborativegrouporder.MaxUtils;
import com.massimodz8.collaborativegrouporder.MyActorRoundActivity;
import com.massimodz8.collaborativegrouporder.PreSeparatorDecorator;
import com.massimodz8.collaborativegrouporder.R;
import com.massimodz8.collaborativegrouporder.RunningServiceHandles;
import com.massimodz8.collaborativegrouporder.SendRequest;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.networkio.ProtoBufferEnum;
import com.massimodz8.collaborativegrouporder.networkio.Pumper;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;
import com.massimodz8.collaborativegrouporder.protocol.nano.StartData;

import java.text.DecimalFormat;

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
    private static int[] actorKeys; // in, server ids of actors to manage here

    public static void prepare(StartData.PartyClientData.Group group, int[] actorKeys_, Pumper.MessagePumpingThread serverWorker_) {
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
        final RecyclerView rv = (RecyclerView) findViewById(R.id.aoa_list);
        rv.setAdapter(lister);
        rv.addItemDecoration(new PreSeparatorDecorator(rv, ActorOverviewActivity.this) {
            @Override
            protected boolean isEligible(int position) {
                final int type = rv.getAdapter().getItemViewType(position);
                return position != 0 && type != MyActorAdapter.T_AWARD;
            }
        });
        new HoriSwipeOnlyTouchCallback(rv) {
            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                if(!(viewHolder instanceof ExperienceAwardVH)) return; // impossible!
                ExperienceAwardVH real = (ExperienceAwardVH)viewHolder;
                final AdventuringService.ActorWithKnownOrder actor = ticker.actors.get(real.peerKey);
                if(actor == null) return; // very unlikely
                actor.xpReceived = 0;
                rv.getAdapter().notifyDataSetChanged();
            }

            @Override
            protected boolean disable() { return false; }

            @Override
            protected boolean canSwipe(RecyclerView rv, RecyclerView.ViewHolder vh) { return vh instanceof ExperienceAwardVH; }
        };

        updateCall = ticker.onActorUpdated.put(new Runnable() {
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
                            lister.notifyDataSetChanged();
                            // awo.xpReceived = 0; // no more here, keep displaying until swiped away
                            awo.updated = false;
                        }
                    }
                }
                if (known == 0) return;
                if (first) {
                    first = false;
                    MaxUtils.beginDelayedTransition(ActorOverviewActivity.this);
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
        rollRequestCall = ticker.onRollRequestPushed.put(new Runnable() {
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
        actorChangedCall = ticker.onCurrentActorChanged.put(new Runnable() {
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
        endedCall = ticker.onSessionEnded.put(new Runnable() {
            @Override
            public void run() {
                setResult(RESULT_GOODBYE);
                finish();
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
        ticker.onActorUpdated.get().run();
        ticker.onRollRequestPushed.get().run();
        ticker.onCurrentActorChanged.get().run();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        final AdventuringService ticker = RunningServiceHandles.getInstance().clientPlay;
        if(ticker != null) {
            ticker.onActorUpdated.remove(updateCall);
            ticker.onCurrentActorChanged.remove(actorChangedCall);
            ticker.onRollRequestPushed.remove(rollRequestCall);
            ticker.onSessionEnded.remove(endedCall);
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
        new AlertDialog.Builder(this, R.style.AppDialogStyle)
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
    private RecyclerView.Adapter lister = new MyActorAdapter();

    private class MyActorAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        final AdventuringService ticker = RunningServiceHandles.getInstance().clientPlay;

        public MyActorAdapter() {
            setHasStableIds(true);
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            switch (viewType) {
                case T_ACTOR: return new AdventuringActorDataVH(getLayoutInflater().inflate(R.layout.vh_adventuring_actor_data, parent, false)) {
                    @Override
                    public void onClick(View v) { }
                };
                case T_AWARD: return new ExperienceAwardVH(getLayoutInflater().inflate(R.layout.vh_xp_awarded, parent, false));
            }
            return null;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            Network.ActorState as = null;
            int xp = 0;
            for (int key : ticker.playedHere) {
                final AdventuringService.ActorWithKnownOrder known = ticker.actors.get(key);
                if(known != null && known.actor != null) {
                    if(position == 0) {
                        as = known.actor;
                        break;
                    }
                    position--;
                    if(known.xpReceived != 0) {
                        if(position == 0) {
                            as = known.actor;
                            xp = known.xpReceived;
                            break;
                        }
                        position--;
                    }
                }
            }
            if(as == null) return; // impossible
            if(holder instanceof AdventuringActorDataVH) {
                AdventuringActorDataVH real = (AdventuringActorDataVH)holder;
                real.bindData(as);
            }
            if(holder instanceof ExperienceAwardVH) {
                ExperienceAwardVH real = (ExperienceAwardVH)holder;
                real.peerKey = as.peerKey;
                DecimalFormat thousands = new DecimalFormat("###,###,###,###");
                String value = thousands.format(xp);
                real.awarded.setText(String.format(getString(R.string.aoa_xpIncrement), value));
                value = thousands.format(as.experience);
                real.total.setText(String.format(getString(R.string.aoa_xpTotal), value));
            }
        }

        @Override
        public int getItemCount() {
            if(ticker == null || ticker.playedHere == null) return 0;
            int count = 0;
            for (int key : ticker.playedHere) {
                final AdventuringService.ActorWithKnownOrder known = ticker.actors.get(key);
                if(known != null && known.actor != null) {
                    count++;
                    if (known.xpReceived != 0) count++;
                }
            }
            return count;
        }

        @Override
        public long getItemId(int position) {
            if(ticker == null || ticker.playedHere == null) return RecyclerView.NO_ID;
            for (int key : ticker.playedHere) {
                final AdventuringService.ActorWithKnownOrder known = ticker.actors.get(key);
                if(known != null && known.actor != null) {
                    if (position == 0) return known.actor.peerKey * OPTIONAL_HOLDERS_COUNT;
                    position--;
                    if (known.xpReceived != 0) {
                        if(position == 0) return known.actor.peerKey * OPTIONAL_HOLDERS_COUNT + XP_AWARD_OFFSET;
                        position--;
                    }
                }
            }
            return RecyclerView.NO_ID;
        }

        @Override
        public int getItemViewType(int position) {
            if(ticker == null || ticker.playedHere == null) return 0; // impossible
            for (int key : ticker.playedHere) {
                final AdventuringService.ActorWithKnownOrder known = ticker.actors.get(key);
                if(known != null && known.actor != null) {
                    if(position == 0) return T_ACTOR;
                    position--;
                    if(known.xpReceived != 0) {
                        if(position == 0) return T_AWARD;
                        position--;
                    }
                }
            }
            return 0; // impossible
        }

        static final int T_ACTOR = 1, T_AWARD = 2;
        static final int OPTIONAL_HOLDERS_COUNT = 2;
        static final int XP_AWARD_OFFSET = 1;
    }

    private static class ExperienceAwardVH  extends RecyclerView.ViewHolder {
        public static final int UNBOUND = -1;
        public @ActorId int peerKey = UNBOUND;

        public ExperienceAwardVH(View iv) {
            super(iv);
            awarded = (TextView)iv.findViewById(R.id.vhXA_awarded);
            total = (TextView)iv.findViewById(R.id.vhXA_total);
        }

        final TextView awarded, total;
    }

    private class SendRollCallback implements Runnable {
        @Override
        public void run() {
            final AdventuringService ticker = RunningServiceHandles.getInstance().clientPlay;
            final Network.Roll ready = ticker.rollRequests.removeFirst();
            final Network.Roll reply = new Network.Roll();
            reply.result = ready.result;
            reply.unique = ready.unique;
            reply.peerKey = ready.peerKey;
            ticker.mailman.out.add(new SendRequest(serverPipe, ProtoBufferEnum.ROLL, reply, null));
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            rollDialog.dlg.dismiss();
            rollDialog = null;
            ticker.onRollRequestPushed.get().run(); // there might be more rolls pending.
        }
    }

    private static final int REQUEST_TURN = 1;

    private static final int CLIENT_ONLY_INTERSTITIAL_FREQUENCY_DIVIDER = 2;

    public static final int RESULT_GOODBYE = RESULT_FIRST_USER;

    private int updateCall, rollRequestCall, actorChangedCall, endedCall;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        final AdventuringService ticker = RunningServiceHandles.getInstance().clientPlay;
        if(requestCode == REQUEST_TURN) {
            lister.notifyDataSetChanged();
            if (resultCode == RESULT_OK) {
                ticker.ticksSinceLastAd++;
                boolean admobReady = true;
                if (ticker.ticksSinceLastAd >= ticker.playedHere.length * CLIENT_ONLY_INTERSTITIAL_FREQUENCY_DIVIDER && admobReady) {
                    ticker.ticksSinceLastAd -= ticker.playedHere.length * CLIENT_ONLY_INTERSTITIAL_FREQUENCY_DIVIDER;
                    startActivity(new Intent(this, InterstitialAdPlaceholderActivity.class));
                    return;
                }
            }
        }
        ticker.onCurrentActorChanged.get().run();
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onResume() {
        final AdventuringService ticker = RunningServiceHandles.getInstance().clientPlay;
        ticker.onCurrentActorChanged.get().run(); // maybe not. But convenient to mangle round and update UI.
        ticker.onActorUpdated.get().run();
        super.onResume();
    }
}

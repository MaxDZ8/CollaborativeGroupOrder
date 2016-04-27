package com.massimodz8.collaborativegrouporder.client;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.NotificationCompat;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.AbsLiveActor;
import com.massimodz8.collaborativegrouporder.AdventuringActorAdapter;
import com.massimodz8.collaborativegrouporder.AdventuringActorDataVH;
import com.massimodz8.collaborativegrouporder.CharacterActor;
import com.massimodz8.collaborativegrouporder.MaxUtils;
import com.massimodz8.collaborativegrouporder.PreSeparatorDecorator;
import com.massimodz8.collaborativegrouporder.R;
import com.massimodz8.collaborativegrouporder.networkio.Pumper;
import com.massimodz8.collaborativegrouporder.protocol.nano.StartData;

import java.util.IdentityHashMap;

public class ActorOverviewActivity extends AppCompatActivity implements ServiceConnection {
    // Before starting this activity, make sure to populate its connection parameters and friends.
    // Those will be cleared as soon as the activity goes onCreate and then never reused again.
    // onCreate assumes those non-null. Just call prepare(...)
    private static Pumper.MessagePumpingThread serverPipe; // in
    private static StartData.PartyClientData.Group connectedParty; // in
    private static int[] actorKeys; // in, server ids of actors to manage here

    public static void prepare(StartData.PartyClientData.Group group, int[] actorKeys_, Pumper.MessagePumpingThread serverWorker) {
        connectedParty = group;
        actorKeys = actorKeys_;
        serverPipe = serverWorker;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_actor_overview);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        final ActionBar sab = getSupportActionBar();
        if(null != sab) sab.setDisplayHomeAsUpEnabled(true);

        final Intent intent = new Intent(this, AdventuringService.class);
        if(savedInstanceState == null) startService(intent);
        if(!bindService(intent, this, 0)) {
            stopService(intent);
            MaxUtils.beginDelayedTransition(this);
            final TextView status = (TextView) findViewById(R.id.aoa_status);
            status.setText(R.string.client_failedServiceBind);
            return;
        }
        mustUnbind = true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mustUnbind) unbindService(this);
        if(!isChangingConfigurations()) {
            ticker.stopForeground(true);
            final Intent intent = new Intent(this, AdventuringService.class);
            stopService(intent);
        }
    }

    boolean mustUnbind;
    static final int NOTIFICATION_ID = 100;
    private AdventuringService ticker;
    private IdentityHashMap<AbsLiveActor, Integer> actorIds = new IdentityHashMap<>();
    private RecyclerView.Adapter lister = new AdventuringActorAdapter<AdventuringActorDataVH>(actorIds) {
        @Override
        public AdventuringActorDataVH onCreateViewHolder(ViewGroup parent, int viewType) {
            return new AdventuringActorDataVH(getLayoutInflater().inflate(R.layout.vh_adventuring_actor_data, parent, false)) {
                @Override
                public void onClick(View v) { }
            };
        }

        @Override
        public void onBindViewHolder(AdventuringActorDataVH holder, int position) {
            holder.bindData(ticker.playedHere[position]);
        }

        @Override
        public int getItemCount() {
            return ticker == null || ticker.playedHere == null? 0 : ticker.playedHere.length;
        }

        @Override
        protected boolean isCurrent(AbsLiveActor actor) {
            return ticker == null || ticker.currentActor == actor;
        }

        @Override
        protected AbsLiveActor getActorByPos(int position) {
            return ticker == null || ticker.playedHere == null? null : ticker.playedHere[position];
        }

        @Override
        protected boolean enabledSetOrGet(AbsLiveActor actor, @Nullable Boolean newValue) {
            return false; // we're not interested in this one
        }

        @Override
        protected LayoutInflater getLayoutInflater() {
            return ActorOverviewActivity.this.getLayoutInflater();
        }
    };

    private void actorDataFetched() {
        MaxUtils.beginDelayedTransition(this);
        final TextView status = (TextView) findViewById(R.id.aoa_status);
        status.setText(R.string.aoa_waitingForBattleStart);
        int index = 0;
        for (CharacterActor el : ticker.playedHere) {
            actorIds.put(el, ticker.actorServerKey[index]);
            index++;
        }
        final RecyclerView rv = (RecyclerView) findViewById(R.id.aoa_list);
        rv.setAdapter(lister);
        rv.addItemDecoration(new PreSeparatorDecorator(rv, this) {
            @Override
            protected boolean isEligible(int position) {
                return position != 0;
            }
        });
        rv.setVisibility(View.VISIBLE);
    }

    // ServiceConnection vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        ticker = ((AdventuringService.LocalBinder) service).getConcreteService();
        if(ticker.playedHere == null) { // Initiate data pull
            ticker.getActorData(actorKeys, serverPipe, new Runnable() {
                @Override
                public void run() { actorDataFetched(); }
            });
            actorKeys = null;
            serverPipe = null;

            final android.support.v4.app.NotificationCompat.Builder help = new NotificationCompat.Builder(this)
                    .setOngoing(true)
                    .setWhen(System.currentTimeMillis())
                    .setShowWhen(true)
                    .setContentTitle(connectedParty.name)
                    .setContentText(getString(R.string.aoa_notificationDesc))
                    .setSmallIcon(R.drawable.ic_notify_icon)
                    .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.placeholder_todo));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                help.setCategory(Notification.CATEGORY_SERVICE);
            }
            ticker.startForeground(NOTIFICATION_ID, help.build());
        }
        int waiting = 0;
        for (CharacterActor check : ticker.playedHere) {
            if(check == null) waiting++;
        }
        if(waiting == 0) actorDataFetched();
        else {
            MaxUtils.beginDelayedTransition(this);
            final TextView status = (TextView) findViewById(R.id.aoa_status);
            status.setText(R.string.aoa_fetchingData);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        // I still don't get what to do here.
    }
    // ServiceConnection ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
}

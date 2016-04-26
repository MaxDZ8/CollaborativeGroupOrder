package com.massimodz8.collaborativegrouporder.client;

import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

import com.massimodz8.collaborativegrouporder.R;
import com.massimodz8.collaborativegrouporder.networkio.Pumper;
import com.massimodz8.collaborativegrouporder.protocol.nano.StartData;

public class ActorOverviewActivity extends AppCompatActivity {
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
    }
}

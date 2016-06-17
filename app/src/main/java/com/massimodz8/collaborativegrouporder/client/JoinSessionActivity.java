package com.massimodz8.collaborativegrouporder.client;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.AccumulatingDiscoveryListener;
import com.massimodz8.collaborativegrouporder.ConnectionAttempt;
import com.massimodz8.collaborativegrouporder.ExplicitConnectionActivity;
import com.massimodz8.collaborativegrouporder.R;
import com.massimodz8.collaborativegrouporder.RunningServiceHandles;
import com.massimodz8.collaborativegrouporder.networkio.Pumper;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;

public class JoinSessionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join_session);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                RunningServiceHandles.getInstance().connectionAttempt = new ConnectionAttempt();
                startActivityForResult(new Intent(JoinSessionActivity.this, ExplicitConnectionActivity.class), REQUEST_EXPLICIT_CONNECTION);
            }
        });
        final ActionBar sab = getSupportActionBar();
        if (null != sab) sab.setDisplayHomeAsUpEnabled(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Two ways to start:
        // I start from the party picking. Then I have a party name to look for and no server connection.
        // I start from the forming activity. Party name to look for and a server pipe alredy there!
        // So there are two modes, only the first needs exploring.
        // In both cases, I get the state from persistent structure whose lifetime is managed by MainMenuActivity
        RunningServiceHandles.getInstance().joinGame.onEvent.put(new Runnable() {
            @Override
            public void run() {
                refresh();
            }
        });
        refresh();
    }

    int eventid;

    @Override
    protected void onPause() {
        super.onPause();
        RunningServiceHandles.getInstance().joinGame.onEvent.remove(eventid);
    }

    private void refresh() {
        final JoinGame state = RunningServiceHandles.getInstance().joinGame;
        TextView status = (TextView) findViewById(R.id.jsa_state);
        if(state.explorer != null) {
            switch (state.explorer.getDiscoveryStatus()) {
                case AccumulatingDiscoveryListener.START_FAILED:
                    status.setText(R.string.jsa_failedNetworkExploreStart);
                    findViewById(R.id.jsa_progressBar).setEnabled(false);
                    break;
                case AccumulatingDiscoveryListener.EXPLORING:
                    status.setText(R.string.jsa_searching);
                    findViewById(R.id.jsa_progressBar).setEnabled(false);
                    break;
            }
        }
        else status.setText(R.string.jsa_pullingFromExistingConnection);
        if(state.result != null) {
            setResult(RESULT_OK);
            finish();
        }
    }

    private static final int REQUEST_EXPLICIT_CONNECTION = 1;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode != REQUEST_EXPLICIT_CONNECTION) return;
        if(resultCode != RESULT_OK) return;
        final RunningServiceHandles handles = RunningServiceHandles.getInstance();
        final Pumper.MessagePumpingThread worker = handles.connectionAttempt.resMaster;
        final Network.GroupInfo ginfo = handles.connectionAttempt.resParty;
        handles.connectionAttempt = null;
        if(ginfo.forming) {
            new AlertDialog.Builder(this, R.style.AppDialogStyle)
                    .setMessage(R.string.jsa_connectedToForming)
                    .show();
            worker.interrupt();
            return;
        }
        if(!ginfo.name.equals(handles.joinGame.party.name)) {
            new AlertDialog.Builder(this, R.style.AppDialogStyle)
                    .setMessage(String.format(getString(R.string.jsa_connectedDifferentName), ginfo.name))
                    .setPositiveButton(R.string.jsa_connectedAttemptJoin, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            handles.joinGame.join(ginfo, worker);
                        }
                    })
                    .setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            worker.interrupt();
                        }
                    })
                    .show();
            return;
        }
        handles.joinGame.join(ginfo, worker);
    }
}

package com.massimodz8.collaborativegrouporder;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.EditText;

import com.massimodz8.collaborativegrouporder.networkio.Events;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.networkio.ProtoBufferEnum;
import com.massimodz8.collaborativegrouporder.networkio.PumpTarget;
import com.massimodz8.collaborativegrouporder.networkio.Pumper;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.Socket;
import java.net.UnknownHostException;

public class ExplicitConnectionActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_explicit_connection);
    }

    @Override
    protected void onResume() {
        super.onResume();
        final ConnectionAttempt state = RunningServiceHandles.getInstance().connectionAttempt;
        eventid = state.onEvent.put(new Runnable() {
            @Override
            public void run() { refresh(); }
        });
    }

    int eventid;

    @Override
    protected void onPause() {
        super.onPause();
        RunningServiceHandles.getInstance().connectionAttempt.onEvent.remove(eventid);
    }

    @Override
    public boolean onSupportNavigateUp() {
        final RunningServiceHandles handles = RunningServiceHandles.getInstance();
        if(handles.connectionAttempt != null) {
            handles.connectionAttempt.shutdown();
            handles.connectionAttempt = null;
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        final RunningServiceHandles handles = RunningServiceHandles.getInstance();
        if(handles.connectionAttempt != null) {
            handles.connectionAttempt.shutdown();
            handles.connectionAttempt = null;
        }
        super.onBackPressed();
    }

    public void connect_callback(View triggerer) {
        final EditText inetAddr = (EditText)findViewById(R.id.eca_inetAddr);
        final EditText inetPort = (EditText)findViewById(R.id.eca_port);
        final String addr = inetAddr.getText().toString();
        final int port;
        try {
            port = Integer.parseInt(inetPort.getText().toString());
        } catch(NumberFormatException e) {
            AlertDialog.Builder build = new AlertDialog.Builder(this, R.style.AppDialogStyle);
            build.setMessage(R.string.eca_badPort);
            build.show();
            inetPort.requestFocus();
            return;
        }
        RunningServiceHandles.getInstance().connectionAttempt = new ConnectionAttempt(addr, port);
        refresh();
    }

    /// Drives both GUI settings and evolution of the state machine.
    void refresh() {
        final ConnectionAttempt state = RunningServiceHandles.getInstance().connectionAttempt;
        if(state == null) {
            MaxUtils.setEnabled(this, true, R.id.eca_inetAddr, R.id.eca_port, R.id.eca_attempt);
            MaxUtils.setVisibility(this, View.GONE,
                    R.id.eca_probing,
                    R.id.eca_probingProgress,
                    R.id.eca_connected);
            return;
        }
        boolean connecting = state.connecting !=  null && state.connecting.error == null;
        MaxUtils.setVisibility(this, connecting? View.VISIBLE : View.GONE,
                R.id.eca_probingProgress,
                R.id.eca_probing);
        findViewById(R.id.eca_connected).setVisibility(state.attempting != null? View.VISIBLE : View.GONE);
        MaxUtils.setEnabled(this, !connecting,
                R.id.eca_inetAddr,
                R.id.eca_port,
                R.id.eca_attempt);
        if(state.connecting != null && state.connecting.error != null) {
            switch(state.connecting.status) {
                case ConnectionAttempt.HANDSHAKE_FAILED_HOST: {
                    findViewById(R.id.eca_inetAddr).requestFocus();
                    new AlertDialog.Builder(this)
                            .setMessage(R.string.eca_badHost)
                            .show();
                } break;
                case ConnectionAttempt.HANDSHAKE_FAILED_OPEN:
                case ConnectionAttempt.HANDSHAKE_FAILED_SEND: {
                    final int res = state.connecting.status == ConnectionAttempt.HANDSHAKE_FAILED_OPEN? R.string.generic_connFailedWithError : R.string.eca_failedHello;
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.generic_IOError)
                            .setMessage(String.format(getString(res), state.connecting.error.getLocalizedMessage()))
                            .show();
                }
            }
            state.connecting = null;
            state.shutdown();
            RunningServiceHandles.getInstance().connectionAttempt = null;
            refresh();
        }
        if(state.resMaster != null) {
            state.shutdown();
            setResult(RESULT_OK);
            finish();
        }
    }
}

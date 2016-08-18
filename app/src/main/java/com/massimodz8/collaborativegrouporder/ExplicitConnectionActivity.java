package com.massimodz8.collaborativegrouporder;

import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.EditText;

import com.massimodz8.collaborativegrouporder.protocol.nano.UserOf;

public class ExplicitConnectionActivity extends AppCompatActivity {
    private @UserOf ConnectionAttempt state;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_explicit_connection);
        state = RunningServiceHandles.getInstance().connectionAttempt;
    }

    @Override
    protected void onResume() {
        super.onResume();
        eventid = state.onEvent.put(new Runnable() {
            @Override
            public void run() { refresh(); }
        });
    }

    int eventid;

    @Override
    protected void onPause() {
        super.onPause();
        state.onEvent.remove(eventid);
    }

    @Override
    public boolean onSupportNavigateUp() {
        state.shutdown();
        return true;
    }

    @Override
    public void onBackPressed() {
        state.shutdown();
        super.onBackPressed();
    }

    public void connect_callback(View triggerer) {
        final EditText inetAddr = (EditText)findViewById(R.id.eca_inetAddr);
        final EditText inetPort = (EditText)findViewById(R.id.eca_port);
        final String addr = inetAddr.getText().toString();
        final int port;
        try {
            port = Integer.parseInt(inetPort.getText().toString());
            if(port < 1024 || port > 65535) throw new NumberFormatException(); // take it easy
        } catch(NumberFormatException e) {
            AlertDialog.Builder build = new AlertDialog.Builder(this, R.style.AppDialogStyle)
                    .setIcon(R.drawable.ic_error_white_24dp)
                    .setMessage(R.string.eca_badPort);
            build.show();
            inetPort.requestFocus();
            return;
        }
        state.connect(addr, port);
        refresh();
    }

    /// Drives both GUI settings and evolution of the state machine.
    void refresh() {
        if(state.connecting == null) {
            MaxUtils.setEnabled(this, true, R.id.eca_inetAddr, R.id.eca_port, R.id.eca_attempt);
            MaxUtils.setVisibility(this, View.GONE,
                    R.id.eca_probing,
                    R.id.eca_probingProgress,
                    R.id.eca_connected);
            return;
        }
        boolean connecting = state.connecting.error == null;
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
                            .setIcon(R.drawable.ic_error_white_24dp)
                            .setMessage(R.string.eca_badHost)
                            .show();
                } break;
                case ConnectionAttempt.HANDSHAKE_FAILED_OPEN:
                case ConnectionAttempt.HANDSHAKE_FAILED_SEND: {
                    final int res = state.connecting.status == ConnectionAttempt.HANDSHAKE_FAILED_OPEN? R.string.generic_connFailedWithError : R.string.eca_failedHello;
                    new AlertDialog.Builder(this)
                            .setIcon(R.drawable.ic_error_white_24dp)
                            .setTitle(R.string.generic_IOError)
                            .setMessage(String.format(getString(res), state.connecting.error.getLocalizedMessage()))
                            .show();
                }
            }
            state.connecting = null;
            refresh();
        }
        if(state.resMaster != null) {
            state.shutdown();
            setResult(RESULT_OK);
            finish();
        }
    }
}

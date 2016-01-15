package com.massimodz8.collaborativegrouporder;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.networkio.joiningClient.InitialConnect;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

public class ExplicitConnectionActivity extends AppCompatActivity {
    InitialConnect helper;
    CallbackHandler handler;
    private static final int MSG_DISCONNECTED = 1;
    private static final int MSG_GOT_REPLY = 2;

    private String addr;
    private int port;

    static class CallbackHandler extends Handler {
        public interface Callback {
            void disconnect();
            void gotGroup(JoinGroupActivity.GroupConnection got);

        }
        final Callback callback;
        CallbackHandler(Callback calls) {
            callback = calls;
        }

        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case MSG_DISCONNECTED: callback.disconnect(); break;
                case MSG_GOT_REPLY: {
                    JoinGroupActivity.GroupConnection got = (JoinGroupActivity.GroupConnection)msg.obj;
                    callback.gotGroup(got);
                }
            }
        }

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_explicit_connection);
        final ExplicitConnectionActivity self = this;
        handler = new CallbackHandler(new CallbackHandler.Callback() {
            @Override
            public void disconnect() {
                dialog(new Error(null, getString(R.string.lostConnectionToServer)));
            }

            @Override
            public void gotGroup(JoinGroupActivity.GroupConnection got) {
                String mismatch = JoinGroupActivity.mismatchAdvice(got.group.version, self);
                if(mismatch != null) {
                    dialog(new Error("Version mismatch", mismatch));
                    return;
                }
                Intent result = new Intent(RESULT_ACTION);
                result.putExtra(RESULT_EXTRA_INET_ADDR, addr);
                result.putExtra(RESULT_EXTRA_PORT, port);
                setResult(Activity.RESULT_OK, result);
                finish();

            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(helper != null) helper.shutdown();
    }

    public void startExplicitConnection(View triggerer) {
        final EditText inetAddr = (EditText)findViewById(R.id.in_explicit_inetAddr);
        final EditText inetPort = (EditText)findViewById(R.id.in_explicit_port);
        final ProgressBar bar = (ProgressBar)findViewById(R.id.waitProbingEnd);
        final TextView wait = (TextView)findViewById(R.id.probingUndergoing);
        addr = inetAddr.getText().toString();
        try {
            port = Integer.parseInt(inetPort.getText().toString());
        } catch(NumberFormatException e) {
            AlertDialog.Builder build = new AlertDialog.Builder(this);
            build.setMessage(R.string.badPort_msg);
            build.show();
            inetPort.requestFocus();
            return;
        }
        triggerer.setVisibility(View.GONE);
        inetAddr.setEnabled(false);
        inetPort.setEnabled(false);
        bar.setVisibility(View.VISIBLE);
        wait.setVisibility(View.VISIBLE);
        if(helper != null) helper.shutdown(); // one at a time
        Error fail = null; // only sent if something goes wrong
        Socket s = null;
        try {
            s = new Socket(addr, port);
        } catch (UnknownHostException e) {
            fail = new Error(null, getString(R.string.badHost_msg));
            fail.refocus = R.id.in_explicit_inetAddr;

        } catch (IOException e) {
            fail = new Error(getString(R.string.explicitConn_IOException_title), String.format(getString(R.string.explicitConn_IOException_msg), e.getLocalizedMessage()));
        }

        if(fail != null) {
            dialog(fail);
            return;
        }

        try {
            if(helper != null) helper.shutdown();
            helper = JoinGroupActivity.initialConnect(handler, s, MSG_DISCONNECTED, MSG_GOT_REPLY);
        } catch (IOException e) {
            fail = new Error(getString(R.string.explicitConn_IOException_title), String.format(getString(R.string.explicitConn_IOException_msg), e.getLocalizedMessage()));
        }

        if(fail != null) dialog(fail);
    }

    private void dialog(Error result) {
        findViewById(R.id.attemptExplicitConnection).setVisibility(View.VISIBLE);
        findViewById(R.id.in_explicit_inetAddr).setEnabled(true);
        findViewById(R.id.in_explicit_port).setEnabled(true);
        findViewById(R.id.waitProbingEnd).setVisibility(View.GONE);
        findViewById(R.id.probingUndergoing).setVisibility(View.GONE);

        AlertDialog.Builder build = new AlertDialog.Builder(this);
        if(result.title != null && !result.title.isEmpty()) build.setTitle(result.title);
        if(result.msg != null && !result.msg.isEmpty()) build.setMessage(result.msg);
        if(result.refocus != null) findViewById(result.refocus).requestFocus();
        build.show();
    }


    public static final String RESULT_EXTRA_INET_ADDR = "address";
    public static final String RESULT_EXTRA_PORT = "port";
    public static final String RESULT_ACTION = "com.massimodz8.collaborativegrouporder.EXPLICIT_CONNECTION_RESULT";

    private static class Error {
        String title;
        String msg;
        Integer refocus;
        Error(String title, String msg) { this.title = title; this.msg = msg; }
    }
}

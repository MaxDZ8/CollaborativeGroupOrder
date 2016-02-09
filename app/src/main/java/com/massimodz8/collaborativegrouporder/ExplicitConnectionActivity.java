package com.massimodz8.collaborativegrouporder;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
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
    Pumper netPump;
    MessageChannel attempting;
    boolean handShaking;
    Handler handler;

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        CrossActivityShare state = (CrossActivityShare) getApplicationContext();
        if(netPump.getClientCount() != 0) state.pumpers = new Pumper.MessagePumpingThread[] { netPump.move(attempting) };
        attempting = null;
    }

    private static final int MSG_DISCONNECTED = 1;
    private static final int MSG_GOT_REPLY = 2;
    private static final int MSG_DETACHED = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_explicit_connection);
        handler = new MyHandler(this);

        final CrossActivityShare state = (CrossActivityShare) getApplicationContext();
        netPump = new Pumper(handler, MSG_DISCONNECTED, MSG_DETACHED);
        netPump.add(ProtoBufferEnum.GROUP_INFO, new PumpTarget.Callbacks<Network.GroupInfo>() {
            @Override
            public Network.GroupInfo make() { return new Network.GroupInfo(); }
            @Override
            public boolean mangle(MessageChannel from, Network.GroupInfo msg) throws IOException {
                handler.sendMessage(handler.obtainMessage(MSG_GOT_REPLY, new Events.GroupInfo(from, msg)));
                return true;
            }
        });
        if(null != state.pumpers) {
            attempting = state.pumpers[0].getSource();
            netPump.pump(state.pumpers[0]);
            state.pumpers = null;
        }
        refreshGUI();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(netPump != null) netPump.shutdown();
        if(attempting != null) {
            try {
                attempting.socket.close();
            } catch (IOException e) {
                // well... nothing?
            }
        }
    }

    public void connect_callback(View triggerer) {
        triggerer.setEnabled(false);
        final EditText inetAddr = (EditText)findViewById(R.id.eca_inetAddr);
        final EditText inetPort = (EditText)findViewById(R.id.eca_port);
        final String addr = inetAddr.getText().toString();
        final int port;
        try {
            port = Integer.parseInt(inetPort.getText().toString());
        } catch(NumberFormatException e) {
            AlertDialog.Builder build = new AlertDialog.Builder(this);
            build.setMessage(R.string.badPort_msg);
            build.show();
            inetPort.requestFocus();
            return;
        }
        handShaking = true;
        refreshGUI();
        new AsyncTask<Void, Void, MessageChannel>() {
            volatile Error fail;

            @Override
            protected MessageChannel doInBackground(Void... params) {
                Socket s;
                try {
                    s = new Socket(addr, port);
                } catch (UnknownHostException e) {
                    fail = new Error(null, getString(R.string.badHost_msg));
                    fail.refocus = R.id.eca_inetAddr;
                    return null;

                } catch (IOException e) {
                    fail = new Error(getString(R.string.generic_IOError), String.format(getString(R.string.generic_connFailedWithError), e.getLocalizedMessage()));
                    return null;
                }
                MessageChannel chan = new MessageChannel(s);
                Network.Hello payload = new Network.Hello();
                payload.version = MainMenuActivity.NETWORK_VERSION;
                try {
                    chan.write(ProtoBufferEnum.HELLO, payload);
                } catch (IOException e) {
                    fail = new Error(getString(R.string.generic_IOError), String.format(getString(R.string.eca_failedHello), e.getLocalizedMessage()));
                    return null;
                }
                return chan;
            }

            @Override
            protected void onPostExecute(MessageChannel pipe) {
                if(pipe != null) {
                    attempting = pipe;
                    netPump.pump(pipe);
                    refreshGUI();
                    return;
                }
                final AlertDialog.Builder build = new AlertDialog.Builder(ExplicitConnectionActivity.this);
                if(fail.title != null && !fail.title.isEmpty()) build.setTitle(fail.title);
                if(fail.msg != null && !fail.msg.isEmpty()) build.setMessage(fail.msg);
                if(fail.refocus != null) findViewById(fail.refocus).requestFocus();
                build.show();
                handShaking = false;
                refreshGUI();
            }
        }.execute();
    }

    public static final String RESULT_ACTION = "com.massimodz8.collaborativegrouporder.ExplicitConnectionActivity.result";

    static class Error {
        String title;
        String msg;
        Integer refocus;
        Error(String title, String msg) { this.title = title; this.msg = msg; }
    }

    void refreshGUI() {
        ViewUtils.setVisibility(this, handShaking ? View.VISIBLE : View.GONE,
                R.id.eca_probing,
                R.id.eca_probingProgress);
        ViewUtils.setEnabled(this, attempting == null,
                R.id.eca_inetAddr,
                R.id.eca_port,
                R.id.eca_attempt);
    }

    private static class MyHandler extends Handler {
        WeakReference<ExplicitConnectionActivity> target;
        public MyHandler(ExplicitConnectionActivity target) { this.target = new WeakReference<>(target); }

        @Override
        public void handleMessage(Message msg) {
            ExplicitConnectionActivity target = this.target.get();
            switch(msg.what) {
                case MSG_DISCONNECTED: target.disconnect(); break;
                case MSG_GOT_REPLY: target.replied((Events.GroupInfo)msg.obj); break;
                case MSG_DETACHED: break; // this comes after MSG_GOT_REPLY and can be ignored here.
            }
            target.refreshGUI();
        }
    }

    private void disconnect() {
        netPump.forget(attempting);
        try {
            attempting.socket.close();
        } catch (IOException e) {
            // Ok, I could signal this really... but I'm lazy
        }
        attempting = null;
        handShaking = false;

        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.eca_disconnected))
                .show();
    }

    private void replied(Events.GroupInfo result) { // oh yeah I like this
        if(!result.payload.forming) {
            String res = getString(R.string.eca_partyNotOpenMsg);
            new AlertDialog.Builder(this)
                    .setMessage(String.format(res, result.payload.name))
                    .show();
        }
        PartyInfo info = new PartyInfo(result.payload.version, result.payload.name);
        info.options = result.payload.options;
        Intent send = new Intent(RESULT_ACTION);
        CrossActivityShare state = (CrossActivityShare) getApplicationContext();
        state.pumpers = new Pumper.MessagePumpingThread[] { netPump.move(attempting) };
        state.probed = info;
        setResult(RESULT_OK, send);
        handShaking = false;
        attempting = null;
        finish();
    }
}

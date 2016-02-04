package com.massimodz8.collaborativegrouporder;

import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
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
    private static final String EXTRA_SERVICED_PUMPER_THREAD = "com.massimodz8.collaborativegrouporder.ExplicitConnectionActivity.pumperThread";
    private static final String EXTRA_SERVICED_CHANNEL = "com.massimodz8.collaborativegrouporder.ExplicitConnectionActivity.channel";
    Pumper netPump;
    MessageChannel attempting;
    Handler handler;

    ServiceConnection serviceConn;
    CrossActivityService.Binder binder;

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if(binder == null) return;
        outState.putLong(EXTRA_SERVICED_CHANNEL, binder.store(attempting));
        outState.putLong(EXTRA_SERVICED_PUMPER_THREAD, binder.store(netPump.move(attempting)));
    }

    private static final int MSG_DISCONNECTED = 1;
    private static final int MSG_GOT_REPLY = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_explicit_connection);
        //final ActionBar bar = getSupportActionBar();
        //if(bar != null) {
        //    bar.setDisplayHomeAsUpEnabled(true);
        //}
        handler = new MyHandler(this);

        final long threadKey = CrossActivityService.pullKey(savedInstanceState, EXTRA_SERVICED_PUMPER_THREAD);
        final long chanKey = CrossActivityService.pullKey(savedInstanceState, EXTRA_SERVICED_CHANNEL);

        Intent sharing = new Intent(this, CrossActivityService.class);
        serviceConn = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                binder = (CrossActivityService.Binder) service;
                netPump = new Pumper(handler, MSG_DISCONNECTED);
                netPump.add(ProtoBufferEnum.GROUP_INFO, new PumpTarget.Callbacks<Network.GroupInfo>() {
                    @Override
                    public Network.GroupInfo make() { return new Network.GroupInfo(); }
                    @Override
                    public void mangle(MessageChannel from, Network.GroupInfo msg) throws IOException {
                        handler.sendMessage(handler.obtainMessage(MSG_GOT_REPLY, new Events.GroupInfo(from, msg)));
                    }
                });
                if(threadKey != 0) {
                    Pumper.MessagePumpingThread worker = (Pumper.MessagePumpingThread) binder.release(threadKey);
                    MessageChannel chan = (MessageChannel) binder.release(chanKey);
                    netPump.pump(chan, worker);
                }
                refreshGUI();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                if(binder == null) return; // it's ok, we are shutting down
                new AlertDialog.Builder(ExplicitConnectionActivity.this)
                        .setMessage(getString(R.string.lostCrossActivitySharingService))
                        .setCancelable(false)
                        .setPositiveButton(R.string.giveUpAndGoBack, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }
                        })
                        .show();
            }
        };
        if(!bindService(sharing, serviceConn, 0)) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.couldNotBindInternalService)
                    .setCancelable(false)
                    .setPositiveButton(R.string.giveUpAndGoBack, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .show();
        }
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
        if(binder != null) {
            binder = null;
            unbindService(serviceConn);
        }
    }

    public void connect_callback(View triggerer) {
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
                    fail = new Error(getString(R.string.explicitConn_IOException_title), String.format(getString(R.string.explicitConn_IOException_msg), e.getLocalizedMessage()));
                    return null;
                }
                MessageChannel chan = new MessageChannel(s);
                Network.Hello payload = new Network.Hello();
                payload.version = MainMenuActivity.NETWORK_VERSION;
                try {
                    chan.write(ProtoBufferEnum.HELLO, payload);
                } catch (IOException e) {
                    fail = new Error(getString(R.string.explicitConn_IOException_title), String.format(getString(R.string.explicitConn_IOException_msg), e.getLocalizedMessage()));
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
            }
        }.execute();
    }

    public static final String RESULT_ACTION = "com.massimodz8.collaborativegrouporder.ExplicitConnectionActivity.result";
    public static final String RESULT_ACTION_CHANNEL = "com.massimodz8.collaborativegrouporder.ExplicitConnectionActivity.result.channel";
    public static final String RESULT_ACTION_PARTY_INFO = "com.massimodz8.collaborativegrouporder.ExplicitConnectionActivity.result.groupInfo";
    public static final String RESULT_ACTION_PUMPER_THREAD = "com.massimodz8.collaborativegrouporder.ExplicitConnectionActivity.result.pumperThread";

    static class Error {
        String title;
        String msg;
        Integer refocus;
        Error(String title, String msg) { this.title = title; this.msg = msg; }
    }

    void refreshGUI() {
        if(binder == null) {
            findViewById(R.id.eca_attempt).setEnabled(false);
            return;
        }
        ViewUtils.setVisibility(this, attempting != null ? View.VISIBLE : View.GONE,
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
        send.putExtra(RESULT_ACTION_PUMPER_THREAD, binder.store(netPump.move(attempting)));
        send.putExtra(RESULT_ACTION_PARTY_INFO, binder.store(info));
        send.putExtra(RESULT_ACTION_CHANNEL, binder.store(attempting));
        setResult(RESULT_OK, send);
        finish();
    }
}

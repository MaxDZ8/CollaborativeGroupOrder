package com.massimodz8.collaborativegrouporder;

import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;

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

/**
 * Created by Massimo on 14/06/2016.
 * Model for ExplicitConnectionActivity. Bleh.
 * This is a bit more complicated as we need to accumulate changes and get the rid of them on need.
 * I want connections to be cancellable.
 */
public class ConnectionAttempt {
    public Pumper.MessagePumpingThread resMaster;
    public Network.GroupInfo resParty;

    void connect(String addr, int port) {
        connecting = new Handshake(addr, port);
        connecting.execute();
    }

    void shutdown() {
        if(connecting != null) connecting.cancel(true);
        new Thread() {
            @Override
            public void run() {
                for (Pumper.MessagePumpingThread goner : netPump.move()) {
                    goner.interrupt();
                    try {
                        goner.getSource().socket.close();
                    } catch (IOException e) {
                        // suppressed is fine
                    }
                }
            }
        }.start();
    }

    private Handler handler = new MyHandler(this);
    private final Pumper netPump = new Pumper(handler, MSG_DISCONNECTED, MSG_DETACHED)
            .add(ProtoBufferEnum.GROUP_INFO, new PumpTarget.Callbacks<Network.GroupInfo>() {
                @Override
                public Network.GroupInfo make() { return new Network.GroupInfo(); }
                @Override
                public boolean mangle(MessageChannel from, Network.GroupInfo msg) throws IOException {
                    handler.sendMessage(handler.obtainMessage(MSG_GOT_REPLY, new Events.GroupInfo(from, msg)));
                    return true;
                }
            });
    MessageChannel attempting;
    Handshake connecting;
    public final PseudoStack<Runnable> onEvent = new PseudoStack<>();

    public static final int HANDSHAKE_READY = 0;
    public static final int HANDSHAKE_FAILED_HOST = 1;
    public static final int HANDSHAKE_FAILED_OPEN = 2;
    public static final int HANDSHAKE_FAILED_SEND = 3;
    public static final int HANDSHAKE_WAITING_REPLY = 4;

    class Handshake extends AsyncTask<Void, Void, MessageChannel> {
        public volatile int status = HANDSHAKE_READY;
        public volatile Exception error; // Maybe there with _FAILED

        private final String addr;
        private final int port;

        public Handshake(String addr, int port) {
            this.addr = addr;
            this.port = port;
        }

        @Override
        protected MessageChannel doInBackground(Void... params) {
            Socket s;
            try {
                s = new Socket(addr, port);
            } catch (UnknownHostException e) {
                status = HANDSHAKE_FAILED_HOST;
                error = e;
                return null;

            } catch (IOException e) {
                status = HANDSHAKE_FAILED_OPEN;
                error = e;
                return null;
            }
            MessageChannel chan = new MessageChannel(s);
            Network.Hello payload = new Network.Hello();
            payload.version = MainMenuActivity.NETWORK_VERSION;
            try {
                chan.write(ProtoBufferEnum.HELLO, payload);
            } catch (IOException e) {
                // most likely because connection timed out
                status = HANDSHAKE_FAILED_SEND;
                error = e;
                return null;
            }
            status = HANDSHAKE_WAITING_REPLY;
            return chan;
        }

        @Override
        protected void onPostExecute(MessageChannel pipe) {
            final Runnable callback = onEvent.get();
            if(pipe != null) {
                attempting = pipe;
                netPump.pump(pipe);
            }
            callback.run();
        }
    }

    private static final int MSG_DISCONNECTED = 1;
    private static final int MSG_GOT_REPLY = 2;
    private static final int MSG_DETACHED = 3;


    private static class MyHandler extends Handler {
        WeakReference<ConnectionAttempt> target;
        public MyHandler(ConnectionAttempt target) { this.target = new WeakReference<>(target); }

        @Override
        public void handleMessage(Message msg) {
            ConnectionAttempt target = this.target.get();
            switch(msg.what) {
                case MSG_DISCONNECTED: {
                    target.attempting = null;
                } break;
                case MSG_GOT_REPLY: {
                    target.attempting = null;
                    final Events.GroupInfo real = (Events.GroupInfo) msg.obj;
                    target.resParty = real.payload;
                    target.resMaster = target.netPump.move(real.which);
                    return; // wait for DETACH, safer.
                }
                case MSG_DETACHED:
                    break;
            }
            final Runnable callback = target.onEvent.get();
            if(callback != null) callback.run();
        }
    }
}

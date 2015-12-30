package com.massimodz8.collaborativegrouporder;

import android.os.Handler;
import android.os.Message;

import java.net.InetAddress;
import java.net.Socket;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by Massimo on 30/12/2015.
 * This abstraction is used to pull some complexity out of NetworkListeningActivity and decouple.
 * Maybe "messager" would be a better name but not bike shedding!
 */
public class FormingPlayerGroupHelper implements PlayerCollectorInterface {
    public Set<InetAddress> reject = Collections.synchronizedSet(new HashSet<InetAddress>());
    final private Handler mainThread;

    public FormingPlayerGroupHelper(Handler mainThread) {
        this.mainThread = mainThread;
    }


    //
    // PlayerCollectorInterface ____________________________________________________________________
    @Override
    public boolean isForbidden(InetAddress connecting) {
        return reject.contains(connecting);
    }

    @Override
    public void connectionFailed(InetAddress byebye, int port, String reason) {
        Message msg = Message.obtain(mainThread, NetworkListeningActivity.MSG_PLAYER_HANDSHAKE_FAILED, new NetworkListeningActivity.HandshakeFailureInfo(byebye, port, reason));
        mainThread.sendMessage(msg);
    }

    @Override
    public void connect(Socket sticker, String hello) {
        Message msg = Message.obtain(mainThread, NetworkListeningActivity.MSG_PLAYER_WELCOME, new NetworkListeningActivity.NewPlayerInfo(sticker, hello));
        mainThread.sendMessage(msg);
    }
}

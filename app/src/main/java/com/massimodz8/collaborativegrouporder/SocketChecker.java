package com.massimodz8.collaborativegrouporder;

import android.os.Handler;
import android.os.SystemClock;

import java.net.Socket;
import java.util.Vector;

/**
 * Created by Massimo on 08/01/2016.
 * A set of (unique) TCP sockets to look for. Periodically calls .isConnected() on them and
 * eventually sends a message to signal Socket is dead.
 * Sockets can still go awry in the meanwhile but at least we're somewhat coherent.
 */
public class SocketChecker extends Thread {
    public static final int SOCKET_CHECKING_PERIOD_MS = 2000;

    private Handler handler;
    private final int event;
    private Vector<Socket> watching = new Vector<>();

    public SocketChecker(Handler destination, int code) {
        handler = destination;
        event = code;
    }

    public void add(Socket s) {
        // Keep them unique. Sure, we'd make it faster using a set here but I want to optimize
        // running instead.
        synchronized (watching) {
            for(Socket unique : watching) {
                if(unique == s) return;
            }
            watching.add(s);
        }
    }

    public void remove(Socket s) {
        synchronized(watching) {
            for(int i = 0; i < watching.size(); i++) {
                if(watching.elementAt(i) == s) {
                    watching.remove(i);
                    return;
                }
            }
        }
    }

    @Override
    public void run() {
        while(!isInterrupted()) {
            SystemClock.sleep(SOCKET_CHECKING_PERIOD_MS);
            for(int pos = 0; pos < watching.size(); pos++) {
                final Socket s = watching.elementAt(pos);
                if(!s.isConnected()) {
                    handler.sendMessage(handler.obtainMessage(event, s));
                    watching.remove(pos);
                }
            }
        }
    }
}

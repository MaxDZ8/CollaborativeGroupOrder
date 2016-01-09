package com.massimodz8.collaborativegrouporder;

import android.os.Handler;

import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Map;


/**
 * Created by Massimo on 09/01/2016.
 * A threaded (object oriented) socket pump blocks on a socket read and pulls out a known object.
 * If successful, the object is then matched against a set of message filters. The result of the match
 * is then forwarded to a looper through a Handler.
 */
class ThreadedSocketPump extends Thread {
    private final Map<Class, Integer> dispatch; // set this before construction so it's read only
    private final OOSocket watch;
    private final Handler handler;
    public final int deadSocketMsg;
    public final int unmatchedMsg;
    
    static public class Message {
        OOSocket origin;
        Object msg;

        public Message(OOSocket origin, Object msg) {
            this.origin = origin;
            this.msg = msg;
        }
    } 

    public ThreadedSocketPump(OOSocket watch, Handler destination, Map<Class, Integer> dispatch, int deadSocket, int unmatchedMsg) {
        this.watch = watch;
        handler = destination;
        deadSocketMsg = deadSocket;
        this.unmatchedMsg = unmatchedMsg;
        this.dispatch = dispatch;
    }

    @Override
    public void run() {
        while (!isInterrupted()) {
            Object o = null;
            try {
                o = watch.reader.readObject();
            } catch (ClassNotFoundException | IOException e) {
                handler.sendMessage(handler.obtainMessage(deadSocketMsg, watch));
                return;
            }
            Integer code = dispatch.get(o.getClass());
            if(code != null) handler.sendMessage(handler.obtainMessage(code, new Message(watch, o)));
            else handler.sendMessage(handler.obtainMessage(unmatchedMsg, new Message(watch, o)));
        }
    }
}

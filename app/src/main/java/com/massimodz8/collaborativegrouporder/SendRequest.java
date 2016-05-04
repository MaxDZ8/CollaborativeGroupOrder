package com.massimodz8.collaborativegrouporder;

import android.support.annotation.NonNull;

import com.google.protobuf.nano.MessageNano;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;

/**
 * Created by Massimo on 04/05/2016.
 * The server must send messages asyncronously to clients. Everything is much easier if
 * order is guaranteed. Use this to pump messages to a pending out queue so I can avoid spawning
 * extra threads, even if pooled this saves a bit and still gets the in-order guarantee, which is
 * really cool.
 */
public class SendRequest {
    public final MessageChannel destination;
    public final int type;
    public final MessageNano one;
    public final MessageNano[] many;

    public Exception error;

    public SendRequest(@NonNull MessageChannel destination, int type, @NonNull MessageNano payload) {
        this.destination = destination;
        this.type = type;
        one = payload;
        this.many = null;
    }

    public SendRequest(@NonNull MessageChannel destination, int type, @NonNull MessageNano[] payload) {
        this.destination = destination;
        this.type = type;
        one = null;
        many = payload;
    }

    public SendRequest() { // causes the mailman to shut down gracefully
        destination = null;
        type = -1;
        one = null;
        many = null;
    }
}

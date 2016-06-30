package com.massimodz8.collaborativegrouporder;

import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;

/**
 * Created by Massimo on 20/06/2016.
 * Wait until receiving a certain amount of messages, then call the provided function.
 */
public class LatchingHandler extends Handler {
    public final Runnable ticker;

    public LatchingHandler(int expect, @NonNull Runnable latched) {
        this.expect = expect;
        this.latched = latched;
        ticker = new Runnable() {
            @Override
            public void run() {
                LatchingHandler.this.sendEmptyMessage(LatchingHandler.MSG_INCREMENT);
            }
        };
    }

    @Override
    public void handleMessage(Message msg) {
        if (msg.what != MSG_INCREMENT) return;
        count++;
        if (count == expect) latched.run();
    }

    private int count;
    private final int expect;
    private final Runnable latched;
    private static final int MSG_INCREMENT = 1;
}

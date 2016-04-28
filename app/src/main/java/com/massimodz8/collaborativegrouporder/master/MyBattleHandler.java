package com.massimodz8.collaborativegrouporder.master;

import android.os.Handler;
import android.os.Message;

import com.massimodz8.collaborativegrouporder.networkio.Events;

import java.lang.ref.WeakReference;

/**
 * Created by Massimo on 28/04/2016.
 * Used to sequentialize messages got by the various pumper threads.
 */
public class MyBattleHandler extends Handler {
    public static final int MSG_DISCONNECTED = 1;
    public static final int MSG_DETACHED = 2;
    public static final int MSG_ROLL = 3;

    private final WeakReference<PartyJoinOrderService> target;

    public MyBattleHandler(PartyJoinOrderService target) {
        this.target = new WeakReference<>(target);
    }

    @Override
    public void handleMessage(Message msg) {
        final PartyJoinOrderService target = this.target.get();
        switch(msg.what) {
            case MSG_DISCONNECTED: {
                // TODO - network architecture is to be redesigned anyway
                break;
            }
            case MSG_DETACHED: {
                // TODO - network architecture is to be redesigned anyway
                break;
            }
            case MSG_ROLL: {
                final Events.Roll real = (Events.Roll) msg.obj;
                final SessionHelper.PlayState session = target.sessionHelper.session;
                session.rollResults.push(real);
                session.onRollReceived();
                break;
            }
            default: super.handleMessage(msg);
        }
    }
}

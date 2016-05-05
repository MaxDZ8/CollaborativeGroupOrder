package com.massimodz8.collaborativegrouporder.master;

import android.os.Handler;
import android.os.Message;
import android.support.v4.util.Pair;

import com.massimodz8.collaborativegrouporder.networkio.Events;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;

import java.lang.ref.WeakReference;

/**
 * Created by Massimo on 28/04/2016.
 * Used to sequentialize messages got by the various pumper threads.
 */
public class MyBattleHandler extends Handler {
    public static final int MSG_DISCONNECTED = 1;
    public static final int MSG_DETACHED = 2;
    public static final int MSG_ROLL = 3;
    public static final int MSG_TURN_DONE = 4;

    private final WeakReference<PartyJoinOrderService> target;

    public MyBattleHandler(PartyJoinOrderService target) {
        this.target = new WeakReference<>(target);
    }

    @Override
    public void handleMessage(Message msg) {
        final PartyJoinOrderService target = this.target.get();
        final SessionHelper.PlayState session = target.sessionHelper.session;
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
                session.rollResults.push(real);
                session.onRollReceived();
                break;
            }
            case MSG_TURN_DONE: {
                final Events.TurnDone real = (Events.TurnDone)msg.obj;
                session.turnDone(real.from, real.peerKey);
                break;
            }
            default: super.handleMessage(msg);
        }
    }
}

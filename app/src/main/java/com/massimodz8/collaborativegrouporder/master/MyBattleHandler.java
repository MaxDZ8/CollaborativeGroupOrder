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
    public static final int MSG_TURN_DONE = 4;
    public static final int MSG_SHUFFLE_ME = 5;
    public static final int MSG_READIED_ACTION_CONDITION = 6;

    private final WeakReference<PartyJoinOrderService> target;

    public MyBattleHandler(PartyJoinOrderService target) {
        this.target = new WeakReference<>(target);
    }

    @Override
    public void handleMessage(Message msg) {
        final PartyJoinOrderService target = this.target.get();
        final SessionHelper session = target.session;
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
            case MSG_SHUFFLE_ME: {
                final Events.ShuffleMe real = (Events.ShuffleMe)msg.obj;
                session.shuffle(real.from, real.peerKey, real.newSlot);
                break;
            }
            case MSG_READIED_ACTION_CONDITION: {
                final Events.ReadiedActionCondition real = (Events.ReadiedActionCondition)msg.obj;
                if(session.battleState.currentActor != real.peerKey) break; // you can only define in your turn, cheater!
                if(real.peerKey >= target.assignmentHelper.assignment.size()) break; // not a valid key!
                final Integer owner = target.assignmentHelper.assignment.get(real.peerKey);
                if(owner == null || owner == PcAssignmentHelper.LOCAL_BINDING) break; // you're cheating big way and I should kick your ass but let's leave it for the good of the group.
                final PcAssignmentHelper.PlayingDevice dev = target.assignmentHelper.peers.get(owner);
                if(dev.pipe == null || dev.pipe == real.from) { // if you're the real owner or you were at a certain point and we are out of sync somehow...
                    session.getActorById(session.battleState.currentActor).prepareCondition = real.desc;
                    final Runnable runnable = target.onActorUpdatedRemote.get();
                    if(runnable != null) runnable.run();
                }
                break;
            }
            default: super.handleMessage(msg);
        }
    }
}

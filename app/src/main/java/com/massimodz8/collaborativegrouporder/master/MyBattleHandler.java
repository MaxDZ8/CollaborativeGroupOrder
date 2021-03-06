package com.massimodz8.collaborativegrouporder.master;

import android.os.Handler;
import android.os.Message;

import com.massimodz8.collaborativegrouporder.networkio.Events;
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
    public static final int MSG_SHUFFLE_ME = 5;
    public static final int MSG_READIED_ACTION_CONDITION = 6;
    public static final int MSG_CHARACTER_LEVELUP_PROPOSAL = 7;

    private final WeakReference<PartyJoinOrder> target;

    public MyBattleHandler(PartyJoinOrder target) {
        this.target = new WeakReference<>(target);
    }

    @Override
    public void handleMessage(Message msg) {
        final PartyJoinOrder target = this.target.get();
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
                if(real.peerKey >= target.assignmentHelper.assignment.length) break; // not a valid key!
                final int owner = target.assignmentHelper.assignment[real.peerKey];
                if(owner == PcAssignmentHelper.PlayingDevice.INVALID_ID || owner == PcAssignmentHelper.PlayingDevice.LOCAL_ID) break; // you're cheating big way and I should kick your ass but let's leave it for the good of the group.
                PcAssignmentHelper.PlayingDevice dev = null;
                for (PcAssignmentHelper.PlayingDevice test : target.assignmentHelper.peers) {
                    if(test.keyIndex == owner) {
                        dev = test;
                        break;
                    }
                }
                if(dev != null && (dev.pipe == null || dev.pipe == real.from)) { // if you're the real owner or you were at a certain point and we are out of sync somehow...
                    session.getActorById(session.battleState.currentActor).prepareCondition = real.desc;
                    final Runnable runnable = target.onActorUpdatedRemote.get();
                    if(runnable != null) runnable.run();
                }
                break;
            }
            case MSG_CHARACTER_LEVELUP_PROPOSAL: {
                Events.CharacterDefinition real = (Events.CharacterDefinition) msg.obj;
                Network.PlayingCharacterDefinition actor = target.upgradeTickets.get(real.character.redefine);
                if(actor == null) break; // not a valid ticket or not the right character
                real.character.peerKey = actor.peerKey; // override, ticket is king
                if(target.assignmentHelper.getMessageChannelByPeerKey(actor.peerKey) != real.origin) break; // you're cheating
                target.upgradeTickets.put(real.character.redefine, real.character);
                Runnable runnable = target.onActorLeveled.get();
                if(null != runnable) runnable.run();
            }
            default: super.handleMessage(msg);
        }
    }
}

package com.massimodz8.collaborativegrouporder.client;

import com.massimodz8.collaborativegrouporder.PartyInfo;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;

/**
 * Created by Massimo on 08/02/2016.
 * Used to collect various informations about groups being discovered by the client when joining.
 */
class GroupState {
    final MessageChannel channel;
    PartyInfo group;
    Exception disconnected; // if null, everything is fine.

    int charBudget;
    int nextMsgDelay_ms;
    String lastMsgSent;
    volatile long nextEnabled_ms = 0; // SystemClock.elapsedRealtime(); /// if now() is >= this, controls are updated if charBudget > 0
    public boolean discovered = true;

    byte[] salt; // set when a group is 'formed'

    public GroupState(MessageChannel pipe) {
        channel = pipe;
    }

    GroupState explicit() {
        discovered = false;
        return this;
    }
}

package com.massimodz8.collaborativegrouporder;

import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;

/**
 * Created by Massimo on 08/02/2016.
 * Used to collect various informations about groups being discovered by the client when joining.
 */
class GroupState {
    final MessageChannel channel;
    PartyInfo group;

    int charBudget;
    int nextMsgDelay_ms;
    String lastMsgSent;
    volatile long nextEnabled_ms = 0; // SystemClock.elapsedRealtime(); /// if now() is >= this, controls are updated if charBudget > 0
    public boolean discovered = true;

    public GroupState(MessageChannel pipe) {
        channel = pipe;
    }

    GroupState explicit() {
        discovered = false;
        return this;
    }
}

package com.massimodz8.collaborativegrouporder;

import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;

import java.util.Date;
import java.util.Vector;

/**
 * Created by Massimo on 05/02/2016.
 * Used by NewPartyDeviceSelectActivity to keep a list of building stuff.
 */
class DeviceStatus {
    final MessageChannel source;
    String lastMessage; // if null still not talking
    int charBudget;
    boolean groupMember;
    boolean kicked;
    Vector<BuildingPlayingCharacter> chars = new Vector<>(); // if contains something we have been promoted
    public Date nextMessage = new Date();

    public DeviceStatus(MessageChannel source) {
        this.source = source;
    }
}

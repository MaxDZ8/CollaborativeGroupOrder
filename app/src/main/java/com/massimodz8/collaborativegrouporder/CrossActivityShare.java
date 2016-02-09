package com.massimodz8.collaborativegrouporder;

import android.app.Application;
import android.os.Bundle;

import com.massimodz8.collaborativegrouporder.networkio.Pumper;
import com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage;

import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Vector;

/**
 * Created by Massimo on 05/02/2016.
 * In origin it was the (local) CrossActivityService.
 * It always felt like an hammer solution. A simpler solution does exist: just inherit from
 * Application class and use it to keep persistent state.
 */
public class CrossActivityShare extends Application {
    public PersistentStorage.PartyOwnerData.Group getGroupByName(String name) {
        if(null == groups) return null;
        for(PersistentStorage.PartyOwnerData.Group test : groups) {
            if(test.name.equals(name)) return test;
        }
        return null;
    }

    public Vector<PersistentStorage.PartyOwnerData.Group> groups;

    // NewPartyDeviceSelectionActivity state -------------------------------------------------------
    public Vector<DeviceStatus> clients;
    PublishedService publisher;
    ServerSocket landing;
    //----------------------------------------------------------------------------------------------

    // SelectFormingGroupActivity ------------------------------------------------------------------
    AccumulatingDiscoveryListener explorer;
    Vector<GroupState> candidates;
    //----------------------------------------------------------------------------------------------

    // ExplicitConnectionActivity result -----------------------------------------------------------
    public PartyInfo probed;
    //----------------------------------------------------------------------------------------------

    // NewCharacterProposalActivity result ---------------------------------------------------------
    public boolean goAdventuring;
    public String newGroupName;
    public byte[] newGroupKey;
    //----------------------------------------------------------------------------------------------


    // NewPartyDeviceSelectionActivity, ExplicitConnectionActivity ---------------------------------
    // SelectFormingGroupActivity
    Pumper.MessagePumpingThread[] pumpers;
    //----------------------------------------------------------------------------------------------
}

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
    public Vector<PersistentStorage.PartyOwnerData.Group> groupDefs;
    public Vector<PersistentStorage.PartyClientData.Group> groupKeys;

    public GatheringActivity.State gaState;
    public JoinSessionActivity.State jsaState;

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
    //public boolean goAdventuring; // does not exist, use the pumpers instead
    public String newGroupName;
    public byte[] newGroupKey;
    //----------------------------------------------------------------------------------------------


    // NewPartyDeviceSelectionActivity, ExplicitConnectionActivity ---------------------------------
    // SelectFormingGroupActivity
    Pumper.MessagePumpingThread[] pumpers;
    //----------------------------------------------------------------------------------------------
}

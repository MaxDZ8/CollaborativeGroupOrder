package com.massimodz8.collaborativegrouporder;

import android.app.Application;

import com.massimodz8.collaborativegrouporder.networkio.Pumper;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;
import com.massimodz8.collaborativegrouporder.protocol.nano.StartData;

import java.util.Vector;

/**
 * Created by Massimo on 05/02/2016.
 * In origin it was the (local) CrossActivityService.
 * It always felt like an hammer solution. A simpler solution does exist: just inherit from
 * Application class and use it to keep persistent state.
 */
public class CrossActivityShare extends Application {

    public JoinSessionActivity.State jsaState;
    public JoinSessionActivity.Result jsaResult;

    // SelectFormingGroupActivity ------------------------------------------------------------------
    AccumulatingDiscoveryListener explorer;
    Vector<GroupState> candidates;
    //----------------------------------------------------------------------------------------------

    // ExplicitConnectionActivity result -----------------------------------------------------------
    public Network.GroupInfo probed;
    //----------------------------------------------------------------------------------------------

    // NewCharacterProposalActivity result ---------------------------------------------------------
    //public boolean goAdventuring; // does not exist, use the pumpers instead
    public StartData.PartyClientData.Group newKey;
    //----------------------------------------------------------------------------------------------


    // NewPartyDeviceSelectionActivity, ExplicitConnectionActivity ---------------------------------
    // SelectFormingGroupActivity
    Pumper.MessagePumpingThread[] pumpers;
    //----------------------------------------------------------------------------------------------
}

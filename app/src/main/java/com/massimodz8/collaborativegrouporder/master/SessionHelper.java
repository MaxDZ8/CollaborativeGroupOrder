package com.massimodz8.collaborativegrouporder.master;

import com.massimodz8.collaborativegrouporder.PersistentDataUtils;
import com.massimodz8.collaborativegrouporder.networkio.Pumper;
import com.massimodz8.collaborativegrouporder.protocol.nano.StartData;

import java.util.ArrayList;

/**
 * Created by Massimo on 14/03/2016.
 * Getting to the real deal!
 * The first thing to do in having our heroes roam around dangerous places is to simply realize
 * from now on their stats are not just mere stats but can change as result of other actions.
 * Something else is easier BTW: we don't care about devices anymore but only about the characters
 * in the fantasy world. Plus we get mobs and perhaps at some point in the future we'll have an
 * undergoing battle.
 *
 * At any point we must be able to go back, serialize changes to our session data and restore
 * state. Session data is a superset of StartData so we have to be careful!
 */
public class SessionHelper {
    public final StartData.PartyOwnerData.Group party;
    public final PersistentDataUtils.SessionStructs stats;

    SessionHelper(StartData.PartyOwnerData.Group party, PersistentDataUtils.SessionStructs stats) {
        this.party = party;
        this.stats = stats;
    }

    void play(ArrayList<Pumper.MessagePumpingThread> boundKickOthers) {
        // TODO
        // TODO
        // TODO
        // TODO
        // TODO
    }

    PlayState getSession() {
        if(session == null) session = new PlayState();
        return session;
    }

    /// Activity interface so I can avoid dealing with the service and all.
    public class PlayState {
    }

    private PlayState session;
}

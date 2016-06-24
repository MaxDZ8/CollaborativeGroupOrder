package com.massimodz8.collaborativegrouporder;

import com.massimodz8.collaborativegrouporder.client.Adventure;
import com.massimodz8.collaborativegrouporder.client.CharacterProposals;
import com.massimodz8.collaborativegrouporder.client.JoinGame;
import com.massimodz8.collaborativegrouporder.client.PartySelection;
import com.massimodz8.collaborativegrouporder.client.PcAssignmentState;
import com.massimodz8.collaborativegrouporder.master.PartyCreator;
import com.massimodz8.collaborativegrouporder.master.PartyJoinOrder;

/**
 * Created by Massimo on 21/05/2016.
 * It turns out Samsung devices have
 *     Don't keep activities
 *     Destroy every activity as soon as the user leaves it
 * On by default.
 * User leaves an activity not only when it goes back... obviously, but also when another activity
 * comes on top. This implies Activity cannot contain any persistent state at all.
 * Now, another problem involves the amount of bindService calls and the silly binding lifetime.
 * So, let's solve those two problems together, hopefully a singleton will do.
 *
 * Cool thing: there's always one single service running.
 * Remember to set those to null as soon as you're finished with them!
 * Now who calls .startService(intent) binds and sets the handle here.
 */
public class RunningServiceHandles {
    public PartyPicker pick;
    public PartyJoinOrder play;
    public PartyCreator create;
    public Adventure clientPlay;
    public PcAssignmentState bindChars;
    public PartySelection partySelection;
    public ConnectionAttempt connectionAttempt;
    public CharacterProposals newChars;
    public JoinGame joinGame;
    public SpawnHelper search;

    public InternalStateService state;


    public static RunningServiceHandles getInstance() {
        return ourInstance;
    }


    private static RunningServiceHandles ourInstance = new RunningServiceHandles();
    private RunningServiceHandles() {}
}

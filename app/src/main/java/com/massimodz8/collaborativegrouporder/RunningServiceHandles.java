package com.massimodz8.collaborativegrouporder;

import com.massimodz8.collaborativegrouporder.master.PartyCreationService;
import com.massimodz8.collaborativegrouporder.master.PartyJoinOrderService;

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
    public PartyPickingService pick;
    public PartyJoinOrderService play;
    public PartyCreationService create;


    public static RunningServiceHandles getInstance() {
        return ourInstance;
    }


    private static RunningServiceHandles ourInstance = new RunningServiceHandles();
    private RunningServiceHandles() {}
}

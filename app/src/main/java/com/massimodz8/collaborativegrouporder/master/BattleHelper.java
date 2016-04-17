package com.massimodz8.collaborativegrouporder.master;

/**
 * Created by Massimo on 17/04/2016.
 * Getting to the real deal!
 */
public class BattleHelper {
    private final int[] initiative;
    private final AbsLiveActor[] battlers;

    /**
     * @param initiative Sorted array of initiative scores.
     * @param battlers Array of actors where battlers[i] corresponds to initiative[i]
     */
    public BattleHelper(int[] initiative, AbsLiveActor[] battlers) {
        this.initiative = initiative;
        this.battlers = battlers;
    }
}

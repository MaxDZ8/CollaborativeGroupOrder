package com.massimodz8.collaborativegrouporder.master;

import java.util.Arrays;

/**
 * Created by Massimo on 17/04/2016.
 * Getting to the real deal!
 */
public class BattleHelper {
    public final int[] initiative;
    public final AbsLiveActor[] battlers;
    public final boolean[] enabled;

    int round = -1;
    int currently = -1;

    /**
     * @param initiative Sorted array of initiative scores.
     * @param battlers Array of actors where battlers[i] corresponds to initiative[i]
     */
    public BattleHelper(int[] initiative, AbsLiveActor[] battlers) {
        this.initiative = initiative;
        this.battlers = battlers;
        enabled = new boolean[battlers.length];
        Arrays.fill(enabled, true);
    }
}

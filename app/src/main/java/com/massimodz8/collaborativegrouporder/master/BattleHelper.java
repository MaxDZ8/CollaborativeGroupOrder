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

    public int round = -1;
    public int currentActor = -1;

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

    public void tickRound() {
        if(round == -1) {
            round = 1;
            currentActor = 0;
            return;
        }
        int next = currentActor + 1;
        while(next < battlers.length && !enabled[next]) next++;
        next %= battlers.length;
        while(next < currentActor && !enabled[next]) next++;
        currentActor = next;
    }
}

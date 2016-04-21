package com.massimodz8.collaborativegrouporder.master;

import android.support.annotation.NonNull;

import com.massimodz8.collaborativegrouporder.InitiativeScore;

import java.util.Arrays;

/**
 * Created by Massimo on 17/04/2016.
 * Getting to the real deal!
 */
public class BattleHelper {
    public final InitiativeScore[] ordered;
    public final boolean[] enabled;

    public int round = -1;
    public int currentActor = -1;

    public BattleHelper(@NonNull InitiativeScore[] ordered) {
        this.ordered = ordered;
        enabled = new boolean[ordered.length];
        Arrays.fill(enabled, true);
    }

    public void tickRound() {
        if(round == -1) {
            round = 1;
            currentActor = 0;
            return;
        }
        int next = currentActor + 1;
        while(next < ordered.length && !enabled[next]) next++;
        next %= ordered.length;
        while(next < currentActor && !enabled[next]) next++;
        if(next <= currentActor) round++;
        currentActor = next;
    }
}

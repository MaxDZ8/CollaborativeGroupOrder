package com.massimodz8.collaborativegrouporder.master;

import android.support.annotation.NonNull;

import com.massimodz8.collaborativegrouporder.InitiativeScore;

/**
 * Created by Massimo on 17/04/2016.
 * Getting to the real deal!
 */
public class BattleHelper {
    public final InitiativeScore[] ordered;

    public int round = -1;
    public int currentActor = -1;
    public boolean dontTick;

    public BattleHelper(@NonNull InitiativeScore[] ordered) {
        this.ordered = ordered;
    }

    public void tickRound() {
        if(round == -1) {
            round = 1;
            currentActor = 0;
            return;
        }
        if(dontTick) { // then, this is nop.
            dontTick = false;
            return;
        }
        int next = currentActor + 1;
        while(next < ordered.length && !ordered[next].enabled) next++;
        next %= ordered.length;
        while(next < currentActor && !ordered[next].enabled) next++;
        if(next <= currentActor) round++;
        currentActor = next;
    }
}

package com.massimodz8.collaborativegrouporder.master;

import android.support.annotation.NonNull;

import com.massimodz8.collaborativegrouporder.InitiativeScore;

import java.util.ArrayList;

/**
 * Created by Massimo on 17/04/2016.
 * Getting to the real deal!
 */
public class BattleHelper {
    public final InitiativeScore[] ordered;

    public int round = -1;
    public int currentActor = -1;
    /**
     * Stack of triggered actions, they temporarily suppress normal order.
     * Triggering a readied action is no real problem: it is a pre-spent action so it does have no permanent effects on order.
     * It also always happen while some other actor is acting. This is either null or contains at least 1 element.
     */
    public ArrayList<AbsLiveActor> triggered;
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

package com.massimodz8.collaborativegrouporder.master;

import android.support.annotation.NonNull;

import com.massimodz8.collaborativegrouporder.AbsLiveActor;
import com.massimodz8.collaborativegrouporder.InitiativeScore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

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
    public boolean orderChanged = true;

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

    /**
     * Shuffle the initiative order by moving currentActor to a new position and inferring its
     * throw value so it stays coherent with 'global' order to be used when a new actor is added.
     * @param newPos Current actor will move back this one and take its place, unless this is last.
     */
    public boolean shuffleCurrent(int newPos) {
        if(currentActor == newPos) return false; // it happens with readied actions and I'm lazy
        final InitiativeScore me = ordered[currentActor];
        final InitiativeScore next = ordered[(currentActor + 1) % ordered.length];
        // Setting the .rand so it sorts correctly is complicated. Easier to rebuild them in a
        // predictable way so I can trivially infer a value transform.
        int count = 0;
        for (InitiativeScore el : ordered) {
            el.rand = count;
            count += 10;
        }
        me.initRoll = ordered[newPos].initRoll;
        me.rand = ordered[newPos].rand;
        me.rand += newPos != 0? 1 : -1;
        Arrays.sort(ordered, new Comparator<InitiativeScore>() {
            @Override
            public int compare(InitiativeScore left, InitiativeScore right) {
                if(left.rand < right.rand) return -1;
                if(left.rand == right.rand) return 0;
                return 1;
            }
        });
        dontTick = next == ordered[currentActor];
        orderChanged = true;
        return true;
    }

    private boolean dontTick;
}

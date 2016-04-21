package com.massimodz8.collaborativegrouporder;

import android.support.annotation.NonNull;

import com.massimodz8.collaborativegrouporder.master.AbsLiveActor;

/**
 * Created by Massimo on 21/04/2016.
 * What is the initiative score? Initiative is very easy at a glance: roll a dice + bonus and sort.
 * Not quite. First complication:
 * - I need to sort by total initiative scores.
 *   Solve ties preferring higher bonus.
 *   Solve further ties at random.
 * Initiative roll+bonus can be negative so I cannot just pack everything to an int in order of
 * importance and be done. Well, I can, but it starts to get messy.
 * It is convenient to keep the actor reference around for obvious reasons.
 * Another reason is actor shuffling.
 * The real rolls must stay constant, but because we have to sort somehow we might need an extra
 * key to move them around in the same roll+bonus pair. This is why our rand helps.
 * We have full control on that and we can transform it as we want, it's not exposed to user anyway!
 */
public class InitiativeScore implements Comparable<InitiativeScore> {
    public final int initRoll;
    public final int bonus;
    public final int rand;
    public final AbsLiveActor actor;

    public InitiativeScore(int initRoll, int bonus, int rand, AbsLiveActor actor) {
        this.initRoll = initRoll;
        this.bonus = bonus;
        this.rand = rand;
        this.actor = actor;
    }

    @Override
    public int compareTo(@NonNull InitiativeScore other) {
        if(initRoll > other.initRoll) return -1;
        else if(initRoll < other.initRoll) return 1;
        if(bonus > other.bonus) return -1;
        else if(bonus < other.bonus) return 1;
        if(rand > other.rand) return -1;
        else if(rand < other.rand) return 1;
        return 0; // super unlikely!
    }
}

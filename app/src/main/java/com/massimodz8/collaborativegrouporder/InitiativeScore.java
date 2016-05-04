package com.massimodz8.collaborativegrouporder;

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
public class InitiativeScore {
    public int initRoll;
    final public int bonus;
    public int rand;
    // We will try to keep those 'constant' and faithful to the original dice rolls BUT
    // this is just best effort. Better to never show those to the user as shuffle initiative
    // will change those values and will no more be coherent with actor bonus.
    public final @ActorId  int actorID;

    public boolean enabled = true; // if false, do not get round actions. Not sorted.

    public InitiativeScore(int initRoll, int bonus, int rand, int actorID) {
        this.initRoll = initRoll;
        this.bonus = bonus;
        this.rand = rand;
        this.actorID = actorID;
    }
}

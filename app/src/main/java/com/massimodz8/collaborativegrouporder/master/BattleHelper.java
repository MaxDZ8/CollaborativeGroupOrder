package com.massimodz8.collaborativegrouporder.master;

import android.support.annotation.NonNull;

import com.massimodz8.collaborativegrouporder.ActorId;
import com.massimodz8.collaborativegrouporder.InitiativeScore;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Created by Massimo on 17/04/2016.
 * Getting to the real deal!
 */
public class BattleHelper {
    public final InitiativeScore[] ordered;

    public @ActorId int currentActor = -1;
    public int round = -1;
    public boolean fromReadiedStack;
    /**
     * Stack of triggered actions, they temporarily suppress normal order.
     * It also always happen while some other actor is acting. This is either null or contains at least 1 element.
     * Do not mess with me. Go with IDs, they are truly persistent while objects might be not, much less order.
     */
    public ArrayDeque<Integer> triggered;
    public boolean orderChanged = true;

    public BattleHelper(@NonNull InitiativeScore[] ordered) {
        this.ordered = ordered;
    }

    /**
     * This was originally called 'tickRound' but what would that mean?
     * It wants to figure out the next actor to (re)activate. Readied actions complicate the thing.
     * @param really Sometimes you want to just know what will be next actor. If that's the case, set this to false.
     *               This will prevent triggered actions to be popped which means you can restore previous state trivially by
     *               setting this.currentActor to result of this function call.
     *               Yes, it will also prevent incrementing the round count and other side-effects, turning this in a getCurrentActorID call.
     * @return ID of previous actor (this.currentActor immediately before call)
     */
    public @ActorId int actorCompleted(boolean really) {
        fromReadiedStack = false;
        final int previd = shuffledPrevid < 0? currentActor : shuffledPrevid;
        if(triggered != null) {
            fromReadiedStack = really;
            currentActor = really? triggered.pop() : triggered.getLast();
            if(triggered.isEmpty()) triggered = null;
            return previd;
        }
        if(really) shuffledPrevid = -1;
        int index = 0;
        for (InitiativeScore check : ordered) {
            if(check.actorID == currentActor) break;
            index++;
        }
        int next = index + 1;
        while (next < ordered.length && !ordered[next].enabled) next++;
        next %= ordered.length;
        while (next < index && !ordered[next].enabled) next++;
        if (next <= index && really) {
            if(noRoundIncrement) {
                round--;
                noRoundIncrement = false;
            }
            round++;
        }
        currentActor = ordered[next].actorID;
        return previd;
    }

    /**
     * You want currentActor to act immediately before actor at the given position index.
     * @param newPos Current actor will be placed here.
     */
    public boolean before(int newPos) {
        int curSlot = 0;
        for (InitiativeScore el : ordered) {
            if(el.actorID == currentActor) break;
            curSlot++;
        }
        if(curSlot == newPos) return false; // it happens with readied actions and I'm lazy
        if(curSlot + 1 == newPos) return false;
        shuffledPrevid = currentActor;
        final InitiativeScore mover = ordered[curSlot];
        final int step = newPos < curSlot? -1 : 1;
        for(int index = curSlot; index != newPos; index += step) ordered[index] = ordered[index + step];
        ordered[newPos] = mover;
        if(newPos != 0 && newPos > curSlot) {
            InitiativeScore temp = ordered[newPos];
            ordered[newPos] = ordered[newPos - 1];
            ordered[newPos - 1] = temp;
        }
        final int previ;
        if(newPos != 0) previ = newPos - 1;
        else {
            previ = ordered.length - 1;
            noRoundIncrement = true;
        }
        currentActor = ordered[previ].actorID;
        orderChanged = true;
        return true;
    }
    boolean noRoundIncrement;
    int shuffledPrevid = -1;
}

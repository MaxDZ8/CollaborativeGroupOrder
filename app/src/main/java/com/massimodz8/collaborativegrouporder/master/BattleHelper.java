package com.massimodz8.collaborativegrouporder.master;

import android.support.annotation.NonNull;

import com.massimodz8.collaborativegrouporder.ActorId;
import com.massimodz8.collaborativegrouporder.InitiativeScore;

import java.util.ArrayDeque;

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
        final int previd = currBeforeShuffle < 0? currentActor : currBeforeShuffle;
        if(triggered != null) {
            fromReadiedStack = really;
            currentActor = really? triggered.pop() : triggered.getLast();
            if(triggered.isEmpty()) triggered = null;
            return previd;
        }
        if(nextBeforeShuffle >= 0) {
            currentActor = nextBeforeShuffle;
            if(really) {
                int curIndex = 0, prevIndex = 0;
                for (InitiativeScore check : ordered) {
                    if(check.actorID == nextBeforeShuffle) break;
                    curIndex++;
                }
                for (InitiativeScore check : ordered) {
                    if(check.actorID == previd) break;
                    prevIndex++;
                }
                currBeforeShuffle = -1;
                nextBeforeShuffle = -1;
                if(curIndex <= prevIndex) round++;
            }
            return previd;
        }
        int index = 0;
        for (InitiativeScore check : ordered) {
            if(check.actorID == currentActor) break;
            index++;
        }
        int next = index + 1;
        while (next < ordered.length && !ordered[next].enabled) next++;
        next %= ordered.length;
        while (next < index && !ordered[next].enabled) next++;
        if (next <= index && really) round++;
        currentActor = ordered[next].actorID;
        return previd;
    }

    /**
     * You want currentActor to act immediately before actor at the given position index.
     * Shuffling last to first is a bit odd as the same actor will act twice.
     * It is assumed a actorCompleted will follow before another shuffle.
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
        currBeforeShuffle = currentActor;
        if (newPos == 0 && curSlot == ordered.length - 1) { // important exception! Two acts in a row
            nextBeforeShuffle = currentActor;
        } else {
            nextBeforeShuffle = curSlot + 1;
            while (nextBeforeShuffle < ordered.length && !ordered[nextBeforeShuffle].enabled) nextBeforeShuffle++;
            nextBeforeShuffle %= ordered.length;
            while (nextBeforeShuffle < curSlot && !ordered[nextBeforeShuffle].enabled) nextBeforeShuffle++;
            nextBeforeShuffle = ordered[nextBeforeShuffle].actorID;
        }
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
        else previ = ordered.length - 1;
        currentActor = ordered[previ].actorID;
        orderChanged = true;
        return true;
    }
    int nextBeforeShuffle = -1;
    int currBeforeShuffle = -1;
}

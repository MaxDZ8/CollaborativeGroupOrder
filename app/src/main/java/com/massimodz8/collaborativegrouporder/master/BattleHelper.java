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
        final int previd = currentActor;
        if(triggered != null) {
            fromReadiedStack = really;
            currentActor = really? triggered.pop() : triggered.getLast();
            if(triggered.isEmpty()) triggered = null;
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

    // Must call an actionCompleted() after this.
    public boolean moveCurrentToSlot(int newPos) {
        if (newPos >= ordered.length) return false; // wut? Impossible!
        int curPos = 0;
        for (InitiativeScore el : ordered) {
            if(el.actorID == currentActor) break;
            curPos++;
        }
        if(newPos == curPos) return false; // NOP
        final boolean forward = newPos > curPos;
        final int mini = forward? curPos : newPos;
        final int maxi = forward? newPos : curPos;
        final InitiativeScore temp = ordered[curPos];
        if(curPos == 0) { // head forward
            if(newPos != ordered.length - 1) currentActor = ordered[ordered.length - 1].actorID; // so next tick will always activate new head.
            round--;
            System.arraycopy(ordered, 1, ordered, 0, newPos);
        }
        else if(forward) {
            currentActor = ordered[curPos - 1].actorID;
            System.arraycopy(ordered, curPos + 1, ordered, curPos + 1 - 1, newPos - curPos);
        }
        else {
            currentActor = ordered[curPos - 1].actorID; // if curPos is zero cannot be moving backwards
            for(int cp = maxi; cp != mini; cp--) ordered[cp] = ordered[cp - 1];
        }
        ordered[newPos] = temp;
        return true;
    }
}

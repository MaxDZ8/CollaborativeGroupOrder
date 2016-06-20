package com.massimodz8.collaborativegrouporder.master;

import android.support.annotation.NonNull;

import com.massimodz8.collaborativegrouporder.ActorId;
import com.massimodz8.collaborativegrouporder.InitiativeScore;
import com.massimodz8.collaborativegrouporder.protocol.nano.Session;

import java.util.ArrayDeque;

/**
 * Created by Massimo on 17/04/2016.
 * Getting to the real deal!
 */
public class BattleHelper {
    public final InitiativeScore[] ordered;

    public static final int INVALID_ACTOR = -1;
    public @ActorId int currentActor = INVALID_ACTOR; // only relevant if this.round != 0
    public int round = 0;
    boolean prevWasReadied;
    /**
     * Stack of triggered actions, they temporarily suppress normal order.
     * It also always happen while some other actor is acting. This is either null or contains at least 1 element.
     * Do not mess with me. Go with IDs, they are truly persistent while objects might be not, much less order.
     * The current actor is always this.currentActor.
     * This contains the ids interrupted, which can be used to restore the 'previous' this.currentActor value.
     */
    public @ActorId ArrayDeque<Integer> interrupted;

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
        prevWasReadied = false;
        final int previd = currentActor;
        if(interrupted != null) {
            currentActor = really? interrupted.pop() : interrupted.getFirst();
            if(interrupted.isEmpty()) interrupted = null;
            prevWasReadied = true;
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
     * @param newPos this.currentActor and newPos define a (improper) sub-sequence in the
     * @param keepCurrentActor Most of the time, you want to keep the next actor to the "current next",
     *            shuffling would most likely change the 'next' as currentActor gets moved forward
     *            or back so next tick would jump forward or backwards.
     *            So, when this is false, this.currentActor is updated so a this.actorCompleted call
     *            retrieves the 'next' actor at the time of the shuffle.
     *            When you trigger readied actions instead you just want to keep this.currentActor
     *            as you set for the shuffle. So you set this param to true.
     * @return False if nothing moves.
     */
    // Must call an actionCompleted() after this.
    public boolean moveCurrentToSlot(int newPos, boolean keepCurrentActor) {
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
            if(newPos != ordered.length - 1 && !keepCurrentActor) currentActor = ordered[ordered.length - 1].actorID; // so next tick will always activate new head.
            round--;
            System.arraycopy(ordered, 1, ordered, 0, newPos);
        }
        else if(forward) {
            if(!keepCurrentActor) currentActor = ordered[curPos - 1].actorID;
            System.arraycopy(ordered, curPos + 1, ordered, curPos + 1 - 1, newPos - curPos);
        }
        else {
            if(!keepCurrentActor) currentActor = ordered[curPos - 1].actorID; // if curPos is zero cannot be moving backwards
            for(int cp = maxi; cp != mini; cp--) ordered[cp] = ordered[cp - 1];
        }
        ordered[newPos] = temp;
        return true;
    }

    Session.BattleState asProtoBuf() {
        Session.BattleState res = new Session.BattleState();
        res.round = round;
        if(res.round != 0) res.currentActor = currentActor;
        res.prevWasReadied = prevWasReadied;
        if(interrupted != null) {
            res.interrupted = new int[interrupted.size()];
            for (int cp = 0; cp < res.interrupted.length; cp++) {
                int dst = res.interrupted.length - 1 - cp;
                res.interrupted[dst] = interrupted.pop();
            }
            for (int el : res.interrupted) interrupted.push(el);
        }
        res.initiative = new int[ordered.length * 3];
        res.id = new int[ordered.length];
        res.enabled = new boolean[ordered.length];
        int slow = 0, fast = 0;
        for (InitiativeScore el : ordered) {
            res.id[slow] = el.actorID;
            res.enabled[slow++] = el.enabled;
            res.initiative[fast++] = el.initRoll;
            res.initiative[fast++] = el.bonus;
            res.initiative[fast++] = el.rand;
        }
        return res;
    }
}

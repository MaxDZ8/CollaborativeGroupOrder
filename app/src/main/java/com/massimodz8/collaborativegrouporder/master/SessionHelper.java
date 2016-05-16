package com.massimodz8.collaborativegrouporder.master;

import android.util.ArraySet;

import com.massimodz8.collaborativegrouporder.ActorId;
import com.massimodz8.collaborativegrouporder.PersistentDataUtils;
import com.massimodz8.collaborativegrouporder.networkio.Events;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.protocol.nano.MonsterData;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;
import com.massimodz8.collaborativegrouporder.protocol.nano.Session;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by Massimo on 14/03/2016.
 * Getting to the real deal!
 * The first thing to do in having our heroes roam around dangerous places is to simply realize
 * from now on their stats are not just mere stats but can change as result of other actions.
 * Something else is easier BTW: we don't care about devices anymore but only about the characters
 * in the fantasy world. Plus we get mobs and perhaps at some point in the future we'll have an
 * undergoing battle.
 *
 * At any point we must be able to go back, serialize changes to our session data and restore
 * state. Session data is a superset of StartData so we have to be careful!
 */
public abstract class SessionHelper {
    abstract void onRollReceived(); // called after rollRequest.push
    abstract public void turnDone(MessageChannel from, int peerKey);
    public abstract void shuffle(MessageChannel from, @ActorId int peerKey, int newSlot);


    public final Session.Suspended stats;
    public final ArrayList<Network.ActorState> existByDef;
    public final MonsterData.MonsterBook monsters, customMobs;
    public BattleHelper battleState;
    public ArrayDeque<Events.Roll> rollResults = new ArrayDeque<>(); // this is to be used even before battle starts.
    /**
     * When a battle is terminated, stuff is moved there for XP awarding.
     * When gone from there, delete it forever (from the pooled ids, usually from SessionHelper.temporaries)
     */
    public ArrayList<DefeatedData> defeated;
    public @ActorId  ArrayList<WinnerData> winners;

    /**
     * If this is non-null then we're preparing to start a new battle.
     * For each actor involved in battle, this map holds the original request sent and the result.
     * This is supposed to be an identity reference hash map reset to null as soon as the battle is done.
     * It originally mapped ActorState to its Initiative structure but those objects can now come and go
     * so we're back to using their guaranteed to be unique peerkeys.
     */
    public Map<Integer, Initiative> initiatives;

    public static class Initiative {
        final Network.Roll request; // can be null for 'local' actors, automatically rolled
        Integer rolled; // if null, no result got yet!

        public Initiative(Network.Roll request) {
            this.request = request;
        }
    }


    SessionHelper(Session.Suspended stats, ArrayList<Network.ActorState> existByDef, MonsterData.MonsterBook monsters, MonsterData.MonsterBook customMobs) {
        this.stats = stats;
        this.existByDef = existByDef;
        this.monsters = monsters;
        this.customMobs = customMobs;
    }

    static class DefeatedData {
        final @ActorId int id;
        final int numerator;
        final int denominator;
        boolean consume = true;

        DefeatedData(@ActorId int id, int numerator, int denominator) {
            this.id = id;
            this.numerator = numerator;
            this.denominator = denominator;
        }
    }

    static class WinnerData {
        final @ActorId int id;
        boolean award = true;

        WinnerData(@ActorId int id) {
            this.id = id;
        }
    }

    void add(Network.ActorState actor) { temporaries.add(actor); }
    boolean willFight(@ActorId int id, Boolean newFlag) {
        boolean currently = fighters.contains(id);
        if(newFlag == null) return currently;
        if(newFlag) fighters.add(id);
        else fighters.remove(id);
        return newFlag;
    }
    int getNumActors() { return existByDef.size() + temporaries.size(); }
    Network.ActorState getActor(int i) {
        int sz = existByDef.size();
        return i < sz ? existByDef.get(i) : temporaries.get(i - sz);
    }

    public Network.ActorState getActorById(@ActorId  int id) {
        for (Network.ActorState el : existByDef) {
            if(el.peerKey == id) return el;
        }
        for (Network.ActorState el : temporaries) {
            if(el.peerKey == id) return el;
        }
        return null;
    }

    public final ArrayList<Network.ActorState> temporaries = new ArrayList<>();
    private final Set<Integer> fighters = new HashSet<>();
}

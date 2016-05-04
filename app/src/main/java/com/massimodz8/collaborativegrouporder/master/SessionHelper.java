package com.massimodz8.collaborativegrouporder.master;

import com.massimodz8.collaborativegrouporder.ActorId;
import com.massimodz8.collaborativegrouporder.PersistentDataUtils;
import com.massimodz8.collaborativegrouporder.networkio.Events;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.protocol.nano.MonsterData;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;
import com.massimodz8.collaborativegrouporder.protocol.nano.StartData;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Map;

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
public class SessionHelper {
    public final StartData.PartyOwnerData.Group party;
    public final PersistentDataUtils.SessionStructs stats;
    public final ArrayList<Network.ActorState> existByDef;

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


    SessionHelper(StartData.PartyOwnerData.Group party, PersistentDataUtils.SessionStructs stats, ArrayList<Network.ActorState> existByDef) {
        this.party = party;
        this.stats = stats;
        this.existByDef = existByDef;
    }

    /// Activity interface so I can avoid dealing with the service and all.
    public static abstract class PlayState {
        public final MonsterData.MonsterBook monsters;
        private final SessionHelper session;
        private final PcAssignmentHelper assignment;
        public BattleHelper battleState;
        public ArrayDeque<Events.Roll> rollResults = new ArrayDeque<>(); // this is to be used even before battle starts.

        abstract void onRollReceived(); // called after rollRequest.push

        public PlayState(SessionHelper session, MonsterData.MonsterBook monsters, PcAssignmentHelper assignment) {
            this.monsters = monsters;
            this.session = session;
            this.assignment = assignment;
        }

        void add(Network.ActorState actor) { session.temporaries.add(actor); }
        boolean willFight(Network.ActorState actor, Boolean newFlag) {
            boolean currently = session.fighters.contains(actor.peerKey);
            if(newFlag == null) return currently;
            if(newFlag) session.fighters.add(actor.peerKey);
            else session.fighters.remove(session.fighters.indexOf(actor.peerKey));
            return newFlag;
        }
        int getNumActors() { return session.existByDef.size() + session.temporaries.size(); }
        Network.ActorState getActor(int i) {
            int sz = session.existByDef.size();
            return i < sz ? session.existByDef.get(i) : session.temporaries.get(i - sz);
        }


        public MessageChannel activateNewActor() {
            final int active = battleState.triggered == null ? battleState.ordered[battleState.currentActor].actorID : battleState.triggered.get(battleState.triggered.size() - 1);
            final MessageChannel pipe = assignment.getMessageChannelByPeerKey(active);
            if(pipe != null) {
                int roundType = battleState.triggered == null ? Network.TurnControl.T_REGULAR : Network.TurnControl.T_PREPARED_TRIGGERED;
                final PcAssignmentHelper.PlayingDevice dev = assignment.getDevice(pipe);
                assignment.activateRemote(dev, active, roundType, battleState.round);
            }
            return pipe;
        }

        public Network.ActorState getActorById(@ActorId  int id) {
            for (Network.ActorState el : session.existByDef) {
                if(el.peerKey == id) return el;
            }
            for (Network.ActorState el : session.temporaries) {
                if(el.peerKey == id) return el;
            }
            return null;
        }
    }

    public PlayState session;
    private final ArrayList<Network.ActorState> temporaries = new ArrayList<>();
    private final ArrayList<Integer> fighters = new ArrayList<>();
}

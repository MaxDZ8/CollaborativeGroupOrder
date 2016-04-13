package com.massimodz8.collaborativegrouporder.master;

import android.support.annotation.NonNull;

import com.massimodz8.collaborativegrouporder.PersistentDataUtils;
import com.massimodz8.collaborativegrouporder.networkio.Pumper;
import com.massimodz8.collaborativegrouporder.protocol.nano.MonsterData;
import com.massimodz8.collaborativegrouporder.protocol.nano.StartData;

import java.util.ArrayList;
import java.util.HashSet;

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
    public final ArrayList<AbsLiveActor> existByDef;

    SessionHelper(StartData.PartyOwnerData.Group party, PersistentDataUtils.SessionStructs stats, ArrayList<AbsLiveActor> existByDef, MonsterData.MonsterBook monsters) {
        this.party = party;
        this.stats = stats;
        this.existByDef = existByDef;
        this.monsters = monsters;
    }

    PlayState getSession() {
        if(session == null) session = new PlayState(monsters);
        return session;
    }

    /// Activity interface so I can avoid dealing with the service and all.
    public class PlayState {
        public final MonsterData.MonsterBook monsters;

        public PlayState(MonsterData.MonsterBook monsters) {
            this.monsters = monsters;
        }

        void begin(@NonNull Runnable onComplete) {
            onComplete.run();
        }
        void end() { }

        void add(AbsLiveActor actor) { temporaries.add(actor); }
        boolean willFight(AbsLiveActor actor, Boolean newFlag) {
            boolean currently = fighters.contains(actor);
            if(newFlag == null) return currently;
            if(newFlag) fighters.add(actor);
            else fighters.remove(actor);
            return newFlag;
        }
        int getNumActors() { return existByDef.size() + temporaries.size(); }
        AbsLiveActor getActor(int i) {
            int sz = existByDef.size();
            return i < sz ? existByDef.get(i) : temporaries.get(i - sz);
        }
    }

    private PlayState session;
    private final MonsterData.MonsterBook monsters;
    private final ArrayList<AbsLiveActor> temporaries = new ArrayList<>();
    private final HashSet<AbsLiveActor> fighters = new HashSet<>();
    private Pumper netPump;

}

package com.massimodz8.collaborativegrouporder.master;

import com.massimodz8.collaborativegrouporder.AbsLiveActor;

/**
 * Created by Massimo on 16/03/2016.
 * Characters (not monsters) might be here, they might be on remote... they have stuff we are
 * interested in tracking.
 */
public class MonsterActor extends AbsLiveActor {
    public int initiativeBonus;
    public int maxHealth;
    public int currentHealth;

    protected MonsterActor(String displayName) {
        super(displayName, TYPE_MONSTER);
    }

    @Override
    public int getInitiativeBonus() { return initiativeBonus; }

    @Override
    public int[] getHealth() {
        return new int[] { currentHealth, maxHealth };
    }
}

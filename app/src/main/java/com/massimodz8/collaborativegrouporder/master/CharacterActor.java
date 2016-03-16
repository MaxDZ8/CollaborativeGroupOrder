package com.massimodz8.collaborativegrouporder.master;

/**
 * Created by Massimo on 16/03/2016.
 * Characters (not monsters) might be here, they might be on remote... they have stuff we are
 * interested in tracking.
 */
public class CharacterActor extends AbsLiveActor {
    public int initiativeBonus;
    public int maxHealth;
    public int currentHealth;
    public int experience;

    protected CharacterActor(String displayName, boolean isPlayingCharacter) {
        super(displayName, isPlayingCharacter? TYPE_PLAYING_CHARACTER : TYPE_NPC);
    }

    @Override
    int getInitiativeBonus() { return initiativeBonus; }

    @Override
    int[] getHealth() {
        return new int[] { currentHealth, maxHealth };
    }
}

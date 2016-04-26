package com.massimodz8.collaborativegrouporder;

import com.massimodz8.collaborativegrouporder.protocol.nano.StartData;

/**
 * Created by Massimo on 16/03/2016.
 * Characters (not monsters) might be here, they might be on remote... they have stuff we are
 * interested in tracking.
 */
public class CharacterActor extends AbsLiveActor {
    public final StartData.ActorDefinition character; // this is tracked so we can decide if this is remote or local
    public int initiativeBonus;
    public int maxHealth;
    public int currentHealth;
    public int experience;

    public CharacterActor(String displayName, boolean isPlayingCharacter, StartData.ActorDefinition key) {
        super(displayName, isPlayingCharacter? TYPE_PLAYING_CHARACTER : TYPE_NPC);
        character = key;
    }

    @Override
    public int getInitiativeBonus() { return initiativeBonus; }

    @Override
    public int[] getHealth() {
        return new int[] { currentHealth, maxHealth };
    }



    // Copy-paste from JoinOrderService!
    public static CharacterActor makeLiveActor(StartData.ActorDefinition definition, boolean playingCharacter) {
        CharacterActor build = new CharacterActor(definition.name, playingCharacter, definition);
        build.initiativeBonus = definition.stats[0].initBonus;
        build.currentHealth = build.maxHealth = definition.stats[0].healthPoints;
        build.experience = definition.experience;
        return build;
    }
}

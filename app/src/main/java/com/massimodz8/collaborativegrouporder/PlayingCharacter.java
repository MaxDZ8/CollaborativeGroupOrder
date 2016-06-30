package com.massimodz8.collaborativegrouporder;

import com.massimodz8.collaborativegrouporder.protocol.nano.Network;

/**
 * Created by Massimo on 22/01/2016.
 * Characters might be created from 'proposals' received from the network or perhaps they might be
 * created from data we loaded from storage. We cannot be pissed off with the differences at runtime
 * so everything will be converted to a 'runtime memory' representation.
 */
public class PlayingCharacter {
    public String name = "";
    public int initiativeBonus = Integer.MIN_VALUE, fullHealth = Integer.MIN_VALUE, experience = Integer.MIN_VALUE;

    PlayingCharacter() {    }

    PlayingCharacter(Network.PlayingCharacterDefinition pc) {
        name = pc.name;
        initiativeBonus = pc.initiativeBonus;
        fullHealth = pc.healthPoints;
        experience = pc.experience;
    }
}

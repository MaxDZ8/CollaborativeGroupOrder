package com.massimodz8.collaborativegrouporder;

import com.massimodz8.collaborativegrouporder.protocol.nano.Network;
import com.massimodz8.collaborativegrouporder.protocol.nano.RPGClass;

/**
 * Created by Massimo on 26/01/2016.
 * Used across client and server when a new party is generated.
 */
public class BuildingPlayingCharacter extends PlayingCharacter {
    public final int peerKey, unique;
    public int status = STATUS_BUILDING;

    public static final int STATUS_BUILDING = 0;
    public static final int STATUS_SENT = 1;
    public static final int STATUS_ACCEPTED = 2;
    public static final int STATUS_REJECTED = 3;

    public final RPGClass.LevelClass lastLevelClass;

    /// Used by the client, there's no real peer list used here, we're fine with creation count.
    public BuildingPlayingCharacter() {
        peerKey = -1;
        unique = ++count;
        lastLevelClass = new RPGClass.LevelClass();
    }

    /// This ctor is used by the server instead: the ids are provided over the wire.
    public BuildingPlayingCharacter(Network.PlayingCharacterDefinition def) {
        super(def);
        peerKey = def.peerKey;
        unique = ++count;
        lastLevelClass = def.career;
    }
    private static int count = 0;
}

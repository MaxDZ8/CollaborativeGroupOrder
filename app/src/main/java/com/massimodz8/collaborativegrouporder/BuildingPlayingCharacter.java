package com.massimodz8.collaborativegrouporder;

import com.massimodz8.collaborativegrouporder.protocol.nano.Network;

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
        private static int count = 0;

    /// This ctor is used by the server instead: the ids are provided over the wire.
    public BuildingPlayingCharacter(Network.PlayingCharacterDefinition def) {
        super(def);
        peerKey = def.peerKey;
        unique = ++count;
    }
}

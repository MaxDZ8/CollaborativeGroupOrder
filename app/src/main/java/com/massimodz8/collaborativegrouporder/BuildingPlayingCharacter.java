package com.massimodz8.collaborativegrouporder;

/**
 * Created by Massimo on 26/01/2016.
 * Used across client and server when a new party is generated.
 */
public class BuildingPlayingCharacter extends PlayingCharacter {
        final int id;
        public int status = STATUS_BUILDING;
        static final int STATUS_BUILDING = 0;
        static final int STATUS_SENT = 1;
        static final int STATUS_ACCEPTED = 2;
        static final int STATUS_REJECTED = 3;
        private static int count = 0;

    /// This ctor can be used by client to generate unique character id with ease.
    /// Remember the ids are unique by device, the server considers devices separately.
    BuildingPlayingCharacter() {
        id = ++count;
    }

    /// This ctor is used by the server instead: the ids are provided over the wire.
    BuildingPlayingCharacter(int provided) {
        id = provided;
    }
}

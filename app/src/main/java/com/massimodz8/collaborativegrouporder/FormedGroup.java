package com.massimodz8.collaborativegrouporder;

import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;

import java.util.Vector;

/**
 * Created by Massimo on 24/01/2016.
 * Not really a 'formed' group! Also used to build a group. The bottom line is that it keeps
 * track of various data regarding pgs for a group definition and peer identification. More or less.
 */
public class FormedGroup extends ConnectedGroup {
    /// This must be a vector, not a map because we need the ids to be in a predictable relationship
    /// with what's being displayed by the talking device list, which indices this by index. MOFO
    public Vector<PlayingCharacter> group = new Vector<>();

    /// Unique id of this group. The outer logic decides what to put there. It is imperative
    /// this is kept around somehow as devices will try to join a group using this token.
    public String unique;

    public FormedGroup(int version, String name) {
        super(version, name);
    }

    static class PlayingCharacter {
        String name; /// Multiple playing characters can have the same name. It's your business.
        int initiativeBonus;
        int experience;
        final String key; // the key uniquely identifies the character in some unspecified context.

        public PlayingCharacter(String key) {
            this.key = key;
        }
    }
}

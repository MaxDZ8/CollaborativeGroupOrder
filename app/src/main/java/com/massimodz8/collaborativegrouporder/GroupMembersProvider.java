package com.massimodz8.collaborativegrouporder;

import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;

import java.util.Vector;

/**
 * Created by Massimo on 22/01/2016.
 * Some abstraction glue to abstract a bit the process of building a list of characters once
 * we have decided which devices are part of the group.
 * Most of the data here is expected to be 'sort of constant'. When views are created, this data
 * is pulled and then assumed to stay there.
 * The only exception is the PlayingCharacter vector, which users of this interface might modify.
 */
public interface GroupMembersProvider {
    /// Most other calls have a parameter index. Its value must be < of return value.
    int getDeviceCount();

    String getLastWords(int index);
    MessageChannel getChannel(int index);

    /** This is not a proxy! It is exactly the place where the owner stores playing characters
     * definitions. This might require work to keep in sync whit usages but the whole point is I want
     * to avoid copying stuff back and forth or sync'ing state across objects.
     */
    Vector<PlayingCharacter> getCharacters(int index);
}

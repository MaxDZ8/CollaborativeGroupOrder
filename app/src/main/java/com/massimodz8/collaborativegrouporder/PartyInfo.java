package com.massimodz8.collaborativegrouporder;

/**
 * Created by Massimo on 31/12/2015.
 * The first step in connecting to a server / group is connecting to it.
 * The server will reply with a server version and a group name.
 * If it's compatible with us the connection is considered up.
 */
public class PartyInfo {
    public int version;
    public String name;
    /** Each of those strings is a group option enabled by server. Examples could be:
     * 1- "player to master msg": send message to master for his/her eyes only
     * 2- "master rolls": players won't roll for initiative, master does.
     */
    public String[] options;

    public final int advancementPace;

    public PartyInfo(int version, String name, int advancementPace) {
        this.version = version;
        this.name = name;
        this.advancementPace = advancementPace;
    }
}

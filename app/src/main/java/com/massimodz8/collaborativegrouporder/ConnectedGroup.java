package com.massimodz8.collaborativegrouporder;

import java.io.Serializable;

/**
 * Created by Massimo on 31/12/2015.
 * The first step in connecting to a server / group is connecting to it.
 * The server will reply with a server version and a group name.
 * If it's compatible with us the connection is considered up.
 */
public class ConnectedGroup implements Serializable {
    public int version;
    public String name;
}

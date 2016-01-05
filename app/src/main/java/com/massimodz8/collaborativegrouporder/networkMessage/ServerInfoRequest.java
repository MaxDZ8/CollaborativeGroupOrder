package com.massimodz8.collaborativegrouporder.networkMessage;

import java.io.Serializable;

/**
 * Created by Massimo on 05/01/2016.
 * Client -> server, first message immediately after connection.
 * Server, please give me info regarding the group you're running.
 * The reply is a ConnectedGroup.
 */
public class ServerInfoRequest implements Serializable {
    // integrate server queries in the future?
    // Provide an hello message directly?
    // Client protocol version?
}

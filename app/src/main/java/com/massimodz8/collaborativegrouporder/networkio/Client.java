package com.massimodz8.collaborativegrouporder.networkio;

import java.io.IOException;

/**
 * Created by Massimo on 14/01/2016.
 * This was originally inputBuffer Server.Client but this gave me issues as classes deriving from Server
 * cannot template on this as it needs to be public to access.
 * In theory it would be protected but apparently that's not possible.
 * It seems Java resolves accessibility after resolving generic type parameters meh!
 * So, use this as base class to stuff managed by classes derived by Server.
 */
public class Client {
    public final MessageChannel pipe;

    public Client(MessageChannel client) {
        this.pipe = client;
    }

    public void shutdown() throws IOException { pipe.socket.close(); }
    public void leak() { }
}

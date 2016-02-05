package com.massimodz8.collaborativegrouporder.networkio;


import java.io.IOException;
import java.net.ServerSocket;

/**
 * Created by Massimo on 13/01/2016.
 * Initially, clients connect through TCP, they get promoted from a server to the other depending
 * on what the callbacks do. This takes a server socket managed by something else, spawns a thread
 * to wait on connections and then for each connection spawns a thread to wait for bytes.
 */
public abstract class LandingServer {
    private final ServerSocket landing;
    private final Thread acceptor;
    private volatile boolean shuttingDown = false;

    public LandingServer(ServerSocket source) {
        this.landing = source;
        acceptor = new Thread() {
            @Override
            public void run() {
                while(!isInterrupted()) {
                    MessageChannel newComer;
                    try {
                        newComer = new MessageChannel(landing.accept());
                    } catch (IOException e) {
                        if(!shuttingDown) failedAccept();
                        return;
                    }
                    connected(newComer);
                }
            }
        };
        acceptor.start();
    }


    public void shutdown() {
        shuttingDown = true;
        acceptor.interrupt();
    }

    /// Called if we cannot create a connection for a new client.
    public abstract void failedAccept();

    /// Called after a connection has been successfully added to the list of managed stuff.
    public abstract void connected(MessageChannel newComer);
}

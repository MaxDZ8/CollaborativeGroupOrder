package com.massimodz8.collaborativegrouporder.networkio;


import java.io.IOException;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

/**
 * Created by Massimo on 13/01/2016.
 * Initially, clients connect through TCP, they get promoted from a server to the other depending
 * on what the callbacks do. This takes a server socket managed by something else, spawns a thread
 * to wait on connections and then for each connection spawns a thread to wait for bytes.
 */
public abstract class LandingServer {
    private final ServerSocket landing;
    private final Thread acceptor;
    private final Callbacks hooks;

    public interface Callbacks {
        /// Called if we cannot create a connection for a new client.
        void failedAccept();

        /// Called after a connection has been successfully added to the list of managed stuff.
        void connected(MessageChannel newComer);
    }

    public LandingServer(ServerSocket source, Callbacks hooks_) {
        this.landing = source;
        hooks = hooks_;
        acceptor = new Thread() {
            @Override
            public void run() {
                while(!isInterrupted()) {
                    MessageChannel newComer;
                    try {
                        newComer = new MessageChannel(landing.accept());
                    } catch (IOException e) {
                        hooks.failedAccept();
                        return;
                    }
                    hooks.connected(newComer);
                }
            }
        };
        acceptor.start();
    }


    public void shutdown() {
        acceptor.interrupt();
        try {
            landing.close();
        } catch(IOException e ) {} // wut?
    }
}

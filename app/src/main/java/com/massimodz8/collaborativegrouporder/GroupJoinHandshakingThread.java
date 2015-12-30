package com.massimodz8.collaborativegrouporder;


import java.net.Socket;

/**
 * Created by Massimo on 30/12/2015.
 */
public class GroupJoinHandshakingThread extends Thread {
    Socket mangling;
    PlayerCollectorInterface group;
    String collected;

    GroupJoinHandshakingThread(Socket friend, PlayerCollectorInterface dst) {
        mangling = friend;
        group = dst;
    }

    @Override
    public void run() {
        collected = "TESTING STUB!";
        try {
            wait(5000);
        } catch (InterruptedException e) {
        }
        group.connect(mangling, collected);
    }
}

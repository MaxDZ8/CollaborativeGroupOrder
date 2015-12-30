package com.massimodz8.collaborativegrouporder;

import java.net.InetAddress;
import java.net.Socket;

/**
 * Created by Massimo on 30/12/2015.
 */
public interface PlayerCollectorInterface {
    /** Returns false if this internet address is already known and to be rejected. */
    boolean isForbidden(InetAddress connecting);

    /** If a peer cannot handshake there's no point in keeping its socket around. We're going
     * to drop it right away but we signal peer info and a reason for failure.
     */
    void connectionFailed(InetAddress byebye, int port, String reason);

    /** So handshake is cool and we can therefore let this device to connect.
     * Handshake is considered completed after the player sends an hello string. */
    void connect(Socket sticker, String hello);
}

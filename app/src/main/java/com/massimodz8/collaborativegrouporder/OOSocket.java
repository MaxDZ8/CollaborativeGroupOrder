package com.massimodz8.collaborativegrouporder;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 * Created by Massimo on 08/01/2016.
 * Object oriented socket. Or: a TCP pipe sending objects across the wire.
 */
public class OOSocket {
    public Socket s;
    public ObjectOutputStream writer;
    public ObjectInputStream reader;

    public OOSocket(Socket s) throws IOException {
        this.s = s;
        writer = new ObjectOutputStream(s.getOutputStream());
        reader = new ObjectInputStream(s.getInputStream());
    }
}

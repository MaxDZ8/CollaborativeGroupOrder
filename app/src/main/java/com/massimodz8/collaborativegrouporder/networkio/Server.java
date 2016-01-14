package com.massimodz8.collaborativegrouporder.networkio;

import android.os.Handler;

import com.google.protobuf.nano.MessageNano;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

/**
 * Created by Massimo on 13/01/2016.
 * The basic server maintains a list of MessageChannels and associates a set of threads to
 * sleep on them waiting for bytes to mangle according to the protocol based on proto3.
 * It also keeps track of exceptions caused the puller thread to give up.
 * The various threads will pull up a protobuf3 message, look it up on a collection of callbacks
 * and dispatch them to matches, hopefully they are thred safe!
 *
 * The underlying goal is to funnel various threads to a single Handler by provided Callbacks.
 */
public abstract class Server<ClientInfo extends Server.Client> {
    public static final int MAX_MSG_FROM_WIRE_BYTES = 4 * 1024;
    protected final Handler handler;
    private final int disconnectMessageCode;

    public Server(Handler handler, int disconnectMessageCode) {
        this.handler = handler;
        this.disconnectMessageCode = disconnectMessageCode;
    }

    public interface Callbacks<ClientInfo, ExtendedMessage extends MessageNano> {
        /** Generate a new message for parsing a blob of data. Must be 'cleared'.
         * Can be called by multiple threads at once.
         */
        ExtendedMessage make();
        /** Consume a blob of data produced by a previous call to make().
         * Note: no way to reply to a message, send something like it is a new message. */
        void mangle(ClientInfo from, ExtendedMessage msg) throws IOException;
    }

    // Call this before starting to mangle stuff so it does not need to be thread protected.
    public Server<ClientInfo> add(int key, Callbacks funcs) {
        allowed.put(key, funcs);
        return this;
    }

    public synchronized void add(MessageChannel c) {
        Managed newComer = new Managed(allocate(c));
        newComer.sleeper = new MessagePumpingThread(newComer);
        clients.add(newComer);
        newComer.sleeper.start();
    }

    public synchronized void remove(MessageChannel c, boolean leaking) throws IOException {
        for(int i = 0; i < clients.size(); i++) {
            Managed el = clients.elementAt(i);
            if(el.smart.pipe == c) {
                el.shutdown(leaking);
                clients.remove(i);
                break;
            }
        }
    }


    protected abstract ClientInfo allocate(MessageChannel c);

    private class Managed {
        public ClientInfo smart;
        Thread sleeper;
        Exception quitError;

        public Managed(ClientInfo smart) {
            this.smart = smart;
        }

        public void shutdown(boolean leakSocket) {
            sleeper.interrupt(); // we don't need this in any case, going to another server.
            smart.shutdown(leakSocket);
        }
    }


    static protected class Client {
        public final MessageChannel pipe;
        public Client(MessageChannel client) {
            this.pipe = client;
        }
        public void shutdown(boolean leakSocket) {
            if(!leakSocket) {
                try {
                    pipe.socket.close();
                } catch (IOException e) { } // uhm... what?
            }
        }
    }
    private final Vector<Managed> clients = new Vector<>();
    private final Map<Integer, Callbacks> allowed = new HashMap<>();


    private class MessagePumpingThread extends Thread {
        private final Managed me;

        public MessagePumpingThread(Managed me) {
            super();
            this.me = me;
        }

        public void run() {
            try {
                while(!isInterrupted()) {
                    readAndDispatch(allowed, me.smart.pipe);
                }
            } catch (IOException | BigMessageException | InvalidMessageException |
                    EmptyMessageException | BytesRemainingException e) {
                synchronized(me) {
                    me.quitError = e;
                }
            }
            try {
                remove(me.smart.pipe, false);
            } catch (IOException e) {
                // Will hopefully never happen. Get the rid of it without releasing anything.
                try {
                    remove(me.smart.pipe, true);
                } catch (IOException e1) {
                    // WTF?? We're fucked. Not really possible.
                }
            }
            quitting(me.smart, me.quitError);
        }
    }

    void shutdown() {
        synchronized(clients) {
            for(Managed c : clients) c.shutdown(false);
            clients.clear();
        }
    }

    /** Called when a pumper thread exits, possibly due to an error. When this is called the
     * message channel has already been removed from the managed list by calling this.remove(MessageChannel, false). */
    protected void quitting(Client gone, Exception error) {
        handler.sendMessage(handler.obtainMessage(disconnectMessageCode, new Events.SocketDisconnected(gone.pipe, error)));
    }

    static public class BigMessageException extends Exception {
        final long requested;

        public BigMessageException(long requested) {
            this.requested = requested;
        }

        @Override
        public String toString() {
            return "message too big: " + requested + " requested but max size is " + MAX_MSG_FROM_WIRE_BYTES;
        }
    }

    static public class EmptyMessageException extends Exception {
        @Override
        public String toString() {
            return "empty message";
        }
    }

    static public class InvalidMessageException extends Exception {
        final int type;

        public InvalidMessageException(int type) {
            this.type = type;
        }

        @Override
        public String toString() {
            return "invalid message received, code " + type;
        }
    }

    static public class BytesRemainingException extends Exception {
        final int type;
        final int expected;

        public BytesRemainingException(int type, int expected) {
            this.type = type;
            this.expected = expected;
        }

        @Override
        public String toString() {
            return "message type " + type + ", trailing bytes found after " + expected;
        }
    }


    // This is supposed to be called only from its own thread pumper sleeping on the socket so
    // it does not need to be sync'ed.
    private void readAndDispatch(Map<Integer, Callbacks> allowed, MessageChannel me) throws IOException, BigMessageException, EmptyMessageException, InvalidMessageException, BytesRemainingException {
        long takes;
        int got = readBytes(me.socket.getInputStream(), me.in, 4);
        if(got == -1) throw new IOException();

        takes = me.recv.readFixed32();
        if(takes > MAX_MSG_FROM_WIRE_BYTES) throw new BigMessageException(takes);
        if(takes == 0) throw new EmptyMessageException();
        int expect = (int) takes;
        got = readBytes(me.socket.getInputStream(), me.in, expect);
        if(got == -1) throw new IOException();

        final int type = me.recv.readUInt32();
        final Callbacks real = allowed.get(type);
        if(real == null) throw new InvalidMessageException(type);
        final MessageNano wire = real.make();
        me.recv.readMessage(wire);
        if(!me.recv.isAtEnd()) throw new BytesRemainingException(type, expect);
        me.recv.resetSizeCounter();
        real.mangle(me, wire);
    }

    private static int readBytes(InputStream input, byte[] dst, int count) throws IOException {
        int got = 0;
        while(got != count) {
            int r = input.read(dst, got, count - got);
            if(r == -1) return -1;
            got += r;
        }
        return got;
    }

    protected void message(int code, Object payload) {
        handler.sendMessage(handler.obtainMessage(code, payload));
    }
}

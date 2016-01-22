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
public abstract class Pumper<ClientInfo extends Client> {
    public static final int MAX_MSG_FROM_WIRE_BYTES = 4 * 1024;
    protected final Handler handler;
    private final int disconnectMessageCode;

    public Pumper(Handler handler, int disconnectMessageCode) {
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
    public Pumper<ClientInfo> add(int key, Callbacks funcs) {
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
                clients.remove(i--);
                break;
            }
        }
    }

    public void removeClearing(MessageChannel c) {
        try {
            remove(c, false);
        } catch (IOException e) {
            try {
                remove(c, true);
            } catch (IOException e1) {
                // impossible, just suppress
            }
        }
    }

    public synchronized boolean yours(MessageChannel c) {
        for(int i = 0; i < clients.size(); i++) {
            Managed el = clients.elementAt(i);
            if(el.smart.pipe == c) return true;
        }
        return false;
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
            sleeper.interrupt(); // we don't need this inputBuffer any case, going to another server.
            smart.shutdown(leakSocket);
        }
    }
    private final Vector<Managed> clients = new Vector<>();
    private final Map<Integer, Callbacks> allowed = new HashMap<>();

    private ClientInfo getClient(final MessageChannel c) {
        ClientInfo match = null;
        for(Managed m : clients) {
            if(m.smart.pipe == c) return m.smart;
        }
        return null;
    }


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
                    EmptyMessageException | ByteCountMismatchException e) {
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
            if(!silence) quitting(me.smart, me.quitError);
        }
    }

    private volatile boolean silence = false;

    public void shutdown() {
        silence = true;
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

    static public class ByteCountMismatchException extends Exception {
        final int type;
        final int expected;
        final int got;

        public ByteCountMismatchException(int type, int expected, int got) {
            this.type = type;
            this.expected = expected;
            this.got = got;
        }

        @Override
        public String toString() {
            return "message type " + type + ", expecting " + expected + "byte(s), got " + got;
        }
    }


    // This is supposed to be called only from its own thread pumper sleeping on the socket so
    // it does not need to be sync'ed.
    private void readAndDispatch(Map<Integer, Callbacks> allowed, MessageChannel me) throws IOException, BigMessageException, EmptyMessageException, InvalidMessageException, ByteCountMismatchException {
        long takes;
        InputStream input = me.socket.getInputStream();
        int got = readBytes(input, me.inputBuffer, 4);
        if(got == -1) throw new IOException();

        takes = me.recv.readFixed32();
        if(takes > MAX_MSG_FROM_WIRE_BYTES) throw new BigMessageException(takes);
        if(takes == 0) throw new EmptyMessageException();
        int expect = (int) takes;
        got = readBytes(input, me.inputBuffer, expect);
        if(got == -1) throw new IOException();

        me.recv.rewindToPosition(0);
        final int type = me.recv.readUInt32();
        final Callbacks real = allowed.get(type);
        if(real == null) throw new InvalidMessageException(type);
        final MessageNano wire = real.make();
        me.recv.readMessage(wire);
        if(me.recv.getPosition() != expect) throw new ByteCountMismatchException(type, expect, me.recv.getPosition());
        me.recv.rewindToPosition(0);
        ClientInfo data = getClient(me);
        if(data == null) return; // might happen if we pull off a client while we're mangling it. Just discard.
        real.mangle(data, wire);
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

    public int getClientCount() { return clients.size(); }
}

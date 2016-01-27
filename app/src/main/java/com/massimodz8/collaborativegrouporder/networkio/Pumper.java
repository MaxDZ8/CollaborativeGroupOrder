package com.massimodz8.collaborativegrouporder.networkio;

import android.os.Handler;

import com.google.protobuf.nano.MessageNano;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
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
public abstract class Pumper<ClientInfo extends Client> implements PumpTarget {
    public static final int MAX_MSG_FROM_WIRE_BYTES = 4 * 1024;
    protected final Handler handler;
    private final int disconnectMessageCode;

    public Pumper(Handler handler, int disconnectMessageCode) {
        this.handler = handler;
        this.disconnectMessageCode = disconnectMessageCode;
    }

    // Call this before starting to mangle stuff so it does not need to be thread protected.
    public Pumper<ClientInfo> add(int key, Callbacks funcs) {
        allowed.put(key, funcs);
        return this;
    }

    /// Give up ownership of c. It will now managed by this object and you must de-register it.
    /// OFC you can keep a reference to it to match against signals.
    public MessageChannel pump(MessageChannel c) {
        Managed newComer = new Managed(allocate(c));
        newComer.sleeper = new MessagePumpingThread(newComer.smart.pipe, this);
        synchronized(clients) {
            clients.add(newComer);
        }
        newComer.sleeper.start();
        return c;
    }

    //// Give up ownership of both objects.
    public void pump(MessageChannel c, MessagePumpingThread rebind) {
        Managed newComer = new Managed(allocate(c));
        newComer.sleeper = rebind;
        synchronized(clients) {
            clients.add(newComer);
        }
        rebind.destination = this;
    }

    public boolean close(MessageChannel c) throws IOException {
        synchronized(clients) {
            for (int i = 0; i < clients.size(); i++) {
                Managed el = clients.get(i);
                if (el.smart.pipe == c) {
                    el.shutdown();
                    clients.remove(i);
                    return true;
                }
            }
        }
        return false;
    }

    public boolean leak(MessageChannel leak) {
        synchronized(clients) {
            for (int i = 0; i < clients.size(); i++) {
                Managed el = clients.get(i);
                if (el.smart.pipe == leak) {
                    el.leak();
                    clients.remove(i);
                    return true;
                }
            }
            return false;
        }
    }

    /// Give up ownership of something identified by channel without releasing anything so
    /// it can be moved to something else.
    public MessagePumpingThread move(MessageChannel c) {
        synchronized(clients) {
            for (int i = 0; i < clients.size(); i++) {
                Managed el = clients.get(i);
                if (el.smart.pipe == c) {
                    final MessagePumpingThread ret = el.sleeper;
                    el.sleeper = null;
                    el.leak();
                    clients.remove(i);
                    return ret;
                }
            }
        }
        return null;
    }

    public void silentShutdown(MessageChannel c) {
        try {
            close(c);
        } catch (IOException e) {
            leak(c);
        }
    }

    public boolean yours(MessageChannel c) {
        synchronized(clients) {
            for (Managed el : clients) {
                if (el.smart.pipe == c) return true;
            }
        }
        return false;
    }

    public MessageChannel[] get() {
        synchronized(clients) {
            MessageChannel[] out = new MessageChannel[clients.size()];
            for (int cp = 0; cp < clients.size(); cp++) out[cp] = clients.get(cp).smart.pipe;
            return out;
        }
    }


    protected abstract ClientInfo allocate(MessageChannel c);

    private class Managed {
        public ClientInfo smart;
        MessagePumpingThread sleeper;

        public Managed(ClientInfo smart) {
            this.smart = smart;
        }

        public void leak() {
            if(sleeper != null) sleeper.interrupt();
            smart.leak();
        }

        public void shutdown() throws IOException {
            if(sleeper != null) sleeper.interrupt(); // we don't need this inputBuffer any case, going to another server.
            smart.shutdown();
        }
    }
    private final ArrayList<Managed> clients = new ArrayList<>();
    private final Map<Integer, Callbacks> allowed = new HashMap<>();


    private static class MessagePumpingThread extends Thread {
        private final MessageChannel source;
        public volatile PumpTarget destination;

        public MessagePumpingThread(MessageChannel mine, PumpTarget sink) {
            super();
            source = mine;
            destination = sink;
        }

        public void run() {
            Exception quitError = null;
            try {
                while(!isInterrupted()) {
                    long takes;
                    InputStream input = source.socket.getInputStream();
                    int got = MessagePumpingThread.readBytes(input, source.inputBuffer, 4);
                    if(got == -1) throw new IOException();

                    takes = source.recv.readFixed32();
                    if(takes > MAX_MSG_FROM_WIRE_BYTES) throw new BigMessageException(takes);
                    if(takes == 0) throw new EmptyMessageException();
                    int expect = (int) takes;
                    got = MessagePumpingThread.readBytes(input, source.inputBuffer, expect);
                    if(got == -1) throw new IOException();

                    source.recv.rewindToPosition(0);
                    final int type = source.recv.readUInt32();
                    final Callbacks real = destination.callbacks().get(type);
                    if(real == null) throw new InvalidMessageException(type);
                    final MessageNano wire = real.make();
                    source.recv.readMessage(wire);
                    if(source.recv.getPosition() != expect) throw new ByteCountMismatchException(type, expect, source.recv.getPosition());
                    source.recv.rewindToPosition(0);
                    real.mangle(source, wire);
                }
            } catch (IOException | BigMessageException | InvalidMessageException |
                    EmptyMessageException | ByteCountMismatchException e) {
                quitError = e;
            }
            if(!destination.signalExit()) destination.quitting(source, quitError);
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
    }

    private volatile boolean silence = false;

    public void shutdown() throws IOException {
        silence = true;
        synchronized(clients) {
            for (Managed c : clients) c.shutdown();
            clients.clear();
        }
    }

    /** Called when a pumper thread exits, possibly due to an error. When this is called the
     * message channel has already been removed from the managed list by calling this.remove(MessageChannel, false). */
    protected void quitting(ClientInfo gone, Exception error) {
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

    protected void message(int code, Object payload) {
        handler.sendMessage(handler.obtainMessage(code, payload));
    }

    protected ClientInfo getClient(MessageChannel c) {
        synchronized(clients) {
            for(Managed test : clients) {
                if(c == test.smart.pipe) return test.smart;
            }
        }
        return null;
    }

    public int getClientCount() {
        synchronized(clients) {
            return clients.size();
        }
    }

    @Override
    public Map<Integer, Callbacks> callbacks() {
        return allowed; // maps are thread safe for reads and non-structure-modifying
    }

    @Override
    public boolean signalExit() {
        return !silence;
    }

    @Override
    public void quitting(MessageChannel source, Exception error) {
        Managed match = null;
        synchronized(clients) {
            for (Managed c : clients) {
                if (source == c.smart.pipe) {
                    match = c;
                    break;
                }
            }
        }
        if(match != null) quitting(match.smart, error);
    }
}

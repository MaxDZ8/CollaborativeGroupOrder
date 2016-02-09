package com.massimodz8.collaborativegrouporder.networkio;

import android.os.Handler;

import com.google.protobuf.nano.MessageNano;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

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
public class Pumper {
    public static final int MAX_MSG_FROM_WIRE_BYTES = 4 * 1024;
    protected final Handler handler;
    private final int disconnectMessageCode, detachingMessageCode;

    public Pumper(Handler handler, int disconnectMessageCode, int detachingMessageCode) {
        this.handler = handler;
        this.disconnectMessageCode = disconnectMessageCode;
        this.detachingMessageCode = detachingMessageCode;
    }

    // Call this before starting to mangle stuff so it does not need to be thread protected.
    public Pumper add(int key, PumpTarget.Callbacks funcs) {
        allowed.put(key, funcs);
        return this;
    }

    /// Add this channel to management. You're still in change of releasing it. This associates
    /// a thread to the channel which will pump messages asynchronously through the provided callbacks.
    public MessageChannel pump(MessageChannel c) {
        MessagePumpingThread newComer = new MessagePumpingThread(c, funnel);
        synchronized(clients) {
            clients.add(newComer);
        }
        newComer.start();
        return c;
    }

    //// MessageChannel is yours. The pumping thread becomes mine, please forget about it.
    public void pump(MessagePumpingThread rebind) {
        synchronized(clients) {
            clients.add(rebind);
        }
        rebind.destination = funnel;
    }

    public boolean forget(MessageChannel c) {
        synchronized(clients) {
            for (int i = 0; i < clients.size(); i++) {
                MessagePumpingThread el = clients.get(i);
                if (el.getSource() == c) {
                    el.interrupt();
                    clients.remove(i);
                    return true;
                }
            }
        }
        return false;
    }

    /// Give up ownership of something identified by channel without releasing anything so
    /// it can be moved to something else.
    public MessagePumpingThread move(MessageChannel c) {
        synchronized(clients) {
            for (int i = 0; i < clients.size(); i++) {
                MessagePumpingThread el = clients.get(i);
                if (el.getSource() == c) {
                    el.destination = null;
                    clients.remove(i);
                    return el;
                }
            }
        }
        return null;
    }
    public MessagePumpingThread[] move() {
        synchronized(clients) {
            MessagePumpingThread[] ret = new MessagePumpingThread[clients.size()];
            for (int i = 0; i < clients.size(); i++) {
                MessagePumpingThread el = clients.get(i);
                el.destination = null;
                ret[i] = el;
            }
            clients.clear();
            return ret;
        }
    }

    public boolean yours(MessageChannel c) {
        synchronized(clients) {
            for (MessagePumpingThread el : clients) {
                if (el.getSource() == c) return true;
            }
        }
        return false;
    }

    private final ArrayList<MessagePumpingThread> clients = new ArrayList<>();
    private final Map<Integer, PumpTarget.Callbacks> allowed = new HashMap<>();
    private final PumpTarget funnel = new PumpTarget() {
        @Override
        public Map<Integer, Callbacks> callbacks() { return allowed; }

        @Override
        public boolean signalExit() { return !silence; }

        @Override
        public void quitting(MessageChannel source, Exception error) {
            handler.sendMessage(handler.obtainMessage(disconnectMessageCode, new Events.SocketDisconnected(source, error)));
        }

        @Override
        public void detaching(MessageChannel source) {
            handler.sendMessage(handler.obtainMessage(detachingMessageCode, source));
        }
    };


    public static class MessagePumpingThread extends Thread {
        private static final int DEFAULT_POLL_PERIOD = 1000;

        public MessageChannel getSource() { return source; }

        private final MessageChannel source;
        private volatile PumpTarget destination;

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

                    PumpTarget lookup = spinForTarget();
                    final PumpTarget.Callbacks real = lookup.callbacks().get(type);
                    if(real == null) throw new InvalidMessageException(type);
                    final MessageNano wire = real.make();
                    source.recv.readMessage(wire);
                    if(source.recv.getPosition() != expect) throw new ByteCountMismatchException(type, expect, source.recv.getPosition());
                    source.recv.rewindToPosition(0);
                    if(real.mangle(source, wire)) {
                        destination = null;
                        spinForTarget();
                    }
                }
            } catch (IOException | BigMessageException | InvalidMessageException |
                    EmptyMessageException | ByteCountMismatchException | InterruptedException e) {
                quitError = e;
            }
            if(null != destination && !destination.signalExit()) destination.quitting(source, quitError);
        }

        private PumpTarget spinForTarget() throws InterruptedException {
            PumpTarget lookup = destination;
            while(lookup == null) {
                sleep(DEFAULT_POLL_PERIOD);
                lookup = destination;
            }
            return lookup;
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

    public void shutdown() {
        silence = true;
        synchronized(clients) {
            for (MessagePumpingThread c : clients) c.interrupt();
            clients.clear();
        }
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

    public int getClientCount() {
        synchronized(clients) {
            return clients.size();
        }
    }
}

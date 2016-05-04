package com.massimodz8.collaborativegrouporder;

import com.google.protobuf.nano.MessageNano;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by Massimo on 04/05/2016.
 * The Mailman is a special thread which also accumulates send errors for ya.
 * Just create one, send everytime you need and check for errors every time in a while.
 */
public class Mailman extends Thread {
    public BlockingQueue<SendRequest> out = new ArrayBlockingQueue<>(USUAL_CLIENT_COUNT * USUAL_AVERAGE_MESSAGES_PENDING_COUNT);
    public ConcurrentLinkedQueue<SendRequest> errors = new ConcurrentLinkedQueue<>();

    @Override
    public void run() {
        while (!isInterrupted()) {
            final SendRequest req;
            try {
                req = out.take();
            } catch (InterruptedException e) {
                return;
            }
            if (req.destination == null) break;
            MessageChannel pipe = req.destination;
            try {
                if (req.one != null) pipe.write(req.type, req.one);
                else if (req.many != null) {
                    for (MessageNano msg : req.many) pipe.write(req.type, msg);
                }
            } catch (IOException e) {
                req.error = e;
                errors.add(req);
            }
        }
    }

    private static final int USUAL_CLIENT_COUNT = 10; // not really! Usually 5 or less but that's for safety!
    private static final int USUAL_AVERAGE_MESSAGES_PENDING_COUNT = 10; // pretty a lot, those will be small!
}

package com.massimodz8.collaborativegrouporder.networkio;

import com.google.protobuf.nano.MessageNano;

import java.io.IOException;
import java.util.Map;

/**
 * Created by Massimo on 27/01/2016.
 * This is used by Pumper to have each thread dispatch Proto3 messages asynchronously.
 */
public interface PumpTarget {
    public interface Callbacks<ExtendedMessage extends MessageNano> {
        /** Generate a new message for parsing a blob of data. Must be 'cleared'.
         * Can be called by multiple threads at once.
         */
        ExtendedMessage make();
        /** Consume a blob of data produced by a previous call to make().
         * Note: no way to reply to a message, send something like it is a new message.
         * \return True if the thread shall detach from the current target. This will keep
         * the thread around (it will not exit) and can be rebound as usual but it'll stop pumping
         * callbacks as it's detached from its target. When this happens, detaching will be called. */
        boolean mangle(MessageChannel from, ExtendedMessage msg) throws IOException;
    }
    Map<Integer, Callbacks> callbacks();

    boolean signalExit();

    /// Called immediately before thread pump exits but only if(signalExit())
    void quitting(MessageChannel source, Exception error);

    /// Called when Callbacks.mangle returns true.
    void detaching(MessageChannel source);
}

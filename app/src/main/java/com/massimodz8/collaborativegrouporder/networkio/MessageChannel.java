package com.massimodz8.collaborativegrouporder.networkio;

import com.google.protobuf.nano.CodedInputByteBufferNano;
import com.google.protobuf.nano.CodedOutputByteBufferNano;
import com.google.protobuf.nano.MessageNano;

import java.io.IOException;
import java.net.Socket;

/**
 * Created by Massimo on 13/01/2016.
 * The spiritual successor of the "object oriented socket" which was based on Java serialization,
 * now for google protobuf.
 */
public class MessageChannel {
    /// Can be changed in case we want to reuse an MessageChannel after player reconnects.
    public Socket socket;

    MessageChannel(Socket s) {
        socket = s;
        in = new byte[Server.MAX_MSG_FROM_WIRE_BYTES];
        out = new byte[Server.MAX_MSG_FROM_WIRE_BYTES];
        send = CodedOutputByteBufferNano.newInstance(out);
        recv = CodedInputByteBufferNano.newInstance(in);
    }

    static class Message {
        public final int type;
        public final int length;
        public final byte[] shared;

        public Message(int type, int length, byte[] shared) {
            this.type = type;
            this.length = length;
            this.shared = shared;
        }
    }

    final byte[] in, out;
    final CodedOutputByteBufferNano send;
    final CodedInputByteBufferNano recv;

    /// Use this with extreme care. You probably want writeSync instead.
    public void write(int type, MessageNano payload) throws IOException {
        send.writeFixed32NoTag(payload.getSerializedSize());
        send.writeUInt32NoTag(type);
        send.writeMessageNoTag(payload);
        socket.getOutputStream().write(out, 0, send.position());
        send.reset();
    }

    /// Strongly suggested to use it from an AsyncTask.
    public synchronized void writeSync(int type, MessageNano payload) throws IOException {
        write(type, payload);
    }
}

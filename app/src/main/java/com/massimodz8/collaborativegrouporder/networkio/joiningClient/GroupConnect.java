package com.massimodz8.collaborativegrouporder.networkio.joiningClient;

import android.os.Handler;

import com.massimodz8.collaborativegrouporder.networkio.Client;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.networkio.ProtoBufferEnum;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;

import java.io.IOException;

/**
 * Created by Massimo on 25/01/2016.
 * So far, InitialConnect was pretty much the deal. Send hello to server, pump back the results.
 * Now that we're getting serious I need ExplicitConnectionActivity to stay easy while group joining
 * mangles a couple of additional messages.
 */
public abstract class GroupConnect extends InitialConnect {
    public GroupConnect(Handler handler, int disconnectMessageCode, boolean forming) {
        super(handler, disconnectMessageCode, forming);
        add(ProtoBufferEnum.GROUP_FORMED, new Callbacks<Network.GroupFormed>() {
            @Override
            public Network.GroupFormed make() {
                return new Network.GroupFormed();
            }

            @Override
            public void mangle(MessageChannel from, Network.GroupFormed msg) throws IOException {
                if(msg.salt != com.google.protobuf.nano.WireFormatNano.EMPTY_BYTES) onGroupFormed(from, msg.salt);
                else onPlayingCharacterReply(from, msg.peerKey, msg.accepted);
            }
        });
    }
    protected abstract void onGroupFormed(MessageChannel origin, byte[] salt);
    protected abstract void onPlayingCharacterReply(MessageChannel origin, int peerKey, boolean accepted);
}

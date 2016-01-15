package com.massimodz8.collaborativegrouporder.networkio.joiningClient;

import android.os.Handler;

import com.massimodz8.collaborativegrouporder.ConnectedGroup;
import com.massimodz8.collaborativegrouporder.networkio.Client;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.networkio.ProtoBufferEnum;
import com.massimodz8.collaborativegrouporder.networkio.Pumper;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;

import java.io.IOException;

/**
 * Created by Massimo on 15/01/2016.
 * Using a whole Pumper here is a bit excessive but I take it easy.
 * The initial connection involves sending an Hello message and checking if the server replies
 * with group information. It's easy!
 */
public abstract class InitialConnect extends Pumper<Client> {
    public static boolean forming;
    public InitialConnect(Handler handler, int disconnectMessageCode, boolean forming) {
        super(handler, disconnectMessageCode);
        this.forming = forming;
        add(ProtoBufferEnum.GROUP_INFO, new Callbacks<Client, Network.GroupInfo>() {
            @Override
            public Network.GroupInfo make() {
                return new Network.GroupInfo();
            }

            @Override
            public void mangle(Client from, Network.GroupInfo msg) throws IOException {
                onValidGroupFound(from.pipe, makeGroupInfo(msg));
            }
        });
    }

    @Override
    protected Client allocate(MessageChannel c) {
        return new Client(c);
    }

    public ConnectedGroup makeGroupInfo(Network.GroupInfo recvd) {
        if(recvd.name == null || recvd.name.isEmpty()) return null;
        if(recvd.version == 0) return null;
        if(recvd.forming != forming) return null;
        return new ConnectedGroup(recvd.version, recvd.name);
    }

    public abstract void onValidGroupFound(MessageChannel c, ConnectedGroup group);
}

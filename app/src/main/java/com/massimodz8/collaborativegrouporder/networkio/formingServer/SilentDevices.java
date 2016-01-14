package com.massimodz8.collaborativegrouporder.networkio.formingServer;

import android.os.Handler;

import com.massimodz8.collaborativegrouporder.networkio.Events;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.networkio.ProtoBufferEnum;
import com.massimodz8.collaborativegrouporder.networkio.Server;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;

import java.io.IOException;

/**
 * Created by Massimo on 13/01/2016.
 * Initial phase, bring devices from silent to talking stage.
 */
public class SilentDevices extends Server<Server.Client> {
    final String name;
    final int wroteSomething;

    public SilentDevices(Handler handler, int disconnectCode, int wroteSomethingCode_, String name) throws IOException {
        super(handler, disconnectCode);
        this.name = name;
        wroteSomething = wroteSomethingCode_;

        add(ProtoBufferEnum.HELLO, new Callbacks<Client, Network.Hello>() {
            @Override
            public Network.Hello make() { return new Network.Hello(); }

            @Override
            public void mangle(Client from, Network.Hello msg) throws IOException {
                from.pipe.write(ProtoBufferEnum.GROUP_INFO, makeGroupInfo(msg));
            }
        }).add(ProtoBufferEnum.PEER_MESSAGE, new Callbacks<Client, Network.PeerMessage>() {
            @Override
            public Network.PeerMessage make() { return new Network.PeerMessage(); }

            @Override
            public void mangle(Client from, Network.PeerMessage msg) throws IOException {
                message(wroteSomething, new Events.PeerMessage(from.pipe, msg.text));
            }
        });
    }

    private Network.GroupInfo makeGroupInfo(Network.Hello msg) {
        Network.GroupInfo ret = new Network.GroupInfo();
        ret.forming = true;
        ret.name = name;
        ret.version = SERVER_VERSION;
        return ret;
    }

    public static final int SERVER_VERSION = 1;


    @Override
    protected Client allocate(MessageChannel c) {
        return new Client(c);
    }
}

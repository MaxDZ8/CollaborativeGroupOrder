package com.massimodz8.collaborativegrouporder.networkio.formingServer;

import android.os.Handler;

import com.massimodz8.collaborativegrouporder.networkio.Client;
import com.massimodz8.collaborativegrouporder.networkio.Events;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.networkio.ProtoBufferEnum;
import com.massimodz8.collaborativegrouporder.networkio.Server;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;

import java.io.IOException;

/**
 * Created by Massimo on 13/01/2016.
 * MessageChannels are put there as soon as we receive a PeerMessage from them.
 * The goal of this thing is to filter the PeerMessages and update the character budget until
 * we pull the clients out, when the group is formed.
 */
public class TalkingDevices extends Server<TalkingDevices.TalkingClient> {
    final int wroteSomething;
    final Handler charQueue;
    public static final int NEW_MSG_DELAY_MS = 2000;
    public static final int MSG_CHAR_BUDGET_TARGET = 1000;

    public TalkingDevices(Handler handler, int disconnectMessageCode, int wroteSomethingCode) {
        super(handler, disconnectMessageCode);
        wroteSomething = wroteSomethingCode;
        charQueue = new Handler();
        add(ProtoBufferEnum.PEER_MESSAGE, new Callbacks<TalkingClient, Network.PeerMessage>() {
            @Override
            public Network.PeerMessage make() {
                return new Network.PeerMessage();
            }

            @Override
            public void mangle(TalkingClient from, Network.PeerMessage msg) throws IOException {
                synchronized (from) {
                    if(msg.text.length() > from.charBudget) {
                        if (from.charBudget > 3)
                            msg.text = msg.text.substring(0, from.charBudget - 3);
                        else msg.text = "";
                        msg.text += "...";
                    }
                    from.charBudget -= msg.text.length();
                    if(msg.text != from.lastMessage) {
                        from.lastMessage = msg.text;
                        message(wroteSomething, new Events.PeerMessage(from.pipe, msg.text));
                    }
                    if(from.charBudget < MSG_CHAR_BUDGET_TARGET && from.pendingBudget == 0) {
                        charQueue.postDelayed(new GiveCharacters(from), NEW_MSG_DELAY_MS);
                        from.pendingBudget = MSG_CHAR_BUDGET_TARGET;
                    }
                }
            }
        });
    }

    private static class GiveCharacters implements Runnable {
        final TalkingClient target;

        public GiveCharacters(TalkingClient target) {
            this.target = target;
        }

        @Override
        public void run() {
            synchronized (target) {
                target.charBudget = target.pendingBudget;
            }
        }
    }

    @Override
    protected TalkingClient allocate(MessageChannel c) {
        return new TalkingClient(c, MSG_CHAR_BUDGET_TARGET);
    }

    protected static class TalkingClient extends Client {
        public String lastMessage;
        Client info;
        int charBudget, pendingBudget;

        TalkingClient(MessageChannel c, int initialChars) {
            super(c);
            charBudget = initialChars;
        }
    }
}

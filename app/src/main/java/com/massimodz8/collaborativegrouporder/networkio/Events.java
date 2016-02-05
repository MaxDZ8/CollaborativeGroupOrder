package com.massimodz8.collaborativegrouporder.networkio;

import com.massimodz8.collaborativegrouporder.protocol.nano.Network;

import java.net.Socket;

/**
 * Created by Massimo on 13/01/2016.
 * A single namespace-like thing to hold the various events to be pushed to GUI handler.
 */
public interface Events {
    class SocketDisconnected {
        public final MessageChannel which;
        public final Exception reason;

        public SocketDisconnected(MessageChannel which, Exception reason) {
            this.which = which;
            this.reason = reason;
        }
    }

    class CharBudget {
        public final MessageChannel which;
        public final Network.CharBudget payload;

        public CharBudget(MessageChannel c, Network.CharBudget b) {
            which = c;
            payload = b;
        }
    }

    class GroupInfo {
        public final MessageChannel which;
        public final Network.GroupInfo payload;

        public GroupInfo(MessageChannel which, Network.GroupInfo payload) {
            this.which = which;
            this.payload = payload;
        }
    }

    class PeerMessage {
        public final MessageChannel which;
        public final Network.PeerMessage msg;

        public PeerMessage(MessageChannel which, Network.PeerMessage msg) {
            this.which = which;
            this.msg = msg;
        }
    }


    class GroupKey {
        public final MessageChannel origin;
        public final byte[] key;

        public GroupKey(MessageChannel origin, byte[] key) {
            this.origin = origin;
            this.key = key;
        }
    }

    class CharacterDefinition {
        public CharacterDefinition(MessageChannel origin, Network.PlayingCharacterDefinition character) {
            this.origin = origin;
            this.character = character;
        }

        public final MessageChannel origin;
        public final Network.PlayingCharacterDefinition character;
    }

    class CharacterAcceptStatus {
        public final MessageChannel origin;
        public final int key;
        public final boolean accepted; // if false then rejected.

        public CharacterAcceptStatus(MessageChannel origin, int key, boolean accepted) {
            this.origin = origin;
            this.key = key;
            this.accepted = accepted;
        }
    }

    class GroupDone {
        public final MessageChannel origin;
        public final boolean goAdventuring;

        public GroupDone(MessageChannel origin, boolean goAdventuring) {
            this.origin = origin;
            this.goAdventuring = goAdventuring;
        }
    }
}

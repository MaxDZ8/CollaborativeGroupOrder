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
        public int count;
        public int delay_ms;

        public CharBudget(MessageChannel c) { this(c, 0, 0); }
        public CharBudget(MessageChannel c, int count, int delay_ms) {
            this.which = c;
            this.count = count;
            this.delay_ms = delay_ms;
        }
    }

    class PeerMessage {
        public final MessageChannel which;
        public final String msg;

        public PeerMessage(MessageChannel which, String msg) {
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
        public final String key;
        public final boolean accepted; // if false then rejected.

        public CharacterAcceptStatus(MessageChannel origin, String key, boolean accepted) {
            this.origin = origin;
            this.key = key;
            this.accepted = accepted;
        }
    }
}

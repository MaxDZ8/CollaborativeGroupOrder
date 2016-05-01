package com.massimodz8.collaborativegrouporder.networkio;

import com.massimodz8.collaborativegrouporder.protocol.nano.Network;
import com.massimodz8.collaborativegrouporder.protocol.nano.StartData;

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

    class AuthToken {
        public final MessageChannel origin;
        public final byte[] doormat;

        public AuthToken(MessageChannel origin, byte[] doormat) {
            this.origin = origin;
            this.doormat = doormat;
        }
    }

    class Hello {
        public final MessageChannel origin;

        public Hello(MessageChannel origin, Network.Hello payload) {
            this.origin = origin;
            this.payload = payload;
        }

        public final Network.Hello payload;
    }

    class CharOwnership {
        public final MessageChannel origin;
        public final Network.CharacterOwnership payload;

        public CharOwnership(MessageChannel origin, Network.CharacterOwnership payload) {
            this.origin = origin;
            this.payload = payload;
        }
    }

    class ActorData {
        public final MessageChannel origin;
        public final StartData.ActorDefinition payload;

        public ActorData(MessageChannel origin, StartData.ActorDefinition payload) {
            this.origin = origin;
            this.payload = payload;
        }
    }

    class Roll {
        public final MessageChannel from;
        public final Network.Roll payload;

        public Roll(MessageChannel from, Network.Roll payload) {
            this.from = from;
            this.payload = payload;
        }
    }
}

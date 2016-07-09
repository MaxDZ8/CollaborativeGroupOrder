package com.massimodz8.collaborativegrouporder.client;

import android.os.Handler;
import android.os.Message;

import com.massimodz8.collaborativegrouporder.Mailman;
import com.massimodz8.collaborativegrouporder.PseudoStack;
import com.massimodz8.collaborativegrouporder.SendRequest;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.networkio.ProtoBufferEnum;
import com.massimodz8.collaborativegrouporder.networkio.PumpTarget;
import com.massimodz8.collaborativegrouporder.networkio.Pumper;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;
import com.massimodz8.collaborativegrouporder.protocol.nano.StartData;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * Created by Massimo on 14/06/2016.
 * Model for CharSelectionActivity.
 */
public class PcAssignmentState {
    final public Pumper.MessagePumpingThread server;
    final public StartData.PartyClientData.Group party;
    public final int advancement;

    public PcAssignmentState(Pumper.MessagePumpingThread server, StartData.PartyClientData.Group party, int advancement, Network.PlayingCharacterDefinition character) {
        this.server = server;
        this.party = party;
        this.advancement = advancement;
        sender.start();
        if(null != character) addChar(character);
        netPump.pump(server);
    }

    public final PseudoStack<Runnable> onDisconnected = new PseudoStack<>();
    public final PseudoStack<Runnable> onDetached = new PseudoStack<>();
    public final PseudoStack<Runnable> onCharacterDefined = new PseudoStack<>();
    public final PseudoStack<Runnable> onPartyReady = new PseudoStack<>();

    public boolean characterRequest(int charKey) {
        TransactingCharacter character = getCharacterByKey(charKey);
        if(null == character) return false; // impossible
        // It might be AVAILABLE or PLAYED_HERE, it doesn't matter to me, get request and notify.
        if(character.pending != PcAssignmentState.TransactingCharacter.NO_REQUEST) return false; // don't mess with us.
        character.pending = ticket++;
        character.sendRequest(sender, server.getSource());
        return true;
    }

    interface OwnershipListener {
        void onRefused(String name);
        void onGiven(String name);
        void onTakenAway(String name);
        void onRefresh();
    }
    public final PseudoStack<OwnershipListener> onOwnershipChanged = new PseudoStack<>();

    // Output
    public int[] playChars;
    boolean completed;

    // Running state
    private int ticket; // next char assignment ID to use

    private final Handler handler = new MyHandler(this);
    private final Pumper netPump = new Pumper(handler, MSG_DISCONNECT, MSG_DETACH)
            .add(ProtoBufferEnum.PLAYING_CHARACTER_DEFINITION, new PumpTarget.Callbacks<Network.PlayingCharacterDefinition>() {
                @Override
                public Network.PlayingCharacterDefinition make() { return new Network.PlayingCharacterDefinition(); }

                @Override
                public boolean mangle(MessageChannel from, Network.PlayingCharacterDefinition msg) throws
                IOException {
                    handler.sendMessage(handler.obtainMessage(MSG_CHARACTER_DEFINITION, msg));
                    return false;
                }})
            .add(ProtoBufferEnum.CHARACTER_OWNERSHIP, new PumpTarget.Callbacks<Network.CharacterOwnership>() {
                @Override
                public Network.CharacterOwnership make() { return new Network.CharacterOwnership(); }

                @Override
                public boolean mangle(MessageChannel from, Network.CharacterOwnership msg) throws IOException {
                    handler.sendMessage(handler.obtainMessage(MSG_CHARACTER_OWNERSHIP, msg));
                    return false;
                }})
            .add(ProtoBufferEnum.PHASE_CONTROL, new PumpTarget.Callbacks<Network.PhaseControl>() {
                @Override
                public Network.PhaseControl make() {
                    return new Network.PhaseControl();
                }

                @Override
                public boolean mangle(MessageChannel from, Network.PhaseControl msg) throws IOException {
                    if (msg.type != Network.PhaseControl.T_DEFINITIVE_CHAR_ASSIGNMENT)
                        return false; // Error?
                    handler.sendMessage(handler.obtainMessage(MSG_PARTY_READY, msg));
                    return true;
                }
            });

    private static class MyHandler extends Handler {
        final WeakReference<PcAssignmentState> self;

        private MyHandler(PcAssignmentState self) {
            this.self = new WeakReference<>(self);
        }

        @Override
        public void handleMessage(Message msg) {
            final PcAssignmentState self = this.self.get();
            switch(msg.what) {
                case MSG_DISCONNECT: {
                    Runnable runnable = self.onDisconnected.get();
                    if(runnable != null) runnable.run();
                } break;
                case MSG_DETACH: {
                    self.completed = true;
                    Runnable runnable = self.onDetached.get();
                    if(runnable != null) runnable.run();
                } break;
                case MSG_CHARACTER_DEFINITION: {
                    Network.PlayingCharacterDefinition real = (Network.PlayingCharacterDefinition) msg.obj;
                    final TransactingCharacter add = new TransactingCharacter(real);
                    final TransactingCharacter character = self.getCharacterByKey(real.peerKey);
                    if(null != character) { // redefined. Not really supposed to happen.
                        self.chars.set(self.chars.indexOf(character), add);
                    }
                    else self.chars.add(add);
                    Runnable runnable = self.onCharacterDefined.get();
                    if(runnable != null) runnable.run();
                } break;
                case MSG_CHARACTER_OWNERSHIP: {
                    Network.CharacterOwnership real = (Network.CharacterOwnership)msg.obj;
                    self.ownership(real);
                } break;
                case MSG_PARTY_READY: { // detach will follow soon, just reset all my characters
                    Network.PhaseControl real = (Network.PhaseControl)msg.obj;
                    self.playChars = real.yourChars;
                    Runnable runnable = self.onPartyReady.get();
                    if(runnable != null) runnable.run();
                } break;
            }
        }
    }

    private static final int MSG_DISCONNECT = 1;
    private static final int MSG_DETACH = 2;
    private static final int MSG_CHARACTER_DEFINITION = 3;
    private static final int MSG_CHARACTER_OWNERSHIP = 4;
    private static final int MSG_PARTY_READY = 5;

    static class TransactingCharacter {
        static final int PLAYED_HERE = 0;
        static final int PLAYED_SOMEWHERE = 1;
        static final int AVAILABLE = 2;
        static final int NO_REQUEST = -1;
        int type = AVAILABLE;
        final Network.PlayingCharacterDefinition pc;

        public TransactingCharacter(Network.PlayingCharacterDefinition pc) {
            this.pc = pc;
        }

        int pending = NO_REQUEST; // check type to understand if being given away or being requested

        public void sendRequest(Mailman sender, MessageChannel server) {
            final Network.CharacterOwnership request = new Network.CharacterOwnership();
            request.ticket = pending;
            request.type = Network.CharacterOwnership.REQUEST;
            request.character = pc.peerKey;
            sender.out.add(new SendRequest(server, ProtoBufferEnum.CHARACTER_OWNERSHIP, request, null));
        }

        public void toggleOwnership() {
            if(type == PLAYED_HERE) type = AVAILABLE;
            else if(type == AVAILABLE) type = PLAYED_HERE;
            // not called on PLAYED_SOMEWHERE, would be bad bad bad
        }
    }

    private ArrayList<TransactingCharacter> chars = new ArrayList<>();
    final private Mailman sender = new Mailman();

    TransactingCharacter getCharacterByKey(int key) {
        for(TransactingCharacter pc : chars) {
            if(pc.pc.peerKey == key) return pc;
        }
        return null;
    }

    public void shutdown() {
        sender.out.add(new SendRequest());
        sender.interrupt();
        final Pumper.MessagePumpingThread[] goner = netPump.move();
            new Thread() {
                @Override
                public void run() {
                    for (Pumper.MessagePumpingThread worker : goner) { // iterates once
                        worker.interrupt();
                        try {
                            worker.getSource().socket.close();
                        } catch (IOException e) {
                            // suppress, it's fine for us
                        }
                    }
                    super.run();
                }
            }.start();
        }

    public Pumper.MessagePumpingThread moveWorker() {
        return netPump.move(server.getSource());
    }

    private void addChar(Network.PlayingCharacterDefinition newDef) {
        final TransactingCharacter add = new TransactingCharacter(newDef);
        final TransactingCharacter character = getCharacterByKey(newDef.peerKey);
        if(null != character) { // redefined. Not really supposed to happen.
            chars.set(chars.indexOf(character), add);
        }
        else chars.add(add);
    }

    void ownership(final Network.CharacterOwnership msg) {
        ticket = msg.ticket;
        final TransactingCharacter character = getCharacterByKey(msg.character);
        if(null == character) return; // super odd. Not sure of what I should be doing in this case. In theory the server prevents this from happening but it currently has a quirk
        switch(msg.type) {
            case Network.CharacterOwnership.REQUEST: return; // the server does not use this currently... perhaps to update ticket?
            case Network.CharacterOwnership.OBSOLETE: { // just try again with the updated ticket.
                character.sendRequest(sender, server.getSource());
                return;
            }
            case Network.CharacterOwnership.ACCEPTED: {
                if(character.pending == TransactingCharacter.NO_REQUEST) break; // happens if the server assigned something to us before our request reached it
                character.pending = TransactingCharacter.NO_REQUEST;
                character.toggleOwnership();
                break;
            }
            case Network.CharacterOwnership.REJECTED: {
                if(character.pending == TransactingCharacter.NO_REQUEST) break; // happens if the server assigned something to us before our request reached it
                character.pending = TransactingCharacter.NO_REQUEST;
                OwnershipListener callback = onOwnershipChanged.get();
                if(callback != null) callback.onRefused(character.pc.name);
                break;
            }
            case Network.CharacterOwnership.YOURS:
            case Network.CharacterOwnership.AVAIL: {
                int matched = msg.type == Network.CharacterOwnership.YOURS? TransactingCharacter.PLAYED_HERE : TransactingCharacter.AVAILABLE;
                if(character.type == matched && character.pending != TransactingCharacter.NO_REQUEST) {
                    msg.type = Network.CharacterOwnership.REJECTED;
                    ownership(msg);
                    return;
                }
                character.pending = TransactingCharacter.NO_REQUEST;
                character.type = matched;
                break;
            }
            case Network.CharacterOwnership.BOUND: { // This is almost like AVAILABLE but not quite.
                switch(character.type) {
                    case TransactingCharacter.PLAYED_SOMEWHERE: return;
                    case TransactingCharacter.PLAYED_HERE:
                        if(character.pending == TransactingCharacter.NO_REQUEST) {
                            OwnershipListener callback = onOwnershipChanged.get();
                            if(callback != null) callback.onGiven(character.pc.name);
                        }
                        break;
                    case TransactingCharacter.AVAILABLE:
                        if(character.pending != TransactingCharacter.NO_REQUEST) {
                            OwnershipListener callback = onOwnershipChanged.get();
                            if(callback != null) callback.onTakenAway(character.pc.name);
                        }
                        character.pending = TransactingCharacter.NO_REQUEST;
                        character.type = TransactingCharacter.PLAYED_SOMEWHERE;
                        break;
                }
                break;
            }
        }
        OwnershipListener callback = onOwnershipChanged.get();
        if(callback != null) callback.onRefresh();
    }


    int count(int type) {
        int match = 0;
        for(TransactingCharacter el : chars) {
            if(type == el.type) match++;
        }
        return match;
    }

    TransactingCharacter getFilteredCharacter(int position, int filter) {
        for(TransactingCharacter pc : chars) {
            if(pc.type != filter) continue;
            if(position == 0) return pc;
            position--;
        }
        return null;
    }
}

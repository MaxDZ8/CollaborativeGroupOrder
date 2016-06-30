package com.massimodz8.collaborativegrouporder.client;

import android.os.Handler;
import android.os.Message;

import com.massimodz8.collaborativegrouporder.AsyncActivityLoadUpdateTask;
import com.massimodz8.collaborativegrouporder.BuildingPlayingCharacter;
import com.massimodz8.collaborativegrouporder.Mailman;
import com.massimodz8.collaborativegrouporder.PseudoStack;
import com.massimodz8.collaborativegrouporder.SendRequest;
import com.massimodz8.collaborativegrouporder.networkio.Events;
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
 * Model for NewCharactersProposalActivity.
 */
public class CharacterProposals {
    public final GroupState party;
    public Pumper.MessagePumpingThread master; // on result, if non null, go adventuring
    public StartData.PartyClientData.Group resParty;
    boolean disconnected;
    boolean detached;
    boolean done;
    public AsyncActivityLoadUpdateTask<StartData.PartyClientData> saving;

    final Handler handler = new MyHandler(this);
    final Pumper pump = new Pumper(handler, MSG_SOCKET_DISCONNECTED, MSG_PUMPER_DETACHED)
            .add(ProtoBufferEnum.GROUP_FORMED, new PumpTarget.Callbacks<Network.GroupFormed>() {
                @Override
                public Network.GroupFormed make() { return new Network.GroupFormed(); }

                @Override
                public boolean mangle(MessageChannel from, Network.GroupFormed msg) throws IOException {
                    if(0 != msg.salt.length) handler.sendMessage(handler.obtainMessage(MSG_NEW_KEY, msg.salt));
                    else handler.sendMessage(handler.obtainMessage(MSG_PC_APPROVAL, new Events.CharacterAcceptStatus(from, msg.peerKey, msg.accepted)));
                    return false;
                }
            }).add(ProtoBufferEnum.PHASE_CONTROL, new PumpTarget.Callbacks<Network.PhaseControl>() {
                @Override
                public Network.PhaseControl make() { return new Network.PhaseControl(); }

                @Override
                public boolean mangle(MessageChannel from, Network.PhaseControl msg) throws IOException {
                    if(msg.type != Network.PhaseControl.T_NO_MORE_DEFINITIONS) return false; // error?
                    handler.sendMessage(handler.obtainMessage(MSG_DONE, !msg.terminated));
                    return true;
                }
            });
    final ArrayList<BuildingPlayingCharacter> characters = new ArrayList<>();
    final Mailman sender = new Mailman();
    final PseudoStack<Runnable> onEvent = new PseudoStack<>();
    public final ArrayList<BuildingPlayingCharacter> rejected = new ArrayList<>();

    // You can reuse a previous sender for this one. It must be already started.
    public CharacterProposals(GroupState party, Pumper.MessagePumpingThread master) {
        this.party = party;
        this.master = master;
        sender.start();
        pump.pump(master);
        characters.add(new BuildingPlayingCharacter());
    }

    public void shutdown() {
        sender.out.add(new SendRequest());
        sender.interrupt();
        new Thread() {
            @Override
            public void run() {
                for (Pumper.MessagePumpingThread goner : pump.move()) {
                    goner.interrupt();
                    try {
                        goner.getSource().socket.close();
                    } catch (IOException e) {
                        // don't care.
                    }
                }
            }
        }.start();
    }

    static class MyHandler extends Handler {
        public MyHandler(CharacterProposals target) { this.target = new WeakReference<>(target); }

        final WeakReference<CharacterProposals> target;

        @Override
        public void handleMessage(Message msg) {
            final CharacterProposals target = this.target.get();
            final Runnable listener = target.onEvent.get();
            switch(msg.what) {
                case MSG_SOCKET_DISCONNECTED:
                    target.disconnected = true;
                    break;
                case MSG_PUMPER_DETACHED:
                    target.detached = true;
                    break;
                case MSG_NEW_KEY: {
                    target.party.salt = (byte[]) msg.obj;
                } break;
                case MSG_PC_APPROVAL: {
                    target.confirmationStatus((Events.CharacterAcceptStatus) msg.obj);
                } break;
                case MSG_DONE: {
                    final Boolean goAdv = (Boolean) msg.obj;
                    if(goAdv) target.master = target.pump.move(target.party.channel);
                    else target.master = null; // leave it in the pump so it'll get closed.
                    target.done = true;
                } break;
            }
            if(listener != null) listener.run();
        }

    }
    static final int MSG_SOCKET_DISCONNECTED = 1;
    static final int MSG_PUMPER_DETACHED = 2;
    static final int MSG_NEW_KEY = 3;
    static final int MSG_PC_APPROVAL = 4;
    static final int MSG_DONE = 5;

    private boolean confirmationStatus(Events.CharacterAcceptStatus obj) {
        BuildingPlayingCharacter match = null;
        for(BuildingPlayingCharacter test : characters) {
            if(test.unique == obj.key) {
                match = test;
                break;
            }
        }
        if(null == match) return false;
        match.status = obj.accepted? BuildingPlayingCharacter.STATUS_ACCEPTED : BuildingPlayingCharacter.STATUS_BUILDING;
        if(!obj.accepted) rejected.add(match);
        return obj.accepted;
    }
}

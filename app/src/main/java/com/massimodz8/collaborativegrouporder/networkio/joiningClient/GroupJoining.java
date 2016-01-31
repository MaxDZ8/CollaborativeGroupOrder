package com.massimodz8.collaborativegrouporder.networkio.joiningClient;

import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AlertDialog;


import com.massimodz8.collaborativegrouporder.ConnectedGroup;
import com.massimodz8.collaborativegrouporder.JoinGroupActivity;
import com.massimodz8.collaborativegrouporder.networkio.Events;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.networkio.ProtoBufferEnum;
import com.massimodz8.collaborativegrouporder.networkio.formingServer.GroupForming;
import com.massimodz8.collaborativegrouporder.networkio.Pumper;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;

import java.io.IOException;
import java.net.Socket;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;


/**
 * Created by Massimo on 15/01/2016.
 * This is the client companion of GroupForming.
 * Take a single message channel to the server and promote it across the various states.
 * Harder than it seems! In the beginning there are many groups we enumerate through discovery
 * or through an explicit connection request. Groups are added to my list, then we choose one.
 */
public abstract class GroupJoining implements NsdManager.DiscoveryListener {
    final Handler handler;
    final boolean groupBeingFormed;
    final NsdManager nsd;
    GroupConnect helper;
    Map<NsdServiceInfo, MessageChannel> probing = new IdentityHashMap<>();
    boolean nsdDeregister = true;

    static public class MessageCodes {
        final int disconnected;
        final int foundGroup;
        final int charBudget;
        final int initiatePCDefinition;
        final int pcAcceptance;
        final int groupDone;

        public MessageCodes(int disconnected, int foundGroup, int charBudget, int initiatePCDefinition, int pcAcceptance, int groupDone) {
            this.disconnected = disconnected;
            this.foundGroup = foundGroup;
            this.charBudget = charBudget;
            this.initiatePCDefinition = initiatePCDefinition;
            this.pcAcceptance = pcAcceptance;
            this.groupDone = groupDone;
        }
    }


    public GroupJoining(Handler handler, boolean groupBeingFormed, NsdManager nsd, final MessageCodes codes) {
        this.handler = handler;
        this.groupBeingFormed = groupBeingFormed;
        this.nsd = nsd;
        nsd.discoverServices(GroupForming.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, this);
        helper = new GroupConnect(handler, codes.disconnected, groupBeingFormed, codes.groupDone) {
            @Override
            public void onGroupFound(MessageChannel c, ConnectedGroup group) {
                message(codes.foundGroup, new JoinGroupActivity.GroupConnection(c, group));
            }

            @Override
            public void onBudgetReceived(MessageChannel c, int newBudget, int delay) {
                message(codes.charBudget, new Events.CharBudget(c, newBudget, delay));
            }

            @Override
            protected void onGroupFormed(MessageChannel c, byte[] salt) {
                message(codes.initiatePCDefinition, new Events.GroupKey(c, salt));
            }

            @Override
            protected void onPlayingCharacterReply(MessageChannel c, int peerKey, boolean accepted) {
                message(codes.pcAcceptance, new Events.CharacterAcceptStatus(c, peerKey, accepted));
            }
        };
    }

    public void shutdown() {
        stopDiscovering();
    }

    protected abstract void onDiscoveryStart(ServiceDiscoveryStartStop status);
    protected abstract void onDiscoveryStop(ServiceDiscoveryStartStop status);

    /// Most likely to be called from an AsyncTask or some other thread.
    /// In general you don't need to call this except when you need an explicit connection.
    public MessageChannel beginHandshake(Socket sock) throws IOException {
        if(helper == null) return null; // we already transitioned to something else
        MessageChannel peer = new MessageChannel(sock);
        Network.Hello hi = new Network.Hello();
        hi.version = JoinGroupActivity.CLIENT_PROTOCOL_VERSION;
        peer.writeSync(ProtoBufferEnum.HELLO, hi);
        helper.pump(peer);
        return peer;
    }


    public void stopDiscovering() {
        if(nsdDeregister) nsd.stopServiceDiscovery(this);
        nsdDeregister = false;
    }

    /// Call this the first time a group key is received from a group owner.
    public void keepOnly(MessageChannel origin) {
        final MessageChannel[] all = helper.get();
        for(MessageChannel c : all) {
            if(c != origin) try {
                helper.close(c);
            } catch (IOException e) {
               // Uhm... what to?
                helper.leak(c); // just in case
            }
        }
    }

    //
    // NsdManager.DiscoveryListener ________________________________________________________________
    @Override
    public void onStartDiscoveryFailed(String serviceType, int errorCode) {
        onDiscoveryStart(new ServiceDiscoveryStartStop(errorCode));
    }

    @Override
    public void onDiscoveryStarted(String serviceType) {
        onDiscoveryStart(new ServiceDiscoveryStartStop());
    }

    @Override
    public void onStopDiscoveryFailed(String serviceType, int errorCode) {
        onDiscoveryStop(new ServiceDiscoveryStartStop(errorCode));
    }

    @Override
    public void onDiscoveryStopped(String serviceType) {
        onDiscoveryStop(new ServiceDiscoveryStartStop());
    }

    @Override
    public void onServiceFound(NsdServiceInfo info) {
        if(helper == null) return;
        Socket socket;
        MessageChannel c;
        try {
            socket = new Socket(info.getHost(), info.getPort());
            c = beginHandshake(socket);
        } catch (IOException e) {
            // do I care?
            return;
        }
        probing.put(info, c);
    }

    @Override
    public void onServiceLost(NsdServiceInfo info) {
        if(helper == null) return;
        MessageChannel stop = probing.remove(info);
        if(stop != null) helper.silentShutdown(stop);
    }

    public static class ServiceDiscoveryStartStop {
        public ServiceDiscoveryStartStop() { successful = true; }
        public ServiceDiscoveryStartStop(int err) {
            error = err;
            successful = false;
        }

        public int error;
        public boolean successful;
    }
}

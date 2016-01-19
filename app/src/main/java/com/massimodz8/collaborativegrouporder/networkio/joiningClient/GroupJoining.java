package com.massimodz8.collaborativegrouporder.networkio.joiningClient;

import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AlertDialog;


import com.massimodz8.collaborativegrouporder.ConnectedGroup;
import com.massimodz8.collaborativegrouporder.JoinGroupActivity;
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
    InitialConnect helper;
    Map<NsdServiceInfo, MessageChannel> probing = new IdentityHashMap<NsdServiceInfo, MessageChannel>();

    class PlaceHolder {
        public final int lostGroup;

        public PlaceHolder(int lostGroup) {
            this.lostGroup = lostGroup;
        }

        public boolean yours(MessageChannel c ) { return false; }
        public void removeCleaning(MessageChannel c ) {
        }
    }
    private PlaceHolder shaken;

    public GroupJoining(Handler handler, boolean groupBeingFormed, NsdManager nsd, int earlyDisconnect, final int foundGroup, final int lostGroup) {
        this.handler = handler;
        this.groupBeingFormed = groupBeingFormed;
        this.nsd = nsd;
        nsd.discoverServices(GroupForming.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, this);
        final GroupJoining self = this;
        helper = new InitialConnect(handler, earlyDisconnect, groupBeingFormed) {
            @Override
            public void onGroupFound(MessageChannel c, ConnectedGroup group) {
                handler.sendMessage(handler.obtainMessage(foundGroup, new JoinGroupActivity.GroupConnection(c, group)));
            }
        };
        shaken = new PlaceHolder(lostGroup);
    }

    public void shutdown() {
        nsd.stopServiceDiscovery(this);
    }

    protected abstract void onDiscoveryStart(ServiceDiscoveryStartStop status);
    protected abstract void onDiscoveryStop(ServiceDiscoveryStartStop status);

    /// Most likely to be called from an AsyncTask or some other thread.
    /// In general you don't need to call this except when you need an explicit connection.
    public MessageChannel beginHandshake(Socket sock) throws IOException {
        if(helper != null) return null; // we already transitioned to something else
        MessageChannel peer = new MessageChannel(sock);
        Network.Hello hi = new Network.Hello();
        hi.version = JoinGroupActivity.CLIENT_PROTOCOL_VERSION;
        peer.writeSync(ProtoBufferEnum.HELLO, new Network.Hello());
        helper.add(peer);
        return peer;
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
        if(stop != null) helper.removeClearing(stop);
        if(shaken.yours(stop)) {
            shaken.removeCleaning(stop);
            handler.sendMessage(handler.obtainMessage(shaken.lostGroup, stop));
        }
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

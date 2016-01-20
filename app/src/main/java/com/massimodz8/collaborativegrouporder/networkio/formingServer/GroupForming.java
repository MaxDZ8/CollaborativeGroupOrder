package com.massimodz8.collaborativegrouporder.networkio.formingServer;

import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Message;

import com.massimodz8.collaborativegrouporder.networkio.LandingServer;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * Created by Massimo on 14/01/2016.
 * Help CreatePartyActivity by shuffling connections around, maintaining state and providing
 * higher level GUI hooks with no thread hazards.
 */
public abstract class GroupForming implements NsdManager.RegistrationListener {
    public static final int INITIAL_CHAR_BUDGET = 20;

    public void shutdown() throws IOException {
        if(forming != null) forming.shutdown();
        if(talking != null) talking.shutdown();
        if(silent != null) silent.shutdown();
        nsd.unregisterService(this);
        landing.close();
    }


    public static class ServiceRegistrationResult {
        public ServiceRegistrationResult(String netName) {
            this.netName = netName;
            successful = true;
        }
        public ServiceRegistrationResult(int err) {
            error = err;
            successful = false;
        }

        public int error;
        public boolean successful;
        public String netName;
    }

    SilentDevices silent;
    TalkingDevices talking;
    PCDefiningDevices forming;

    final ServerSocket landing;
    public final String userName;
    public final Handler handler;
    public final int serviceRegistrationCompleteCode;
    NsdManager nsd;

    public static final String SERVICE_TYPE = "_formingGroupInitiative._tcp";

    private LandingServer acceptor;

    public GroupForming(String userName, NsdManager nsd, Handler handler, int serviceRegistrationCompleteCode) throws IOException {
        this.userName = userName;
        this.handler = handler;
        this.nsd = nsd;
        this.serviceRegistrationCompleteCode = serviceRegistrationCompleteCode;
        landing = new ServerSocket(0);

        NsdServiceInfo servInfo  = new NsdServiceInfo();
        servInfo.setServiceName(userName);
        servInfo.setServiceType(SERVICE_TYPE);
        servInfo.setPort(getLocalPort());
        nsd.registerService(servInfo, NsdManager.PROTOCOL_DNS_SD, this);
    }

    public int getLocalPort() { return landing.getLocalPort(); }

    public abstract void onFailedAccept();
    public abstract void refreshSilentCount(int currently);

    /// Start forming a group by starting a listen server and accepting connections.
    public void begin(int disconnect, int peerMessage) throws IOException {
        acceptor = new LandingServer(landing, new LandingServer.Callbacks() {
            @Override
            public void failedAccept() { onFailedAccept(); }

            @Override
            public void connected(MessageChannel newComer) {
                if(silent != null) {
                    silent.add(newComer);
                    refreshSilentCount(silent.getClientCount());
                }
                else {
                    try {
                        newComer.socket.close();
                    } catch (IOException e) {
                        // nothing to do here really, IDK. The dude was late to the party.
                        // Very unlikely to happen.
                    }
                }
            }
        });
        silent = new SilentDevices(handler, disconnect, peerMessage, userName, INITIAL_CHAR_BUDGET);
    }

    /// Listen for the various events, do your mangling and promote peers to a different stage.
    /// When not matched the function call is NOP, so you can just call this blindly.
    public void promoteSilent(MessageChannel peer, int disconnect, int peerMessage) throws IOException {
        if(talking == null) talking = new TalkingDevices(handler, disconnect, peerMessage);
        if(silent.yours(peer)) {
            talking.add(peer);
            silent.remove(peer, true);
        }
    }

    /// Brings a talking devices to group forming phase and terminates connections to others.
    public void promoteTalking(MessageChannel[] members, int disconnect, int definedCharacter) throws IOException {
        if(forming == null) forming = new PCDefiningDevices(handler, disconnect, definedCharacter);
        acceptor.shutdown();
        acceptor = null;
        landing.close();
        silent.shutdown();
        silent = null;

        for(MessageChannel c : members) forming.add(c);

        talking.shutdown();
        talking = null;
        nsd.unregisterService(this);
    }

    public int getSilentCount() {
        if(silent == null) return 0;
        return silent.getClientCount();
    }

    public int getTalkingCount() {
        if(talking == null) return 0;
        return talking.getClientCount();
    }

    //
    // NsdManager.RegistrationListener() ___________________________________________________________
    @Override
    public void onServiceRegistered(NsdServiceInfo info) {
        final String netName = info.getServiceName();
        Message msg = Message.obtain(handler, serviceRegistrationCompleteCode, new ServiceRegistrationResult(netName));
        handler.sendMessage(msg);
    }

    @Override
    public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
        Message msg = Message.obtain(handler, serviceRegistrationCompleteCode, new ServiceRegistrationResult(errorCode));
        handler.sendMessage(msg);
    }

    @Override
    public void onServiceUnregistered(NsdServiceInfo arg0) { }

    @Override
    public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) { }

}

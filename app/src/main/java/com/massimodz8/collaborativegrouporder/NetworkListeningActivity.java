package com.massimodz8.collaborativegrouporder;

import android.content.Context;
import android.content.DialogInterface;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.regex.Pattern;

/* This activity is started by the MainMenuActivity when the user wants to assemble a new party.
We open a Wi-Fi direct bubble and a proper service and listen to network to find users joining.
 */
/** todo: this is an excellent moment to provide some ads: after the GM started scanning
 * he has to wait for users to join and I can push to him whatever I want.  */
public class NetworkListeningActivity extends AppCompatActivity implements NsdManager.RegistrationListener {
    private ServerSocket landing;
    private NsdManager nsdService;
    private FormingPlayerGroupHelper grouping; // composition is cooler mofo


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_network_listening);
    }
    protected void onDestroy() {
        nsdService.unregisterService(this);
        super.onDestroy();
    }

    @Override
    public void onResume() {
        // regen service if I decide to tear it down.
        super.onResume();
    }

    @Override
    public void onPause() {
        // nsdService.unregisterService(this);
        // I don't do this. I really want the service to stay on.
        super.onPause();
    }

    public void initiatePartyHandshake(View view) {
        final EditText groupNameView = (EditText)findViewById(R.id.in_partyName);
        final String gname = groupNameView.getText().toString();
        if(gname.isEmpty()) {
            AlertDialog.Builder build = new AlertDialog.Builder(this);
            build.setTitle(R.string.groupNameIsEmpty_title)
                    .setMessage(R.string.groupNameIsEmpty_msg)
                    .setPositiveButton(R.string.groupName_retry, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            groupNameView.requestFocus();
                        }
                    });
            build.show();
            return;
        }

        try {
            landing = new ServerSocket(0);
        } catch (IOException e) {
            AlertDialog.Builder build = new AlertDialog.Builder(this);
            build.setTitle(R.string.serverSocketFailed_title)
                    .setMessage(R.string.serverSocketFailed_msg);
            build.show();
            return;
        }

        nsdService = (NsdManager)getSystemService(Context.NSD_SERVICE);
        if(nsdService == null) {
            AlertDialog.Builder build = new AlertDialog.Builder(this);
            build.setTitle(R.string.nullNSDService_title)
                    .setMessage(R.string.nullNSDService_msg);
            build.show();
        }


        final NetworkListeningActivity self = this;
        guiThreadHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                switch(msg.what) {
                    case MSG_SERVICE_REGISTRATION_COMPLETE:
                        ServiceRegistrationResult res = (ServiceRegistrationResult)msg.obj;
                        if(res == null) res = new ServiceRegistrationResult(-1); // impossible by construction... for now.
                        if(res.successful) {
                            if(!res.netName.equals(gname)) {
                                TextView note = (TextView)findViewById(R.id.txt_renamedService);
                                String text = getString(R.string.renamedGroup);
                                note.setText(String.format(text, res.netName));
                                note.setVisibility(View.VISIBLE);
                            }
                            TextView port = (TextView)findViewById(R.id.txt_FYI_port);
                            String hostInfo = getString(R.string.FYI_explicitConnectInfo);
                            hostInfo += String.format(getString(R.string.explicit_portInfo), landing.getLocalPort());

                            Enumeration<NetworkInterface> nics = null;
                            try {
                                nics = NetworkInterface.getNetworkInterfaces();
                            } catch (SocketException e) {
                                AlertDialog.Builder build = new AlertDialog.Builder(self);
                                build.setTitle("Network error")
                                        .setMessage("Failed to get network information. This isn't really a problem but you'll have to find this device network address in some other way in case your friends need explicit connection data.");
                                build.show();
                            }
                            if(nics != null) {
                                while(nics.hasMoreElements()) {
                                    NetworkInterface n = nics.nextElement();
                                    Enumeration<InetAddress> addrs = n.getInetAddresses();
                                    Inet4Address ipFour = null;
                                    Inet6Address ipSix = null;
                                    while(addrs.hasMoreElements()) {
                                        InetAddress a = addrs.nextElement();
                                        if(a.isAnyLocalAddress()) continue; // ~0.0.0.0 or ::, sure not useful
                                        if(a.isLoopbackAddress()) continue; // ~127.0.0.1 or ::1, not useful
                                        if(ipFour == null && a instanceof Inet4Address) ipFour = (Inet4Address)a;
                                        if(ipSix == null && a instanceof Inet6Address) ipSix = (Inet6Address)a;
                                    }
                                    if(ipFour != null) hostInfo += String.format(getString(R.string.explicit_address), stripUselessChars(ipFour.toString()));
                                    if(ipSix != null) hostInfo += String.format(getString(R.string.explicit_address), stripUselessChars(ipSix.toString()));
                                }
                            }

                            port.setText(hostInfo);
                            port.setVisibility(View.VISIBLE);

                            findViewById(R.id.txt_scanning).setVisibility(View.VISIBLE);
                            findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
                            findViewById(R.id.list_characters).setVisibility(View.VISIBLE);
                            View closeGroupBtn = findViewById(R.id.btn_closeGroup);
                            closeGroupBtn.setVisibility(View.VISIBLE);
                            closeGroupBtn.setEnabled(false);
                        }
                        else {
                            AlertDialog.Builder build = new AlertDialog.Builder(self);
                            String readable = nsdErrorString(res.error);
                            build.setTitle(R.string.serviceRegFailed_title)
                                    .setMessage(String.format(getString(R.string.serviceRegFailed_msg), readable));
                            build.show();
                        }
                        return true;
                    case MSG_PLAYER_HANDSHAKE_FAILED:
                    case MSG_PLAYER_WELCOME:
                        AlertDialog.Builder build = new AlertDialog.Builder(self);
                        build.setTitle("STUB")
                                .setMessage("Something attempted to connect");
                        build.show();
                }
                return false;
            }
        });
        NsdServiceInfo servInfo  = new NsdServiceInfo();
        servInfo.setServiceName(gname);
        servInfo.setServiceType("_groupInitiative._tcp");
        servInfo.setPort(landing.getLocalPort());
        nsdService.registerService(servInfo, NsdManager.PROTOCOL_DNS_SD, this);

        view.setEnabled(false);
        groupNameView.setEnabled(false);

        grouping = new FormingPlayerGroupHelper(guiThreadHandler);
        Thread acceptor = new Thread(new Runnable() {
            @Override
            public void run() {
                boolean keepGoing = true;
                while (keepGoing) {
                    try {
                        Socket newComer = landing.accept();
                        new GroupJoinHandshakingThread(newComer, grouping).run();

                        //} catch(InterruptedException e) {
                    } catch (IOException e) {
                        // Also identical to ClosedByInterruptException
                        /// TODO: notify user?
                        keepGoing = false;
                    }
                }
            }
        }, "Group join async acceptor");
        acceptor.start();
    }

    private static String stripUselessChars(String s) {
        for(int i = 0; i < s.length(); i++) {
            if(s.charAt(i) == '%') {
                s = s.substring(0, i);
                break;
            }
        }
        return s.charAt(0) == '/'? s.substring(1) : s;
    }

    private static String nsdErrorString(int error) {
        switch(error) {
            case NsdManager.FAILURE_ALREADY_ACTIVE: return "FAILURE_ALREADY_ACTIVE";
            case NsdManager.FAILURE_INTERNAL_ERROR: return "FAILURE_INTERNAL_ERROR";
            case NsdManager.FAILURE_MAX_LIMIT: return "FAILURE_MAX_LIMIT";
        }
        return String.format("%1$d", error);
    }


    // NsdManager.RegistrationListener() is async AND on a different thread so I cannot just
    // modify the various controls from there. Instead, wait for success/fail and then
    // pass a notification to the UI thread.
    public static final int MSG_SERVICE_REGISTRATION_COMPLETE = 1;
    public static final int MSG_PLAYER_HANDSHAKE_FAILED = 2;
    public static final int MSG_PLAYER_WELCOME = 3;

    static class ServiceRegistrationResult {
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
    static public class HandshakeFailureInfo {
        public HandshakeFailureInfo(InetAddress byebye, int port, String reason) {
            this.byebye = byebye;
            this.port = port;
            this.reason = reason;
        }

        public InetAddress byebye;
        public int port;
        public String reason;
    }
    static public class NewPlayerInfo {
        public NewPlayerInfo(Socket sticker, String hello) {
            this.sticker = sticker;
            this.hello = hello;
        }

        public Socket sticker;
        public String hello;
    }
    Handler guiThreadHandler;

    //
    // NsdManager.RegistrationListener() ___________________________________________________________
    @Override
    public void onServiceRegistered(NsdServiceInfo info) {
        final String netName = info.getServiceName();
        Message msg = Message.obtain(guiThreadHandler, MSG_SERVICE_REGISTRATION_COMPLETE, new ServiceRegistrationResult(netName));
        guiThreadHandler.sendMessage(msg);
    }

    @Override
    public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
        Message msg = Message.obtain(guiThreadHandler, MSG_SERVICE_REGISTRATION_COMPLETE, new ServiceRegistrationResult(errorCode));
        guiThreadHandler.sendMessage(msg);
    }

    @Override
    public void onServiceUnregistered(NsdServiceInfo arg0) { }

    @Override
    public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) { }
}

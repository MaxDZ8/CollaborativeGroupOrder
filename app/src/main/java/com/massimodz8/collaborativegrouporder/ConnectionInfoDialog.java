package com.massimodz8.collaborativegrouporder;

import android.content.Context;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;

/**
 * Created by Massimo on 19/02/2016.
 * Information for explicit connection takes just too much space.
 * Better to present it in its own dialog, in case it's needed. Plus, I can use it across
 * different activities with no trouble.
 */
public class ConnectionInfoDialog {
    AppCompatActivity activity;
    int serverPort;
    public ConnectionInfoDialog(AppCompatActivity activity, int serverPort) {
        diag = new AlertDialog.Builder(activity, R.style.AppDialogStyle).create();
        this.activity = activity;
        this.serverPort = serverPort;
    }
    public void show() {
        diag.show();
        diag.setContentView(R.layout.dialog_info_explicit_connect);
        final TextView port = (TextView) diag.findViewById(R.id.dlg_iec_port);
        final TextView addr = (TextView) diag.findViewById(R.id.dlg_iec_addresses);
        port.setText(String.format(activity.getString(R.string.dlgIEC_port), serverPort));
        addr.setText(listAddresses(activity));
        MaxUtils.setVisibility(diag, serverPort == 0? View.GONE : View.VISIBLE,
                R.id.dlg_iec_addrInstructions,
                R.id.dlg_iec_port,
                R.id.dlg_iec_portInstructions);
        diag.findViewById(R.id.dlg_iec_noPort).setVisibility(serverPort == 0? View.VISIBLE : View.GONE);
    }
    private final AlertDialog diag;



    private static String listAddresses(Context ctx) {
        Enumeration<NetworkInterface> nics;
        try {
            nics = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            return ctx.getString(R.string.master_cannotEnumerateNICs);
        }
        String hostInfo = "";
        if (nics != null) {
            ArrayList<String> unique = new ArrayList<>();
            while (nics.hasMoreElements()) {
                NetworkInterface n = nics.nextElement();
                Enumeration<InetAddress> addrs = n.getInetAddresses();
                Inet4Address ipFour = null;
                Inet6Address ipSix = null;
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a.isAnyLocalAddress()) continue; // ~0.0.0.0 or ::, sure not useful
                    if (a.isLoopbackAddress()) continue; // ~127.0.0.1 or ::1, not useful
                    if (ipFour == null && a instanceof Inet4Address) ipFour = (Inet4Address) a;
                    if (ipSix == null && a instanceof Inet6Address) ipSix = (Inet6Address) a;
                }
                String addr;
                if(ipFour != null) addr = ipFour.toString();
                else if(ipSix != null) addr = ipSix.toString();
                else continue;
                addr = stripUselessChars(addr);
                boolean found = false;
                for (String already : unique) {
                    if(already.equals(addr)) {
                        found = true;
                        break;
                    }
                }
                if(found) continue;
                unique.add(addr);
            }
            for (String str : unique) {
                if(hostInfo.length() > 0) hostInfo += '\n';
                hostInfo += str;
            }
        }
        return hostInfo;
    }

    private static String stripUselessChars(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '%') {
                s = s.substring(0, i);
                break;
            }
        }
        return s.charAt(0) == '/' ? s.substring(1) : s;
    }
}

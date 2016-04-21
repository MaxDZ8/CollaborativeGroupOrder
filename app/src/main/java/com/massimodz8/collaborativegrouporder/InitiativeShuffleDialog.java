package com.massimodz8.collaborativegrouporder;

import android.content.Context;
import android.content.DialogInterface;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;

import com.massimodz8.collaborativegrouporder.master.AbsLiveActor;

import java.util.Arrays;
import java.util.IdentityHashMap;

/**
 * Created by Massimo on 20/04/2016.
 * Lists the given actors and allows to move a specific one. On successful result the list will be
 * updated and the outer code figures out what to do with the new list, where at most 1 element
 * will have been shuffled.
 */
public class InitiativeShuffleDialog {
    private final AbsLiveActor[] order;
    private IdentityHashMap<AbsLiveActor, Integer> actorId;
    private int actor;

    interface OnApplyCallback {
        void newOrder(AbsLiveActor[] target);
    }

    public InitiativeShuffleDialog(AbsLiveActor[] order, int actor, IdentityHashMap<AbsLiveActor, Integer> actorId) {
        this.order = order;
        this.actor = actor;
        this.actorId = actorId;
    }
    public void show(@NonNull final AppCompatActivity activity, @NonNull final OnApplyCallback confirmed) {
        final AlertDialog dlg = new AlertDialog.Builder(activity).setView(R.layout.dialog_shuffle_initiative_order)
                .setPositiveButton(activity.getString(R.string.mara_dlgSIO_apply), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        confirmed.newOrder(order);
                    }
                })
                .show();
        final RecyclerView list = (RecyclerView) dlg.findViewById(R.id.mara_dlgSIO_list);
        list.setAdapter(new AdventuringActorAdapter(actorId, null, true) {
            @Override
            protected boolean isCurrent(AbsLiveActor actor) {
                return order[InitiativeShuffleDialog.this.actor] == actor;
            }

            @Override
            protected AbsLiveActor getActorByPos(int position) {
                return order[position];
            }

            @Override
            protected boolean enabledSetOrGet(AbsLiveActor actor, @Nullable Boolean newValue) {
                return false; // we don't do this.
            }

            @Override
            protected LayoutInflater getLayoutInflater() {
                return activity.getLayoutInflater();
            }

            @Override
            public int getItemCount() {
                return order.length;
            }
        });
        list.addItemDecoration(new PreSeparatorDecorator(list, activity) {
            @Override
            protected boolean isEligible(int position) {
                return true;
            }
        });
        final Button after = (Button) dlg.findViewById(R.id.mara_dlgSIO_moveAfter);
        final Button before = (Button) dlg.findViewById(R.id.mara_dlgSIO_moveBefore);
        before.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final AbsLiveActor temp = order[actor - 1];
                order[actor - 1] = order[actor];
                order[actor] = temp;
                actor--;
                if(actor == 0) before.setEnabled(false);
                after.setEnabled(true);
                list.getAdapter().notifyItemMoved(actor, actor - 1);
            }
        });
        after.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final AbsLiveActor temp = order[actor];
                order[actor] = order[actor + 1];
                order[actor + 1] = temp;
                actor++;
                before.setEnabled(true);
                if(actor == order.length) after.setEnabled(false);
                list.getAdapter().notifyItemMoved(actor, actor + 1);
            }
        });
        before.setEnabled(actor != 0);
        after.setEnabled(actor != order.length);
    }




/*
    AppCompatActivity activity;
    int serverPort;
    public ConnectionInfoDialog(AppCompatActivity activity, int serverPort) {
        diag = new AlertDialog.Builder(activity).create();
        this.activity = activity;
        this.serverPort = serverPort;
    }
    public void show() {
        diag.show();
        diag.setContentView(R.layout.dialog_info_explicit_connect);
        final TextView port = (TextView) diag.findViewById(R.id.dlg_iec_port);
        final TextView addr = (TextView) diag.findViewById(R.id.dlg_iec_addresses);
        port.setText(String.format(activity.getString(R.string.dlg_iec_port), serverPort));
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
            return ctx.getString(R.string.cannotEnumerateNICs);
        }
        String hostInfo = "";
        if (nics != null) {
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
                if (ipFour != null)
                    hostInfo += String.format(ctx.getString(R.string.explicit_address), stripUselessChars(ipFour.toString()));
                if (ipSix != null)
                    hostInfo += String.format(ctx.getString(R.string.explicit_address), stripUselessChars(ipSix.toString()));
            }
        }
        return hostInfo.substring(0, hostInfo.length() - 1);
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
    */
}

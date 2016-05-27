package com.massimodz8.collaborativegrouporder;

import android.content.DialogInterface;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.massimodz8.collaborativegrouporder.master.AdventuringActorControlsVH;
import com.massimodz8.collaborativegrouporder.master.AdventuringActorWithControlsAdapter;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;

/**
 * Created by Massimo on 20/04/2016.
 * Lists the given actors and allows to move a specific one. On successful result the list will be
 * updated and the outer code figures out what to do with the new list, where at most 1 element
 * will have been shuffled.
 */
public class InitiativeShuffleDialog {
    private final Network.ActorState[] order;
    private int actor;

    interface OnApplyCallback {
        void newOrder(int newPos);
    }

    public InitiativeShuffleDialog(Network.ActorState[] order, int actor) {
        this.order = order;
        this.actor = actor;
    }
    public void show(@NonNull final AppCompatActivity activity, @NonNull final OnApplyCallback confirmed) {
        final AlertDialog dlg = new AlertDialog.Builder(activity, R.style.AppDialogStyle)
                .setView(R.layout.dialog_shuffle_initiative_order)
                .setPositiveButton(activity.getString(R.string.mara_dlgSIO_apply), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        confirmed.newOrder(actor);
                    }
                })
                .show();
        final RecyclerView list = (RecyclerView) dlg.findViewById(R.id.mara_dlgSIO_list);
        final AdventuringActorWithControlsAdapter lister = new AdventuringActorWithControlsAdapter() {
            @Override
            public AdventuringActorControlsVH onCreateViewHolder(ViewGroup parent, int viewType) {
                AdventuringActorControlsVH res = super.onCreateViewHolder(parent, viewType);
                res.selected.setEnabled(false);
                MaxUtils.setVisibility(View.GONE, res.selected, res.avatar, res.prepared);
                return res;
            }

            @Override
            protected boolean isCurrent(Network.ActorState actor) {
                return order[InitiativeShuffleDialog.this.actor].peerKey == actor.peerKey;
            }

            @Override
            protected LayoutInflater getLayoutInflater() { return activity.getLayoutInflater(); }

            @Override
            public Network.ActorState getActorByPos(int position) { return order[position]; }

            @Override
            public int getItemCount() { return order.length; }

            @Override
            protected boolean isChecked(Network.ActorState actor) { return false; }
        };
        list.setAdapter(lister);
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
                final Network.ActorState temp = order[actor - 1];
                order[actor - 1] = order[actor];
                order[actor] = temp;
                actor--;
                if(actor == 0) before.setEnabled(false);
                after.setEnabled(true);
                list.getAdapter().notifyDataSetChanged();
            }
        });
        after.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Network.ActorState temp = order[actor];
                order[actor] = order[actor + 1];
                order[actor + 1] = temp;
                actor++;
                before.setEnabled(true);
                if(actor == order.length - 1) after.setEnabled(false);
                list.getAdapter().notifyDataSetChanged();
            }
        });
        before.setEnabled(actor > 0);
        after.setEnabled(actor < order.length - 1);
    }
}

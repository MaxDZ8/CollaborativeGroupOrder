package com.massimodz8.collaborativegrouporder;

import android.content.DialogInterface;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

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
        final AdventuringActorAdapter<AdventuringActorDataVH> lister = new AdventuringActorAdapter<AdventuringActorDataVH>() {
            @Override
            public AdventuringActorDataVH onCreateViewHolder(ViewGroup parent, int viewType) {
                return new AdventuringActorDataVH(getLayoutInflater().inflate(R.layout.vh_adventuring_actor_data, parent, false)) {
                    @Override
                    public void onClick(View v) {
                        // nothing, this thing is not click enabled.
                    }
                };
            }

            @Override
            public void onBindViewHolder(AdventuringActorDataVH holder, int position) {
                holder.bindData(getActorByPos(position));
            }

            @Override
            public int getItemCount() {
                return order.length;
            }

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
        };
        lister.actorId = actorId;
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
                final AbsLiveActor temp = order[actor - 1];
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
                final AbsLiveActor temp = order[actor];
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

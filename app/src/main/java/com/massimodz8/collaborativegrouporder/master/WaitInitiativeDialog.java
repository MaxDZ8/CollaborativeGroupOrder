package com.massimodz8.collaborativegrouporder.master;


import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import com.massimodz8.collaborativegrouporder.R;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;

/**
 * Created by Massimo on 28/04/2016.
 * This dialog is shown on master device while waiting for players to roll initiative.
 * It kinda uses the 'controls' actor viewholder and provides some extra hooks to update state.
 * Some outer component pushes state changes here so it's already monitoring when to dismiss this.
 */
public class WaitInitiativeDialog {
    final SessionHelper session;
    RecyclerView.Adapter<AdventuringActorControlsVH> lister;
    AlertDialog dlg;

    public WaitInitiativeDialog(SessionHelper session) {
        this.session = session;
    }
    public WaitInitiativeDialog show(@NonNull final AppCompatActivity activity) {
        dlg = new AlertDialog.Builder(activity).setView(R.layout.dialog_wait_initiative_rolls)
                .setCancelable(false)
                .show();
        lister = new RecyclerView.Adapter<AdventuringActorControlsVH>() {
            @Override
            public AdventuringActorControlsVH onCreateViewHolder(ViewGroup parent, int viewType) {
                final AdventuringActorControlsVH vh = new AdventuringActorControlsVH(activity.getLayoutInflater().inflate(R.layout.vh_adventuring_actor_controls, parent, false)) {
                    @Override
                    public void onClick(View v) {
                    } // nothing, this is not interactive

                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    } // nothing, outer code handles this.
                };
                vh.showHilight = false;
                return vh;
            }

            @Override
            public void onBindViewHolder(AdventuringActorControlsVH holder, int position) {
                SessionHelper.Initiative initiative = null;
                Network.ActorState match = null;
                for(int loop = 0; loop < session.session.getNumActors(); loop++) {
                    final Network.ActorState actor = session.session.getActor(loop);
                    final SessionHelper.Initiative test = session.initiatives.get(actor.peerKey);
                    if(test.request != null) {
                        if(position == 0) {
                            initiative = test;
                            match = actor;
                            break;
                        }
                        position--;
                    }
                    // else rolled automatically, do not list.
                }
                if(initiative == null) return;
                holder.checked = initiative.rolled != null;
                holder.bindData(match);
                holder.selected.setEnabled(false);
            }

            @Override
            public int getItemCount() {
                int count = 0;
                for(int loop = 0; loop < session.session.getNumActors(); loop++) {
                    final SessionHelper.Initiative initiative = session.initiatives.get(session.session.getActor(loop).peerKey);
                    if(initiative.request != null) count++;
                    // else rolled automatically, do not list.
                }
                return count;
            }

            @Override
            public long getItemId(int position) {
                Network.ActorState match = null;
                for(int loop = 0; loop < session.session.getNumActors(); loop++) {
                    final Network.ActorState actor = session.session.getActor(loop);
                    final SessionHelper.Initiative test = session.initiatives.get(actor.peerKey);
                    if(test.request != null) {
                        if(position == 0) return actor.peerKey;
                        position--;
                    }
                    // else rolled automatically, do not list.
                }
                return RecyclerView.NO_ID;
            }
        };
        lister.setHasStableIds(true);
        ((RecyclerView) dlg.findViewById(R.id.fra_dlgWIR_list)).setAdapter(lister);
        return this;
    }
}

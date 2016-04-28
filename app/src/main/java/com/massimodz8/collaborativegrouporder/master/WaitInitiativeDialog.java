package com.massimodz8.collaborativegrouporder.master;


import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import com.massimodz8.collaborativegrouporder.AbsLiveActor;
import com.massimodz8.collaborativegrouporder.R;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Created by Massimo on 28/04/2016.
 * This dialog is shown on master device while waiting for players to roll initiative.
 * It kinda uses the 'controls' actor viewholder and provides some extra hooks to update state.
 * Some outer component pushes state changes here so it's already monitoring when to dismiss this.
 */
public class WaitInitiativeDialog {
    final IdentityHashMap<AbsLiveActor, Integer> actorId;
    final Map<AbsLiveActor, SessionHelper.Initiative> initRolls;
    RecyclerView.Adapter<AdventuringActorControlsVH> lister;
    AlertDialog dlg;

    public WaitInitiativeDialog(IdentityHashMap<AbsLiveActor, Integer> actorId, Map<AbsLiveActor, SessionHelper.Initiative> initRolls) {
        this.actorId = actorId;
        this.initRolls = initRolls;
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
                AbsLiveActor match = null;
                for (Map.Entry<AbsLiveActor, SessionHelper.Initiative> el : initRolls.entrySet()) {
                    if(el.getValue().request == null) continue;
                    if(position == 0) {
                        match = el.getKey();
                        break;
                    }
                    position--;
                }
                if(match == null) return;
                holder.checked = initRolls.get(match).rolled != null;
                holder.bindData(match);
                holder.selected.setEnabled(false);
            }

            @Override
            public int getItemCount() {
                int count = 0;
                for (Map.Entry<AbsLiveActor, SessionHelper.Initiative> el : initRolls.entrySet()) {
                    if(el.getValue().request != null) count++;
                    // else rolled automatically, do not list.
                }

                return count;
            }

            @Override
            public long getItemId(int position) {
                AbsLiveActor match = null;
                for (Map.Entry<AbsLiveActor, SessionHelper.Initiative> el : initRolls.entrySet()) {
                    if(el.getValue().request == null) continue;
                    if(position == 0) {
                        match = el.getKey();
                        break;
                    }
                    position--;
                }
                if(match == null) return RecyclerView.NO_ID;
                return actorId.get(match);
            }
        };
        lister.setHasStableIds(true);
        ((RecyclerView) dlg.findViewById(R.id.fra_dlgWIR_list)).setAdapter(lister);
        return this;
    }
}

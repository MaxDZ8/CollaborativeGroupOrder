package com.massimodz8.collaborativegrouporder.master;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import com.massimodz8.collaborativegrouporder.ActorId;
import com.massimodz8.collaborativegrouporder.R;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;

import java.util.ArrayList;

/**
 * Created by Massimo on 08/05/2016.
 * The current actor lister is too inflexible. Trying to make something less generic and
 * more reusable.
 */
public abstract class ActorListerWithControls<E> extends RecyclerView.Adapter<AdventuringActorControlsVH> {
    private final ArrayList<E> list;
    private final LayoutInflater inflater;
    private final SessionHelper.PlayState resolver;

    public ActorListerWithControls(ArrayList<E> list, LayoutInflater inflater, SessionHelper.PlayState resolver) {
        this.list = list;
        this.inflater = inflater;
        this.resolver = resolver;
        setHasStableIds(true);
    }


    protected abstract void setRepresentedProperty(E entry, boolean newValue);
    protected abstract @ActorId int getPeerKey(E entry);
    protected abstract boolean match(E entry, @ActorId int id);

    @Override
    public AdventuringActorControlsVH onCreateViewHolder(ViewGroup parent, int viewType) {
        AdventuringActorControlsVH res = new AdventuringActorControlsVH(inflater.inflate(R.layout.vh_adventuring_actor_controls, parent, false)) {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (actor == null) return;
                for (E el : list) {
                    if(match(el, actor.peerKey)) {
                        setRepresentedProperty(el, isChecked);
                        return;
                    }
                }
            }

            @Override
            public void onClick(View v) {
                selected.setSelected(!selected.isSelected());
            }
        };
        res.avatar.setVisibility(View.GONE);
        return res;
    }

    @Override
    public void onBindViewHolder(AdventuringActorControlsVH holder, int position) {
        holder.bindData(resolver.getActorById(getPeerKey(list.get(position))));
    }

    @Override
    public int getItemCount() { return list.size(); }
}

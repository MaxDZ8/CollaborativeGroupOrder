package com.massimodz8.collaborativegrouporder.master;

import android.support.annotation.Nullable;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;

import com.massimodz8.collaborativegrouporder.AbsLiveActor;
import com.massimodz8.collaborativegrouporder.AdventuringActorDataVH;
import com.massimodz8.collaborativegrouporder.R;

/**
 * Created by Massimo on 27/04/2016.
 * Refactoring for an unified, type-safe management of AdventuringActorVH.
 * It has to toggle between two modes: master device and client.
 * The former adds a layout nesting and a couple of controls.
 */
public abstract class AdventuringActorControlsVH  extends AdventuringActorDataVH implements CompoundButton.OnCheckedChangeListener {
    public final CheckBox selected;
    final View hilite;

    public AdventuringActorControlsVH(View iv) {
        super(iv);
        selected = (CheckBox) iv.findViewById(R.id.vhAAC_selected);
        selected.setOnCheckedChangeListener(this);
        hilite = iv.findViewById(R.id.vhAAC_currentPlayerHighlight);
    }

    boolean showHilight;
    boolean checked;

    @Override
    public void bindData(AbsLiveActor actor) {
        super.bindData(actor);
        selected.setChecked(checked);
        selected.setEnabled(!showHilight);
        hilite.setVisibility(showHilight ? View.VISIBLE : View.GONE);
    }
}

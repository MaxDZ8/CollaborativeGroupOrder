package com.massimodz8.collaborativegrouporder;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.design.widget.TextInputLayout;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;

/**
 * Created by Massimo on 26/05/2016.
 * How to input a Playing character or validate it, for client/server use, plus data validation.
 */
public abstract class PCViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, AdapterView.OnItemSelectedListener {
    protected abstract void action();

    public TextInputLayout name, health, initiative, experience, customClass;
    public TextView classLabel;
    public Spinner classSelector;
    public Button send;
    public CheckBox accepted;

    public BuildingPlayingCharacter who;

    public PCViewHolder(View inflated) {
        super(inflated);
        name = (TextInputLayout) itemView.findViewById(R.id.vhPCDI_tilName);
        health = (TextInputLayout) itemView.findViewById(R.id.vhPCDI_tilMaxHealth);
        initiative = (TextInputLayout) itemView.findViewById(R.id.vhPCDI_tilInitiative);
        experience = (TextInputLayout) itemView.findViewById(R.id.vhPCDI_tilExperience);
        send = (Button) itemView.findViewById(R.id.vhPCDI_makeNewChar);
        accepted = (CheckBox) itemView.findViewById(R.id.vhPCDI_accepted);
        if(null != send) send.setOnClickListener(this);

        classAdapter = new SelectClassAdapter(inflated.getContext());
        classLabel = (TextView) itemView.findViewById(R.id.vhPCDI_classLabel);
        classSelector = (Spinner) itemView.findViewById(R.id.vhPCDI_class);
        customClass = (TextInputLayout)itemView.findViewById(R.id.vhPCDI_tilCustomClass);
        classSelector.setAdapter(classAdapter);
        classSelector.setOnItemSelectedListener(this);
        
        ctx = inflated.getContext();
    }

    @Override
    public void onClick(View v) {
        if(!validate()) return;
        action();
    }

    @SuppressWarnings("ConstantConditions")
    private boolean validate() {
        int hp = 0, init = 0, xp = 0;
        int errorCount = 0;
        String error = null;
        if(name.getEditText().getText().length() < 1) error = ctx.getString(R.string.pcVH_badName);
        setError(name, error);
        if(error != null) errorCount++;

        error = null;
        try {
            hp = Integer.parseInt(health.getEditText().getText().toString());
            if (hp <= 0) error = ctx.getString(R.string.generic_mustBeAtLeast1);
        } catch (NumberFormatException e) {
            error = ctx.getString(R.string.generic_mustBeInteger);
        }
        setError(health, error);
        if(error != null) errorCount++;

        error = null;
        try {
            init = Integer.parseInt(initiative.getEditText().getText().toString());
        } catch(NumberFormatException e) {
            error = ctx.getString(R.string.generic_mustBeInteger);
        }
        setError(initiative, error);
        if(error != null) errorCount++;

        error = null;
        try {
            xp = Integer.parseInt(experience.getEditText().getText().toString());
            if(xp < 0) error = ctx.getString(R.string.generic_mustBeAtLeast0);
        } catch(NumberFormatException e) {
            error = ctx.getString(R.string.generic_mustBeInteger);
        }
        setError(experience, error);
        if(error != null) errorCount++;

        final String customClassName;
        final int classEnum;
        if(classSelector.getSelectedItemPosition() == classAdapter.customPosition()) {
            customClassName = customClass.getEditText().getText().toString();
            if(customClassName == null || customClassName.length() < 3) {
                error = ctx.getString(R.string.ctx_both_ui_badCustomClassName);
            }
            classEnum = -1;
        }
        else {
            customClassName = "";
            classEnum = classAdapter.protobufEnumValue(classSelector.getSelectedItemPosition());
        }
        setError(customClass, error);
        if(error != null) errorCount++;

        if(errorCount != 0) return false;
        who.experience = xp;
        who.fullHealth = hp;
        who.initiativeBonus = init;
        who.name = name.getEditText().getText().toString().trim();
        who.lastLevelClass.known = customClassName.isEmpty()? classEnum : 0;
        who.lastLevelClass.present = customClassName;
        return true;
    }

    @SuppressWarnings("ConstantConditions")
    public void bind(BuildingPlayingCharacter pc, boolean editable) {
        who = pc;
        // Ok so, stuff is here or maybe it's not. Since those can be cycled I need to assume
        // everything must be enabled/disabled/shown/hidden.
        visibility(accepted, who, BuildingPlayingCharacter.STATUS_ACCEPTED);
        send.setVisibility(editable? View.VISIBLE : View.GONE);
        initiative.getEditText().setText(valueof(who.initiativeBonus));
        setError(initiative, null);
        health.getEditText().setText(valueof(who.fullHealth));
        setError(health, null);
        experience.getEditText().setText(valueof(who.experience));
        setError(experience, null);
        name.getEditText().setText(who.name);
        setError(name, null);

        MaxUtils.setEnabled(editable,
                initiative.getEditText(),
                health.getEditText(),
                experience.getEditText(),
                name.getEditText(),
                customClass.getEditText(),
                classSelector);

        if(pc.lastLevelClass.present.isEmpty()) {
            customClass.setVisibility(View.GONE);
            customClass.getEditText().setText("");
            classSelector.setVisibility(View.VISIBLE);
            classSelector.setSelection(classAdapter.positionOf(who.lastLevelClass.known));
        }
        else {
            customClass.setVisibility(View.VISIBLE);
            customClass.getEditText().setText(pc.lastLevelClass.present);
            classSelector.setVisibility(View.GONE);
            classSelector.setSelection(classAdapter.customPosition());
        }
    }

    private static void visibility(View target, BuildingPlayingCharacter state, int match) {
        if(null == target) return;
        target.setVisibility(match == state.status? View.VISIBLE : View.GONE);
    }


    private static void setError(@NonNull TextInputLayout til, String error) {
        til.setErrorEnabled(error != null);
        til.setError(error);
    }


    static String valueof(int x) {
        if(x == Integer.MIN_VALUE) return "";
        return String.valueOf(x);
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if(position == classAdapter.customPosition()) {
            classSelector.setVisibility(View.GONE);
            customClass.setVisibility(View.VISIBLE);
        }
        who.lastLevelClass.known = classAdapter.protobufEnumValue(position);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // ? can this happen ? Do I care?
    }

    private final SelectClassAdapter classAdapter;
    
    private final Context ctx;
}
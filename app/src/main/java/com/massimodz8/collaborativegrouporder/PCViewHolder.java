package com.massimodz8.collaborativegrouporder;

import android.support.annotation.StringRes;
import android.support.design.widget.TextInputLayout;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;

/**
 * Created by Massimo on 26/05/2016.
 * How to input a Playing character or validate it, for client/server use, plus data validation.
 */
public abstract class PCViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
    protected abstract String getString(@StringRes int resid);
    protected abstract void action();

    public TextInputLayout name, level, health, initiative, experience;
    public Button send;
    public CheckBox accepted;

    public BuildingPlayingCharacter who;

    public PCViewHolder(View inflated) {
        super(inflated);
        name = (TextInputLayout) itemView.findViewById(R.id.vhPCDI_tilName);
        level = (TextInputLayout) itemView.findViewById(R.id.vhPCDI_tilLevel);
        health = (TextInputLayout) itemView.findViewById(R.id.vhPCDI_tilMaxHealth);
        initiative = (TextInputLayout) itemView.findViewById(R.id.vhPCDI_tilInitiative);
        experience = (TextInputLayout) itemView.findViewById(R.id.vhPCDI_tilExperience);
        send = (Button) itemView.findViewById(R.id.vhPCDI_makeNewChar);
        accepted = (CheckBox) itemView.findViewById(R.id.vhPCDI_accepted);
        if(null != send) send.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        if(!validate()) return;
        action();
    }

    @SuppressWarnings("ConstantConditions")
    private boolean validate() {
        int hp = 0, lvl = 0, init = 0, xp = 0;
        int errorCount = 0;
        String error = null;
        if(name.getEditText().getText().length() < 1) error = getString(R.string.charInputValidation_badName);
        name.setError(error);
        if(error != null) errorCount++;

        error = null;
        try {
            hp = Integer.parseInt(health.getEditText().getText().toString());
            if (hp <= 0) error = getString(R.string.charInputValidation_hpMustBePositive);
        } catch (NumberFormatException e) {
            error = getString(R.string.charInputValidation_hpMustBeInteger);
        }
        health.setError(error);
        if(error != null) errorCount++;

        error = null;
        try {
            lvl = Integer.parseInt(level.getEditText().getText().toString());
            if(lvl <= 0) error = getString(R.string.charInputValidation_lvlMustBePositive);
        } catch(NumberFormatException e) {
            error = getString(R.string.charInputValidation_lvlMustBeInteger);
        }
        level.setError(error);
        if(error != null) errorCount++;

        error = null;
        try {
            init = Integer.parseInt(initiative.getEditText().getText().toString());
        } catch(NumberFormatException e) {
            error = getString(R.string.charInputValidation_initMustBeNumber);
        }
        initiative.setError(error);
        if(error != null) errorCount++;

        error = null;
        try {
            xp = Integer.parseInt(experience.getEditText().getText().toString());
            if(xp <= 0) error = getString(R.string.charInputValidation_xpMustBePositive);
        } catch(NumberFormatException e) {
            error = getString(R.string.charInputValidation_xpMustBeNumeric);
        }
        experience.setError(error);
        if(error != null) errorCount++;

        if(errorCount != 0) return false;
        who.experience = xp;
        who.fullHealth = hp;
        who.initiativeBonus = init;
        who.name = name.getEditText().getText().toString();
        who.level = lvl;
        return true;
    }

    @SuppressWarnings("ConstantConditions")
    public void bind(BuildingPlayingCharacter pc) {
        who = pc;
        // Ok so, stuff is here or maybe it's not. Since those can be cycled I need to assume
        // everything must be enabled/disabled/shown/hidden.
        visibility(accepted, who, BuildingPlayingCharacter.STATUS_ACCEPTED);
        visibility(send, who, BuildingPlayingCharacter.STATUS_BUILDING);
        initiative.getEditText().setText(valueof(who.initiativeBonus));
        initiative.setError(null);
        health.getEditText().setText(valueof(who.fullHealth));
        health.setError(null);
        experience.getEditText().setText(valueof(who.experience));
        experience.setError(null);
        name.getEditText().setText(who.name);
        name.setError(null);
        level.getEditText().setText(valueof(who.level));
        level.setError(null);

        MaxUtils.setEnabled(BuildingPlayingCharacter.STATUS_BUILDING == who.status,
                initiative.getEditText(),
                health.getEditText(),
                experience.getEditText(),
                name.getEditText(),
                level.getEditText());
    }

    private static void visibility(View target, BuildingPlayingCharacter state, int match) {
        if(null == target) return;
        target.setVisibility(match == state.status? View.VISIBLE : View.GONE);
    }


    static String valueof(int x) {
        if(x == Integer.MIN_VALUE) return "";
        return String.valueOf(x);
    }
}
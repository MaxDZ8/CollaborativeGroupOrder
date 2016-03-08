package com.massimodz8.collaborativegrouporder.master;

import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.view.ActionMode;
import android.widget.NumberPicker;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.ActionModeCancellingDialog;
import com.massimodz8.collaborativegrouporder.R;

/**
 * Created by Massimo on 08/03/2016.
 * A glorified number picker.
 */
public abstract class SetCharBudgetDialog extends ActionModeCancellingDialog implements NumberPicker.OnValueChangeListener {
    public SetCharBudgetDialog(AppCompatActivity activity, ActionMode mode, @NonNull PartyDefinitionHelper.DeviceStatus devStat) {
        super(activity, mode, R.layout.dialog_set_character_budget);
        this.devStat = devStat;
        NumberPicker picker = (NumberPicker) dialog.findViewById(R.id.dlgSCB_budget);
        picker.setMaxValue(devStat.charBudget + 100);
        picker.setMinValue(0);
        picker.setValue(devStat.charBudget);
        picker.setOnValueChangedListener(this);
        TextView name = (TextView) dialog.findViewById(R.id.dlgSCB_devName);
        name.setText(devStat.name);
    }

    @Override
    public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
        if(oldVal == newVal) return;
        newValue = newVal;
    }

    @Override
    protected void dismissed() {
        if(newValue == null) return;
        requestBudgetChange(devStat, newValue);
    }

    protected abstract void requestBudgetChange(PartyDefinitionHelper.DeviceStatus devStat, int newValue);

    private final PartyDefinitionHelper.DeviceStatus devStat;
    private Integer newValue;
}

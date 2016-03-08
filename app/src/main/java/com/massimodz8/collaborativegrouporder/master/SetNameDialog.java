package com.massimodz8.collaborativegrouporder.master;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.AppCompatActivity;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.widget.EditText;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.ActionModeCancellingDialog;
import com.massimodz8.collaborativegrouporder.R;

/**
 * Created by Massimo on 08/03/2016.
 * Automatically populate according to device status, set and call update on list.
 */
public class SetNameDialog extends ActionModeCancellingDialog implements TextView.OnEditorActionListener {
    public SetNameDialog(AppCompatActivity activity, ActionMode mode, @NonNull PartyDefinitionHelper.DeviceStatus devStat, @Nullable Runnable onNameChanged) {
        super(activity, mode, R.layout.dialog_set_device_name);
        final TextInputLayout view = (TextInputLayout) dialog.findViewById(R.id.dlgSDN_newName);
        EditText input = view.getEditText();
        if(input != null) input.setOnEditorActionListener(this); // lint happy
        final TextView curName = (TextView) dialog.findViewById(R.id.dlgSDN_currentName);
        curName.setText(devStat.name);
        this.devStat = devStat;
        this.onNameChanged = onNameChanged;
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        final TextInputLayout view = (TextInputLayout) dialog.findViewById(R.id.dlgSDN_newName);
        EditText input = view.getEditText();
        if(input == null) return false; // lint happy
        String got = input.getText().toString().trim();
        if(got.isEmpty()) {
            view.setError(activity.getString(R.string.dlgSDN_badName));
            v.requestFocus();
            return true;
        }
        devStat.name = got;
        dialog.dismiss();
        if(onNameChanged != null) onNameChanged.run();
        return true;
    }

    private final PartyDefinitionHelper.DeviceStatus devStat;
    private final Runnable onNameChanged;

    @Override
    protected void dismissed() {
        final TextInputLayout view = (TextInputLayout) dialog.findViewById(R.id.dlgSDN_newName);
        EditText input = view.getEditText();
        if(input == null) return; // lint happy
        input.clearFocus();
    }
}

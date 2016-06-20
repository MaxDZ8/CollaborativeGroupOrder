package com.massimodz8.collaborativegrouporder;

import android.app.Activity;
import android.content.DialogInterface;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.ActionMode;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

/**
 * Created by Massimo on 08/03/2016.
 * Dialogs used as 'extensions' of ActionMode. The bottom line is that they will call .finish
 * when they get dismissed, whatever they are cancelled or not.
 */
public abstract class ActionModeCancellingDialog {
    final protected AlertDialog dialog;
    final protected AppCompatActivity activity;

    protected ActionModeCancellingDialog(@NonNull final AppCompatActivity activity, @NonNull final ActionMode mode, @LayoutRes int layout) {
        this.activity = activity;
        dialog = new AlertDialog.Builder(activity, R.style.AppDialogStyle)
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        InputMethodManager imm = (InputMethodManager)activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
                        final View focused = activity.getCurrentFocus();
                        if(focused != null && imm != null && imm.isActive()) {
                            imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
                        }
                        dismissed();
                        mode.finish();
                    }
                }).show();
        dialog.setContentView(layout);
        dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
    }

    protected abstract void dismissed();
}

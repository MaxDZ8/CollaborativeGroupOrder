package com.massimodz8.collaborativegrouporder;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.net.nsd.NsdManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;

import java.security.MessageDigest;


/**
 * Created by Massimo on 01/02/2016.
 * A few helper calls for all needs. Initially dealing with Views, then extended as convenient.
 * Consider yourself lucky, I haven't called this MaxDZ8Utils or MaxDz8Utils.
 *
 * Not an interface because I don't want it to be marked red as it is mangled as extension
 * methods.
 */
public abstract class MaxUtils {
    public static MessageDigest hasher;

    public static void setVisibility(Activity parent, int visibility, int... targets) {
        for (int id : targets) {
            final View v = parent.findViewById(id);
            if (v != null) v.setVisibility(visibility);
        }
    }

    public static void setVisibility(AlertDialog parent, int visibility, int... targets) {
        for (int id : targets) {
            final View v = parent.findViewById(id);
            if (v != null) v.setVisibility(visibility);
        }
    }
    public static void setVisibility(int visibility, View... targets) {
        for (View v : targets) {
            if (v != null) v.setVisibility(visibility);
        }
    }
    public static void setEnabled(Activity parent, boolean enabled, int... targets) {
        for (int id : targets) {
            final View v = parent.findViewById(id);
            if (v != null) v.setEnabled(enabled);
        }
    }

    public static String NsdManagerErrorToString(int err, Context ctx) {
        switch(err) {
            case NsdManager.FAILURE_ALREADY_ACTIVE: return ctx.getString(R.string.nsdError_alreadyActive);
            case NsdManager.FAILURE_INTERNAL_ERROR: return ctx.getString(R.string.nsdError_internal);
            case NsdManager.FAILURE_MAX_LIMIT: return ctx.getString(R.string.nsdError_maxLimitReached);
        }
        return ctx.getString(R.string.nsdError_unknown);
    }

    public static void askExitConfirmation(final AppCompatActivity goner) {
        askExitConfirmation(goner, R.string.master_carefulDlgMessage);
    }

    public static void askExitConfirmation(final AppCompatActivity goner, @StringRes int msg) {
        new AlertDialog.Builder(goner)
                .setTitle(R.string.generic_carefulDlgTitle)
                .setMessage(msg)
                .setPositiveButton(R.string.master_exitConfirmedDlgAction, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        goner.finish();
                    }
                }).show();
    }

    /**
     * @param view The view which will be target of state manipulation.
     * @param text String to set when not null.
     * @param nullVis Visibility to apply when text == null, otherwise ignored
     * @return true if the view is set to VISIBLE, always true if text != null
     */
    public static boolean setTextUnlessNull(@NonNull TextView view, @Nullable String text, int nullVis) {
        view.setVisibility(text == null? nullVis : View.VISIBLE);
        if(text != null) view.setText(text);
        return text != null;
    }
}

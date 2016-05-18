package com.massimodz8.collaborativegrouporder;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.net.nsd.NsdManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.transition.TransitionManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.protocol.nano.Network;
import com.massimodz8.collaborativegrouporder.protocol.nano.StartData;

import java.io.IOException;
import java.io.InputStream;
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
        view.setVisibility(text == null ? nullVis : View.VISIBLE);
        if(text != null) view.setText(text);
        return text != null;
    }

    public static void beginDelayedTransition(Activity ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            TransitionManager.beginDelayedTransition((ViewGroup) ctx.findViewById(R.id.activityRoot));
        }
    }

    public static Network.ActorState makeActorState(StartData.ActorDefinition el, @ActorId int id, int type) {
        final Network.ActorState res = new Network.ActorState();
        res.peerKey = id;
        res.type = type;
        res.name = el.name;
        res.maxHP = res.currentHP = el.stats[0].healthPoints;
        res.initiativeBonus = el.stats[0].initBonus;
        res.experience = el.experience;
        return res;
    }

    public static class TotalLoader {
        final int validBytes;
        final byte[] fullData;
        public TotalLoader(@NonNull InputStream src, @Nullable byte[] buffer) throws IOException {
            final int increment = 4 * 1024;
            int loaded = 0;
            if(buffer == null) buffer = new byte[increment];
            int chunks = 0;
            while(true) {
                int got = src.read(buffer, loaded, buffer.length - loaded);
                if(got == -1) break;
                loaded += got;
                if(loaded == buffer.length) {
                    byte[] realloc = new byte[buffer.length + (increment << (chunks / 10))];
                    System.arraycopy(buffer, 0, realloc, 0, buffer.length);
                    buffer = realloc;
                }
            }
            validBytes = loaded;
            fullData = buffer;
        }
    }
}
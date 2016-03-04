package com.massimodz8.collaborativegrouporder;

import android.app.Activity;
import android.content.Context;
import android.net.nsd.NsdManager;
import android.view.View;


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
}

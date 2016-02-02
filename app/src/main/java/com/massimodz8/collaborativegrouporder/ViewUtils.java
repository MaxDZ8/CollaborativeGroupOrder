package com.massimodz8.collaborativegrouporder;

import android.app.Activity;
import android.view.View;

/**
 * Created by Massimo on 01/02/2016.
 */
public abstract class ViewUtils {
    static void setVisibility(Activity parent, int visibility, int... targets) {
        for (int id : targets) {
            final View v = parent.findViewById(id);
            if (v != null) v.setVisibility(visibility);
        }
    }
}

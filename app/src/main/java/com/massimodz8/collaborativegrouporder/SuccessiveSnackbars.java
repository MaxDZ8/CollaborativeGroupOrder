package com.massimodz8.collaborativegrouporder;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.design.widget.Snackbar;
import android.view.View;

import java.lang.ref.WeakReference;

/**
 * Created by Massimo on 13/06/2016.
 * Snackbars for long messages suck! (where long means 2 lines)
 * They're often better to stream one after the other.
 * This class does that and holds weak references to the containers. It will generating Snackbar
 * object and show them as long as the references are still there.
 */
public class SuccessiveSnackbars extends Snackbar.Callback {
    final WeakReference<View> root;
    final int duration;
    final String[] messages;
    int next = 0;

    SuccessiveSnackbars(@NonNull View root, final int duration, @NonNull Context resolver, @StringRes int... messages) {
        this.root = new WeakReference<>(root);
        this.duration = duration;
        this.messages = new String[messages.length];
        for(int loop = 0; loop < messages.length; loop++) this.messages[loop] = resolver.getString(messages[loop]);
    }

    SuccessiveSnackbars(@NonNull View root, final int duration, String... messages) {
        this.root = new WeakReference<>(root);
        this.duration = duration;
        this.messages = messages;
    }

    public void show() { {
        View view = root.get();
        if(view == null) return;
        Snackbar.make(view, messages[next], duration)
                .setCallback(this)
                .show();
    }};

    @Override
    public void onDismissed(Snackbar snackbar, int event) {
        next++;
        if(next < messages.length) show();
    }
}

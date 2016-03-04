package com.massimodz8.collaborativegrouporder;

import android.os.Build;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.transition.TransitionManager;
import android.view.View;

import com.google.protobuf.nano.MessageNano;
import com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage;

import java.util.Vector;

/**
 * Created by Massimo on 04/03/2016.
 * Base class originally intended to reduce overriding count. Now it's just for uniformity,
 * but at least the ctor auto-registers itself.
 */
public abstract class HoriSwipeOnlyTouchCallback extends ItemTouchHelper.SimpleCallback {
    static final int DRAG_FORBIDDEN = 0;
    static final int SWIPE_HORIZONTAL = ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT;
    protected HoriSwipeOnlyTouchCallback(RecyclerView rv) {
        super(DRAG_FORBIDDEN, SWIPE_HORIZONTAL);

        final ItemTouchHelper swiper = new ItemTouchHelper(this);
        rv.addItemDecoration(swiper);
        swiper.attachToRecyclerView(rv);
    }

    abstract boolean disable();
    abstract boolean canSwipe(RecyclerView rv, RecyclerView.ViewHolder vh);

    @Override
    public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
        return false;
    }

    @Override
    public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
        if(disable()) return 0;
        int swipe = 0;
        if(canSwipe(recyclerView, viewHolder)) swipe = SWIPE_HORIZONTAL;
        return makeMovementFlags(DRAG_FORBIDDEN, swipe);
    }
}

package com.massimodz8.collaborativegrouporder;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.view.View;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Vector;

/**
 * Created by Massimo on 12/02/2016.
 * It turns out item decorations cannot be added while scroll or layout...
 *     java.lang.IllegalStateException: Cannot add item decoration during a scroll  or layout
 * which, in layman terms means: you cannot fiddle refresh them in onBindViewHolder, you cannot
 * call .addItemDecoration(decoration, position).
 *
 * We cannot even just override notifyDataSetChanged as it's final so we need to keep
 * a decorator around. This takes the logic to decide if a position in a RecyclerView
 * needs a separator or not.
 */
public abstract class PreSeparatorDecorator implements RecyclerView.OnChildAttachStateChangeListener {
    public PreSeparatorDecorator(RecyclerView container) {
        this.container = container;
    }
    protected abstract boolean isEligible(int position);
    protected abstract AppCompatActivity getResolver();

    final RecyclerView container;

    Map<View, Decoration> items = new IdentityHashMap<>();

    @Override
    public void onChildViewAttachedToWindow(View view) {
        /*
        Remove the stateful decorations... .invalidateItemDecorations is said to trigger
        .requestLayout() and I don't want that to happen... such note is not there for the
        other so I keep a list of stateful decorations around and unregister them on need.
        */
        Decoration el = items.get(view);
        final int pos = container.getChildAdapterPosition(view);
        if(isEligible(pos)) {
            if(null == el) {
                el = new Decoration(getResolver());
                items.put(view, el);
                container.addItemDecoration(el, pos);
            }
        }
        else if(null != el) {
            items.remove(view);
            container.removeItemDecoration(el);
        }
    }

    @Override
    public void onChildViewDetachedFromWindow(View view) {
        final Decoration dec = items.get(view);
        if(null != dec) container.removeItemDecoration(dec);
        items.remove(view);
    }

    /**
     * Created by Massimo on 12/02/2016.
     * Attempt at having easygoing, ready to go list separators I can just put in RecyclerView.
     * It turns out this is almost stateless, in the sense that yes, RecyclerView.State can pass
     * "arbitrary objects" by resource ids... WTF.. I also don't have enough info to associate views
     * to call sequences, as I cannot track their lifetimes... so, super cool thing icing on the cake...
     * the interface for item decorators is the same as RecycleView decorators... wtf... in both cases,
     * you will get full canvas (not some sort of viewHolder subregion) and this complicates things.
     *
     * To be used as item decorator, so it can keep state. This means you apply this only on
     * items required to be shaded and goodbye.
     */
    public static class Decoration extends RecyclerView.ItemDecoration {
        Paint paint;
        Rect rect = new Rect();
        final int thickness;

        Decoration(AppCompatActivity ctx, int thickness) {
            int color = new ResourcesCompat().getColor(ctx.getResources(), R.color.listSeparator, ctx.getTheme());
            this.thickness = thickness;
            paint = new Paint();
            paint.setColor(color);
        }
        Decoration(AppCompatActivity ctx) {
            this(ctx, Math.round(ctx.getResources().getDimension(R.dimen.list_separator_thickness)));
        }

        @Override
        //public void onDraw(Canvas c, RecyclerView parent, RecyclerView.State state) {
        public void onDrawOver(Canvas c, RecyclerView parent, RecyclerView.State state) {
            rect.bottom = rect.top + thickness;
            c.drawRect(rect, paint);
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
            outRect.set(0, thickness, 0, 0);
            view.getDrawingRect(rect);
        }
    }
}

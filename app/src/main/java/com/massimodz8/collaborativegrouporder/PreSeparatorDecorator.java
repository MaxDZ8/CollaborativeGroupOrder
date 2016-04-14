package com.massimodz8.collaborativegrouporder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;

import java.util.ArrayList;

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
 *
 * Note that while they are called RecyclerView.ItemDecoration they don't decorate items at all.
 * They just draw stuff in the canvas. They get an handle to all views contained (ViewHolders)
 * and then they figure out how to move the element and how to draw the decoration, so there's
 * only one decorator for each decoration type, which works across all items. Meh.
 *
 */
public abstract class PreSeparatorDecorator extends RecyclerView.ItemDecoration {
    public PreSeparatorDecorator(RecyclerView container, Context ctx, int thickness) {
        this.container = container;
        int color = new ResourcesCompat().getColor(ctx.getResources(), R.color.listSeparator, ctx.getTheme());
        this.thickness = thickness;
        paint = new Paint();
        paint.setColor(color);
    }
    public PreSeparatorDecorator(RecyclerView container, Context ctx) {
        this(container, ctx, Math.round(ctx.getResources().getDimension(R.dimen.list_separator_thickness)));
    }

    protected abstract boolean isEligible(int position);

    final RecyclerView container;
    final Paint paint;
    final int thickness;

    @Override
    public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
        final int pos = parent.getChildAdapterPosition(view);
        if(!isEligible(pos)) {
            outRect.setEmpty();
            return;
        }
        outRect.set(0, thickness, 0, 0);
    }

    // from android support v7 demos DividerItemDecoration
    @Override
    //public void onDraw(Canvas c, RecyclerView parent, RecyclerView.State state) {
    public void onDrawOver(Canvas c, RecyclerView parent, RecyclerView.State state) {
        final int left = parent.getPaddingLeft();
        final int right = parent.getWidth() - parent.getPaddingRight();
        final int childCount = parent.getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View v = parent.getChildAt(i);
            if(isEligible(parent.getChildAdapterPosition(v))) {
                final RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) v.getLayoutParams();
                final int bottom = v.getTop() - params.topMargin - Math.round(ViewCompat.getTranslationY(v));
                final int top = bottom - thickness;
                c.drawRect(left, top, right, bottom, paint);
            }
        }
    }
}

package com.massimodz8.collaborativegrouporder;

import android.animation.ArgbEvaluator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.support.annotation.ColorInt;
import android.util.AttributeSet;
import android.view.View;

import com.massimodz8.collaborativegrouporder.R;

/**
 * Created by Massimo on 15/03/2016.
 * The health bar is basically a determinate ProgressBar which contents change colors according
 * to the value set. It would be nice to use the progress bar directly but it really takes way
 * too much cruft with it so I'm starting from scratch with View.
 */
public class HealthBar extends View {
    public static final int SHOW_CURRENT_NONE = 0;
    public static final int SHOW_CURRENT_REMAINING = 1;
    public static final int SHOW_CURRENT_LOST = 2;

    public int maxHp = 100, currentHp; // after modifying those, call View.invalidate()

    public HealthBar(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.HealthBar,
                0, 0);
        maxVisible = a.getBoolean(R.styleable.HealthBar_showMax, true);
        showCurrentMode = a.getInt(R.styleable.HealthBar_showCurrent, SHOW_CURRENT_REMAINING);
        a.recycle();
    }


    public void setMaxVisible(boolean visible) {
        maxVisible = visible;
        invalidate();
        requestLayout();
    }
    public boolean isMaxVisible() { return maxVisible; }

    /** SHOW_CURRENT_NONE, SHOW_CURRENT_REMAINING, SHOW_CURRENT_LOST */
    public void setCurrentVisibilityMode(int mode) {
        showCurrentMode = mode;
        invalidate();
        requestLayout();
    }

    int slowDown = 100;

    @Override
    protected void onDraw(Canvas canvas) {
        paintRatio();
        float x = getWidth() * widthFraction;
        float y = getHeight() * .75f;
        canvas.drawRect(0, 0, x, getHeight(), paint);
        canvas.drawRect(x, y, getWidth(), getHeight(), paint);
    }


    private boolean maxVisible;
    private int showCurrentMode; // TODO! Show current health points!
    private float widthFraction;
    private Paint paint;
    private static float[] points =       new float[] { .0f,        .15f,       .66f,       .85f,       1.0f };
    private static @ColorInt int[] colors = new int[] { 0xFFFF0000, 0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF0000FF };

    private static ArgbEvaluator lerp = new ArgbEvaluator(); // because apparently .getInstance() does not get resolved you mofo

    private void paintRatio() {
        if(paint == null) {
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setStyle(Paint.Style.FILL);
        }
        Integer use;
        if(currentHp <= 0) {
            widthFraction = .0f;
            use = colors[0];
        }
        else if(currentHp >= maxHp) {
            widthFraction = 1.0f;
            use = colors[colors.length - 1];
        }
        else {
            widthFraction = (float) currentHp / (float) maxHp; //[0..1]
            int start = 0;
            while(start < points.length && points[start] < widthFraction) start++;
            float fraction = widthFraction - points[start - 1];
            fraction /= (points[start] - points[start - 1]);
            use = (Integer)lerp.evaluate(fraction, colors[start - 1], colors[start]);
        }
        paint.setColor(use);
    }
}

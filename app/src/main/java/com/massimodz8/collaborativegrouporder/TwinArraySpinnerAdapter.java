package com.massimodz8.collaborativegrouporder;

import android.content.Context;
import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

/**
 * Created by Massimo on 14/05/2016.
 * Spinner adapter enumerating all monster sizes... then generalized to use a map.
 * It's very simple: key[i] can be visualized by string value [i].
 * Data is static so we don't really care about notifying changes or anything!
 */
public class TwinArraySpinnerAdapter implements SpinnerAdapter {
    public TwinArraySpinnerAdapter(Spinner bind, Context ctx, int[] key, String[] value) {
        this.ctx = ctx;
        this.key = key;
        this.value = value;
        bind.setAdapter(this);
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        if(convertView == null || !(convertView instanceof TextView)) convertView = new TextView(ctx);
        final TextView tv = (TextView) convertView;
        tv.setText(value[position]);
        return tv;
    }

    @Override
    public void registerDataSetObserver(DataSetObserver observer) { }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) { }

    @Override
    public int getCount() { return key.length; }

    @Override
    public Object getItem(int position) { return value[position]; }

    @Override
    public long getItemId(int position) { return 0; }

    @Override
    public boolean hasStableIds() { return false; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return getDropDownView(position, convertView, parent);
    }

    @Override
    public int getItemViewType(int position) { return 0; }

    @Override
    public int getViewTypeCount() { return 0; }

    @Override
    public boolean isEmpty() { return false; }

    private final Context ctx;
    private final int[] key;
    private final String[] value;
}

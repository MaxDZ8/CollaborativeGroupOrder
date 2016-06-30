package com.massimodz8.collaborativegrouporder;

import android.content.Context;
import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * Created by Massimo on 30/06/2016.
 * Truly ugly thing used by spinner to enumerate all known classes +1 'custom' class.
 */
public class SelectClassAdapter extends KnownClassSpinnerAdapter {
    public SelectClassAdapter(Context ctx) {
        super(ctx);
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        if(position < presentation.size()) return super.getDropDownView(position, convertView, parent);
        final TextView use;
        if(convertView != null && convertView instanceof TextView) use = (TextView)convertView;
        else use = new TextView(ctx);
        use.setText(R.string.both_ui_customClassSpinnerLabel);
        return use;
    }

    @Override
    public void registerDataSetObserver(DataSetObserver observer) {
        // static data
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
        // static data
    }

    @Override
    public int getCount() {
        return super.getCount() + 1;
    }

    @Override
    public Object getItem(int position) {
        return position < presentation.size()? super.getItem(position) : ctx.getString(R.string.both_ui_customClassSpinnerLabel);
    }

    public int positionOf(int protobufKnownClass) {
        return protobufKnownClass - 1;
    }

    public int customPosition() {
        return presentation.size();
    }
}

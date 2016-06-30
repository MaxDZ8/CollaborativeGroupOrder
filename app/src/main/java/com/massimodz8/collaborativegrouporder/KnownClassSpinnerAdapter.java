package com.massimodz8.collaborativegrouporder;

import android.content.Context;
import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.protocol.nano.RPGClass;

import java.util.ArrayList;

/**
 * Created by Massimo on 30/06/2016.
 * Enumerate everything in RPGClass.KnownClass.
 */
public class KnownClassSpinnerAdapter implements SpinnerAdapter {
    public KnownClassSpinnerAdapter(Context ctx) {
        this.ctx = ctx;
        if(presentation.isEmpty()) {
            final String invalid = ProtobufSupport.knownClassToString(0, ctx);
            int scan = 1;
            while (true) {
                final String mine = ProtobufSupport.knownClassToString(scan, ctx);
                if (mine.equals(invalid)) break;
                presentation.add(mine);
                scan++;
            }
        }
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        final TextView use;
        if(convertView != null && convertView instanceof TextView) use = (TextView)convertView;
        else use = new TextView(ctx);
        use.setText(presentation.get(position));
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
        return presentation.size();
    }

    @Override
    public Object getItem(int position) {
        return presentation.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return getDropDownView(position, convertView, parent);
    }

    @Override
    public int getItemViewType(int position) {
        return 0;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    protected static final ArrayList<String> presentation = new ArrayList<>(100);
    protected final Context ctx;

    public final int protobufEnumValue(int position) {
        if(position < 0 || position >= presentation.size()) return RPGClass.KC_INVALID;
        return position + 1;
    }
}

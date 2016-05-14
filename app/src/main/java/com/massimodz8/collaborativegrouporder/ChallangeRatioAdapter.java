package com.massimodz8.collaborativegrouporder;

import android.database.DataSetObserver;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.master.AwardExperienceActivity;
import com.massimodz8.collaborativegrouporder.protocol.nano.MonsterData;

import java.util.Locale;

/**
 * Created by Massimo on 14/05/2016.
 * Enumerates all challange ratios from 1/8 to max specified and auto-binds itself to the spinner.
 */
public class ChallangeRatioAdapter implements SpinnerAdapter {
    public ChallangeRatioAdapter(Spinner bind, int lastCr, LayoutInflater inflater) {
        limit = lastCr;
        this.inflater = inflater;
        bind.setAdapter(this);
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        if(convertView == null || !(convertView instanceof LinearLayout)) {
            convertView = inflater.inflate(R.layout.spinner_entry_cr, parent, false);
        }
        MonsterData.Monster.ChallangeRatio cr = (MonsterData.Monster.ChallangeRatio) getItem(position);
        int experience = AwardExperienceActivity.xpFrom(cr.numerator, cr.denominator);
        final TextView view = (TextView) convertView.findViewById(R.id.seCR_cr);
        if(cr.denominator == 1) view.setText(String.valueOf(cr.numerator));
        else view.setText(String.format(Locale.getDefault(), "%1$d/%2$d", cr.numerator, cr.denominator));
        ((TextView) convertView.findViewById(R.id.seCR_xp)).setText(String.valueOf(experience));
        return convertView;
    }

    private final int limit;
    private final LayoutInflater inflater;
    private static final int[] knownDens = {
            8, 4, 6, 3, 2
    };

    @Override
    public void registerDataSetObserver(DataSetObserver observer) { /* nope, this thing is static sorta */ }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) { }

    @Override
    public int getCount() { return limit + knownDens.length; }

    @Override
    public Object getItem(int position) {
        final MonsterData.Monster.ChallangeRatio cr = new MonsterData.Monster.ChallangeRatio();
        if(position < knownDens.length) {
            cr.numerator = 1;
            cr.denominator = knownDens[position];
        }
        else {
            cr.numerator = position - knownDens.length + 1;
            cr.denominator = 1;
        }
        return cr;
    }

    @Override
    public long getItemId(int position) { return position; }

    @Override
    public boolean hasStableIds() { return false; /* I take it easy here */ }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) { return getDropDownView(position, convertView, parent); }

    @Override
    public int getItemViewType(int position) { return 0; }

    @Override
    public int getViewTypeCount() { return 1; }

    @Override
    public boolean isEmpty() { return false; }
}

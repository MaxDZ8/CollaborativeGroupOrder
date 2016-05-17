package com.massimodz8.collaborativegrouporder;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.protocol.nano.PreparedEncounters;

import java.text.DateFormat;
import java.util.Date;

/**
 * Created by Massimo on 16/05/2016.
 * A card-based layout to be used with PreparedBattlesActivity as separator.
 */
public class PreparedBattleHeaderVH extends RecyclerView.ViewHolder {
    public final TextView name, create;
    public PreparedEncounters.Battle data;
    public static String createFormat;

    public PreparedBattleHeaderVH(View iv) {
        super(iv);
        name = (TextView) iv.findViewById(R.id.vhPB_desc);
        create = (TextView) iv.findViewById(R.id.vhPB_creationDate);
    }

    public void bindData(@NonNull PreparedEncounters.Battle data) {
        this.data = data;
        name.setText(data.desc);
        Date created = new Date(data.created.seconds * 1000);
        DateFormat bruh = DateFormat.getDateInstance(DateFormat.MEDIUM);
        create.setText(String.format(createFormat, bruh.format(created)));
    }
}

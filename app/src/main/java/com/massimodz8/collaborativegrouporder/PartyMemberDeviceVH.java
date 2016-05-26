package com.massimodz8.collaborativegrouporder;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.protocol.nano.StartData;

/**
 * Created by Massimo on 26/05/2016.
 * Perhaps it would be worth investing in an unique device viewholder.
 */
public class PartyMemberDeviceVH extends RecyclerView.ViewHolder {
    public final TextView name;
    public final ImageView icon;

    public PartyMemberDeviceVH(LayoutInflater li, ViewGroup parent) {
        super(li.inflate(R.layout.vh_party_member_device, parent, false));
        name = (TextView) itemView.findViewById(R.id.vhPMD_name);
        icon = (ImageView) itemView.findViewById(R.id.vhPMD_icon);
    }

    public void bindData(StartData.PartyOwnerData.DeviceInfo dev) {
        name.setText(dev.name);
    }
}

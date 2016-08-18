package com.massimodz8.collaborativegrouporder.master;

import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.PreSeparatorDecorator;
import com.massimodz8.collaborativegrouporder.R;

import java.util.ArrayList;

/**
 * Created by Massimo on 07/03/2016.
 * Lists kicked devices so we can populate a list to restore them or disconnect them.
 */
public abstract class HiddenDeviceManagementDialog {
    public final AlertDialog dialog;

    public HiddenDeviceManagementDialog(final AppCompatActivity ctx, final ArrayList<PartyDefinitionHelper.DeviceStatus> kicked) {
        dialog = new AlertDialog.Builder(ctx, R.style.AppDialogStyle)
                .setIcon(R.drawable.ic_info_white_24dp)
                .show();
        dialog.setContentView(R.layout.dialog_manage_hidden_devices);
        RecyclerView rv = (RecyclerView) dialog.findViewById(R.id.dlgMHD_list);
        new PreSeparatorDecorator(rv, ctx) {
            @Override
            protected boolean isEligible(int position) {
                return position != 0;
            }
        };
        rv.setAdapter(new RecyclerView.Adapter<HiddenDeviceVH>() {
            @Override
            public HiddenDeviceVH onCreateViewHolder(ViewGroup parent, int viewType) {
                return new HiddenDeviceVH(ctx.getLayoutInflater().inflate(R.layout.vh_hidden_device, parent, false));
            }

            @Override
            public void onBindViewHolder(HiddenDeviceVH holder, int position) {
                holder.data = kicked.get(position);
                holder.name.setText(holder.data.name);
            }

            @Override
            public int getItemCount() {
                return kicked.size();
            }
        });
    }

    private class HiddenDeviceVH extends RecyclerView.ViewHolder {
        TextView name;
        PartyDefinitionHelper.DeviceStatus data;

        public HiddenDeviceVH(View view) {
            super(view);
            name = (TextView) view.findViewById(R.id.vhHD_name);
            view.findViewById(R.id.vhHD_disconnect).setOnClickListener(new MyAction(this, true));
            view.findViewById(R.id.vhHD_restore).setOnClickListener(new MyAction(this, false));
        }
    }

    private class MyAction implements View.OnClickListener {
        private HiddenDeviceVH viewHolder;
        private final boolean disconnect;

        public MyAction(HiddenDeviceVH hiddenDeviceVH, boolean disconnect) {
            viewHolder = hiddenDeviceVH;
            this.disconnect = disconnect;
        }

        @Override
        public void onClick(View v) {
            dialog.dismiss();
            if(disconnect) requestDisconnect(viewHolder.data);
            else requestRestore(viewHolder.data);
        }
    }

    protected abstract void requestDisconnect(PartyDefinitionHelper.DeviceStatus data);
    protected abstract void requestRestore(PartyDefinitionHelper.DeviceStatus data);
}

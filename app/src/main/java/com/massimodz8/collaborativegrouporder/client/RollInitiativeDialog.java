package com.massimodz8.collaborativegrouporder.client;

import android.content.DialogInterface;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.NumberPicker;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.AdventuringActorDataVH;
import com.massimodz8.collaborativegrouporder.R;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;

/**
 * Created by Massimo on 28/04/2016.
 * A dialog to request a dice roll. This is the simplest form, which is the point of the whole app.
 */
public class RollInitiativeDialog {
    final Network.Roll request;
    final AlertDialog dlg;

    public RollInitiativeDialog(Network.ActorState actor, final Network.Roll request, final Runnable onEntered, @NonNull final AppCompatActivity activity) {
        this.request = request;
        dlg = new AlertDialog.Builder(activity, R.style.AppDialogStyle)
                .setIcon(R.drawable.ic_info_white_24dp)
                .setView(R.layout.dialog_roll_initiative)
                .setCancelable(false)
                .setPositiveButton(activity.getString(R.string.rid_done), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        request.result = picker.getValue();
                        onEntered.run();
                    }
                })
                .show();
        final AdventuringActorDataVH help = new AdventuringActorDataVH(dlg.findViewById(R.id.vhRoot)) {
            @Override
            public void onClick(View v) {
            } // not interactive
        };
        help.bindData(actor);
        final TextView db = (TextView) dlg.findViewById(R.id.aoa_dlgRI_descAndBonus);
        int modifier = actor.initiativeBonus;
        String ms = String.valueOf(modifier);
        if(modifier >= 0) ms = '+' + ms;
        db.setText(String.format(activity.getString(R.string.rid_instructionsAndModifier), ms));
        picker = (NumberPicker) dlg.findViewById(R.id.aoa_dlgRI_rollResult);
        picker.setMinValue(1);
        picker.setMaxValue(20);
        picker.setValue(10);
    }
    private final NumberPicker picker;
}

package com.massimodz8.collaborativegrouporder;

import android.content.Context;
import android.content.DialogInterface;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.ToggleButton;

import java.util.Locale;

/**
 * Created by Massimo on 27/05/2016.
 * There are a few dialogs around and I want to unify them in both use and appearance.
 * Plus, I want to track them and they're mostly small. Trying a new method.
 */
public abstract class MyDialogsFactory {
    public static void showNetworkDiscoveryTroubleshoot(Context ctx, boolean forming) {
        String title = ctx.getString(forming? R.string.dlgNSDTroubleshoot_h2_title_forming : R.string.dlgNSDTroubleshoot_h2_title_gathering);
        String msg = ctx.getString(forming? R.string.dlgNSDTroubleshoot_h2_msg_forming : R.string.dlgNSDTroubleshoot_h2_msg_gathering);
        final AlertDialog temp = new AlertDialog.Builder(ctx, R.style.AppDialogStyle).setView(R.layout.dialog_nsd_troubleshoot).show();
        ((TextView)temp.findViewById(R.id.dlgNSDTroubleshoot_h2_title)).setText(title);
        ((TextView)temp.findViewById(R.id.dlgNSDTroubleshoot_h2_msg)).setText(msg);
    }

    public interface ActorProposal {
        void onInputCompleted(BuildingPlayingCharacter pc);
    }
    public static AlertDialog showActorDefinitionInput(final Context ctx, final ActorProposal onInputCompleted, @Nullable BuildingPlayingCharacter currently, final int advancementPace) {
        final AlertDialog dlg = new AlertDialog.Builder(ctx, R.style.AppDialogStyle)
                .setTitle(currently == null? R.string.ncaa_genCharTitle : R.string.aea_dlgLevelUp)
                .setView(R.layout.vh_playing_character_definition_input)
                .show();
        final BuildingPlayingCharacter pc = currently == null? new BuildingPlayingCharacter() : currently;
        final PCViewHolder helper = new PCViewHolder(dlg.findViewById(R.id.vhRoot)) {
            @Override
            protected void action() {
                dlg.dismiss();
                pc.status = BuildingPlayingCharacter.STATUS_ACCEPTED;
                onInputCompleted.onInputCompleted(pc);
            }
        };
        pc.status = BuildingPlayingCharacter.STATUS_BUILDING;
        helper.bind(pc, true);
        if(currently != null) {
            dlg.findViewById(R.id.vhPCDI_tilExperience).setVisibility(View.GONE);
            ((Button)helper.itemView.findViewById(R.id.vhPCDI_makeNewChar)).setText(R.string.fra_updateChar);
            dlg.setCancelable(false);
            final TextView view = (TextView) dlg.findViewById(R.id.vhPCDI_newLevel);
            view.setVisibility(View.VISIBLE);
            final int level = MaxUtils.level(ctx.getResources(), advancementPace, pc.experience);
            view.setText(String.format(Locale.getDefault(), ctx.getString(R.string.fra_levelUpDlgSubTitle), level));
        }
        return dlg;
    }
    public interface ApprovalListener {
        void approval(boolean status);
    }
    public static AlertDialog showActorComparison(final Context ctx, final BuildingPlayingCharacter origin, final BuildingPlayingCharacter proposed,
                                                  final ApprovalListener callback) {
        AlertDialog dlg = new AlertDialog.Builder(ctx, R.style.AppDialogStyle)
                .setTitle(R.string.fra_approveLevelUp)
                .setView(R.layout.dialog_leveled_character_compare)
                .setPositiveButton(R.string.fra_approveDlgActionPositive, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        callback.approval(true);
                    }
                })
                .setNegativeButton(R.string.fra_approveDlgActionNegative, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        callback.approval(false);
                    }
                })
                .setCancelable(false)
                .show();
        final PCViewHolder helper = new PCViewHolder(dlg.findViewById(R.id.vhRoot)) {
            @Override
            protected void action() { /* not used */ }
        };
        ToggleButton toggle = (ToggleButton) dlg.findViewById(R.id.dlgLCC_toggle);
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked) helper.bind(proposed, false);
                else helper.bind(origin, false);
                helper.itemView.findViewById(R.id.vhPCDI_tilExperience).setVisibility(View.GONE);
                MaxUtils.setVisibility(View.GONE,
                        helper.itemView.findViewById(R.id.vhPCDI_accepted),
                        helper.itemView.findViewById(R.id.vhPCDI_makeNewChar));
                MaxUtils.setEnabled(false,
                        helper.itemView.findViewById(R.id.vhPCDI_name),
                        helper.itemView.findViewById(R.id.vhPCDI_initiative),
                        helper.itemView.findViewById(R.id.vhPCDI_health));
            }
        });
        toggle.performClick();
        return dlg;
    }
}

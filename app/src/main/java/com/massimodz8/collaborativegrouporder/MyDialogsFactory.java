package com.massimodz8.collaborativegrouporder;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.TextView;

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
    public static AlertDialog showActorDefinitionInput(final Context ctx, final ActorProposal onInputCompleted, @Nullable BuildingPlayingCharacter currently) {
        final AlertDialog dlg = new AlertDialog.Builder(ctx, R.style.AppDialogStyle)
                .setTitle(currently == null? R.string.ncaa_genCharTitle : R.string.aea_dlgLevelUp)
                .setView(R.layout.vh_playing_character_definition_input)
                .show();
        final BuildingPlayingCharacter pc = currently == null? new BuildingPlayingCharacter() : currently;
        final PCViewHolder helper = new PCViewHolder(dlg.findViewById(R.id.vhRoot)) {
            @Override
            protected String getString(@StringRes int resid) {
                return ctx.getString(resid);
            }

            @Override
            protected void action() {
                dlg.dismiss();
                pc.status = BuildingPlayingCharacter.STATUS_ACCEPTED;
                onInputCompleted.onInputCompleted(pc);
            }
        };
        helper.bind(pc);
        if(currently != null) {
            MaxUtils.setEnabled(false,
                    dlg.findViewById(R.id.vhPCDI_tilName),
                    dlg.findViewById(R.id.vhPCDI_tilExperience),
                    dlg.findViewById(R.id.vhPCDI_tilLevel));
            dlg.setCancelable(false);
            final TextView view = (TextView) dlg.findViewById(R.id.vhPCDI_newLevel);
            view.setVisibility(View.VISIBLE);
            view.setText(String.format(Locale.getDefault(), ctx.getString(R.string.fra_levelUpDlgSubTitle), pc.level));
        }
        return dlg;

    }
}

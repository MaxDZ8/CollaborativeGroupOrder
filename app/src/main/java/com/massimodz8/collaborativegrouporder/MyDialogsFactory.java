package com.massimodz8.collaborativegrouporder;

import android.content.Context;
import android.support.annotation.StringRes;
import android.support.v7.app.AlertDialog;

import com.massimodz8.collaborativegrouporder.master.PartyCreationService;

/**
 * Created by Massimo on 27/05/2016.
 * There are a few dialogs around and I want to unify them in both use and appearance.
 * Plus, I want to track them and they're mostly small. Trying a new method.
 */
public abstract class MyDialogsFactory {
    public interface ActorProposal {
        void onInputCompleted(BuildingPlayingCharacter pc);
    }
    public static AlertDialog showActorDefinitionInput(final Context ctx, final ActorProposal onInputCompleted) {
        final AlertDialog dlg = new AlertDialog.Builder(ctx, R.style.AppDialogStyle)
                .setTitle(R.string.ncaa_genCharTitle)
                .setView(R.layout.vh_playing_character_definition_input)
                .show();
        final BuildingPlayingCharacter pc = new BuildingPlayingCharacter();
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
        return dlg;
    }
}

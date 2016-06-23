package com.massimodz8.collaborativegrouporder;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.protocol.nano.MonsterData;

import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Created by Massimo on 15/04/2016.
 * Monster data has to be visualized. The 'master' already has two needs to show them: a 'complete'
 * list of monsters to use when the user requests monster book infos and a more detailed version
 * when the search results presents monsters to spawn in battle.
 * I have written a layout which allows some flexibility to accomodate the various uses, this means
 * the logic to set up the Views is a bit more complicated and useful to have in its own class.
 */
public class MonsterVH extends RecyclerView.ViewHolder {
    final TextView name, otherNames, cr, publisherInfo, extraNotes;
    final TextView exampleCreature, initLabel, init;
    final TextView inBattle;
    final Drawable battlingIcon;
    final Map<MonsterData.Monster, Integer> battleCount;

    public static final int MODE_MINIMAL = 0; // Primary name, CR, alternate names?, publisher?
    public static final int MODE_STANDARD = 1; // MINIMAL, example creature?, initiative, cond initiative?

    public int visMode;
    public Runnable onSpawnableChanged; // called every time the spawn count changes as the monster has been toggled.

    /**
     * @param li Layout inflater used once to generate the itemView.
     * @param parent Pass it as received from Adapter call, will go to li.inflate
     * @param ctx_ Used to resolve string resources only.
     * @param battleCount For each monster entry, >0 for monsters to be added to battle.
     *                    Negative values are used to remember the count to display.
     *                    When provided, the VH is made clickable and an handler is installed to set the entries.
     *                    OFC a monster is not guaranteed to be there and OFC if not there then spawn count is 0.
     */
    public MonsterVH(LayoutInflater li, ViewGroup parent, Context ctx_, @Nullable final Map<MonsterData.Monster, Integer> battleCount) {
        super(li.inflate(R.layout.vh_monster_list_entry, parent, false));
        this.ctx = ctx_;
        name = (TextView) itemView.findViewById(R.id.vhMLE_primaryName);
        otherNames = (TextView) itemView.findViewById(R.id.vhMLE_otherNames);
        cr = (TextView) itemView.findViewById(R.id.vhMLE_cr);
        extraNotes = (TextView) itemView.findViewById(R.id.vhMLE_extraNotes);
        publisherInfo = (TextView) itemView.findViewById(R.id.vhMLE_publisherNotes);
        exampleCreature = (TextView) itemView.findViewById(R.id.vhMLE_exampleCreature);
        init = (TextView) itemView.findViewById(R.id.vhMLE_initiative);
        initLabel = (TextView) itemView.findViewById(R.id.vhMLE_initiativeLabel);
        final TextView temp = (TextView) itemView.findViewById(R.id.vhMLE_inBattle);
        if(battleCount == null) {
            temp.setVisibility(View.GONE);
            this.battleCount = null;
            inBattle = null;
            battlingIcon = null;
        }
        else {
            this.battleCount = battleCount;
            inBattle = temp;
            battlingIcon = inBattle.getCompoundDrawables()[2];
            inBattle.setCompoundDrawables(null, null, null, null);
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(currentBinding == null) return;
                    Integer count = battleCount.get(currentBinding);
                    count = count != null? -count : 1;
                    battleCount.put(currentBinding, count);
                    updatedBattleCount(count);
                }
            });
        }

        if(publisherStrings == null) {
            publisherStrings = new IdentityHashMap<>();
            publisherStrings.put(MonsterData.Monster.MetaData.P_INVALID, ctx.getString(R.string.mVH_incoherentPublisherInvalid));
            publisherStrings.put(MonsterData.Monster.MetaData.P_THIRD_PART_GENERIC, ctx.getString(R.string.mVH_thirdPartyPublisher));
            publisherStrings.put(MonsterData.Monster.MetaData.P_FGG, "(FGG)");
            publisherStrings.put(MonsterData.Monster.MetaData.P_OD, "(OD)");
            publisherStrings.put(MonsterData.Monster.MetaData.P_TO, "(TO)");
            publisherStrings.put(MonsterData.Monster.MetaData.P_JBE, "(JBE)");
            publisherStrings.put(MonsterData.Monster.MetaData.P_CGP, "(CGP)");
            publisherStrings.put(MonsterData.Monster.MetaData.P_SMG, "(SMG)");
            publisherStrings.put(MonsterData.Monster.MetaData.P_KP, "(KP)");

            maturityStrings = new IdentityHashMap<>();
            maturityStrings.put(MonsterData.Monster.MetaData.DA_WYRMLING, ctx.getString(R.string.mobs_maturity_da_wyrmling));
            maturityStrings.put(MonsterData.Monster.MetaData.DA_VERY_YOUNG, ctx.getString(R.string.mobs_maturity_da_veryYoung));
            maturityStrings.put(MonsterData.Monster.MetaData.DA_YOUNG, ctx.getString(R.string.mobs_maturity_da_young));
            maturityStrings.put(MonsterData.Monster.MetaData.DA_JUVENILE, ctx.getString(R.string.mobs_maturity_da_juvenile));
            maturityStrings.put(MonsterData.Monster.MetaData.DA_YOUNG_ADULT, ctx.getString(R.string.mobs_maturity_da_youngAdult));
            maturityStrings.put(MonsterData.Monster.MetaData.DA_ADULT, ctx.getString(R.string.mobs_maturity_da_adult));
            maturityStrings.put(MonsterData.Monster.MetaData.DA_MATURE_ADULT, ctx.getString(R.string.mobs_maturity_da_matureAdult));
            maturityStrings.put(MonsterData.Monster.MetaData.DA_OLD, ctx.getString(R.string.mobs_maturity_da_old));
            maturityStrings.put(MonsterData.Monster.MetaData.DA_VERY_OLD, ctx.getString(R.string.mobs_maturity_da_veryOld));
            maturityStrings.put(MonsterData.Monster.MetaData.DA_ANCIENT, ctx.getString(R.string.mobs_maturity_da_ancient));
            maturityStrings.put(MonsterData.Monster.MetaData.DA_WYRM, ctx.getString(R.string.mobs_maturity_da_wyrm));
            maturityStrings.put(MonsterData.Monster.MetaData.DA_GREAT_WYRM, ctx.getString(R.string.mobs_maturity_da_greatWyrm));
            
            maturityStrings.put(MonsterData.Monster.MetaData.EG_SMALL, ctx.getString(R.string.mobs_maturity_eg_small));
            maturityStrings.put(MonsterData.Monster.MetaData.EG_MEDIUM, ctx.getString(R.string.mobs_maturity_eg_medium));
            maturityStrings.put(MonsterData.Monster.MetaData.EG_LARGE, ctx.getString(R.string.mobs_maturity_eg_large));
            maturityStrings.put(MonsterData.Monster.MetaData.EG_HUGE, ctx.getString(R.string.mobs_maturity_eg_huge));
            maturityStrings.put(MonsterData.Monster.MetaData.EG_GREATER, ctx.getString(R.string.mobs_maturity_eg_greater));
            maturityStrings.put(MonsterData.Monster.MetaData.EG_ELDER, ctx.getString(R.string.mobs_maturity_eg_elder));

            sizeStrings = new IdentityHashMap<>();
            sizeStrings.put(MonsterData.FINE, ctx.getString(R.string.mobs_size_fine));
            sizeStrings.put(MonsterData.DIMINUTIVE, ctx.getString(R.string.mobs_size_diminutive));
            sizeStrings.put(MonsterData.TINY, ctx.getString(R.string.mobs_size_tiny));
            sizeStrings.put(MonsterData.SMALL, ctx.getString(R.string.mobs_size_small));
            sizeStrings.put(MonsterData.MEDIUM, ctx.getString(R.string.mobs_size_medium));
            sizeStrings.put(MonsterData.LARGE, ctx.getString(R.string.mobs_size_large));
            sizeStrings.put(MonsterData.HUGE, ctx.getString(R.string.mobs_size_huge));
            sizeStrings.put(MonsterData.GARGANTUAN, ctx.getString(R.string.mobs_size_gargantuan));
            sizeStrings.put(MonsterData.COLOSSAL, ctx.getString(R.string.mobs_size_colossal));
        }
    }

    public void bindData(String[] names, MonsterData.Monster data) {
        currentBinding = data;
        if(visMode < MODE_STANDARD) MaxUtils.setVisibility(View.GONE, exampleCreature, init, initLabel);
        else {
            MaxUtils.setVisibility(View.VISIBLE, init, initLabel);
            MaxUtils.setTextUnlessNull(exampleCreature, data.header.example.isEmpty()? null : data.header.example, View.GONE);
            init.setText(String.valueOf(data.header.initiative));

        }
        // Minimal
        String main = names[0];
        StringBuilder extraNotes = null;
        for (MonsterData.Monster.Tag tag : data.header.tags) {
            if(tag.type != MonsterData.Monster.TT_EXTRA_METADATA) continue;
            switch(tag.note.type) {
                case MonsterData.Monster.MetaData.MATURITY: {
                    final int mat = tag.note.maturity;
                    final int expansion = mat < MonsterData.Monster.MetaData.EG_SMALL? R.string.mVH_dragonMaturityNameExpansion : R.string.mVH_elementalMaturityNameExpansion;
                    main = String.format(ctx.getString(expansion), main, maturityStrings.get(mat));
                } break;
                case MonsterData.Monster.MetaData.ADDITIONAL_SELECTION_INFO: {
                    if(extraNotes == null) extraNotes = new StringBuilder("(");
                    if(extraNotes.length() > 1) extraNotes.append(", ");
                    extraNotes.append(tag.note.selectionInfo);
                } break;
                case MonsterData.Monster.MetaData.GENERIC_VARIANT: {
                    if(extraNotes == null) extraNotes = new StringBuilder("(");
                    if(extraNotes.length() > 1) extraNotes.append(", ");
                    extraNotes.append(tag.note.selectionInfo);
                } break;
                case MonsterData.Monster.MetaData.VARIANT_MORPH_TARGET: {
                    if(extraNotes == null) extraNotes = new StringBuilder("(");
                    if(extraNotes.length() > 1) extraNotes.append(", ");
                    extraNotes.append(tag.note.selectionInfo);
                } break;
                case MonsterData.Monster.MetaData.VARIANT_SIZE: {
                    if(extraNotes == null) extraNotes = new StringBuilder("(");
                    if(extraNotes.length() > 1) extraNotes.append(", ");
                    extraNotes.append(sizeStrings.get(data.header.size));
                } break;
            }
        }
        name.setText(main);
        String noteString = null;
        if(extraNotes != null && extraNotes.length() > 1) {
            extraNotes.append(')');
            noteString = extraNotes.toString();
        }
        MaxUtils.setTextUnlessNull(this.extraNotes, noteString, View.GONE);
        String concat = "";
        for(int loop = 1; loop < names.length; loop++) {
            if(loop != 1) concat += '\n';
            concat += " aka " + names[loop];
        }
        MaxUtils.setTextUnlessNull(otherNames, concat.length() == 0? null : concat, View.GONE);
        if(data.header.cr.denominator == 1) {
            final String crInt = ctx.getString(R.string.mVH_challangeRatio_integral);
            cr.setText(String.format(Locale.ENGLISH, crInt, data.header.cr.numerator));  // TODO: how are numbers going here?
        }
        else {
            final String crFrac = ctx.getString(R.string.mVH_challangeRatio_fraction);
            cr.setText(String.format(Locale.ENGLISH, crFrac, data.header.cr.numerator, data.header.cr.denominator)); // TODO: how are numbers going here?
        }
        concat = "";
        for (MonsterData.Monster.Tag tag : data.header.tags) {
            if(tag.type != MonsterData.Monster.TT_EXTRA_METADATA) continue;
            if(tag.note.type != MonsterData.Monster.MetaData.PUBLISHER) continue;
            String known = publisherStrings.get(tag.note.publisher);
            if(null == known) known = ctx.getString(R.string.mVH_incoherentPublisherUnknown);
            concat = known;
            break;
        }
        MaxUtils.setTextUnlessNull(publisherInfo, concat.length() == 0? null : concat, View.GONE);
        if(battleCount != null) {
            final Integer integer = battleCount.get(currentBinding);
            updatedBattleCount(integer != null? integer : 0);
        }
    }

    private void updatedBattleCount(int count) {
        if(count > 0) {
            inBattle.setText(String.format(Locale.getDefault(), ctx.getString(R.string.mVH_spawnCountFeedback), count));
            inBattle.setCompoundDrawables(null, null, battlingIcon, null);
        }
        else {
            inBattle.setText(R.string.mVH_tappedFeedback);
            inBattle.setCompoundDrawables(null, null, null, null);
        }
        if(onSpawnableChanged != null) onSpawnableChanged.run();
    }

    public MonsterData.Monster currentBinding;
    private static IdentityHashMap<Integer, String> publisherStrings, maturityStrings, sizeStrings;
    private final Context ctx;
}

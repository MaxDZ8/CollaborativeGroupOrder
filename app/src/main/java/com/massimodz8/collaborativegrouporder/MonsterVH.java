package com.massimodz8.collaborativegrouporder;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
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
    final TextView name, otherNames, cr, publisherInfo;
    final TextView exampleCreature, initLabel, init, conditionalInitiative;
    final CheckBox inBattle;
    final Map<MonsterData.Monster, Integer> battleCount;

    public static final int MODE_MINIMAL = 0; // Primary name, CR, alternate names?, publisher?
    public static final int MODE_STANDARD = 1; // MINIMAL, example creature?, initiative, cond initiative?

    public int visMode;

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
        publisherInfo = (TextView) itemView.findViewById(R.id.vhMLE_publisherNotes);
        exampleCreature = (TextView) itemView.findViewById(R.id.vhMLE_exampleCreature);
        init = (TextView) itemView.findViewById(R.id.vhMLE_initiative);
        conditionalInitiative = (TextView) itemView.findViewById(R.id.vhMLE_conditionalInitiative);
        initLabel = (TextView) itemView.findViewById(R.id.vhMLE_initiativeLabel);
        if(battleCount != null) {
            this.battleCount = battleCount;
            inBattle = (CheckBox) itemView.findViewById(R.id.vhMLE_inBattle);
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(currentBinding == null) return;
                    Integer count = battleCount.get(currentBinding);
                    count = count != null? -count : 1;
                    battleCount.put(currentBinding, count);
                    inBattle.setChecked(!inBattle.isChecked());
                    if(inBattle.isChecked()) inBattle.setText(String.format(ctx.getString(R.string.vhMLE_spawnCountFeedback), String.valueOf(count)));
                    else inBattle.setText(R.string.vhMLE_tappedFeedback);
                }
            });
        }
        else {
            this.battleCount = null;
            inBattle = null;
            itemView.findViewById(R.id.vhMLE_inBattle).setVisibility(View.GONE);
        }

        if(publisherStrings == null) {
            publisherStrings = new IdentityHashMap<>();
            publisherStrings.put(MonsterData.Monster.MetaData.P_INVALID, ctx.getString(R.string.sma_dlg_mbi_incoherentPublisherInvalid));
            publisherStrings.put(MonsterData.Monster.MetaData.P_THIRD_PART_GENERIC, ctx.getString(R.string.sma_dlg_mbi_thirdPartyPublisher));
            publisherStrings.put(MonsterData.Monster.MetaData.P_FGG, "(FGG)");
            publisherStrings.put(MonsterData.Monster.MetaData.P_OD, "(OD)");
            publisherStrings.put(MonsterData.Monster.MetaData.P_TO, "(TO)");
            publisherStrings.put(MonsterData.Monster.MetaData.P_JBE, "(JBE)");
            publisherStrings.put(MonsterData.Monster.MetaData.P_CGP, "(CGP)");
            publisherStrings.put(MonsterData.Monster.MetaData.P_SMG, "(SMG)");
            publisherStrings.put(MonsterData.Monster.MetaData.P_KP, "(KP)");
        }
    }

    public void bindData(String[] names, MonsterData.Monster data) {
        currentBinding = data;
        if(visMode < MODE_STANDARD) MaxUtils.setVisibility(View.GONE, exampleCreature, init, initLabel, conditionalInitiative);
        else {
            MaxUtils.setVisibility(View.VISIBLE, init, initLabel);
            MaxUtils.setTextUnlessNull(exampleCreature, data.header.example.isEmpty()? null : data.header.example, View.GONE);
            MaxUtils.setTextUnlessNull(conditionalInitiative, buildCondInitString(), View.GONE);
            init.setText(String.valueOf(data.header.initiative));

        }
        // Minimal
        name.setText(names[0]);
        String concat = "";
        for(int loop = 1; loop < names.length; loop++) {
            if(loop != 1) concat += '\n';
            concat += " aka " + names[loop];
        }
        MaxUtils.setTextUnlessNull(otherNames, concat.length() == 0? null : concat, View.GONE);
        if(data.header.cr.denominator == 1) {
            final String crInt = ctx.getString(R.string.vhMLE_challangeRatio_integral);
            cr.setText(String.format(Locale.ENGLISH, crInt, data.header.cr.numerator));  // TODO: how are numbers going here?
        }
        else {
            final String crFrac = ctx.getString(R.string.vhMLE_challangeRatio_fraction);
            cr.setText(String.format(Locale.ENGLISH, crFrac, data.header.cr.numerator, data.header.cr.denominator)); // TODO: how are numbers going here?
        }
        concat = "";
        for (MonsterData.Monster.Tag tag : data.header.tags) {
            if(tag.type != MonsterData.Monster.TT_EXTRA_METADATA) continue;
            if(tag.note.type != MonsterData.Monster.MetaData.PUBLISHER) continue;
            String known = publisherStrings.get(tag.note.publisher);
            if(null == known) known = ctx.getString(R.string.sma_dlg_mbi_incoherentPublisherUnknown);
            concat = known;
            break;
        }
        MaxUtils.setTextUnlessNull(publisherInfo, concat.length() == 0? null : concat, View.GONE);
    }

    private String buildCondInitString() {
        StringBuilder result = new StringBuilder();
        for (MonsterData.Monster.Tag tag : currentBinding.header.tags) {
            if(tag.type != MonsterData.Monster.TT_CONDITIONAL_INITIATIVE) continue;
            if(result.length() > 0) result.append('\n');
            switch(tag.ctxInit.when) {
                case MonsterData.Monster.ConditionalInitiative.ACTION_CLIMB: {
                    String expr = ctx.getString(R.string.vhMLE_badActionClimbToken);
                    if(tag.ctxInit.params.length != 1) expr = ctx.getString(R.string.vhMLE_badActionClimbParamArray);
                    else if(tag.ctxInit.params[0] == MonsterData.Monster.ConditionalInitiative.TREE) expr = ctx.getString(R.string.vhMLE_actionClimb_tree);
                    // TODO: this is stupid! Just let this be a presentation string!
                    result.append(String.format(Locale.ENGLISH, ctx.getString(R.string.vhMLE_actionClimbingMessage), tag.ctxInit.init, ctx.getString(R.string.vhMLE_actionClimbing), expr));
                } break;
                default:
                    result.append(ctx.getString(R.string.vhMLE_badConditionalInitiative));
            }
        }
        return result.length() == 0? null : result.toString();
    }

    private MonsterData.Monster currentBinding;
    private static IdentityHashMap<Integer, String> publisherStrings;
    private final Context ctx;
}

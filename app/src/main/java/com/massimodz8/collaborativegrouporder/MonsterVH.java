package com.massimodz8.collaborativegrouporder;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.protocol.nano.MonsterData;

import java.util.IdentityHashMap;
import java.util.Locale;

/**
 * Created by Massimo on 15/04/2016.
 * Monster data has to be visualized. The 'master' already has two needs to show them: a 'complete'
 * list of monsters to use when the user requests monster book infos and a more detailed version
 * when the search results presents monsters to spawn in battle.
 * I have written a layout which allows some flexibility to accomodate the various uses, this means
 * the logic to set up the Views is a bit more complicated and useful to have in its own class.
 */
public class MonsterVH extends RecyclerView.ViewHolder {
    final Context ctx;
    final TextView name, otherNames, cr, publisherInfo;

    public MonsterVH(LayoutInflater li, ViewGroup parent, Context ctx) {
        super(li.inflate(R.layout.vh_monster_list_entry, parent, false));
        this.ctx = ctx;
        name = (TextView) itemView.findViewById(R.id.vhMLE_primaryName);
        otherNames = (TextView) itemView.findViewById(R.id.vhMLE_otherNames);
        cr = (TextView) itemView.findViewById(R.id.vhMLE_cr);
        publisherInfo = (TextView) itemView.findViewById(R.id.vhMLE_publisherNotes);
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

    public void bindData(String[] names, MonsterData.Monster.Header data) {
        name.setText(names[0]);
        String concat = "";
        for(int loop = 1; loop < names.length; loop++) {
            if(loop != 1) concat += '\n';
            concat += " aka " + names[loop];
        }
        MaxUtils.setTextUnlessNull(otherNames, concat.length() == 0? null : concat, View.GONE);
        if(data.cr.denominator == 1) {
            final String crInt = ctx.getString(R.string.vhMLE_challangeRatio_integral);
            cr.setText(String.format(Locale.ENGLISH, crInt, data.cr.numerator));  // TODO: how are numbers going here?
        }
        else {
            final String crFrac = ctx.getString(R.string.vhMLE_challangeRatio_fraction);
            cr.setText(String.format(Locale.ENGLISH, crFrac, data.cr.numerator, data.cr.denominator)); // TODO: how are numbers going here?
        }
        concat = "";
        for (MonsterData.Monster.Tag tag : data.tags) {
            if(tag.type != MonsterData.Monster.TT_EXTRA_METADATA) continue;
            if(tag.note.type != MonsterData.Monster.MetaData.PUBLISHER) continue;
            String known = publisherStrings.get(tag.note.publisher);
            if(null == known) known = ctx.getString(R.string.sma_dlg_mbi_incoherentPublisherUnknown);
            concat = known;
            break;
        }
        MaxUtils.setTextUnlessNull(publisherInfo, concat.length() == 0? null : concat, View.GONE);
    }

    private static IdentityHashMap<Integer, String> publisherStrings;
}

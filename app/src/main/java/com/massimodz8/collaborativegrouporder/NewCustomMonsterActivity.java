package com.massimodz8.collaborativegrouporder;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import com.massimodz8.collaborativegrouporder.master.AwardExperienceActivity;
import com.massimodz8.collaborativegrouporder.protocol.nano.MonsterData;

import java.util.ArrayList;
import java.util.Locale;

import javax.crypto.spec.PBEKeySpec;

public class NewCustomMonsterActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_custom_monster);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        final android.support.v7.app.ActionBar sab = getSupportActionBar();
        if(sab != null) sab.setDisplayHomeAsUpEnabled(true);
        refresh();
    }

    @Override
    protected void onDestroy() {
        if(!isChangingConfigurations()) {
            xpAward = null;
            size = null;
            type = null;
            race = null;
            tags = null;
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.new_custom_monster_activity, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.ncma_save: {
                Snackbar.make(findViewById(R.id.activityRoot), "TODO: validate & save", Snackbar.LENGTH_SHORT).show();
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    public void setCr_callback(View v) {
        new AlertDialog.Builder(NewCustomMonsterActivity.this)
                .setTitle(R.string.ncma_crDlg_title)
                .setItems(crStrings(20), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        xpAward = crByIndex(which);
                        refresh();
                    }
                }).show();
    }

    public void setSize_callback(View v) {
        new AlertDialog.Builder(NewCustomMonsterActivity.this)
            .setTitle(R.string.ncma_sizeDlg_title)
            .setItems(new String[]{
                    getString(R.string.mobs_size_fine),
                    getString(R.string.mobs_size_diminutive),
                    getString(R.string.mobs_size_tiny),
                    getString(R.string.mobs_size_small),
                    getString(R.string.mobs_size_medium),
                    getString(R.string.mobs_size_large),
                    getString(R.string.mobs_size_huge),
                    getString(R.string.mobs_size_gargantuan),
                    getString(R.string.mobs_size_colossal)}, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    final int[] known = {
                            MonsterData.FINE,
                            MonsterData.DIMINUTIVE,
                            MonsterData.TINY,
                            MonsterData.SMALL,
                            MonsterData.MEDIUM,
                            MonsterData.LARGE,
                            MonsterData.HUGE,
                            MonsterData.GARGANTUAN,
                            MonsterData.COLOSSAL
                    };
                    size = known[which];
                    refresh();
                }
            }).show();
    }

    public void setType_callback(View v) {
        new AlertDialog.Builder(NewCustomMonsterActivity.this)
                .setTitle(R.string.ncma_sizeDlg_title)
                .setItems(new String[]{
                        getString(R.string.mobs_type_aberration),
                        getString(R.string.mobs_type_animal),
                        getString(R.string.mobs_type_construct),
                        getString(R.string.mobs_type_dragon),
                        getString(R.string.mobs_type_fey),
                        getString(R.string.mobs_type_humanoid),
                        getString(R.string.mobs_type_magicalBeast),
                        getString(R.string.mobs_type_monstrousHumanoid),
                        getString(R.string.mobs_type_ooze),
                        getString(R.string.mobs_type_outsider),
                        getString(R.string.mobs_type_plant),
                        getString(R.string.mobs_type_undead),
                        getString(R.string.mobs_type_vermin)}, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final int[] known = {
                                MonsterData.ABERRATION,
                                MonsterData.ANIMAL,
                                MonsterData.CONSTRUCT,
                                MonsterData.DRAGON,
                                MonsterData.FEY,
                                MonsterData.HUMANOID,
                                MonsterData.MAGICAL_BEAST,
                                MonsterData.MONSTROUS_HUMANOID,
                                MonsterData.OOZE,
                                MonsterData.OUTSIDER,
                                MonsterData.PLANT,
                                MonsterData.UNDEAD,
                                MonsterData.VERMIN
                        };
                        type = known[which];
                        refresh();
                    }
                }).show();
    }

    public void setRace_callback(View v) {
        new AlertDialog.Builder(NewCustomMonsterActivity.this)
                .setTitle(R.string.ncma_sizeDlg_title)
                .setItems(new String[]{
                        getString(R.string.mobs_race_dwarf),
                        getString(R.string.mobs_race_elf),
                        getString(R.string.mobs_race_gnome),
                        getString(R.string.mobs_race_halfOrc),
                        getString(R.string.mobs_race_halfling),
                        getString(R.string.mobs_race_human),
                        getString(R.string.mobs_race_aasimar),
                        getString(R.string.mobs_race_catfolk),
                        getString(R.string.mobs_race_dhampir),
                        getString(R.string.mobs_race_drow),
                        getString(R.string.mobs_race_fetchling),
                        getString(R.string.mobs_race_goblin),
                        getString(R.string.mobs_race_hobgoblin),
                        getString(R.string.mobs_race_ifrit),
                        getString(R.string.mobs_race_kobold),
                        getString(R.string.mobs_race_orc),
                        getString(R.string.mobs_race_oread),
                        getString(R.string.mobs_race_ratfolk),
                        getString(R.string.mobs_race_sylph),
                        getString(R.string.mobs_race_tengu),
                        getString(R.string.mobs_race_tiefling),
                        getString(R.string.mobs_race_undine),
                        getString(R.string.mobs_race_gnoll),
                        getString(R.string.mobs_race_lizardfolk),
                        getString(R.string.mobs_race_monkeyGoblin),
                        getString(R.string.mobs_race_skinWalker),
                        getString(R.string.mobs_race_triaxian),
                        getString(R.string.mobs_race_android),
                        getString(R.string.mobs_race_gathlain),
                        getString(R.string.mobs_race_ghoran),
                        getString(R.string.mobs_race_kasatha),
                        getString(R.string.mobs_race_lashunta),
                        getString(R.string.mobs_race_shabti),
                        getString(R.string.mobs_race_syrinx),
                        getString(R.string.mobs_race_wyrwood),
                        getString(R.string.mobs_race_wyvaran),
                        getString(R.string.mobs_race_centaur),
                        getString(R.string.mobs_race_ogre),
                        getString(R.string.mobs_race_shobhad),
                        getString(R.string.mobs_race_trox),
                        getString(R.string.mobs_race_drider),
                        getString(R.string.mobs_race_gargoyle),
                        getString(R.string.mobs_race_changeling),
                        getString(R.string.mobs_race_duergar),
                        getString(R.string.mobs_race_gillmen),
                        getString(R.string.mobs_race_grippli),
                        getString(R.string.mobs_race_kitsune),
                        getString(R.string.mobs_race_merfolk),
                        getString(R.string.mobs_race_nagaji),
                        getString(R.string.mobs_race_samsaran),
                        getString(R.string.mobs_race_strix),
                        getString(R.string.mobs_race_suli),
                        getString(R.string.mobs_race_svirfneblin),
                        getString(R.string.mobs_race_vanara),
                        getString(R.string.mobs_race_vishkanya),
                        getString(R.string.mobs_race_wayang),
                        getString(R.string.mobs_race_aquaticElf),
                        getString(R.string.mobs_race_astmoi),
                        getString(R.string.mobs_race_caligni),
                        getString(R.string.mobs_race_deepOneHybrid),
                        getString(R.string.mobs_race_ganzi),
                        getString(R.string.mobs_race_kuru),
                        getString(R.string.mobs_race_manavri),
                        getString(R.string.mobs_race_orangPendak),
                        getString(R.string.mobs_race_reptoid)}, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final int[] known = {
                                MonsterData.DWARF,
                                MonsterData.ELF,
                                MonsterData.GNOME,
                                MonsterData.HALF_ORC,
                                MonsterData.HALFLING,
                                MonsterData.HUMAN,
                                // Featured
                                MonsterData.AASIMAR,
                                MonsterData.CATFOLK,
                                MonsterData.DHAMPIR,
                                MonsterData.DROW,
                                MonsterData.FETCHLING,
                                MonsterData.GOBLIN,
                                MonsterData.HOBGOBLIN,
                                MonsterData.IFRIT,
                                MonsterData.KOBOLD,
                                MonsterData.ORC,
                                MonsterData.OREAD,
                                MonsterData.RATFOLK,
                                MonsterData.SYLPH,
                                MonsterData.TENGU,
                                MonsterData.TIEFLING,
                                MonsterData.UNDINE,
                                // Standard
                                MonsterData.GNOLL,
                                MonsterData.LIZARDFOLK,
                                MonsterData.MONKEY_GOBLIN,
                                MonsterData.SKINWALKER,
                                MonsterData.TRIAXIAN,
                                // Advanced
                                MonsterData.ANDROID,
                                MonsterData.GATHLAIN,
                                MonsterData.GHORAN,
                                MonsterData.KASATHA,
                                MonsterData.LASHUNTA,
                                MonsterData.SHABTI,
                                MonsterData.SYRINX,
                                MonsterData.WYRWOOD,
                                MonsterData.WYVARAN,
                                // Monstrous
                                MonsterData.CENTAUR,
                                MonsterData.OGRE,
                                MonsterData.SHOBHAD,
                                MonsterData.TROX,
                                // Very powerful
                                MonsterData.DRIDER,
                                MonsterData.GARGOYLE,
                                // uncommon
                                MonsterData.CHANGELING,
                                MonsterData.DUERGAR,
                                MonsterData.GILLMEN,
                                MonsterData.GRIPPLI,
                                MonsterData.KITSUNE,
                                MonsterData.MERFOLK,
                                MonsterData.NAGAJI,
                                MonsterData.SAMSARAN,
                                MonsterData.STRIX,
                                MonsterData.SULI,
                                MonsterData.SVIRFNEBLIN,
                                MonsterData.VANARA,
                                MonsterData.VISHKANYA,
                                MonsterData.WAYANG,
                                // unknown race points
                                MonsterData.AQUATIC_ELF,
                                MonsterData.ASTMOI,
                                MonsterData.CALIGNI,
                                MonsterData.DEEP_ONE_HYBRID,
                                MonsterData.GANZI,
                                MonsterData.KURU,
                                MonsterData.MANAVRI,
                                MonsterData.ORANG__PENDAK,
                                MonsterData.REPTOID
                        };
                        race = known[which];
                        refresh();
                    }
                }).show();
    }

    private static String[] crStrings(int lastCR) {
        final String[] res = new String[lastCR + 5];
        res[0] = crString(1, 8);
        res[1] = crString(1, 6);
        res[2] = crString(1, 4);
        res[3] = crString(1, 3);
        res[4] = crString(1, 2);
        for(int init = 0; init < lastCR; init++) res[init + 5] = crString(init + 1, 1);
        return res;
    }

    private static MonsterData.Monster.ChallangeRatio crByIndex(int slot) {
        MonsterData.Monster.ChallangeRatio res = new MonsterData.Monster.ChallangeRatio();
        switch(slot) {
            case 0: res.denominator = 8; break;
            case 1: res.denominator = 6; break;
            case 2: res.denominator = 4; break;
            case 3: res.denominator = 3; break;
            case 4: res.denominator = 2; break;
            default: res.denominator = 1;
        }
        res.numerator = slot < 5? 1 : (slot - 4);
        return res;
    }

    private static String crString(int numerator, int denominator) {
        String cr;
        if(denominator != 1) cr = String.format(Locale.getDefault(), "%1$d/%2$d", numerator, denominator);
        else cr = String.valueOf(numerator);
        return String.format(Locale.getDefault(), "%1$s (%2$d xp)", cr, AwardExperienceActivity.xpFrom(numerator, denominator));
    }

    private void refresh() {
        MaxUtils.beginDelayedTransition(this);
        Button btn = (Button) findViewById(R.id.ncma_cr);
        if(xpAward == null) btn.setText(R.string.ncma_crBtn_notSet);
        else btn.setText(String.format(getString(R.string.ncma_crBtnSet), crString(xpAward.numerator, xpAward.denominator)));
        btn = (Button) findViewById(R.id.ncma_size);
        if(size == null) btn.setText(R.string.ncma_sizeBtn_notSet);
        else btn.setText(ProtoBufSupport.monsterSizeToString(size, this));
        btn = (Button) findViewById(R.id.ncma_type);
        if(type == null) btn.setText(R.string.ncma_typeBtn_notSet);
        else btn.setText(ProtoBufSupport.monsterTypeToString(type, this));
        btn = (Button) findViewById(R.id.ncma_race);
        if(race == null) btn.setText(R.string.ncma_race_notSet);
        else btn.setText(ProtoBufSupport.monsterRaceToString(race, this));
    }

    private static MonsterData.Monster.ChallangeRatio xpAward;
    private static Integer size, type, race;
    private static ArrayList<Integer> tags;
}

package com.massimodz8.collaborativegrouporder;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.StringRes;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.massimodz8.collaborativegrouporder.master.AwardExperienceActivity;
import com.massimodz8.collaborativegrouporder.protocol.nano.MonsterData;
import com.massimodz8.collaborativegrouporder.protocol.nano.UserOf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

public class NewCustomMonsterActivity extends AppCompatActivity {
    private @UserOf MonsterData.MonsterBook cmobs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_custom_monster);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        final android.support.v7.app.ActionBar sab = getSupportActionBar();
        if (sab != null) sab.setDisplayHomeAsUpEnabled(true);
        cmobs = RunningServiceHandles.getInstance().state.data.customMonsters;
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onBackPressed() {
        if(saving == null) {
            reset();
            super.onBackPressed();
        }
    }

    @Override
    public boolean onSupportNavigateUp() { // takes a second anyway.
        if(saving == null) {
            reset();
        }
        return saving == null && super.onSupportNavigateUp();
    }

    private void reset() {
        xpAward = null;
        size = null;
        type = null;
        race = null;
        if(alignFlags != null) Arrays.fill(alignFlags, false);
        if(tagFlags != null) Arrays.fill(tagFlags, false);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.new_custom_monster_activity, menu);
        saveAction = menu.findItem(R.id.ncma_save);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.ncma_save: {
                // First I validate the data.
                final View root = findViewById(R.id.activityRoot);
                TextInputLayout til = (TextInputLayout)findViewById(R.id.ncma_tilName);
                View view = findViewById(R.id.ncma_name);
                final String name = ((EditText) view).getText().toString();
                if(name.length() == 0) {
                    til.setError(getString(R.string.ncma_emptyName));
                    view.requestFocus();
                    return true;
                }
                til.setError(null);
                if(xpAward == null) {
                    findViewById(R.id.ncma_cr).performClick();
                    return true;
                }
                if(size == null) {
                    findViewById(R.id.ncma_size).performClick();
                    return true;
                }
                if(type == null) {
                    findViewById(R.id.ncma_type).performClick();
                    return true;
                }
                til = (TextInputLayout)findViewById(R.id.ncma_tilInit);
                view = findViewById(R.id.ncma_init);
                final String init = ((EditText) view).getText().toString();
                if(init.length() == 0) {
                    til.setError(getString(R.string.ncma_emptyInitiative));
                    view.requestFocus();
                    return true;
                }
                int modifier;
                try {
                    modifier = Integer.parseInt(init);
                } catch(NumberFormatException e) { // input method prevents this but w/e
                    til.setError(getString(R.string.ncma_badInitiative));
                    view.requestFocus();
                    return true;
                }
                til.setError(null);
                // Alignment... uff. This is a bit harder.
                boolean valid = false;
                int alignCount = 0;
                int scan = 0;
                if(alignFlags != null) {
                    for (boolean yes : alignFlags) {
                        if (yes) {
                            if (alignEnum[scan] != MonsterData.ALIGNMENT_RESTRICTED) valid = true;
                            alignCount++;
                        }
                        scan++;
                    }
                }
                if(!valid) {
                    Snackbar.make(root, R.string.ncma_badAlignment, Snackbar.LENGTH_LONG).show();
                    return true;
                }
                // Race and tags are optional so we don't really care about them. Starting to build
                // the new monster...
                final MonsterData.Monster mob = new MonsterData.Monster();
                mob.header = new MonsterData.Monster.Header();
                mob.header.name = new String[] { name };
                mob.header.cr = xpAward;
                mob.header.alignment = new int[alignCount];
                scan = alignCount = 0;
                for (boolean yes : alignFlags) {
                    if(yes) {
                        mob.header.alignment[alignCount++] = alignEnum[scan];
                    }
                    scan++;
                }
                mob.header.size = size;
                mob.header.type = type;
                mob.header.initiative = modifier;
                ArrayList<MonsterData.Monster.Tag> easy = new ArrayList<>();
                if(race != null) {
                    MonsterData.Monster.Tag tag = new MonsterData.Monster.Tag();
                    tag.type = MonsterData.Monster.TT_RACE;
                    tag.race = race;
                    easy.add(tag);
                }
                if(tagFlags != null) {
                    scan = -1;
                    for (int value : tagEnum) {
                        scan++;
                        if(!tagFlags[scan]) continue;
                        MonsterData.Monster.Tag tag = new MonsterData.Monster.Tag();
                        tag.type = MonsterData.Monster.TT_SUBTYPE;
                        tag.subtype = value;
                        easy.add(tag);
                    }
                }
                mob.header.tags = new MonsterData.Monster.Tag[easy.size()];
                scan = 0;
                for (MonsterData.Monster.Tag tag :easy) mob.header.tags[scan++] = tag;
                // Our new mob is ready. Kinda. Now let's add it to the book and start a save!
                MonsterData.MonsterBook.Entry parent = new MonsterData.MonsterBook.Entry();
                parent.main = mob;
                final MonsterData.MonsterBook.Entry[] longer = Arrays.copyOf(cmobs.entries, cmobs.entries.length + 1);
                longer[cmobs.entries.length] = parent;
                cmobs.entries = longer;
                saveAction.setEnabled(false);
                saving = new AsyncRenamingStore<MonsterData.MonsterBook>(getFilesDir(), PersistentDataUtils.USER_CUSTOM_DATA_SUBDIR, PersistentDataUtils.CUSTOM_MOBS_FILE_NAME, cmobs) {
                    @Override
                    protected String getString(@StringRes int res) {
                        return NewCustomMonsterActivity.this.getString(res);
                    }

                    @Override
                    protected void onPostExecute(Exception e) {
                        reset();
                        final SpawnHelper search = RunningServiceHandles.getInstance().search;
                        if(null != search) search.shutdown();
                        RunningServiceHandles.getInstance().search = null;
                        setResult(RESULT_OK);
                        finish();
                    }
                };
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    public void setCr_callback(View v) {
        xpAward = null;
        refresh();
        new AlertDialog.Builder(NewCustomMonsterActivity.this, R.style.AppDialogStyle)
                .setTitle(R.string.ncma_crDlg_title)
                .setSingleChoiceItems(crStrings(20), -1, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        xpAward = crByIndex(which);
                        dialog.dismiss();
                        refresh();
                    }
                }).show();
    }

    public void setSize_callback(View v) {
        final CharSequence[] ss = new CharSequence[sizeEnum.length];
        int init = 0;
        for(int el : sizeEnum) ss[init++] = ProtobufSupport.monsterSizeToString(el, this);
        size = null;
        refresh();
        new AlertDialog.Builder(NewCustomMonsterActivity.this, R.style.AppDialogStyle)
            .setTitle(R.string.ncma_sizeDlg_title)
            .setSingleChoiceItems(ss, -1, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    size = sizeEnum[which];
                    dialog.dismiss();
                    refresh();
                }
            }).show();
    }

    public void setType_callback(View v) {
        final CharSequence[] ts = new CharSequence[typeEnum.length];
        int init = 0;
        for(int el : typeEnum) ts[init++] = ProtobufSupport.monsterTypeToString(el, true, this);
        type = null;
        refresh();
        new AlertDialog.Builder(NewCustomMonsterActivity.this, R.style.AppDialogStyle)
                .setTitle(R.string.ncma_sizeDlg_title)
                .setSingleChoiceItems(ts, -1, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        type = typeEnum[which];
                        dialog.dismiss();
                        refresh();
                    }
                }).show();
    }

    public void setRace_callback(View v) {
        final CharSequence[] rs = new CharSequence[raceEnum.length];
        int init = 0;
        for (int el : raceEnum) rs[init++] = ProtobufSupport.monsterRaceToString(el, this);
        race = null;
        refresh();
        new AlertDialog.Builder(NewCustomMonsterActivity.this, R.style.AppDialogStyle)
                .setTitle(R.string.ncma_sizeDlg_title)
                .setSingleChoiceItems(rs, -1, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        race = raceEnum[which];
                        dialog.dismiss();
                        refresh();
                    }
                }).show();
    }

    public void setAlignment_callback(View v) {
        if(alignFlags == null) {
            alignFlags = new boolean[alignEnum.length];
        }
        final CharSequence[] alignStrings = new CharSequence[alignEnum.length];
        int init = 0;
        for (int el : alignEnum) alignStrings[init++] = ProtobufSupport.monsterAlignmentToString(el, false, this);
        new AlertDialog.Builder(this, R.style.AppDialogStyle)
                .setTitle(R.string.ncma_alignDlg_title)
                .setMultiChoiceItems(alignStrings, alignFlags, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        alignFlags[which] = isChecked;
                    }
                    }).setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            refresh();
                        }
                    }).show();
    }

    public void setAdditionalFlags_callback(View v) {
        if(tagFlags == null) {
            tagFlags = new boolean[tagEnum.length];
        }
        final CharSequence[] tagStrings = new CharSequence[tagEnum.length];
        int init = 0;
        for(int el : tagEnum) tagStrings[init++] = ProtobufSupport.monsterTypeToString(el, false, this);
        new AlertDialog.Builder(this, R.style.AppDialogStyle)
                .setTitle(R.string.ncma_tagDlg_title)
                .setMultiChoiceItems(tagStrings, tagFlags, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        tagFlags[which] = isChecked;
                    }
                }).setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
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
        else btn.setText(ProtobufSupport.monsterSizeToString(size, this));
        btn = (Button) findViewById(R.id.ncma_type);
        if(type == null) btn.setText(R.string.ncma_typeBtn_notSet);
        else btn.setText(ProtobufSupport.monsterTypeToString(type, true, this));
        btn = (Button) findViewById(R.id.ncma_race);
        if(race == null) btn.setText(R.string.ncma_race_notSet);
        else btn.setText(ProtobufSupport.monsterRaceToString(race, this));

        btn = (Button) findViewById(R.id.ncma_alignment);
        int count = 0;
        if(alignFlags != null) {
            for (boolean el : alignFlags) count += el? 1 : 0;
        }
        if(count == 0) btn.setText(R.string.ncma_alignBtn_notSet);
        else {
            int restrict = 0;
            for (int v : alignEnum) {
                if(v == MonsterData.ALIGNMENT_RESTRICTED) break;
                restrict++;
            }
            String build = "";
            String interleave = "";
            if(alignFlags[restrict]) {
                build = ProtobufSupport.monsterAlignmentToString(MonsterData.ALIGNMENT_RESTRICTED, false, this);
                interleave = " ";
            }
            for(int check = 0; check < alignFlags.length; check++) {
                if(alignEnum[check] == MonsterData.ALIGNMENT_RESTRICTED) continue;
                if(alignFlags[check]) {
                    build += interleave;
                    build += ProtobufSupport.monsterAlignmentToString(alignEnum[check], true, this);
                    interleave = ", ";
                }
            }
            btn.setText(build);
        }

        btn = (Button) findViewById(R.id.ncma_addTagButton);
        count = 0;
        if(tagFlags != null) {
            for (boolean el : tagFlags) count += el? 1 : 0;
        }
        if(count == 0) btn.setText(R.string.ncma_tagBtn_notSet);
        else {
            String build = "";
            String interleave = "";
            for(int check = 0; check < tagFlags.length; check++) {
                if(tagFlags[check]) {
                    build += interleave;
                    build += ProtobufSupport.monsterTypeToString(tagEnum[check], false, this);
                    interleave = ", ";
                }
            }
            btn.setText(build);
        }
    }

    private static MonsterData.Monster.ChallangeRatio xpAward;
    private static Integer size, type, race;

    private final static int[] alignEnum = {
            MonsterData.LEGAL_GOOD,
            MonsterData.LEGAL_NEUTRAL,
            MonsterData.LEGAL_EVIL,
            MonsterData.NEUTRAL_GOOD,
            MonsterData.JUST_NEUTRAL,
            MonsterData.NEUTRAL_EVIL,
            MonsterData.CHAOTIC_GOOD,
            MonsterData.CHAOTIC_NEUTRAL,
            MonsterData.CHAOTIC_EVIL,
            MonsterData.ALIGNMENT_RESTRICTED,
            MonsterData.ALIGNMENT_AS_CREATOR
    };
    private static boolean[] alignFlags, tagFlags;

    private final static int[] raceEnum = {
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
    private static final int[] sizeEnum = {
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
    private static final int[] typeEnum = {
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
    private static final int[] tagEnum = {
            MonsterData.SUB_ACID,             MonsterData.SUB_ADLET,
            MonsterData.SUB_AEON,             MonsterData.SUB_AGATHION,
            MonsterData.SUB_AIR,              MonsterData.SUB_AMPHIBIOUS,
            MonsterData.SUB_ANGEL,            MonsterData.SUB_AQUATIC,
            MonsterData.SUB_ARCHON,           MonsterData.SUB_ASURA,
            MonsterData.SUB_AZATA,            MonsterData.SUB_BEHEMOTH,
            MonsterData.SUB_BOGGARD,          MonsterData.SUB_CATFOLK,
            MonsterData.SUB_CHAOTIC,          MonsterData.SUB_CHARAU__KA,
            MonsterData.SUB_CLOCKWORK,        MonsterData.SUB_COLD,
            MonsterData.SUB_COLOSSUS,         MonsterData.SUB_DAEMON,
            MonsterData.SUB_DARK_FOLK,        MonsterData.SUB_DEEP_ONE,
            MonsterData.SUB_DEMODAND,         MonsterData.SUB_DEMON,
            MonsterData.SUB_DERRO,            MonsterData.SUB_DEVIL,
            MonsterData.SUB_DIV,              MonsterData.SUB_DWARF,
            MonsterData.SUB_EARTH,            MonsterData.SUB_ELECTRICITY,
            MonsterData.SUB_ELEMENTAL,        MonsterData.SUB_ELF,
            MonsterData.SUB_EVIL,             MonsterData.SUB_EXTRAPLANAR,
            MonsterData.SUB_FEYBLOOD,         MonsterData.SUB_FIRE,
            MonsterData.SUB_GIANT,            MonsterData.SUB_GNOLL,
            MonsterData.SUB_GNOME,            MonsterData.SUB_GOBLIN,
            MonsterData.SUB_GOBLINOID,        MonsterData.SUB_GODSPAWN,
            MonsterData.SUB_GOOD,             MonsterData.SUB_GRAVITY,
            MonsterData.SUB_GREAT_OLD_ONE,    MonsterData.SUB_HALFLING,
            MonsterData.SUB_HERALD,           MonsterData.SUB_HORDE,
            MonsterData.SUB_HUMAN,            MonsterData.SUB_HUMANOID,
            MonsterData.SUB_INCORPOREAL,      MonsterData.SUB_INEVITABLE,
            MonsterData.SUB_KAIJU,            MonsterData.SUB_KAMI,
            MonsterData.SUB_KASATHA,          MonsterData.SUB_KITSUNE,
            MonsterData.SUB_KUAH__LIJ,        MonsterData.SUB_KYTON,
            MonsterData.SUB_LAWFUL,           MonsterData.SUB_LESHY,
            MonsterData.SUB_MYTHIC,           MonsterData.SUB_NATIVE,
            MonsterData.SUB_NIGHTSHADE,       MonsterData.SUB_OGREN,
            MonsterData.SUB_OGRILLON,         MonsterData.SUB_ONI,
            MonsterData.SUB_ORC,              MonsterData.SUB_PROTEAN,
            MonsterData.SUB_PSYCHOPOMP,       MonsterData.SUB_QLIPPOTH,
            MonsterData.SUB_RAKSHASA,         MonsterData.SUB_RATFOLK,
            MonsterData.SUB_REPTILIAN,        MonsterData.SUB_ROBOT,
            MonsterData.SUB_SAMSARAN,         MonsterData.SUB_SASQUATCH,
            MonsterData.SUB_SHAPECHANGER,     MonsterData.SUB_SKULK,
            MonsterData.SUB_STORMWARDEN,      MonsterData.SUB_SWARM,
            MonsterData.SUB_TABAXI,           MonsterData.SUB_TENGU,
            MonsterData.SUB_TIME,             MonsterData.SUB_TROOP,
            MonsterData.SUB_UDAEUS,           MonsterData.SUB_UNBREATHING,
            MonsterData.SUB_VANARA,           MonsterData.SUB_VAPOR,
            MonsterData.SUB_VISHKANYA,        MonsterData.SUB_WATER,
            MonsterData.SUB_WAYANG,           MonsterData.SUB_FUNGUS,
            MonsterData.SUB_PSIONIC
    };
    private AsyncRenamingStore<MonsterData.MonsterBook> saving;
    private MenuItem saveAction;
}

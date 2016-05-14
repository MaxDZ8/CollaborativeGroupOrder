package com.massimodz8.collaborativegrouporder;

import android.content.Context;

import com.massimodz8.collaborativegrouporder.protocol.nano.MonsterData;

import java.util.HashMap;

/**
 * Created by Massimo on 14/05/2016.
 * Because mapping protobuf enums to strings needs singletons for good!
 */
public abstract class ProtoBufSupport {
    public static String monsterRaceToString(int protbufEnum, Context ctx) {
        if(monRace == null) {
            monRace = new HashMap<>();
            monRace.put(MonsterData.INVALID_RACE, ctx.getString(R.string.mobs_race_invalid));
            monRace.put(MonsterData.DWARF, ctx.getString(R.string.mobs_race_dwarf));
            monRace.put(MonsterData.ELF, ctx.getString(R.string.mobs_race_elf));
            monRace.put(MonsterData.GNOME, ctx.getString(R.string.mobs_race_gnome));
            monRace.put(MonsterData.HALF_ORC, ctx.getString(R.string.mobs_race_halfOrc));
            monRace.put(MonsterData.HALFLING, ctx.getString(R.string.mobs_race_halfling));
            monRace.put(MonsterData.HUMAN, ctx.getString(R.string.mobs_race_human));
            monRace.put(MonsterData.AASIMAR, ctx.getString(R.string.mobs_race_aasimar));
            monRace.put(MonsterData.CATFOLK, ctx.getString(R.string.mobs_race_catfolk));
            monRace.put(MonsterData.DHAMPIR, ctx.getString(R.string.mobs_race_dhampir));
            monRace.put(MonsterData.DROW, ctx.getString(R.string.mobs_race_drow));
            monRace.put(MonsterData.FETCHLING, ctx.getString(R.string.mobs_race_fetchling));
            monRace.put(MonsterData.GOBLIN, ctx.getString(R.string.mobs_race_goblin));
            monRace.put(MonsterData.HOBGOBLIN, ctx.getString(R.string.mobs_race_hobgoblin));
            monRace.put(MonsterData.IFRIT, ctx.getString(R.string.mobs_race_ifrit));
            monRace.put(MonsterData.KOBOLD, ctx.getString(R.string.mobs_race_kobold));
            monRace.put(MonsterData.ORC, ctx.getString(R.string.mobs_race_orc));
            monRace.put(MonsterData.OREAD, ctx.getString(R.string.mobs_race_oread));
            monRace.put(MonsterData.RATFOLK, ctx.getString(R.string.mobs_race_ratfolk));
            monRace.put(MonsterData.SYLPH, ctx.getString(R.string.mobs_race_sylph));
            monRace.put(MonsterData.TENGU, ctx.getString(R.string.mobs_race_tengu));
            monRace.put(MonsterData.TIEFLING, ctx.getString(R.string.mobs_race_tiefling));
            monRace.put(MonsterData.UNDINE, ctx.getString(R.string.mobs_race_undine));
            monRace.put(MonsterData.GNOLL, ctx.getString(R.string.mobs_race_gnoll));
            monRace.put(MonsterData.LIZARDFOLK, ctx.getString(R.string.mobs_race_lizardfolk));
            monRace.put(MonsterData.MONKEY_GOBLIN, ctx.getString(R.string.mobs_race_monkeyGoblin));
            monRace.put(MonsterData.SKINWALKER, ctx.getString(R.string.mobs_race_skinWalker));
            monRace.put(MonsterData.TRIAXIAN, ctx.getString(R.string.mobs_race_triaxian));
            monRace.put(MonsterData.ANDROID, ctx.getString(R.string.mobs_race_android));
            monRace.put(MonsterData.GATHLAIN, ctx.getString(R.string.mobs_race_gathlain));
            monRace.put(MonsterData.GHORAN, ctx.getString(R.string.mobs_race_ghoran));
            monRace.put(MonsterData.KASATHA, ctx.getString(R.string.mobs_race_kasatha));
            monRace.put(MonsterData.LASHUNTA, ctx.getString(R.string.mobs_race_lashunta));
            monRace.put(MonsterData.SHABTI, ctx.getString(R.string.mobs_race_shabti));
            monRace.put(MonsterData.SYRINX, ctx.getString(R.string.mobs_race_syrinx));
            monRace.put(MonsterData.WYRWOOD, ctx.getString(R.string.mobs_race_wyrwood));
            monRace.put(MonsterData.WYVARAN, ctx.getString(R.string.mobs_race_wyvaran));
            monRace.put(MonsterData.CENTAUR, ctx.getString(R.string.mobs_race_centaur));
            monRace.put(MonsterData.OGRE, ctx.getString(R.string.mobs_race_ogre));
            monRace.put(MonsterData.SHOBHAD, ctx.getString(R.string.mobs_race_shobhad));
            monRace.put(MonsterData.TROX, ctx.getString(R.string.mobs_race_trox));
            monRace.put(MonsterData.DRIDER, ctx.getString(R.string.mobs_race_drider));
            monRace.put(MonsterData.GARGOYLE, ctx.getString(R.string.mobs_race_gargoyle));
            monRace.put(MonsterData.CHANGELING, ctx.getString(R.string.mobs_race_changeling));
            monRace.put(MonsterData.DUERGAR, ctx.getString(R.string.mobs_race_duergar));
            monRace.put(MonsterData.GILLMEN, ctx.getString(R.string.mobs_race_gillmen));
            monRace.put(MonsterData.GRIPPLI, ctx.getString(R.string.mobs_race_grippli));
            monRace.put(MonsterData.KITSUNE, ctx.getString(R.string.mobs_race_kitsune));
            monRace.put(MonsterData.MERFOLK, ctx.getString(R.string.mobs_race_merfolk));
            monRace.put(MonsterData.NAGAJI, ctx.getString(R.string.mobs_race_nagaji));
            monRace.put(MonsterData.SAMSARAN, ctx.getString(R.string.mobs_race_samsaran));
            monRace.put(MonsterData.STRIX, ctx.getString(R.string.mobs_race_strix));
            monRace.put(MonsterData.SULI, ctx.getString(R.string.mobs_race_suli));
            monRace.put(MonsterData.SVIRFNEBLIN, ctx.getString(R.string.mobs_race_svirfneblin));
            monRace.put(MonsterData.VANARA, ctx.getString(R.string.mobs_race_vanara));
            monRace.put(MonsterData.VISHKANYA, ctx.getString(R.string.mobs_race_vishkanya));
            monRace.put(MonsterData.WAYANG, ctx.getString(R.string.mobs_race_wayang));
            monRace.put(MonsterData.AQUATIC_ELF, ctx.getString(R.string.mobs_race_aquaticElf));
            monRace.put(MonsterData.ASTMOI, ctx.getString(R.string.mobs_race_astmoi));
            monRace.put(MonsterData.CALIGNI, ctx.getString(R.string.mobs_race_caligni));
            monRace.put(MonsterData.DEEP_ONE_HYBRID, ctx.getString(R.string.mobs_race_deepOneHybrid));
            monRace.put(MonsterData.GANZI, ctx.getString(R.string.mobs_race_ganzi));
            monRace.put(MonsterData.KURU, ctx.getString(R.string.mobs_race_kuru));
            monRace.put(MonsterData.MANAVRI, ctx.getString(R.string.mobs_race_manavri));
            monRace.put(MonsterData.ORANG__PENDAK, ctx.getString(R.string.mobs_race_orangPendak));
            monRace.put(MonsterData.REPTOID, ctx.getString(R.string.mobs_race_reptoid));
        }
        final String match = monRace.get(protbufEnum);
        return match == null? monRace.get(MonsterData.INVALID_RACE) : match;
    }
    
    public static String monsterSizeToString(int protbufEnum, Context ctx) {
        if(monSize == null) {
            monSize = new HashMap<>();
            monSize.put(MonsterData.INVALID_MONSTER_SIZE, ctx.getString(R.string.mobs_size_invalid));
            monSize.put(MonsterData.FINE, ctx.getString(R.string.mobs_size_fine));
            monSize.put(MonsterData.DIMINUTIVE, ctx.getString(R.string.mobs_size_diminutive));
            monSize.put(MonsterData.TINY, ctx.getString(R.string.mobs_size_tiny));
            monSize.put(MonsterData.SMALL, ctx.getString(R.string.mobs_size_small));
            monSize.put(MonsterData.MEDIUM, ctx.getString(R.string.mobs_size_medium));
            monSize.put(MonsterData.LARGE, ctx.getString(R.string.mobs_size_large));
            monSize.put(MonsterData.HUGE, ctx.getString(R.string.mobs_size_huge));
            monSize.put(MonsterData.GARGANTUAN, ctx.getString(R.string.mobs_size_gargantuan));
            monSize.put(MonsterData.COLOSSAL, ctx.getString(R.string.mobs_size_colossal));
            
        }
        final String match = monSize.get(protbufEnum);
        return match == null? monSize.get(MonsterData.INVALID_MONSTER_SIZE) : match;
    }

    public static String monsterTypeToString(int protobufEnum, Context ctx) {
        if(monType == null) {
            monType = new HashMap<>();
            monType.put(MonsterData.INVALID_MONSTER_TYPE, ctx.getString(R.string.mobs_type_invalid));
            monType.put(MonsterData.ABERRATION, ctx.getString(R.string.mobs_type_aberration));
            monType.put(MonsterData.ANIMAL, ctx.getString(R.string.mobs_type_animal));
            monType.put(MonsterData.CONSTRUCT, ctx.getString(R.string.mobs_type_construct));
            monType.put(MonsterData.DRAGON, ctx.getString(R.string.mobs_type_dragon));
            monType.put(MonsterData.FEY, ctx.getString(R.string.mobs_type_fey));
            monType.put(MonsterData.HUMANOID, ctx.getString(R.string.mobs_type_humanoid));
            monType.put(MonsterData.MAGICAL_BEAST, ctx.getString(R.string.mobs_type_magicalBeast));
            monType.put(MonsterData.MONSTROUS_HUMANOID, ctx.getString(R.string.mobs_type_monstrousHumanoid));
            monType.put(MonsterData.OOZE, ctx.getString(R.string.mobs_type_ooze));
            monType.put(MonsterData.OUTSIDER, ctx.getString(R.string.mobs_type_outsider));
            monType.put(MonsterData.PLANT, ctx.getString(R.string.mobs_type_plant));
            monType.put(MonsterData.UNDEAD, ctx.getString(R.string.mobs_type_undead));
            monType.put(MonsterData.VERMIN, ctx.getString(R.string.mobs_type_vermin));
            monType.put(MonsterData.SUB_ACID, ctx.getString(R.string.mobs_subType_acid));
            monType.put(MonsterData.SUB_ADLET, ctx.getString(R.string.mobs_subType_adlet));
            monType.put(MonsterData.SUB_AEON, ctx.getString(R.string.mobs_subType_aeon));
            monType.put(MonsterData.SUB_AGATHION, ctx.getString(R.string.mobs_subType_agathion));
            monType.put(MonsterData.SUB_AIR, ctx.getString(R.string.mobs_subType_air));
            monType.put(MonsterData.SUB_AMPHIBIOUS, ctx.getString(R.string.mobs_subType_amphibious));
            monType.put(MonsterData.SUB_ANGEL, ctx.getString(R.string.mobs_subType_angel));
            monType.put(MonsterData.SUB_AQUATIC, ctx.getString(R.string.mobs_subType_aquatic));
            monType.put(MonsterData.SUB_ARCHON, ctx.getString(R.string.mobs_subType_archon));
            monType.put(MonsterData.SUB_ASURA, ctx.getString(R.string.mobs_subType_asura));
            monType.put(MonsterData.SUB_AZATA, ctx.getString(R.string.mobs_subType_azata));
            monType.put(MonsterData.SUB_BEHEMOTH, ctx.getString(R.string.mobs_subType_behemoth));
            monType.put(MonsterData.SUB_BOGGARD, ctx.getString(R.string.mobs_subType_boggard));
            monType.put(MonsterData.SUB_CATFOLK, ctx.getString(R.string.mobs_subType_catfolk));
            monType.put(MonsterData.SUB_CHAOTIC, ctx.getString(R.string.mobs_subType_chaotic));
            monType.put(MonsterData.SUB_CHARAU__KA, ctx.getString(R.string.mobs_subType_charauKa));
            monType.put(MonsterData.SUB_CLOCKWORK, ctx.getString(R.string.mobs_subType_clockwork));
            monType.put(MonsterData.SUB_COLD, ctx.getString(R.string.mobs_subType_cold));
            monType.put(MonsterData.SUB_COLOSSUS, ctx.getString(R.string.mobs_subType_colossus));
            monType.put(MonsterData.SUB_DAEMON, ctx.getString(R.string.mobs_subType_daemon));
            monType.put(MonsterData.SUB_DARK_FOLK, ctx.getString(R.string.mobs_subType_darkFolk));
            monType.put(MonsterData.SUB_DEEP_ONE, ctx.getString(R.string.mobs_subType_deepOne));
            monType.put(MonsterData.SUB_DEMODAND, ctx.getString(R.string.mobs_subType_demodand));
            monType.put(MonsterData.SUB_DEMON, ctx.getString(R.string.mobs_subType_demon));
            monType.put(MonsterData.SUB_DERRO, ctx.getString(R.string.mobs_subType_derro));
            monType.put(MonsterData.SUB_DEVIL, ctx.getString(R.string.mobs_subType_devil));
            monType.put(MonsterData.SUB_DIV, ctx.getString(R.string.mobs_subType_div));
            monType.put(MonsterData.SUB_DWARF, ctx.getString(R.string.mobs_subType_dwarf));
            monType.put(MonsterData.SUB_EARTH, ctx.getString(R.string.mobs_subType_earth));
            monType.put(MonsterData.SUB_ELECTRICITY, ctx.getString(R.string.mobs_subType_electricity));
            monType.put(MonsterData.SUB_ELEMENTAL, ctx.getString(R.string.mobs_subType_elemental));
            monType.put(MonsterData.SUB_ELF, ctx.getString(R.string.mobs_subType_elf));
            monType.put(MonsterData.SUB_EVIL, ctx.getString(R.string.mobs_subType_evil));
            monType.put(MonsterData.SUB_EXTRAPLANAR, ctx.getString(R.string.mobs_subType_extraplanar));
            monType.put(MonsterData.SUB_FEYBLOOD, ctx.getString(R.string.mobs_subType_feyblood));
            monType.put(MonsterData.SUB_FIRE, ctx.getString(R.string.mobs_subType_fire));
            monType.put(MonsterData.SUB_GIANT, ctx.getString(R.string.mobs_subType_giant));
            monType.put(MonsterData.SUB_GNOLL, ctx.getString(R.string.mobs_subType_gnoll));
            monType.put(MonsterData.SUB_GNOME, ctx.getString(R.string.mobs_subType_gnome));
            monType.put(MonsterData.SUB_GOBLIN, ctx.getString(R.string.mobs_subType_goblin));
            monType.put(MonsterData.SUB_GOBLINOID, ctx.getString(R.string.mobs_subType_goblinoid));
            monType.put(MonsterData.SUB_GODSPAWN, ctx.getString(R.string.mobs_subType_godspawn));
            monType.put(MonsterData.SUB_GOOD, ctx.getString(R.string.mobs_subType_good));
            monType.put(MonsterData.SUB_GRAVITY, ctx.getString(R.string.mobs_subType_gravity));
            monType.put(MonsterData.SUB_GREAT_OLD_ONE, ctx.getString(R.string.mobs_subType_greatOldOne));
            monType.put(MonsterData.SUB_HALFLING, ctx.getString(R.string.mobs_subType_halfling));
            monType.put(MonsterData.SUB_HERALD, ctx.getString(R.string.mobs_subType_herald));
            monType.put(MonsterData.SUB_HORDE, ctx.getString(R.string.mobs_subType_horde));
            monType.put(MonsterData.SUB_HUMAN, ctx.getString(R.string.mobs_subType_human));
            monType.put(MonsterData.SUB_HUMANOID, ctx.getString(R.string.mobs_subType_humanoid));
            monType.put(MonsterData.SUB_INCORPOREAL, ctx.getString(R.string.mobs_subType_incorporeal));
            monType.put(MonsterData.SUB_INEVITABLE, ctx.getString(R.string.mobs_subType_inevitable));
            monType.put(MonsterData.SUB_KAIJU, ctx.getString(R.string.mobs_subType_kaiju));
            monType.put(MonsterData.SUB_KAMI, ctx.getString(R.string.mobs_subType_kami));
            monType.put(MonsterData.SUB_KASATHA, ctx.getString(R.string.mobs_subType_kasatha));
            monType.put(MonsterData.SUB_KITSUNE, ctx.getString(R.string.mobs_subType_kitsune));
            monType.put(MonsterData.SUB_KUAH__LIJ, ctx.getString(R.string.mobs_subType_kuahLij));
            monType.put(MonsterData.SUB_KYTON, ctx.getString(R.string.mobs_subType_kyton));
            monType.put(MonsterData.SUB_LAWFUL, ctx.getString(R.string.mobs_subType_lawful));
            monType.put(MonsterData.SUB_LESHY, ctx.getString(R.string.mobs_subType_leshy));
            monType.put(MonsterData.SUB_MYTHIC, ctx.getString(R.string.mobs_subType_mythic));
            monType.put(MonsterData.SUB_NATIVE, ctx.getString(R.string.mobs_subType_native));
            monType.put(MonsterData.SUB_NIGHTSHADE, ctx.getString(R.string.mobs_subType_nightshade));
            monType.put(MonsterData.SUB_OGREN, ctx.getString(R.string.mobs_subType_ogren));
            monType.put(MonsterData.SUB_OGRILLON, ctx.getString(R.string.mobs_subType_ogrillon));
            monType.put(MonsterData.SUB_ONI, ctx.getString(R.string.mobs_subType_oni));
            monType.put(MonsterData.SUB_ORC, ctx.getString(R.string.mobs_subType_orc));
            monType.put(MonsterData.SUB_PROTEAN, ctx.getString(R.string.mobs_subType_protean));
            monType.put(MonsterData.SUB_PSYCHOPOMP, ctx.getString(R.string.mobs_subType_psychopomp));
            monType.put(MonsterData.SUB_QLIPPOTH, ctx.getString(R.string.mobs_subType_qlippoth));
            monType.put(MonsterData.SUB_RAKSHASA, ctx.getString(R.string.mobs_subType_rakshasa));
            monType.put(MonsterData.SUB_RATFOLK, ctx.getString(R.string.mobs_subType_ratfolk));
            monType.put(MonsterData.SUB_REPTILIAN, ctx.getString(R.string.mobs_subType_reptilian));
            monType.put(MonsterData.SUB_ROBOT, ctx.getString(R.string.mobs_subType_robot));
            monType.put(MonsterData.SUB_SAMSARAN, ctx.getString(R.string.mobs_subType_samsaran));
            monType.put(MonsterData.SUB_SASQUATCH, ctx.getString(R.string.mobs_subType_sasquatch));
            monType.put(MonsterData.SUB_SHAPECHANGER, ctx.getString(R.string.mobs_subType_shapechanger));
            monType.put(MonsterData.SUB_SKULK, ctx.getString(R.string.mobs_subType_skulk));
            monType.put(MonsterData.SUB_STORMWARDEN, ctx.getString(R.string.mobs_subType_stormwarden));
            monType.put(MonsterData.SUB_SWARM, ctx.getString(R.string.mobs_subType_swarm));
            monType.put(MonsterData.SUB_TABAXI, ctx.getString(R.string.mobs_subType_tabaxi));
            monType.put(MonsterData.SUB_TENGU, ctx.getString(R.string.mobs_subType_tengu));
            monType.put(MonsterData.SUB_TIME, ctx.getString(R.string.mobs_subType_time));
            monType.put(MonsterData.SUB_TROOP, ctx.getString(R.string.mobs_subType_troop));
            monType.put(MonsterData.SUB_UDAEUS, ctx.getString(R.string.mobs_subType_udaeus));
            monType.put(MonsterData.SUB_UNBREATHING, ctx.getString(R.string.mobs_subType_unbreathing));
            monType.put(MonsterData.SUB_VANARA, ctx.getString(R.string.mobs_subType_vanara));
            monType.put(MonsterData.SUB_VAPOR, ctx.getString(R.string.mobs_subType_vapor));
            monType.put(MonsterData.SUB_VISHKANYA, ctx.getString(R.string.mobs_subType_vishkanya));
            monType.put(MonsterData.SUB_WATER, ctx.getString(R.string.mobs_subType_water));
            monType.put(MonsterData.SUB_WAYANG, ctx.getString(R.string.mobs_subType_wayang));
            monType.put(MonsterData.SUB_FUNGUS, ctx.getString(R.string.mobs_subType_fungus));
            monType.put(MonsterData.SUB_PSIONIC, ctx.getString(R.string.mobs_subType_psionic));
        }
        final String match = monType.get(protobufEnum);
        return match == null? monType.get(MonsterData.INVALID_MONSTER_TYPE) : match;
    }
    
    
    private static HashMap<Integer, String> monRace, monSize, monType;
}
